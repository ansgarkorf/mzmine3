/*
 * Copyright (c) 2004-2024 The mzmine Development Team
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

package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic;

import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters.MIN_NUMBER_OF_DATAPOINTS;
import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic.ClassicResolverParameters.SEARCH_RT_RANGE;
import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic.ClassicResolverParameters.SIGNAL_TO_NOISE;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.AbstractResolver;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * AutoMinimumSearchResolver with these key points:
 * <p>
 * (1) One-pass detection with a ratioParam (e.g., 1.2). (2) searchXWidth is used only to decide if
 * the region is "big enough" to check for a separate peak or local minimum. The final peak width
 * can exceed searchXWidth if intensities remain high. (3) No overlapping data points: once we
 * finalize [start..end], we skip that range for the next peak. (4) We do NOT zero out intensities
 * below local threshold. The threshold is used only in final shape/validity checks. (5)
 * postFilterRatio(...) to handle high/mid/low ratio peaks with some hard-coded thresholds.
 */
public class ClassicResolver extends AbstractResolver {

  private final ParameterSet parameters;
  private final double searchXWidth;
  private final int minDataPoints;
  private final double signalToNoise;

  /**
   * Hardcoded ratio thresholds:
   *   - If ratio >= 10 => High ratio => always keep
   *   - If ratio < 1.5 => Low ratio => keep only if few
   */
  private double HIGH_RATIO_THRESHOLD = 10.0;
  private double LOW_RATIO_THRESHOLD = 1.5;
  private int MAX_LOW_RATIO_ALLOWED = 2; // If we detect >2 "low ratio" peaks, discard them all

  public ClassicResolver(ParameterSet parameterSet, ModularFeatureList flist) {
    super(parameterSet, flist);
    this.parameters = parameterSet;
    this.minDataPoints = parameters.getParameter(MIN_NUMBER_OF_DATAPOINTS).getValue();
    this.searchXWidth = parameters.getParameter(SEARCH_RT_RANGE).getValue();
    this.signalToNoise = parameters.getParameter(SIGNAL_TO_NOISE).getValue();
  }

  @Override
  public @NotNull Class<? extends MZmineModule> getModuleClass() {
    return ClassicResolverModule.class;
  }

  /**
   * Main resolve method:
   *  1) detectCandidates(...) with ratioParam=1.2 (somewhat permissive),
   *  2) postFilterRatio(...) to handle High/Mid/Low ratio peaks.
   */
  @Override
  @NotNull
  public List<Range<Double>> resolve(double[] x, double[] y) {
    if (x.length != y.length) {
      throw new AssertionError("Lengths of x and y arrays must match.");
    }
    if (x.length == 0) {
      return new ArrayList<>();
    }

    // 1) Detect candidates (local-min search + shape checks)
    List<PeakCandidate> rawCandidates = detectCandidates(x, y, /* ratioParam= */ 1.2);

    // 2) Post-filter the final set
    List<PeakCandidate> finalCandidates = postFilterRatio(rawCandidates);

    // Convert to List<Range<Double>>
    List<Range<Double>> resolvedRanges = new ArrayList<>(finalCandidates.size());
    for (PeakCandidate pc : finalCandidates) {
      resolvedRanges.add(Range.closed(pc.startX, pc.endX));
    }
    return resolvedRanges;
  }

  /**
   * Step #2: postFilterRatio
   * <p>
   * - ratio >= HIGH_RATIO_THRESHOLD (e.g., 10) => keep always
   * - ratio < LOW_RATIO_THRESHOLD (e.g., 1.5) => keep only if we detect <= MAX_LOW_RATIO_ALLOWED
   * - else => "mid ratio"
   */
  private List<PeakCandidate> postFilterRatio(List<PeakCandidate> candidates) {
    List<PeakCandidate> highRatio = new ArrayList<>();
    List<PeakCandidate> lowRatio = new ArrayList<>();
    List<PeakCandidate> midRatio = new ArrayList<>();

    for (PeakCandidate pc : candidates) {
      double edge = Math.min(pc.peakMinLeft, pc.peakMinRight);
      double ratio = (edge > 0) ? (pc.topIntensity / edge) : Double.POSITIVE_INFINITY;

      if (ratio >= HIGH_RATIO_THRESHOLD) {
        highRatio.add(pc); // definitely keep
      } else if (ratio < LOW_RATIO_THRESHOLD) {
        lowRatio.add(pc);  // keep only if few
      } else {
        midRatio.add(pc);
      }
    }

    // Discard all low-ratio peaks if we found more than allowed
    if (lowRatio.size() > MAX_LOW_RATIO_ALLOWED) {
      lowRatio.clear();
    }

    List<PeakCandidate> finalList = new ArrayList<>(
        highRatio.size() + midRatio.size() + lowRatio.size());
    finalList.addAll(highRatio);
    finalList.addAll(midRatio);
    finalList.addAll(lowRatio);

    return finalList;
  }

  // ----------------------------------------------------------------------------
  // Step #1: detectCandidates with local-min search, ensuring no overlap in data
  // ----------------------------------------------------------------------------
  private List<PeakCandidate> detectCandidates(double[] x, double[] y, double ratioParam) {
    final int valueCount = x.length;

    // (A) Compute local thresholds but do NOT zero intensities.
    int windowSize = (int) Math.ceil(valueCount * 0.25);
    double factor = 0.5;
    double[] localThresholds = calculateLocalThresholds(y, windowSize, factor);

    // Keep track of which points are above threshold if we want shape checks later
    boolean[] aboveThreshold = new boolean[valueCount];
    for (int i = 0; i < valueCount; i++) {
      aboveThreshold[i] = (y[i] >= localThresholds[i]);
    }

    // (B) Estimate baseline => define minHeight
    double baselineNoise = median(localThresholds);
    double minHeight = baselineNoise * signalToNoise;

    // (C) Local-min search with no overlap
    List<PeakCandidate> detectedPeaks = new ArrayList<>();
    final int lastScan = valueCount - 1;

    // We'll iterate data points with "start," finalize a peak [start..end], then skip to end+1
    for (int start = 0; start <= lastScan; ) {

      // Skip zero or negative intensities
      if (y[start] <= 0) {
        start++;
        continue;
      }

      double peakTop = y[start];
      double leftEdge = y[start];
      int bestEnd = -1;

      //  find region
      for (int end = start + 1; end <= lastScan; end++) {

        peakTop = Math.max(peakTop, y[end]);
        double rightEdge = y[end];

        // (1) If next intensity is <= 0 OR we reached the last scan => finalize region
        if (end == lastScan || y[end + 1] <= 0) {
          int usedEnd = tryToFinalizePeak(x, y, start, end, peakTop, minHeight, leftEdge, rightEdge,
              ratioParam, aboveThreshold, localThresholds, detectedPeaks);
          bestEnd = usedEnd;
          break;
        }

        // (2) If we've reached searchXWidth, check if there's a local min or reason to finalize
        if ((x[end] - x[start]) >= searchXWidth) {
          // Decide if y[end] is near a local minimum
          if (isLocalMinimum(y, end)) {
            int usedEnd = tryToFinalizePeak(x, y, start, end, peakTop, minHeight, leftEdge,
                rightEdge, ratioParam, aboveThreshold, localThresholds, detectedPeaks);
            bestEnd = usedEnd;
            break;
          }
        }
      }

      // If we never finalized a region, move on by 1
      if (bestEnd < 0) {
        start++;
      } else {
        // we used [start..bestEnd], skip them
        start = bestEnd + 1;
      }
    }

    return detectedPeaks;
  }

  /**
   * Decide if y[idx] is a local minimum: For example: y[idx] < y[idx-1] && y[idx] <= y[idx+1]. You
   * can make a more advanced check if needed.
   */
  private boolean isLocalMinimum(double[] y, int idx) {
    if (idx <= 0 || idx >= (y.length - 1)) {
      return false;
    }
    // Example: strict local min if it's lower than both neighbors
    return (y[idx] < y[idx - 1]) && (y[idx] <= y[idx + 1]);
  }

  /**
   * Attempt to finalize a peak [start..end]. If shape checks pass => create PeakCandidate. Returns
   * the "end" if we used that region, else returns "start" if invalid.
   */
  private int tryToFinalizePeak(double[] x, double[] y, int start, int end, double peakTop,
      double minHeight, double peakMinLeft, double peakMinRight, double ratioParam,
      boolean[] aboveThreshold, double[] localThresholds, List<PeakCandidate> detected) {

    final int numberOfPoints = (end - start + 1);

    boolean valid = checkPeakShape(numberOfPoints, peakTop, minHeight, peakMinLeft, peakMinRight,
        ratioParam, start, end, y, aboveThreshold, localThresholds);

    if (valid) {
      // Possibly expand boundaries to local min if desired
      Range<Double> adjusted = adjustStartAndEnd(x, y, start, end);

      double startX = adjusted.lowerEndpoint();
      double endX = adjusted.upperEndpoint();

      PeakCandidate candidate = new PeakCandidate(startX, endX, peakTop, peakMinLeft, peakMinRight);
      detected.add(candidate);

      return end;
    }

    return start;
  }

  /**
   * Evaluate shape constraints:
   *  - Enough points
   *  - top >= minHeight
   *  - top >= peakMin*(ratioParam)
   *  - optional fraction above threshold
   */
  private boolean checkPeakShape(int numberOfDataPoints, double regionTop, double minHeight,
      double peakMinLeft, double peakMinRight, double ratioParam, int regionStart, int regionEnd,
      double[] y, boolean[] aboveThreshold, double[] localThresholds) {

    // 1) Enough data
    if (numberOfDataPoints < minDataPoints) {
      return false;
    }
    // 2) apex above minHeight
    if (regionTop < minHeight) {
      return false;
    }
    // 3) apex >= edges * ratioParam
    if (regionTop < (peakMinLeft * ratioParam)) {
      return false;
    }
    if (regionTop < (peakMinRight * ratioParam)) {
      return false;
    }
    // 4) optionally require some fraction above threshold
    int countAbove = 0;
    for (int i = regionStart; i <= regionEnd; i++) {
      if (aboveThreshold[i]) {
        countAbove++;
      }
    }
    double fractionAbove = (double) countAbove / (double) numberOfDataPoints;
    // e.g., require at least 40% above local threshold
    if (fractionAbove < 0.4) {
      return false;
    }

    return true;
  }

  /**
   * Expand the boundaries to the local minima or zero intensities as needed, instead of stopping
   * exactly at "start" or "end".
   */
  private @NotNull Range<Double> adjustStartAndEnd(double[] x, double[] y, int currentRegionStart,
      int currentRegionEnd) {

    int start = currentRegionStart;
    int end = currentRegionEnd;

    // Expand left if intensities suggest we haven't reached min
    while (start > 0 && y[start - 1] > 0 && y[start] <= y[start - 1]) {
      start--;
    }
    // Expand right
    while (end < (y.length - 1) && y[end + 1] > 0 && y[end] <= y[end + 1]) {
      end++;
    }

    double startX = x[start];
    double endX = x[end];
    if (startX > endX) {
      double tmp = startX;
      startX = endX;
      endX = tmp;
    }

    return Range.closed(startX, endX);
  }

  // -------------------------------------------------------------
  // Local thresholding and baseline logic
  // -------------------------------------------------------------
  private double[] calculateLocalThresholds(double[] intensities, int windowSize, double factor) {
    final int length = intensities.length;
    double[] thresholds = new double[length];
    int halfWindow = windowSize / 2;

    for (int i = 0; i < length; i++) {
      int wStart = Math.max(0, i - halfWindow);
      int wEnd = Math.min(length, i + halfWindow + 1);

      double[] subArray = Arrays.copyOfRange(intensities, wStart, wEnd);
      double localMed = median(subArray);
      double localMad = medianAbsoluteDeviation(subArray, localMed);
      thresholds[i] = localMed + factor * localMad;
    }

    return thresholds;
  }

  private double median(double[] data) {
    if (data.length == 0) {
      return 0.0;
    }
    Arrays.sort(data);
    int mid = data.length / 2;
    if (data.length % 2 == 0) {
      return (data[mid - 1] + data[mid]) / 2.0;
    }
    return data[mid];
  }

  private double medianAbsoluteDeviation(double[] data, double med) {
    double[] dev = new double[data.length];
    for (int i = 0; i < data.length; i++) {
      dev[i] = Math.abs(data[i] - med);
    }
    return median(dev);
  }

  // -------------------------------------------------------------
  // Simple container for a detected peak region
  // -------------------------------------------------------------
  private record PeakCandidate(double startX, double endX, double topIntensity, double peakMinLeft,
                               double peakMinRight) {
    // ratio is computed in postFilterRatio
  }
}