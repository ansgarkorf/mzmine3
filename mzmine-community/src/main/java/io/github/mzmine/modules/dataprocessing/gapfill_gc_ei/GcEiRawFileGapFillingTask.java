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

package io.github.mzmine.modules.dataprocessing.gapfill_gc_ei;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleRBTreeMap;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.BuildingIonSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.featdet_extract_mz_ranges.ExtractMzRangesIonSeriesFunction;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.FeatureSmoothingOptions;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingAlgorithm;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.collections.IndexRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fills the gaps of one raw data file in a GC-EI feature list. For each gap, the quantifier and top
 * signals of the row are extracted in the retention time window of the row. The quantifier feature
 * is added to the row if all signals have a peak apex that co-elutes with the quantifier apex, which
 * itself needs to be within the retention time tolerance of the row.
 * <p>
 * GCxGC rows (with second dimension tolerances) are extracted from the mobility scans within the
 * second dimension window of the row. The co-elution is checked in both dimensions and the
 * gap-filled feature keeps the second dimension.
 */
class GcEiRawFileGapFillingTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(GcEiRawFileGapFillingTask.class.getName());

  // decision: additional scans around the window so that smoothing has data at the window edges
  private static final int PADDING_SCANS = 10;
  // decision: a confirmed 2D apex needs a few consecutive data points in the second dimension
  private static final int MIN_2D_DATA_POINTS = 3;

  private final @NotNull ModularFeatureList flist;
  private final @NotNull RawDataFile file;
  private final @NotNull List<GcEiGapFillTarget> gaps;
  private final @NotNull GcEiFeatureFindingSettings settings;
  private final @NotNull RTTolerance sampleToSampleRtTolerance;
  private final @NotNull RTTolerance intraSampleCoelutionTolerance;
  private final @Nullable RTTolerance sampleToSample2DTolerance;
  private final @Nullable RTTolerance intraSampleCoelution2DTolerance;
  private final @NotNull MZTolerance mzTolerance;
  private final int minConsecutiveScans;
  private final @Nullable SmoothingAlgorithm smoother;
  private final @NotNull Runnable onFinished;
  private final AtomicInteger processedGaps = new AtomicInteger(0);
  private int filledGaps;

  /**
   * @param flist                         the feature list to add the gap-filled features to
   * @param file                          the raw data file to fill gaps in
   * @param gaps                          the rows without feature in this raw data file
   * @param settings                      the original feature detection settings
   * @param sampleToSampleRtTolerance     maximum distance of the quantifier apex to the row RT
   * @param intraSampleCoelutionTolerance maximum distance of the top signal apices to the
   *                                      quantifier apex
   * @param sampleToSample2DTolerance     maximum distance of the quantifier 2D apex to the row 2D
   *                                      RT, null for one-dimensional gap filling
   * @param intraSampleCoelution2DTolerance maximum distance of the top signal 2D apices to the
   *                                      quantifier 2D apex
   * @param onFinished                    called once all gaps were processed
   */
  GcEiRawFileGapFillingTask(@NotNull final ModularFeatureList flist,
      @NotNull final RawDataFile file, @NotNull final List<GcEiGapFillTarget> gaps,
      @NotNull final GcEiFeatureFindingSettings settings,
      @NotNull final RTTolerance sampleToSampleRtTolerance,
      @NotNull final RTTolerance intraSampleCoelutionTolerance,
      @Nullable final RTTolerance sampleToSample2DTolerance,
      @Nullable final RTTolerance intraSampleCoelution2DTolerance,
      @NotNull final Runnable onFinished, @NotNull final Instant moduleCallDate) {
    // final features are stored in the storage of the feature list
    super(null, moduleCallDate);
    this.flist = flist;
    this.file = file;
    this.gaps = gaps;
    this.settings = settings;
    this.sampleToSampleRtTolerance = sampleToSampleRtTolerance;
    this.intraSampleCoelutionTolerance = intraSampleCoelutionTolerance;
    this.sampleToSample2DTolerance = sampleToSample2DTolerance;
    this.intraSampleCoelution2DTolerance = intraSampleCoelution2DTolerance;
    this.onFinished = onFinished;
    mzTolerance = settings.mzTolerance();
    minConsecutiveScans = settings.minConsecutiveScans();
    // smoothers are created per file as they may keep buffers
    smoother = settings.smoothingParameters() != null ? FeatureSmoothingOptions.createSmoother(
        settings.smoothingParameters()) : null;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    final List<? extends Scan> scans = getScans();
    if (scans.isEmpty()) {
      logger.warning(
          () -> "No scans selected for %s in feature list %s. Skipping gap filling.".formatted(
              file.getName(), flist.getName()));
    } else {
      for (final GcEiGapFillTarget gap : gaps) {
        if (isCanceled()) {
          return;
        }
        if (fillGap(gap, scans)) {
          filledGaps++;
        }
        processedGaps.incrementAndGet();
      }
    }
    onFinished.run();
    setStatus(TaskStatus.FINISHED);
  }

  /**
   * @return the scans used to build the original chromatograms
   */
  private @NotNull List<? extends Scan> getScans() {
    final List<? extends Scan> selected = flist.getSeletedScans(file);
    if (selected != null) {
      return selected;
    }
    // assumption: without selected scans, the scan selection of the chromatogram builder applies
    return List.of(settings.scanSelection().getMatchingScans(file));
  }

  /**
   * @return true if the quantifier feature was added to the row
   */
  private boolean fillGap(@NotNull final GcEiGapFillTarget gap,
      @NotNull final List<? extends Scan> allScans) {
    final Range<Float> quantifierRange = sampleToSampleRtTolerance.getToleranceRange(gap.rt());
    // other signals may peak within the co-elution tolerance around the quantifier apex
    final Range<Float> searchRange = Range.closed(
        intraSampleCoelutionTolerance.getToleranceRange(quantifierRange.lowerEndpoint())
            .lowerEndpoint(),
        intraSampleCoelutionTolerance.getToleranceRange(quantifierRange.upperEndpoint())
            .upperEndpoint());
    final IndexRange window = BinarySearch.indexRange(searchRange.span(gap.detectedRange()),
        allScans, Scan::getRetentionTime);
    if (window.isEmpty()) {
      return false;
    }
    final List<? extends Scan> scans = allScans.subList(
        Math.max(0, window.min() - PADDING_SCANS),
        Math.min(allScans.size(), window.maxExclusive() + PADDING_SCANS));

    final GcEiGapFillSecondDimension secondDim = gap.secondDimension();
    if (secondDim != null && sampleToSample2DTolerance != null
        && intraSampleCoelution2DTolerance != null && file instanceof IMSRawDataFile ims) {
      return fillGap2D(gap, secondDim, ims, (List<Frame>) scans, quantifierRange,
          sampleToSample2DTolerance, intraSampleCoelution2DTolerance);
    }

    // first signal is the quantifier
    final double[] mzs = gap.signalsToConfirm(mzTolerance);
    final List<IonTimeSeries<? extends Scan>> chromatograms = extractChromatograms(mzs, scans);
    if (chromatograms.isEmpty()) {
      return false;
    }

    final IonTimeSeries<? extends Scan> quantifier = chromatograms.getFirst();
    final GcEiSignalApex quantifierApex = findApex(quantifier, quantifierRange);
    if (quantifierApex == null) {
      return false;
    }
    final Range<Float> coelutionRange = intraSampleCoelutionTolerance.getToleranceRange(
        quantifierApex.rt());
    for (int i = 1; i < chromatograms.size(); i++) {
      if (findApex(chromatograms.get(i), coelutionRange) == null) {
        return false;
      }
    }

    // decision: integrate the typical peak width of the row around the apex found in this sample
    final IonTimeSeries<? extends Scan> featureData = quantifier.subSeries(
        flist.getMemoryMapStorage(), quantifierApex.rt() - gap.leftWidth(),
        quantifierApex.rt() + gap.rightWidth());
    if (featureData.getNumberOfValues() == 0) {
      return false;
    }
    final ModularFeature feature = new ModularFeature(flist, file, featureData,
        FeatureStatus.ESTIMATED);
    // row bindings are applied once after all files were processed
    gap.row().addFeature(file, feature, false);
    return true;
  }

  /**
   * Gap filling of a GCxGC row. All signals are extracted from the mobility scans within the second
   * dimension window of the row, so that compounds of the same modulations are not summed. The
   * first dimension apex is searched in the summed traces of each frame (modulation), the second
   * dimension apex in the summed 2D trace of the frames around the first dimension apex.
   *
   * @return true if the quantifier feature was added to the row
   */
  private boolean fillGap2D(@NotNull final GcEiGapFillTarget gap,
      @NotNull final GcEiGapFillSecondDimension secondDim, @NotNull final IMSRawDataFile ims,
      @NotNull final List<Frame> frames, @NotNull final Range<Float> quantifierRange,
      @NotNull final RTTolerance sampleToSample2D,
      @NotNull final RTTolerance intraSample2D) {
    final Range<Float> quantifier2DRange = sampleToSample2D.getToleranceRange(
        secondDim.secondDim());
    // other signals may peak within the 2D co-elution tolerance around the quantifier 2D apex
    final Range<Float> searchRange2D = Range.closed(
        quantifier2DRange.lowerEndpoint() - intraSample2D.getToleranceInMinutes(),
        quantifier2DRange.upperEndpoint() + intraSample2D.getToleranceInMinutes());

    final BinningMobilogramDataAccess binning = BinningMobilogramDataAccess.createWithPreviousParameters(
        ims, flist);
    final double[] mzs = gap.signalsToConfirm(mzTolerance);
    final List<IonMobilogramTimeSeries> traces = new ArrayList<>(mzs.length);
    for (final double mz : mzs) {
      if (isCanceled()) {
        return false;
      }
      traces.add(IonTimeSeriesUtils.extractIonMobilogramTimeSeries(
          new MobilityScanDataAccess(ims, MobilityScanDataType.MASS_LIST, frames),
          mzTolerance.getToleranceRange(mz), null, searchRange2D, null, binning));
    }

    // first dimension: frame sums within the 2D window
    final IonMobilogramTimeSeries quantifier = traces.getFirst();
    final GcEiSignalApex quantifierApex = findApex(quantifier, quantifierRange);
    if (quantifierApex == null) {
      return false;
    }
    final Range<Float> coelutionRange = intraSampleCoelutionTolerance.getToleranceRange(
        quantifierApex.rt());
    // second dimension: summed over the peak width around the first dimension apex
    final Range<Float> peakRtRange = Range.closed(quantifierApex.rt() - gap.leftWidth(),
        quantifierApex.rt() + gap.rightWidth());
    final Float quantifier2DApex = find2DApex(quantifier, peakRtRange, quantifier2DRange);
    if (quantifier2DApex == null) {
      return false;
    }
    final Range<Float> coelution2DRange = intraSample2D.getToleranceRange(quantifier2DApex);
    for (int i = 1; i < traces.size(); i++) {
      if (findApex(traces.get(i), coelutionRange) == null
          || find2DApex(traces.get(i), peakRtRange, coelution2DRange) == null) {
        return false;
      }
    }

    // decision: integrate the typical peak width of the row in both dimensions around the apex
    final IonMobilogramTimeSeries featureData = IonTimeSeriesUtils.extractIonMobilogramTimeSeries(
        new MobilityScanDataAccess(ims, MobilityScanDataType.MASS_LIST, frames),
        mzTolerance.getToleranceRange(gap.quantifierMz()), peakRtRange,
        Range.closed(quantifier2DApex - secondDim.leftWidth(),
            quantifier2DApex + secondDim.rightWidth()), flist.getMemoryMapStorage(), binning);
    if (featureData.getNumberOfValues() == 0 || featureData.getMobilograms().isEmpty()) {
      return false;
    }
    final ModularFeature feature = new ModularFeature(flist, file, featureData,
        FeatureStatus.ESTIMATED);
    if (feature.getHeight() <= 0) {
      return false;
    }
    // row bindings are applied once after all files were processed
    gap.row().addFeature(file, feature, false);
    return true;
  }

  /**
   * The second dimension apex is the highest local maximum of the summed 2D trace of all frames in
   * the retention time range. The 2D trace is summed per mobility scan number.
   *
   * @return the second dimension of the apex or null if there is no apex in the range
   */
  private static @Nullable Float find2DApex(@NotNull final IonMobilogramTimeSeries series,
      @NotNull final Range<Float> rtRange, @NotNull final Range<Float> range2D) {
    final Int2DoubleRBTreeMap summed = new Int2DoubleRBTreeMap();
    final Int2DoubleOpenHashMap secondDims = new Int2DoubleOpenHashMap();
    for (final IonMobilitySeries mobilogram : series.getMobilograms()) {
      if (mobilogram.getNumberOfValues() == 0 || !rtRange.contains(
          mobilogram.getSpectrum(0).getRetentionTime())) {
        continue;
      }
      for (int i = 0; i < mobilogram.getNumberOfValues(); i++) {
        final MobilityScan scan = mobilogram.getSpectrum(i);
        summed.addTo(scan.getMobilityScanNumber(), mobilogram.getIntensity(i));
        secondDims.put(scan.getMobilityScanNumber(), scan.getMobility());
      }
    }
    // order of the 2D values, scan numbers are ascending with the 2D RT for GCxGC
    final int[] scanNumbers = summed.keySet().toIntArray();
    Float best = null;
    double bestIntensity = 0;
    for (int i = 1; i < scanNumbers.length - 1; i++) {
      final double intensity = summed.get(scanNumbers[i]);
      final float secondDim = (float) secondDims.get(scanNumbers[i]);
      if (!range2D.contains(secondDim) || intensity <= bestIntensity
          || intensity <= summed.get(scanNumbers[i - 1])
          || intensity < summed.get(scanNumbers[i + 1])
          || countConsecutive(summed, scanNumbers, i) < MIN_2D_DATA_POINTS) {
        continue;
      }
      best = secondDim;
      bestIntensity = intensity;
    }
    return best;
  }

  /**
   * @return the number of consecutive data points with an intensity above zero around the index
   */
  private static int countConsecutive(@NotNull final Int2DoubleRBTreeMap intensities,
      final int @NotNull [] keys, final int index) {
    int start = index;
    while (start > 0 && intensities.get(keys[start - 1]) > 0) {
      start--;
    }
    int end = index;
    while (end < keys.length - 1 && intensities.get(keys[end + 1]) > 0) {
      end++;
    }
    return end - start + 1;
  }

  /**
   * Extracts the most intense data point per scan within the m/z tolerance of each m/z and applies
   * the smoothing of the original processing.
   *
   * @return one chromatogram per m/z in the input order, including zero intensities. Empty if
   * canceled
   */
  private @NotNull List<IonTimeSeries<? extends Scan>> extractChromatograms(
      final double @NotNull [] mzs, @NotNull final List<? extends Scan> scans) {
    // the extraction requires m/z ranges sorted ascending
    final int[] sortedIndices = IntStream.range(0, mzs.length).boxed()
        .sorted(Comparator.comparingDouble(i -> mzs[i])).mapToInt(Integer::intValue).toArray();
    final List<Range<Double>> sortedRanges = new ArrayList<>(mzs.length);
    for (final int index : sortedIndices) {
      sortedRanges.add(mzTolerance.getToleranceRange(mzs[index]));
    }
    final BuildingIonSeries[] extracted = new ExtractMzRangesIonSeriesFunction(file, scans,
        sortedRanges, ScanDataType.MASS_LIST, this).get();
    if (extracted.length != mzs.length) {
      return List.of();
    }

    final List<IonTimeSeries<? extends Scan>> chromatograms = new ArrayList<>(mzs.length);
    for (int i = 0; i < mzs.length; i++) {
      chromatograms.add(null);
    }
    for (int sorted = 0; sorted < sortedIndices.length; sorted++) {
      chromatograms.set(sortedIndices[sorted], smooth(extracted[sorted].toFullIonTimeSeries(null,
          scans)));
    }
    return chromatograms;
  }

  private @NotNull IonTimeSeries<? extends Scan> smooth(
      @NotNull final IonTimeSeries<? extends Scan> chromatogram) {
    if (smoother == null) {
      return chromatogram;
    }
    // null if retention time smoothing is disabled
    final double[] smoothed = smoother.smoothRt(chromatogram);
    return smoothed == null ? chromatogram : chromatogram.copyAndReplace(null, smoothed);
  }

  /**
   * A peak apex is the highest local maximum within the range with at least the minimum number of
   * consecutive scans of the chromatogram builder.
   *
   * @return the apex or null if there is no peak apex within the range
   */
  private @Nullable GcEiSignalApex findApex(@NotNull final IonTimeSeries<? extends Scan> series,
      @NotNull final Range<Float> range) {
    GcEiSignalApex best = null;
    // decision: the first and last data points cannot be a confirmed apex
    for (int i = 1; i < series.getNumberOfValues() - 1; i++) {
      final float rt = series.getRetentionTime(i);
      final double intensity = series.getIntensity(i);
      if (!range.contains(rt) || intensity <= 0 || intensity <= series.getIntensity(i - 1)
          || intensity < series.getIntensity(i + 1)) {
        continue;
      }
      if ((best == null || intensity > best.height())
          && countConsecutiveScans(series, i) >= minConsecutiveScans) {
        best = new GcEiSignalApex(i, rt, intensity);
      }
    }
    return best;
  }

  /**
   * @return the number of consecutive data points with an intensity above zero around the index
   */
  private static int countConsecutiveScans(@NotNull final IonTimeSeries<? extends Scan> series,
      final int index) {
    int start = index;
    while (start > 0 && series.getIntensity(start - 1) > 0) {
      start--;
    }
    int end = index;
    while (end < series.getNumberOfValues() - 1 && series.getIntensity(end + 1) > 0) {
      end++;
    }
    return end - start + 1;
  }

  int getFilledGaps() {
    return filledGaps;
  }

  @Override
  public String getTaskDescription() {
    return "GC-EI gap filling of %s (%d/%d gaps)".formatted(file.getName(), processedGaps.get(),
        gaps.size());
  }

  @Override
  public double getFinishedPercentage() {
    return gaps.isEmpty() ? 1 : processedGaps.get() / (double) gaps.size();
  }
}
