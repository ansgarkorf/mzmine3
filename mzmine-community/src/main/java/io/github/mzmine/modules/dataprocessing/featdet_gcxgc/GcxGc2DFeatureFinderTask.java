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
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.IonMobilogramTimeSeriesFactory;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonMobilitySeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.annotationpriority.AnnotationSummarySortConfig;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionUtils;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskPriority;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataTypeUtils;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.exceptions.MissingMassListException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the 2D features of one GCxGC file in its ion images, see {@link IonImageWatershed}. The
 * signals are processed in m/z blocks to limit the memory: each block reads the detected signals
 * of all single scans once, groups them into m/z channels and segments the ion image of each
 * channel. A 2D feature is stored like an ion mobility feature (one series of single scans per
 * modulation), so that all GCxGC modules downstream can use it.
 */
class GcxGc2DFeatureFinderTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(GcxGc2DFeatureFinderTask.class.getName());

  private static final double HISTOGRAM_BIN_WIDTH = 0.01;
  // assumption: about 20 bytes per signal, a block holds a few hundred MB
  private static final int MAX_SIGNALS_PER_BLOCK = 20_000_000;
  // decision: a block border is placed at the least populated m/z bin within this many bins
  private static final int BLOCK_BORDER_SEARCH_BINS = 50;

  private final @NotNull MZmineProject project;
  private final @NotNull RawDataFile file;
  private final @NotNull ParameterSet parameters;
  private final @Nullable Range<Float> rtRange;
  private final @NotNull MZTolerance mzTolerance;
  private final double minHeight;
  private final double minRatio;
  private final double edgeLevel;
  private final double min2DShapeQuality;
  private final @Nullable ParameterSet templateDeconvolution;
  private final int backgroundWindow;
  private final int minModulations;
  private final int min2DDataPoints;
  private final @NotNull String suffix;

  // the parallel stages run on the task pool of mzmine
  private final GcxGcJobs jobs = new TaskPoolGcxGcJobs(this::isCanceled);
  // progress of the current stage, weighted into the total progress
  private final AtomicLong stageDone = new AtomicLong(0);
  private volatile long stageTotal = 1;
  private volatile double stageStart = 0;
  private volatile double stageWeight = 0;
  // size of the 2D RT axis of the ion images
  private int scansPerModulation = 1;
  private volatile @NotNull String description;

  GcxGc2DFeatureFinderTask(@NotNull final MZmineProject project, @NotNull final RawDataFile file,
      @NotNull final ParameterSet parameters, @Nullable final MemoryMapStorage storage,
      @NotNull final Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.file = file;
    this.parameters = parameters;
    final Range<Double> range = parameters.getEmbeddedParameterValueIfSelectedOrElse(
        GcxGc2DFeatureFinderParameters.RT_RANGE, null);
    rtRange = range == null ? null : RangeUtils.toFloatRange(range);
    mzTolerance = parameters.getValue(GcxGc2DFeatureFinderParameters.MZ_TOLERANCE);
    minHeight = parameters.getValue(GcxGc2DFeatureFinderParameters.MIN_HEIGHT);
    minRatio = parameters.getValue(GcxGc2DFeatureFinderParameters.MIN_RATIO);
    edgeLevel = parameters.getValue(GcxGc2DFeatureFinderParameters.EDGE_LEVEL);
    min2DShapeQuality = parameters.getValue(GcxGc2DFeatureFinderParameters.MIN_2D_SHAPE_QUALITY);
    templateDeconvolution = parameters.getValue(
        GcxGc2DFeatureFinderParameters.TEMPLATE_DECONVOLUTION) ? parameters.getParameter(
        GcxGc2DFeatureFinderParameters.TEMPLATE_DECONVOLUTION).getEmbeddedParameters() : null;
    backgroundWindow = parameters.getValue(GcxGc2DFeatureFinderParameters.BACKGROUND_WINDOW);
    minModulations = parameters.getValue(GcxGc2DFeatureFinderParameters.MIN_MODULATIONS);
    min2DDataPoints = parameters.getValue(GcxGc2DFeatureFinderParameters.MIN_2D_DATA_POINTS);
    suffix = parameters.getValue(GcxGc2DFeatureFinderParameters.SUFFIX);
    description = "Finding GCxGC 2D features in " + file.getName();
  }

  @Override
  public TaskPriority getTaskPriority() {
    // the main task waits for its sub tasks and must not block the task controller
    return TaskPriority.HIGH;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    try {
      process();
    } catch (final CancellationException e) {
      if (!isCanceled()) {
        error("GCxGC 2D feature finder failed on " + file.getName() + ": " + e.getMessage());
      }
    }
  }

  private void process() {
    final GCxGCRawDataFile gcxgc = GcxGcUtils.asGcxGcFile(file);
    if (gcxgc == null || !GcxGcUtils.isGcxGc(file)) {
      error(file.getName() + " is no GCxGC data file.");
      return;
    }
    final List<Frame> modulations = gcxgc.getFrames().stream().filter(m -> m.getMSLevel() == 1)
        .filter(m -> rtRange == null || rtRange.contains(m.getRetentionTime()))
        .map(Frame.class::cast).toList();
    if (modulations.isEmpty()) {
      error("No modulations of %s within the retention time range %s.".formatted(file.getName(),
          rtRange));
      return;
    }
    scansPerModulation = Math.max(1,
        modulations.stream().mapToInt(Frame::getNumberOfMobilityScans).max().orElse(1));

    // stages: m/z histogram, detection per m/z block, template deconvolution, feature creation
    final double deconvolutionWeight = templateDeconvolution != null ? 0.25 : 0;
    final double detectionWeight = 0.85 - deconvolutionWeight;
    startStage(0, 0.05, modulations.size());
    final long[] histogram;
    try {
      histogram = mzHistogram(gcxgc, modulations);
    } catch (MissingMassListException e) {
      error("""
          The single scans of %s have no detected signals (mass lists). Run mass detection on \
          all scan types before.""".formatted(file.getName()));
      return;
    }
    final List<Range<Double>> blocks = mzBlocks(histogram);

    final ModularFeatureList flist = new ModularFeatureList(file.getName() + " " + suffix,
        getMemoryMapStorage(), file);
    DataTypeUtils.addDefaultChromatographicTypeColumns(flist);
    DataTypeUtils.addDefaultGcxGcTypeColumns(flist);
    flist.setSelectedScans(file, modulations);

    // 1. candidate 2D features of all m/z channels
    final List<IonImageFeature> candidates = new ArrayList<>();
    int numChannels = 0;
    long numSignals = 0;
    for (int b = 0; b < blocks.size(); b++) {
      final Range<Double> block = blocks.get(b);
      description = "Finding GCxGC 2D features in %s, m/z block %d/%d".formatted(file.getName(),
          b + 1, blocks.size());
      final double blockWeight = detectionWeight / blocks.size();
      final double blockStart = 0.05 + b * blockWeight;
      startStage(blockStart, blockWeight / 2, modulations.size());
      final IonImagePoints points = collectSignals(gcxgc, modulations, block);
      if (isCanceled()) {
        return;
      }
      final List<IntArrayList> channels = buildChannels(points);
      startStage(blockStart + blockWeight / 2, blockWeight / 2, channels.size());
      final int channelOffset = numChannels;
      numChannels += channels.size();
      numSignals += points.size();
      // background corrected intensities for the detection, each channel writes its signals
      final float[] corrected = new float[points.size()];
      // decision: channels are independent; they are packed into balanced jobs, because single
      // channels (e.g., m/z 73 of TMS derivatives) hold far more signals than others
      final List<Callable<List<IonImageFeature>>> channelJobs = new ArrayList<>();
      for (final IntArrayList bin : balancedBins(channels, TaskPoolGcxGcJobs.threads() * 4)) {
        channelJobs.add(() -> {
          final List<IonImageFeature> found = new ArrayList<>();
          for (int i = 0; i < bin.size() && !isCanceled(); i++) {
            final int c = bin.getInt(i);
            stageDone.incrementAndGet();
            found.addAll(detect(points, channels.get(c), corrected, channelOffset + c,
                modulations.size()));
          }
          return found;
        });
      }
      jobs.run(description, channelJobs).forEach(candidates::addAll);
      if (isCanceled()) {
        return;
      }
    }
    // decision: a fixed order, independent of the number of threads
    candidates.sort(Comparator.comparingInt(IonImageFeature::channel)
        .thenComparingInt(IonImageFeature::apexModulation)
        .thenComparingInt(IonImageFeature::apexScan));
    final int channelCount = numChannels;
    final long signalCount = numSignals;
    logger.info(() -> "Found %d GCxGC 2D features in %d m/z channels (%d signals, %d m/z blocks) of %s"
        .formatted(candidates.size(), channelCount, signalCount, blocks.size(), file.getName()));

    // 2. optional template deconvolution into compounds
    final List<List<IonImageFeature>> compounds;
    final List<IonImageFeature> toCreate;
    if (templateDeconvolution != null) {
      description = "Deconvoluting GCxGC compounds in " + file.getName();
      // every candidate is visited twice: model peaks, then the template fit
      startStage(0.05 + detectionWeight, deconvolutionWeight, 2L * candidates.size());
      compounds = deconvolute(modulations, candidates);
      toCreate = compounds.stream().flatMap(List::stream).toList();
      logger.info(() -> "Deconvoluted %d compounds with %d ions from %d GCxGC 2D features of %s"
          .formatted(compounds.size(), toCreate.size(), candidates.size(), file.getName()));
    } else {
      compounds = List.of();
      toCreate = candidates;
    }
    if (isCanceled()) {
      return;
    }

    // 3. mzmine features
    description = "Creating GCxGC 2D features of " + file.getName();
    startStage(0.9, 0.1, 2L * toCreate.size());
    final List<Callable<List<IonMobilogramTimeSeries>>> dataJobs = new ArrayList<>();
    for (final List<IonImageFeature> part : Lists.partition(toCreate,
        Math.max(1, toCreate.size() / (TaskPoolGcxGcJobs.threads() * 4) + 1))) {
      dataJobs.add(() -> {
        // the binning keeps state, one per job
        final BinningMobilogramDataAccess binning = EfficientDataAccess.of(gcxgc,
            BinningMobilogramDataAccess.getRecommendedBinWidth(gcxgc));
        final List<IonMobilogramTimeSeries> series = new ArrayList<>(part.size());
        for (final IonImageFeature feature : part) {
          if (isCanceled()) {
            break;
          }
          stageDone.incrementAndGet();
          series.add(toFeatureData(feature, modulations, binning));
        }
        return series;
      });
    }
    final List<IonMobilogramTimeSeries> data = new ArrayList<>(toCreate.size());
    jobs.run(description, dataJobs).forEach(data::addAll);
    if (isCanceled()) {
      return;
    }
    final Map<IonImageFeature, ModularFeature> created = new IdentityHashMap<>();
    int id = 1;
    for (int i = 0; i < toCreate.size(); i++) {
      final ModularFeature feature = new ModularFeature(flist, file, data.get(i),
          FeatureStatus.DETECTED);
      flist.addRow(new ModularFeatureListRow(flist, id++, feature));
      created.put(toCreate.get(i), feature);
      stageDone.incrementAndGet();
    }
    FeatureListUtils.sortByDefault(flist, true);
    file.getAppliedMethods().forEach(m -> flist.getAppliedMethods().add(m));
    flist.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(GcxGc2DFeatureFinderModule.class, parameters,
            getModuleCallDate()));
    if (templateDeconvolution == null) {
      project.addFeatureList(flist);
    } else {
      // the deconvoluted list with compounds replaces the 2D feature list
      final List<List<ModularFeature>> groups = compounds.stream()
          .map(ions -> ions.stream().map(created::get).toList()).toList();
      final ModularFeatureList deconvoluted = SpectralDeconvolutionUtils.createDeconvolutedFeatureList(
          flist, groups, null,
          templateDeconvolution.getValue(GcxGcTemplateDeconvolutionParameters.SUFFIX),
          getMemoryMapStorage());
      // no m/z score in GC-EI without a precursor, like the GC-EI spectral deconvolution
      final AnnotationSummarySortConfig config = deconvoluted.getAnnotationSortConfig();
      deconvoluted.setAnnotationSortConfig(
          config.withCombinedScoreWeights(config.combinedScoreWeights().withMz(0d)));
      project.addFeatureList(deconvoluted);
    }
    setStatus(TaskStatus.FINISHED);
  }

  /**
   * Template deconvolution with the tolerances converted to modulations and scans
   */
  private @NotNull List<List<IonImageFeature>> deconvolute(@NotNull final List<Frame> modulations,
      @NotNull final List<IonImageFeature> candidates) {
    assert templateDeconvolution != null;
    // modulation period and 2D scan interval in minutes
    final double period = modulations.size() < 2 ? 1 :
        (modulations.getLast().getRetentionTime() - modulations.getFirst().getRetentionTime()) / (
            modulations.size() - 1);
    final Frame first = modulations.getFirst();
    final int scans = first.getNumberOfMobilityScans();
    final double scanInterval = scans < 2 ? 1 : Math.abs(
        first.getMobilityForMobilityScanNumber(scans - 1) - first.getMobilityForMobilityScanNumber(
            0)) / (scans - 1);
    final int rtTolerance = (int) Math.round(
        templateDeconvolution.getValue(GcxGcTemplateDeconvolutionParameters.RT_TOLERANCE)
            .getToleranceInMinutes() / period);
    final int secondDimensionTolerance = (int) Math.round(templateDeconvolution.getValue(
            GcxGcTemplateDeconvolutionParameters.SECOND_DIMENSION_TOLERANCE).getToleranceInMinutes()
        / scanInterval);
    return new IonImageTemplateDeconvolution(scansPerModulation, rtTolerance,
        secondDimensionTolerance,
        templateDeconvolution.getValue(GcxGcTemplateDeconvolutionParameters.MIN_CORRELATION),
        templateDeconvolution.getValue(GcxGcTemplateDeconvolutionParameters.MIN_SIGNALS),
        min2DDataPoints, jobs).deconvolute(candidates, stageDone);
  }

  /**
   * @return the candidate 2D features of one m/z channel
   */
  private @NotNull List<IonImageFeature> detect(@NotNull final IonImagePoints points,
      @NotNull final IntArrayList channel, final float @NotNull [] corrected, final int channelId,
      final int numModulations) {
    IonImageBackground.subtract(points, channel, numModulations, backgroundWindow, corrected);
    final List<IonImageFeature> found = new ArrayList<>();
    for (final int[] region : IonImageWatershed.segment(points,
        sortedByIntensity(channel, corrected), corrected, scansPerModulation, minHeight, minRatio,
        edgeLevel)) {
      for (final int[] part : IonImageRegionSplitter.split(points, region, corrected, minRatio,
          minModulations, min2DDataPoints)) {
        final IonImageFeature candidate = toCandidate(points, part, corrected, channelId);
        if (candidate != null) {
          found.add(candidate);
        }
      }
    }
    return found;
  }

  /**
   * Longest processing time first: the largest channels are distributed first, each to the bin
   * with the fewest signals.
   *
   * @return channel indices per bin, bins in a fixed order
   */
  static @NotNull List<IntArrayList> balancedBins(@NotNull final List<IntArrayList> channels,
      final int numBins) {
    final int n = Math.max(1, Math.min(numBins, channels.size()));
    final List<IntArrayList> bins = new ArrayList<>(n);
    final long[] load = new long[n];
    for (int b = 0; b < n; b++) {
      bins.add(new IntArrayList());
    }
    final Integer[] order = new Integer[channels.size()];
    for (int c = 0; c < order.length; c++) {
      order[c] = c;
    }
    Arrays.sort(order, Comparator.comparingInt((Integer c) -> channels.get(c).size()).reversed()
        .thenComparingInt(c -> c));
    for (final int c : order) {
      int lightest = 0;
      for (int b = 1; b < n; b++) {
        if (load[b] < load[lightest]) {
          lightest = b;
        }
      }
      bins.get(lightest).add(c);
      load[lightest] += channels.get(c).size();
    }
    return bins;
  }

  /**
   * Runs a function on blocks of consecutive modulations in parallel
   *
   * @param function first modulation index and the data access of the block
   * @return the results in modulation order
   */
  private <T> @NotNull List<T> parallelOverModulations(@NotNull final GCxGCRawDataFile gcxgc,
      @NotNull final List<Frame> modulations,
      @NotNull final BiFunction<Integer, MobilityScanDataAccess, T> function) {
    final int chunkSize = Math.max(1,
        (int) Math.ceil(modulations.size() / (double) (TaskPoolGcxGcJobs.threads() * 2)));
    final List<List<Frame>> chunks = Lists.partition(modulations, chunkSize);
    final List<Callable<T>> chunkJobs = new ArrayList<>(chunks.size());
    for (int c = 0; c < chunks.size(); c++) {
      final int first = c * chunkSize;
      final List<Frame> chunk = chunks.get(c);
      chunkJobs.add(() -> function.apply(first,
          new MobilityScanDataAccess(gcxgc, MobilityScanDataType.MASS_LIST, chunk)));
    }
    return jobs.run(description, chunkJobs);
  }

  /**
   * @return number of signals per m/z bin of {@link #HISTOGRAM_BIN_WIDTH}
   */
  private long @NotNull [] mzHistogram(@NotNull final GCxGCRawDataFile gcxgc,
      @NotNull final List<Frame> modulations) {
    final List<long[]> histograms = parallelOverModulations(gcxgc, modulations,
        (_, access) -> {
          long[] counts = new long[1024];
          while (access.hasNextFrame() && !isCanceled()) {
            access.nextFrame();
            while (access.hasNextMobilityScan()) {
              access.nextMobilityScan();
              for (int i = 0; i < access.getNumberOfDataPoints(); i++) {
                final int bin = (int) (access.getMzValue(i) / HISTOGRAM_BIN_WIDTH);
                if (bin >= counts.length) {
                  counts = Arrays.copyOf(counts, Math.max(bin + 1, counts.length * 2));
                }
                counts[bin]++;
              }
            }
            stageDone.incrementAndGet();
          }
          return counts;
        });
    final int length = histograms.stream().mapToInt(h -> h.length).max().orElse(0);
    final long[] sum = new long[length];
    for (final long[] histogram : histograms) {
      for (int i = 0; i < histogram.length; i++) {
        sum[i] += histogram[i];
      }
    }
    return sum;
  }

  /**
   * @return m/z ranges [lower, upper) with at most about {@link #MAX_SIGNALS_PER_BLOCK} signals,
   * borders at sparsely populated m/z values
   */
  private @NotNull List<Range<Double>> mzBlocks(final long @NotNull [] histogram) {
    final List<Range<Double>> blocks = new ArrayList<>();
    int lowerBin = 0;
    long count = 0;
    for (int bin = 0; bin < histogram.length; bin++) {
      count += histogram[bin];
      if (count < MAX_SIGNALS_PER_BLOCK) {
        continue;
      }
      // place the border at the least populated bin before this one
      int border = bin;
      for (int b = Math.max(lowerBin + 1, bin - BLOCK_BORDER_SEARCH_BINS); b <= bin; b++) {
        if (histogram[b] < histogram[border]) {
          border = b;
        }
      }
      blocks.add(Range.closedOpen(lowerBin * HISTOGRAM_BIN_WIDTH, border * HISTOGRAM_BIN_WIDTH));
      lowerBin = border;
      count = 0;
      for (int b = border; b <= bin; b++) {
        count += histogram[b];
      }
    }
    blocks.add(Range.closedOpen(lowerBin * HISTOGRAM_BIN_WIDTH,
        (histogram.length + 1) * HISTOGRAM_BIN_WIDTH));
    return blocks;
  }

  /**
   * @return all signals of the single scans within the m/z block
   */
  private @NotNull IonImagePoints collectSignals(@NotNull final GCxGCRawDataFile gcxgc,
      @NotNull final List<Frame> modulations, @NotNull final Range<Double> block) {
    final List<IonImagePoints> parts = parallelOverModulations(gcxgc, modulations,
        (firstModulation, access) -> {
          final IonImagePoints points = new IonImagePoints(1024);
          int modulation = firstModulation;
          while (access.hasNextFrame() && !isCanceled()) {
            access.nextFrame();
            while (access.hasNextMobilityScan()) {
              final MobilityScan scan = access.nextMobilityScan();
              for (int i = 0; i < access.getNumberOfDataPoints(); i++) {
                final double mz = access.getMzValue(i);
                if (block.contains(mz)) {
                  points.add(mz, (float) access.getIntensityValue(i), modulation,
                      scan.getMobilityScanNumber());
                }
              }
            }
            modulation++;
            stageDone.incrementAndGet();
          }
          return points;
        });
    final IonImagePoints all = new IonImagePoints(
        parts.stream().mapToInt(IonImagePoints::size).sum());
    parts.forEach(all::addAll);
    return all;
  }

  /**
   * Groups the signals into non-overlapping m/z channels like the ADAP chromatogram builder: the
   * most intense signals start channels within the m/z tolerance, less intense signals join them.
   *
   * @return the signal indices of each channel, sorted by descending intensity
   */
  private @NotNull List<IntArrayList> buildChannels(@NotNull final IonImagePoints points) {
    final int n = points.size();
    // intensities are positive, their float bits sort like the values
    final long[] order = new long[n];
    for (int i = 0; i < n; i++) {
      order[i] = ((long) Float.floatToIntBits(points.intensity(i)) << 32) | i;
    }
    // decision: sequential sort, a parallel sort would use the common pool and ignore the thread
    // setting
    Arrays.sort(order);

    final RangeMap<Double, Integer> ranges = TreeRangeMap.create();
    final List<IntArrayList> channels = new ArrayList<>();
    for (int o = n - 1; o >= 0; o--) {
      final int point = (int) order[o];
      final double mz = points.mz(point);
      final Entry<Range<Double>, Integer> existing = ranges.getEntry(mz);
      if (existing != null) {
        channels.get(existing.getValue()).add(point);
        continue;
      }
      if (points.intensity(point) < minHeight) {
        continue;
      }
      // the new channel must not overlap existing channels
      final Range<Double> tolerance = mzTolerance.getToleranceRange(mz);
      final Entry<Range<Double>, Integer> below = ranges.getEntry(tolerance.lowerEndpoint());
      final Entry<Range<Double>, Integer> above = ranges.getEntry(tolerance.upperEndpoint());
      final double lower =
          below == null ? tolerance.lowerEndpoint() : below.getKey().upperEndpoint();
      final double upper =
          above == null ? tolerance.upperEndpoint() : above.getKey().lowerEndpoint();
      if (lower < upper) {
        ranges.put(Range.closedOpen(lower, upper), channels.size());
        final IntArrayList channel = new IntArrayList();
        channel.add(point);
        channels.add(channel);
      }
    }
    return channels;
  }

  /**
   * @return the signals of the channel with a corrected intensity above zero, sorted by descending
   * corrected intensity
   */
  private static @NotNull IntArrayList sortedByIntensity(@NotNull final IntArrayList channel,
      final float @NotNull [] corrected) {
    final long[] order = new long[channel.size()];
    int n = 0;
    for (int i = 0; i < channel.size(); i++) {
      final int point = channel.getInt(i);
      if (corrected[point] > 0) {
        // positive floats sort like their bits
        order[n++] = ((long) Float.floatToIntBits(corrected[point]) << 32) | point;
      }
    }
    Arrays.sort(order, 0, n);
    final IntArrayList sorted = new IntArrayList(n);
    for (int i = n - 1; i >= 0; i--) {
      sorted.add((int) order[i]);
    }
    return sorted;
  }

  /**
   * @return the candidate 2D feature of a region or null if it is too small, too low or no peak
   */
  private @Nullable IonImageFeature toCandidate(@NotNull final IonImagePoints points,
      final int @NotNull [] region, final float @NotNull [] corrected, final int channel) {
    if (isCanceled()) {
      return null;
    }
    final IonImageFeature candidate = createCandidate(points, region, corrected, channel);
    if (!isLargeEnough(candidate)) {
      return null;
    }
    // decision (user): in the 2D RT a feature must look like a chromatographic peak, the first
    // dimension has too few modulations for a shape requirement
    if (candidate.shapeQuality() >= min2DShapeQuality) {
      return candidate;
    }
    // decision (user): a region that cannot be split into peaks keeps its main peak instead of
    // being discarded
    final int[] main = IonImageRegionSplitter.mainPeak(points, region, corrected, minRatio);
    if (main.length == region.length) {
      return null;
    }
    final IonImageFeature mainPeak = createCandidate(points, main, corrected, channel);
    return isLargeEnough(mainPeak) && mainPeak.shapeQuality() >= min2DShapeQuality ? mainPeak
        : null;
  }

  /**
   * @return the signals of the region sorted by modulation and scan
   */
  private @NotNull IonImageFeature createCandidate(@NotNull final IonImagePoints points,
      final int @NotNull [] region, final float @NotNull [] corrected, final int channel) {
    final long[] sorted = new long[region.length];
    for (int k = 0; k < region.length; k++) {
      sorted[k] = (points.pixel(region[k], scansPerModulation) << 32) | k;
    }
    Arrays.sort(sorted);
    final int n = region.length;
    final int[] modulationIndices = new int[n];
    final int[] scans = new int[n];
    final double[] mzs = new double[n];
    final float[] intensities = new float[n];
    final float[] correctedIntensities = new float[n];
    for (int k = 0; k < n; k++) {
      final int point = region[(int) sorted[k]];
      modulationIndices[k] = points.modulation(point);
      scans[k] = points.scan(point);
      mzs[k] = points.mz(point);
      intensities[k] = points.intensity(point);
      correctedIntensities[k] = corrected[point];
    }
    return new IonImageFeature(channel, modulationIndices, scans, mzs, intensities,
        correctedIntensities);
  }

  /**
   * decision: the 2D peak in the apex modulation needs consecutive scans around the apex,
   * scattered noise signals of a modulation are no 2D peak
   *
   * @return true if the apex is high enough and the feature spans the minimum modulations and
   * consecutive 2D data points
   */
  private boolean isLargeEnough(@NotNull final IonImageFeature feature) {
    if (feature.size() == 0 || feature.height() < minHeight) {
      return false;
    }
    final IntOpenHashSet apexModulationScans = new IntOpenHashSet();
    int numModulations = 0;
    int lastModulation = -1;
    for (int i = 0; i < feature.size(); i++) {
      if (feature.modulation(i) != lastModulation) {
        numModulations++;
        lastModulation = feature.modulation(i);
      }
      if (feature.modulation(i) == feature.apexModulation()) {
        apexModulationScans.add(feature.scan(i));
      }
    }
    int consecutive = 1;
    for (int scan = feature.apexScan() - 1; apexModulationScans.contains(scan); scan--) {
      consecutive++;
    }
    for (int scan = feature.apexScan() + 1; apexModulationScans.contains(scan); scan++) {
      consecutive++;
    }
    return consecutive >= min2DDataPoints && numModulations >= minModulations;
  }

  /**
   * @return the feature data with one series of single scans per modulation, measured
   * intensities like gap filling
   */
  private @NotNull IonMobilogramTimeSeries toFeatureData(@NotNull final IonImageFeature feature,
      @NotNull final List<Frame> modulations, @NotNull final BinningMobilogramDataAccess binning) {
    final List<IonMobilitySeries> series = new ArrayList<>();
    final List<Frame> frames = new ArrayList<>();
    int start = 0;
    for (int k = 1; k <= feature.size(); k++) {
      if (k < feature.size() && feature.modulation(k) == feature.modulation(start)) {
        continue;
      }
      final Frame frame = modulations.get(feature.modulation(start));
      final int length = k - start;
      final double[] mzs = new double[length];
      final double[] intensities = new double[length];
      final List<MobilityScan> scans = new ArrayList<>(length);
      for (int s = 0; s < length; s++) {
        mzs[s] = feature.mz(start + s);
        intensities[s] = feature.intensity(start + s);
        scans.add(frame.getMobilityScan(feature.scan(start + s)));
      }
      // IonMobilitySeries are kept in memory until they are added to the time series
      series.add(new SimpleIonMobilitySeries(null, mzs, intensities, scans));
      frames.add(frame);
      start = k;
    }
    return IonMobilogramTimeSeriesFactory.of(getMemoryMapStorage(), series, binning, frames);
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  /**
   * The progress of a stage counts from start to start + weight of the total progress
   */
  private void startStage(final double start, final double weight, final long total) {
    stageDone.set(0);
    stageTotal = Math.max(1, total);
    stageStart = start;
    stageWeight = weight;
  }

  @Override
  public double getFinishedPercentage() {
    if (getStatus() == TaskStatus.FINISHED) {
      return 1d;
    }
    final double stage = Math.min(1d, stageDone.get() / (double) stageTotal);
    return Math.min(1d, stageStart + stageWeight * stage);
  }
}
