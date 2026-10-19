/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.featdet_gcxgc;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.IonMobilogramTimeSeriesFactory;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.FeatureDataType;
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataTypeUtils;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.exceptions.MissingMassListException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Expands GCxGC chromatograms built along the 2D RT spectra into the first dimension. Each
 * chromatogram is extracted from the single scans of all modulations within its 2D RT scan range
 * and becomes a 2D feature (internally an {@link IonMobilogramTimeSeries}: modulation = frame, 2D
 * RT = mobility) that is resolved in the retention time dimension afterwards.
 */
class GcxGc2DFeatureBuilderTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(GcxGc2DFeatureBuilderTask.class.getName());
  private static final String SUFFIX = " 2D";

  private final @NotNull ParameterSet parameters;
  private final @NotNull ModularFeatureList flist;
  private final @NotNull MZmineProject project;
  private final @NotNull MZTolerance mzTolerance;
  private final @NotNull OriginalFeatureListOption handleOriginal;
  private final AtomicInteger processedModulations = new AtomicInteger(0);
  private int totalModulations = 1;
  private int createdFeatures = 0;
  private int totalFeatures = 1;
  // m/z tolerance range of each chromatogram by row index
  private List<Range<Double>> mzRanges = List.of();

  GcxGc2DFeatureBuilderTask(@Nullable final MemoryMapStorage storage,
      @NotNull final ParameterSet parameters, @NotNull final ModularFeatureList flist,
      @NotNull final MZmineProject project, @NotNull final Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.parameters = parameters;
    this.flist = flist;
    this.project = project;
    mzTolerance = parameters.getValue(GcxGc2DFeatureBuilderParameters.MZ_TOLERANCE);
    handleOriginal = parameters.getValue(GcxGc2DFeatureBuilderParameters.HANDLE_ORIGINAL);
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    final RawDataFile file = flist.getNumberOfRawDataFiles() == 1 ? flist.getRawDataFile(0) : null;
    final GCxGCRawDataFile gcxgc = GcxGcUtils.asGcxGcFile(file);
    if (gcxgc == null || !GcxGcUtils.isGcxGc(file)) {
      error("The GCxGC 2D feature builder requires a feature list of one GCxGC file: "
          + flist.getName());
      return;
    }

    // the chromatograms with their 2D RT scan index range
    final List<ModularFeatureListRow> rows = new ArrayList<>();
    for (final FeatureListRow row : flist.getRows()) {
      final ModularFeature feature = (ModularFeature) row.getFeature(file);
      if (feature == null) {
        continue;
      }
      if (!GcxGcUtils.isSecondDimensionChromatogram(feature)) {
        error("""
            Feature list %s contains features that were not built along the GCxGC 2D RT spectra. \
            Build chromatograms with the chromatogram builder on a GCxGC file after the "%s" \
            module and resolve them in the 2D RT dimension first.""".formatted(flist.getName(),
            GcxGcSecondDimensionSpectraModule.NAME));
        return;
      }
      rows.add((ModularFeatureListRow) row);
    }

    // the same modulations as summed into the 2D RT spectra
    final Range<Float> rtRange = gcxgc.getSecondDimensionSpectraRTRange();
    final List<Frame> modulations = gcxgc.getFrames().stream().filter(m -> m.getMSLevel() == 1)
        .filter(m -> rtRange == null || rtRange.contains(m.getRetentionTime()))
        .map(m -> (Frame) m).toList();
    final int numScanIndices = modulations.stream().mapToInt(Frame::getNumberOfMobilityScans)
        .max().orElse(0);
    totalModulations = Math.max(1, modulations.size());
    totalFeatures = Math.max(1, rows.size());
    mzRanges = rows.stream().map(row -> mzTolerance.getToleranceRange(row.getAverageMZ()))
        .toList();

    // per 2D scan index the chromatograms covering it, sorted by m/z
    final List<List<Integer>> chromatogramsPerScanIndex = indexChromatograms(rows, file,
        numScanIndices);

    // decision: one pass over all single scans, parallel in blocks of modulations, so each signal
    // is read once although the chromatograms span the whole first dimension
    final int numBlocks = Math.max(1,
        Math.min(modulations.size(), Runtime.getRuntime().availableProcessors() * 2));
    final List<List<Frame>> blocks = Lists.partition(modulations,
        Math.max(1, (int) Math.ceil(modulations.size() / (double) numBlocks)));
    final List<ModulationTrace[]> blockTraces;
    try {
      blockTraces = blocks.parallelStream()
          .map(block -> extract(gcxgc, block, rows, chromatogramsPerScanIndex)).toList();
    } catch (MissingMassListException e) {
      error("""
          The single scans of %s have no detected signals (mass lists). Run mass detection on \
          all scan types before.""".formatted(file.getName()));
      return;
    }
    if (isCanceled()) {
      return;
    }

    final ModularFeatureList newFlist = new ModularFeatureList(flist.getName() + SUFFIX,
        getMemoryMapStorage(), file);
    // do not transfer types add them later
    FeatureListUtils.transferMetadata(flist, newFlist, false);
    DataTypeUtils.addDefaultChromatographicTypeColumns(newFlist);
    DataTypeUtils.addDefaultGcxGcTypeColumns(newFlist);
    // the 2D features are resolved along the modulations in the retention time dimension
    newFlist.setSelectedScans(file, modulations);

    final BinningMobilogramDataAccess binning = EfficientDataAccess.of(gcxgc,
        BinningMobilogramDataAccess.getRecommendedBinWidth(gcxgc));
    for (int i = 0; i < rows.size(); i++) {
      if (isCanceled()) {
        return;
      }
      final ModulationTrace trace = combine(blockTraces, i);
      createdFeatures++;
      // assumption: like the ion mobility expansion, a single signal is no 2D feature
      if (trace == null || trace.getNumberOfScans() < 2) {
        continue;
      }
      final List<Frame> traceModulations = new ArrayList<>();
      final List<IonMobilitySeries> series = trace.toSeriesPerModulation(traceModulations);
      final IonMobilogramTimeSeries expanded = IonMobilogramTimeSeriesFactory.of(
          getMemoryMapStorage(), series, binning, traceModulations);

      final ModularFeatureListRow oldRow = rows.get(i);
      final ModularFeatureListRow row = new ModularFeatureListRow(newFlist, oldRow, false);
      final ModularFeature f = new ModularFeature(newFlist,
          (ModularFeature) oldRow.getFeature(file));
      f.set(FeatureDataType.class, expanded);
      FeatureDataUtils.recalculateIonSeriesDependingTypes(f);
      row.addFeature(file, f);
      newFlist.addRow(row);
    }
    logger.fine(() -> "Built %d GCxGC 2D features from %d chromatograms of %s".formatted(
        newFlist.getNumberOfRows(), rows.size(), flist.getName()));

    // explicitly don't renumber, IDs are kept from the old flist.
    FeatureListUtils.sortByDefault(newFlist, false);
    newFlist.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(GcxGc2DFeatureBuilderModule.class, parameters,
            getModuleCallDate()));
    handleOriginal.reflectNewFeatureListToProject(SUFFIX, project, newFlist, flist);
    setStatus(TaskStatus.FINISHED);
  }

  /**
   * @return per 2D scan index the indices of the rows whose chromatogram covers it, sorted by the
   * lower m/z bound
   */
  private @NotNull List<List<Integer>> indexChromatograms(
      @NotNull final List<ModularFeatureListRow> rows, @NotNull final RawDataFile file,
      final int numScanIndices) {
    final List<List<Integer>> perScanIndex = new ArrayList<>(numScanIndices);
    for (int i = 0; i < numScanIndices; i++) {
      perScanIndex.add(new ArrayList<>());
    }
    for (int i = 0; i < rows.size(); i++) {
      final IonTimeSeries<? extends Scan> series = rows.get(i).getFeature(file).getFeatureData();
      // the scan number of a 2D RT spectrum is its scan index within the modulations
      final int first = Math.max(0, series.getSpectrum(0).getScanNumber());
      final int last = Math.min(numScanIndices - 1,
          series.getSpectrum(series.getNumberOfValues() - 1).getScanNumber());
      for (int scanIndex = first; scanIndex <= last; scanIndex++) {
        perScanIndex.get(scanIndex).add(i);
      }
    }
    final Comparator<Integer> byLowerMz = Comparator.comparingDouble(
        i -> mzRanges.get(i).lowerEndpoint());
    perScanIndex.forEach(list -> list.sort(byLowerMz));
    return perScanIndex;
  }

  /**
   * Extracts the signals of all chromatograms from the single scans of a block of modulations
   *
   * @return the traces of this block by row index, null for rows without signals
   */
  private @NotNull ModulationTrace[] extract(@NotNull final GCxGCRawDataFile file,
      @NotNull final List<Frame> block, @NotNull final List<ModularFeatureListRow> rows,
      @NotNull final List<List<Integer>> chromatogramsPerScanIndex) {
    final ModulationTrace[] traces = new ModulationTrace[rows.size()];
    final MobilityScanDataAccess access = new MobilityScanDataAccess(file,
        MobilityScanDataType.MASS_LIST, block);
    for (int f = 0; f < access.getNumberOfScans(); f++) {
      if (isCanceled()) {
        return traces;
      }
      access.nextFrame();
      while (access.hasNextMobilityScan()) {
        final MobilityScan scan = access.nextMobilityScan();
        final int scanIndex = scan.getMobilityScanNumber();
        if (scanIndex >= chromatogramsPerScanIndex.size() || access.getNumberOfDataPoints() == 0) {
          continue;
        }
        final List<Integer> candidates = chromatogramsPerScanIndex.get(scanIndex);
        if (candidates.isEmpty()) {
          continue;
        }
        offerSignals(access, scan, candidates, rows, traces);
      }
      processedModulations.incrementAndGet();
    }
    return traces;
  }

  /**
   * Offers the m/z sorted signals of the current scan to the m/z sorted candidate chromatograms
   */
  private void offerSignals(@NotNull final MobilityScanDataAccess access,
      @NotNull final MobilityScan scan, @NotNull final List<Integer> candidates,
      @NotNull final List<ModularFeatureListRow> rows, @NotNull final ModulationTrace[] traces) {
    // the first candidate whose m/z range may still contain the next signals
    int firstCandidate = 0;
    for (int dp = 0; dp < access.getNumberOfDataPoints() && firstCandidate < candidates.size();
        dp++) {
      final double mz = access.getMzValue(dp);
      for (int c = firstCandidate; c < candidates.size(); c++) {
        final int rowIndex = candidates.get(c);
        final Range<Double> range = mzRanges.get(rowIndex);
        if (range.lowerEndpoint() > mz) {
          // all further candidates start at a higher m/z
          break;
        }
        if (range.upperEndpoint() < mz) {
          if (c == firstCandidate) {
            firstCandidate++;
          }
          continue;
        }
        ModulationTrace trace = traces[rowIndex];
        if (trace == null) {
          trace = new ModulationTrace(range, rows.get(rowIndex).getAverageMZ());
          traces[rowIndex] = trace;
        }
        trace.offer(scan, mz, access.getIntensityValue(dp));
      }
    }
  }

  /**
   * @return the trace of a row over all blocks in acquisition order or null without signals
   */
  private static @Nullable ModulationTrace combine(@NotNull final List<ModulationTrace[]> blocks,
      final int rowIndex) {
    ModulationTrace combined = null;
    for (final ModulationTrace[] block : blocks) {
      final ModulationTrace trace = block[rowIndex];
      if (trace == null) {
        continue;
      }
      if (combined == null) {
        combined = trace;
      } else {
        combined.addAll(trace);
      }
    }
    return combined;
  }

  @Override
  public String getTaskDescription() {
    return "Building GCxGC 2D features of " + flist.getName();
  }

  @Override
  public double getFinishedPercentage() {
    return 0.8 * processedModulations.get() / totalModulations
        + 0.2 * createdFeatures / (double) totalFeatures;
  }
}
