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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.impl.SecondDimensionSpectrum;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.maths.CenterFunction;
import io.github.mzmine.util.maths.Weighting;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.IntensityMergingType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sums the detected signals of each second dimension scan index over the modulations of a GCxGC
 * file into one {@link SecondDimensionSpectrum} per scan index.
 */
class GcxGcSecondDimensionSpectraTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      GcxGcSecondDimensionSpectraTask.class.getName());

  private final @NotNull RawDataFile file;
  private final @NotNull ParameterSet parameters;
  private final @Nullable Range<Float> rtRange;
  private final @NotNull MZTolerance mzTolerance;
  private final int minDetections;
  private final AtomicInteger processed = new AtomicInteger(0);
  private int total = 1;

  GcxGcSecondDimensionSpectraTask(@NotNull final RawDataFile file,
      @NotNull final ParameterSet parameters, @NotNull final Instant moduleCallDate) {
    // the spectra are stored in the raw data file
    super(null, moduleCallDate);
    this.file = file;
    this.parameters = parameters;
    final Range<Double> range = parameters.getEmbeddedParameterValueIfSelectedOrElse(
        GcxGcSecondDimensionSpectraParameters.RT_RANGE, null);
    rtRange = range == null ? null : RangeUtils.toFloatRange(range);
    mzTolerance = parameters.getValue(GcxGcSecondDimensionSpectraParameters.MZ_TOLERANCE);
    minDetections = parameters.getValue(GcxGcSecondDimensionSpectraParameters.MIN_DETECTIONS);
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    final GCxGCRawDataFile gcxgc = GcxGcUtils.asGcxGcFile(file);
    if (gcxgc == null || !GcxGcUtils.isGcxGc(file)) {
      error(file.getName() + " is no GCxGC data file.");
      return;
    }

    final List<? extends Frame> modulations = gcxgc.getFrames().stream()
        .filter(m -> m.getMSLevel() == 1)
        .filter(m -> rtRange == null || rtRange.contains(m.getRetentionTime())).toList();
    if (modulations.isEmpty()) {
      error("No modulations of %s within the retention time range %s.".formatted(file.getName(),
          rtRange));
      return;
    }
    if (!hasMassLists(modulations)) {
      error("""
          The single scans of %s have no detected signals (mass lists). Run mass detection on \
          all scan types before.""".formatted(file.getName()));
      return;
    }

    final int numScans = modulations.stream().mapToInt(Frame::getNumberOfMobilityScans).max()
        .orElse(0);
    total = Math.max(numScans, 1);
    final CenterFunction cf = new CenterFunction(SpectraMerging.DEFAULT_CENTER_MEASURE,
        Weighting.LINEAR);

    final List<SecondDimensionSpectrum> spectra = IntStream.range(0, numScans).parallel()
        .mapToObj(scanIndex -> {
          if (isCanceled()) {
            return null;
          }
          final SecondDimensionSpectrum spectrum = sumScanIndex(gcxgc, modulations, scanIndex,
              cf);
          processed.incrementAndGet();
          return spectrum;
        }).filter(Objects::nonNull).toList();
    if (isCanceled()) {
      return;
    }

    final Range<Float> usedRtRange = Range.closed(modulations.getFirst().getRetentionTime(),
        modulations.getLast().getRetentionTime());
    gcxgc.setSecondDimensionSpectra(spectra, usedRtRange);
    logger.fine(() -> "Created %d GCxGC 2D RT spectra of %s from %d modulations".formatted(
        spectra.size(), file.getName(), modulations.size()));

    // one applied method so that project loading replays this module
    file.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(GcxGcSecondDimensionSpectraModule.class, parameters,
            getModuleCallDate()));
    setStatus(TaskStatus.FINISHED);
  }

  /**
   * @return true if the single scans have mass lists, checks the first modulation with scans
   */
  private static boolean hasMassLists(@NotNull final List<? extends Frame> modulations) {
    for (final Frame modulation : modulations) {
      if (modulation.getNumberOfMobilityScans() > 0) {
        final MobilityScan scan = modulation.getMobilityScan(0);
        return scan != null && scan.getMassList() != null;
      }
    }
    return false;
  }

  /**
   * @return the signals of this scan index summed over all modulations
   */
  private @NotNull SecondDimensionSpectrum sumScanIndex(@NotNull final GCxGCRawDataFile gcxgc,
      @NotNull final List<? extends Frame> modulations, final int scanIndex,
      @NotNull final CenterFunction cf) {
    final List<MassList> massLists = new ArrayList<>(modulations.size());
    double sumSecondDimensionRT = 0;
    int numModulations = 0;
    for (final Frame modulation : modulations) {
      if (scanIndex >= modulation.getNumberOfMobilityScans()) {
        continue;
      }
      // assumption: the 2D RT of a scan index is almost equal in all modulations, use the mean
      sumSecondDimensionRT += modulation.getMobilityForMobilityScanNumber(scanIndex);
      numModulations++;
      final MobilityScan scan = modulation.getMobilityScan(scanIndex);
      final MassList massList = scan == null ? null : scan.getMassList();
      if (massList != null && massList.getNumberOfDataPoints() > 0) {
        massLists.add(massList);
      }
    }

    final double[][] merged = SpectraMerging.calculatedMergedMzsAndIntensities(massLists,
        mzTolerance, IntensityMergingType.SUMMED, cf, null, null, minDetections);
    final Frame first = modulations.getFirst();
    return new SecondDimensionSpectrum(gcxgc, scanIndex,
        (float) (sumSecondDimensionRT / Math.max(numModulations, 1)), merged[0], merged[1],
        first.getPolarity(), first.getScanningMZRange());
  }

  @Override
  public String getTaskDescription() {
    return "Creating GCxGC 2D RT spectra of " + file.getName();
  }

  @Override
  public double getFinishedPercentage() {
    return getStatus() == TaskStatus.FINISHED ? 1 : processed.get() / (double) total;
  }
}
