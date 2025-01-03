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

package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.autominimumsearch;

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
 * A local-minimum-based feature resolver that does a more robust "machine learning" style parameter
 * search plus an advanced feature-quality score (including Gaussian fitting, kurtosis, and tailing
 * factor). It also supports automatic peak-splitting if we detect multiple maxima in the same
 * region.
 */
public class AutoMinimumSearchResolver extends AbstractResolver {

  // We'll store the "best" parameters after searching, for reference
  private double bestChromThreshold;
  private double bestSearchXWidth;
  private double bestMinRatio;
  private double bestMinAbsoluteHeight;
  private int bestMinDataPoints;

  public AutoMinimumSearchResolver(ParameterSet parameterSet, ModularFeatureList flist) {
    super(parameterSet, flist);
  }

  @Override
  public @NotNull Class<? extends MZmineModule> getModuleClass() {
    return AutoMinimumSearchResolverModule.class;
  }

  /**
   * The main entry point:
   * 1) We generate a robust set of parameter combos (grid search).
   * 2) For each combo, detect peaks => compute average feature-quality score (includes Gaussian
   * fit, kurtosis, tailing, etc.).
   * 3) Pick best combo => re-detect peaks => final result
   */
  @Override
  @NotNull
  public List<Range<Double>> resolve(double[] x, double[] y) {
    if (x == null || y == null || x.length != y.length || x.length == 0) {
      return new ArrayList<>();
    }

    // 1) Generate parameter combos (robust grid or random approach)
    List<ParamCombo> candidates = generateParameterCandidates(x, y);

    // 2) Evaluate them to pick the best
    double bestScore = -Double.MAX_VALUE;
    ParamCombo best = null;
    for (ParamCombo pc : candidates) {
      double score = testParameters(x, y, pc);
      if (score > bestScore) {
        bestScore = score;
        best = pc;
      }
    }

    if (best == null) {
      // fallback => no combos or no peaks
      return new ArrayList<>();
    }

    // Store for reference
    bestChromThreshold = best.chromThreshold;
    bestSearchXWidth = best.searchXWidth;
    bestMinRatio = best.minRatio;
    bestMinAbsoluteHeight = best.minHeight;
    bestMinDataPoints = best.minDataPoints;

    // 3) Re-run detection with the best parameters => final peaks
    List<Range<Double>> finalPeaks = detectPeaks(x, y, best);

    // Optional: if you want to do final splitting of multi-max peaks here, you can
    finalPeaks = splitMultiMaxima(x, y, finalPeaks);

    return finalPeaks;
  }

  // ------------------------------------------------------------------------
  // (1) Generate a robust set of parameter combos
  // ------------------------------------------------------------------------
  private List<ParamCombo> generateParameterCandidates(double[] x, double[] y) {
    List<ParamCombo> combos = new ArrayList<>();

    // We'll compute a baseline as before: median of lowest 10%
    double[] sortedY = Arrays.copyOf(y, y.length);
    Arrays.sort(sortedY);
    int cutoffIndex = Math.max(1, (int) (y.length * 0.1));
    double baseline = median(Arrays.copyOf(sortedY, cutoffIndex));

    double fullSpan = x[x.length - 1] - x[0];

    // For a more robust grid, let's define more intervals for each param:
    double[] chromThresholds = {baseline * 0.5, baseline * 0.8, baseline, baseline * 1.2,
        baseline * 1.5};
    double[] searchWidths = {0.01 * fullSpan, 0.05 * fullSpan, 0.10 * fullSpan, 0.20 * fullSpan,
        0.30 * fullSpan};
    double[] minRatios = {1.1, 1.2, 1.3, 1.5, 1.8};
    double[] minHeights = {baseline * 1.0, baseline * 2.0, baseline * 3.0, baseline * 5.0};
    int[] dataPointsList = {3, 5, 7, 9};

    // Build combos
    for (double cth : chromThresholds) {
      for (double sw : searchWidths) {
        for (double r : minRatios) {
          for (double mh : minHeights) {
            for (int dp : dataPointsList) {
              combos.add(new ParamCombo(cth, sw, r, mh, dp));
            }
          }
        }
      }
    }

    // (Optionally, you could do random sampling if the grid is too large.)
    return combos;
  }

  // ------------------------------------------------------------------------
  // (2) Test parameter combo => detect peaks => measure average feature quality
  // ------------------------------------------------------------------------
  private double testParameters(double[] x, double[] y, ParamCombo pc) {
    // Copy data => zero out intensities < pc.chromThreshold
    double[] yCopy = Arrays.copyOf(y, y.length);
    for (int i = 0; i < yCopy.length; i++) {
      if (yCopy[i] < pc.chromThreshold) {
        yCopy[i] = 0.0;
      }
    }
    // Local-min detection
    List<Range<Double>> peaks = detectPeaks(x, yCopy, pc);

    // If we want to split multiple maxima right away for better scoring, do it now
    peaks = splitMultiMaxima(x, yCopy, peaks);

    // measure average featureQuality
    if (peaks.isEmpty()) {
      return -99999.0; // penalize combos that find no peaks
    }
    double totalQ = 0.0;
    for (Range<Double> rg : peaks) {
      double q = computeFeatureQuality(x, yCopy, rg);
      totalQ += q;
    }
    return totalQ / peaks.size();
  }

  /**
   * Local-min detection using the ParamCombo. Similar approach:
   * - We find consecutive non-zero data points
   * - If next is zero or we pass searchXWidth with ratio checks, we finalize a peak
   */
  private List<Range<Double>> detectPeaks(double[] x, double[] y, ParamCombo pc) {
    List<Range<Double>> peaks = new ArrayList<>();
    final int n = x.length;

    outerLoop:
    for (int start = 0; start < n - 2; start++) {
      if (y[start] == 0 || y[start + 1] == 0) {
        continue;
      }

      double regionTop = y[start];

      innerLoop:
      for (int end = start + 1; end < n; end++) {
        regionTop = Math.max(regionTop, y[end]);

        // finalize if next is zero or last
        if (end == n - 1 || y[end + 1] == 0) {
          double leftEdge = y[start];
          double rightEdge = y[end];
          int newStart = tryToFinalizePeak(x, y, start, end, regionTop, pc, leftEdge, rightEdge,
              peaks);
          start = newStart;
          continue outerLoop;
        }

        // check if we reached searchXWidth => local-min boundary
        if (x[end] - x[start] >= pc.searchXWidth) {
          if (regionTop >= (y[end] * pc.minRatio)) {
            double leftEdge = y[start];
            double rightEdge = y[end];
            int newStart = tryToFinalizePeak(x, y, start, end, regionTop, pc, leftEdge, rightEdge,
                peaks);
            start = newStart;
            continue outerLoop;
          }
        }
      }
    }
    return peaks;
  }

  /**
   * Check shape constraints for [start..end], if valid => add to peaks. Then skip that region.
   */
  private int tryToFinalizePeak(double[] x, double[] y, int start, int end, double apex,
      ParamCombo pc, double leftEdge, double rightEdge, List<Range<Double>> peaks) {
    int numPts = (end - start + 1);
    if (numPts >= pc.minDataPoints && apex >= pc.minHeight) {
      double ratioLeft = (leftEdge > 0) ? (apex / leftEdge) : Double.POSITIVE_INFINITY;
      double ratioRight = (rightEdge > 0) ? (apex / rightEdge) : Double.POSITIVE_INFINITY;
      if (ratioLeft >= pc.minRatio && ratioRight >= pc.minRatio) {
        Range<Double> finalRange = adjustStartAndEnd(x, y, start, end);
        peaks.add(finalRange);
      }
    }
    return end - 1;
  }

  /**
   * Expand edges if the adjacent neighbor is 0
   */
  private Range<Double> adjustStartAndEnd(double[] x, double[] y, int s, int e) {
    int start = s;
    if (start > 0 && y[start] != 0 && y[start - 1] == 0) {
      start--;
    }
    int end = e;
    if (end < (y.length - 1) && y[end] != 0 && y[end + 1] == 0) {
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

  // ------------------------------------------------------------------------
  // Splitting multi-maxima in a single candidate peak
  // ------------------------------------------------------------------------

  /**
   * If a single detected region has multiple maxima that appear to be genuine sub-peaks, we split
   * them.
   * <p>
   * Example: we look for local maxima >= e.g. 50% of the region's apex. Then we create sub-peaks
   * separated by local minima.
   */
  private List<Range<Double>> splitMultiMaxima(double[] x, double[] y, List<Range<Double>> input) {
    List<Range<Double>> result = new ArrayList<>();
    for (Range<Double> rg : input) {
      // check how many significant maxima are in this region
      List<Range<Double>> splits = splitIfMultiMax(x, y, rg);
      result.addAll(splits);
    }
    return result;
  }

  /**
   * Returns one or more sub-peaks from the range if multiple maxima are found and separated by
   * local minima.
   */
  private List<Range<Double>> splitIfMultiMax(double[] x, double[] y, Range<Double> region) {
    List<Range<Double>> splitted = new ArrayList<>();

    // 1) Extract the subarray
    int startIdx = findClosestIndex(x, region.lowerEndpoint());
    int endIdx = findClosestIndex(x, region.upperEndpoint());
    double apexVal = 0.0;
    for (int i = startIdx; i <= endIdx; i++) {
      apexVal = Math.max(apexVal, y[i]);
    }

    // 2) Find local maxima >= e.g. 50% of apexVal
    List<Integer> significantMaxima = new ArrayList<>();
    for (int i = startIdx + 1; i < endIdx; i++) {
      if (y[i] > y[i - 1] && y[i] > y[i + 1] && y[i] >= 0.5 * apexVal) {
        significantMaxima.add(i);
      }
    }

    if (significantMaxima.isEmpty()) {
      // no sub-peaks => just keep the region
      splitted.add(region);
      return splitted;
    }
    // If there's only 1 significant local max, it's basically the same
    // as the main apex. Keep the region.
    if (significantMaxima.size() == 1) {
      splitted.add(region);
      return splitted;
    }

    // 3) We have multiple "significant" maxima => we will split
    // between local minima. Sort them:
    significantMaxima.sort(Integer::compareTo);

    // We'll track boundaries from startIdx => maxima => endIdx
    int currentStart = startIdx;
    for (int m = 0; m < significantMaxima.size(); m++) {
      int maxIdx = significantMaxima.get(m);

      // if not the last max, find local min between this max and the next
      int nextMaxIdx = (m < significantMaxima.size() - 1) ? significantMaxima.get(m + 1) : endIdx;

      int localMinIdx = findLocalMinBetween(y, maxIdx, nextMaxIdx);

      // define sub-range [currentStart.. localMinIdx]
      int subEnd = (localMinIdx > 0) ? localMinIdx : maxIdx;
      if (subEnd < currentStart) {
        subEnd = maxIdx;
      }

      // store sub-range
      double startX = x[currentStart];
      double endX = x[subEnd];
      if (startX > endX) {
        double tmp = startX;
        startX = endX;
        endX = tmp;
      }
      splitted.add(Range.closed(startX, endX));
      currentStart = subEnd + 1;
    }

    // if there's leftover region from the last local min to endIdx
    if (currentStart < endIdx) {
      double startX = x[currentStart];
      double endX = x[endIdx];
      if (startX < endX) {
        splitted.add(Range.closed(startX, endX));
      }
    }

    return splitted;
  }

  /**
   * Finds a local minimum index between 'from' and 'to'. If none found, returns -1. A naive
   * approach: pick the index with the lowest y among (from..to).
   */
  private int findLocalMinBetween(double[] y, int from, int to) {
    if (from >= to) {
      return -1;
    }
    double minVal = Double.MAX_VALUE;
    int minIdx = -1;
    for (int i = from + 1; i < to; i++) {
      if (y[i] < minVal) {
        minVal = y[i];
        minIdx = i;
      }
    }
    return minIdx;
  }

  // ------------------------------------------------------------------------
  // (3) Feature quality with advanced shape metrics
  // ------------------------------------------------------------------------

  /**
   * We combine our previous shape metrics with:
   * - Gaussian fitting => R^2 (0..1).
   * - Kurtosis => typically 3 for a normal distribution, <3 => platykurtic, >3 => leptokurtic.
   * - Tailing factor => e.g. (time at 5%peak height on right side / time at 5%peak on left).
   * <p>
   * We mix them into a single finalScore. You can adjust weighting as you like.
   */
  private double computeFeatureQuality(double[] x, double[] y, Range<Double> rg) {
    // Extract subarray
    int startIdx = findClosestIndex(x, rg.lowerEndpoint());
    int endIdx = findClosestIndex(x, rg.upperEndpoint());
    if (endIdx <= startIdx) {
      return 0.0;
    }

    double apexVal = 0;
    int apexIdx = startIdx;
    for (int i = startIdx; i <= endIdx; i++) {
      if (y[i] > apexVal) {
        apexVal = y[i];
        apexIdx = i;
      }
    }
    double leftVal = y[startIdx];
    double rightVal = y[endIdx];

    // 1) Asymmetry measure (like before)
    double meanEdge = (leftVal + rightVal) / 2.0;
    double asym = (meanEdge > 0) ? (apexVal / meanEdge) : 1.0;
    double asymPenalty = 2.0 - Math.abs(asym - 1.0);

    // 2) Zigzag
    int zigzagCount = 0;
    double prevSlope = 0;
    for (int i = startIdx + 1; i <= endIdx; i++) {
      double slope = y[i] - y[i - 1];
      if (prevSlope != 0 && slope * prevSlope < 0) {
        zigzagCount++;
      }
      if (Math.abs(slope) > 1e-7) {
        prevSlope = slope;
      }
    }
    double zigzagPenalty = zigzagCount * 0.2;

    // 3) Additional maxima
    int additionalMaxima = 0;
    for (int i = startIdx + 1; i < endIdx; i++) {
      if (y[i] > y[i - 1] && y[i] > y[i + 1] && (y[i] >= 0.5 * apexVal) && (i != apexIdx)) {
        additionalMaxima++;
      }
    }
    double maximaPenalty = additionalMaxima * 0.5;

    // 4) Gaussian fit R^2
    double r2Gauss = fitGaussian(x, y, startIdx, endIdx);

    // 5) Kurtosis
    double kurt = computeKurtosis(y, startIdx, endIdx);
    // typical normal => kurt ~3. We'll define a small penalty for large deviation from 3
    // For example: (2 - |kurt-3|/3) => near 2 if kurt=3, less if kurt is far from 3
    double kurtScore = 2.0 - (Math.abs(kurt - 3.0) / 3.0);
    if (kurtScore < 0) {
      kurtScore = 0;
    }

    // 6) Tailing factor
    double tailingFactor = computeTailingFactor(x, y, startIdx, endIdx, apexIdx, apexVal);
    // Ideal tailing factor is ~1. We'll do:
    // tailScore = 2.0 - (|tailingFactor-1|)
    double tailScore = 2.0 - Math.abs(tailingFactor - 1.0);
    if (tailScore < 0) {
      tailScore = 0;
    }

    // Combine everything
    // We can weight them. For example:
    // finalScore = asymPenalty + (0.5 * r2Gauss) + (0.5 * kurtScore) + (0.5 * tailScore)
    //              - zigzagPenalty - maximaPenalty
    // Adjust weighting to your preference
    double finalScore =
        asymPenalty + (0.5 * r2Gauss) + (0.5 * kurtScore) + (0.5 * tailScore) - zigzagPenalty
            - maximaPenalty;

    return finalScore;
  }

  /**
   * Naive Gaussian fit: We do a quick approach to see how well log(y) fits a parabola vs. x^2, or
   * use a simple non-linear least squares if you prefer. Here we return an R^2 in [0..1].
   */
  private double fitGaussian(double[] x, double[] y, int startIdx, int endIdx) {
    // If the subarray is too small or all zero, skip
    int length = endIdx - startIdx + 1;
    if (length < 3) {
      return 0.0;
    }

    // Build arrays for fitting
    List<Double> X = new ArrayList<>();
    List<Double> logY = new ArrayList<>();
    for (int i = startIdx; i <= endIdx; i++) {
      if (y[i] > 0) {
        X.add(x[i]);
        logY.add(Math.log(y[i]));
      }
    }
    if (X.size() < 3) {
      return 0.0;
    }

    // We'll do a naive polynomial fit: logY ~ a + bX + cX^2
    // Then compare actual vs predicted => compute R^2
    // (This is a rough approach to see if it's "Gaussian-ish".
    //  Real code might do a non-linear Gauss fit.)
    double[] arrX = X.stream().mapToDouble(Double::doubleValue).toArray();
    double[] arrLogY = logY.stream().mapToDouble(Double::doubleValue).toArray();

    // Solve polynomial regression: logY = a + b*x + c*x^2
    // We'll do a standard least-squares approach
    double[][] design = new double[arrX.length][3];
    for (int i = 0; i < arrX.length; i++) {
      double xx = arrX[i];
      design[i][0] = 1.0;    // intercept
      design[i][1] = xx;     // x
      design[i][2] = xx * xx;  // x^2
    }
    // Solve for [a,b,c] using normal equations
    double[] coeff = solveLeastSquares(design, arrLogY);
    // Evaluate predictions => compute R^2
    double ssTot = 0.0;
    double meanLogY = 0.0;
    for (double v : arrLogY) {
      meanLogY += v;
    }
    meanLogY /= arrLogY.length;

    double ssRes = 0.0;
    for (int i = 0; i < arrX.length; i++) {
      double pred = coeff[0] + coeff[1] * arrX[i] + coeff[2] * arrX[i] * arrX[i];
      double diffRes = arrLogY[i] - pred;
      ssRes += diffRes * diffRes;

      double diffTot = arrLogY[i] - meanLogY;
      ssTot += diffTot * diffTot;
    }
    if (ssTot < 1e-12) {
      return 0.0;
    }
    double r2 = 1.0 - (ssRes / ssTot);
    if (r2 < 0) {
      r2 = 0.0;
    }
    if (r2 > 1) {
      r2 = 1.0;
    }
    return r2;
  }

  /**
   * Minimal least-squares solver for designMatrix * param = obs, using normal eqn: (X^T X) param =
   * (X^T y). A real implementation might call Apache Commons Math or similar library.
   */
  private double[] solveLeastSquares(double[][] design, double[] obs) {
    // design is n x 3, obs is n x 1
    // Build X^T X => 3x3, X^T y => 3x1
    double[][] xtx = new double[3][3];
    double[] xty = new double[3];
    int n = design.length;
    for (int i = 0; i < n; i++) {
      double[] row = design[i];
      double val = obs[i];
      for (int r = 0; r < 3; r++) {
        xty[r] += row[r] * val;
        for (int c = 0; c < 3; c++) {
          xtx[r][c] += row[r] * row[c];
        }
      }
    }
    // solve 3x3 system
    return solve3x3(xtx, xty);
  }

  /**
   * Solve a 3x3 linear system A*x = b. Returns x or [0,0,0] if degenerate.
   */
  private double[] solve3x3(double[][] A, double[] b) {
    double det = determinant3x3(A);
    if (Math.abs(det) < 1e-12) {
      return new double[]{0, 0, 0};
    }
    // invert A or do partial pivot. For brevity, do naive Cramer's rule
    double[] x = new double[3];
    for (int i = 0; i < 3; i++) {
      double[][] Ai = copyMatrix(A);
      // replace column i with b
      for (int r = 0; r < 3; r++) {
        Ai[r][i] = b[r];
      }
      x[i] = determinant3x3(Ai) / det;
    }
    return x;
  }

  private double determinant3x3(double[][] m) {
    return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2]
        - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
  }

  private double[][] copyMatrix(double[][] src) {
    double[][] cpy = new double[src.length][src[0].length];
    for (int r = 0; r < src.length; r++) {
      System.arraycopy(src[r], 0, cpy[r], 0, src[r].length);
    }
    return cpy;
  }

  /**
   * Compute sample excess kurtosis in [startIdx..endIdx]. We'll use a standard formula for unbiased
   * sample kurtosis: kurt = (n*(n+1)/(n-1)(n-2)(n-3)) * sum((xi-mean)^4)/sd^4 -
   * 3*(n-1)^2/((n-2)(n-3)) We do a simpler approach if we want just an approximate measure.
   */
  private double computeKurtosis(double[] y, int startIdx, int endIdx) {
    int length = endIdx - startIdx + 1;
    if (length < 4) {
      return 3.0; // assume near normal
    }
    double mean = 0;
    for (int i = startIdx; i <= endIdx; i++) {
      mean += y[i];
    }
    mean /= length;

    double s2 = 0.0;
    double s4 = 0.0;
    for (int i = startIdx; i <= endIdx; i++) {
      double diff = (y[i] - mean);
      double d2 = diff * diff;
      s2 += d2;
      s4 += (d2 * d2);
    }
    double var = s2 / (length - 1);
    if (var < 1e-12) {
      return 3.0; // nearly constant => no shape
    }
    double m4 = s4 / length;
    double kurt = (m4 / (var * var)) - 3.0;
    // add 3 => we get "Pearson's definition" => normal=3
    double total = kurt + 3.0;
    return total;
  }

  /**
   * Compute tailing factor. For example, we measure the time from apex->5% of apex on left,
   * apex->5% on right, ratio the larger side vs. smaller side.
   */
  private double computeTailingFactor(double[] x, double[] y, int startIdx, int endIdx, int apexIdx,
      double apexVal) {
    if (apexVal < 1e-12) {
      return 1.0;
    }
    double fivePct = 0.05 * apexVal;

    // find left edge where y ~ 5% of apex
    int leftEdge = apexIdx;
    for (int i = apexIdx; i >= startIdx; i--) {
      if (y[i] < fivePct) {
        break;
      }
      leftEdge = i;
    }
    // find right edge
    int rightEdge = apexIdx;
    for (int i = apexIdx; i <= endIdx; i++) {
      if (y[i] < fivePct) {
        break;
      }
      rightEdge = i;
    }

    double leftWidth = Math.abs(x[apexIdx] - x[leftEdge]);
    double rightWidth = Math.abs(x[rightEdge] - x[apexIdx]);
    if (Math.min(leftWidth, rightWidth) < 1e-12) {
      return 1.0; // degenerate
    }
    // Tailing factor => ratio of bigger side over smaller side
    double bigger = Math.max(leftWidth, rightWidth);
    double smaller = Math.min(leftWidth, rightWidth);
    return bigger / smaller;
  }

  // ------------------------------------------------------------------------
  // Utilities
  // ------------------------------------------------------------------------
  private int findClosestIndex(double[] x, double val) {
    int idx = 0;
    double best = Double.MAX_VALUE;
    for (int i = 0; i < x.length; i++) {
      double diff = Math.abs(x[i] - val);
      if (diff < best) {
        best = diff;
        idx = i;
      }
    }
    return idx;
  }

  private static double median(double[] arr) {
    if (arr.length == 0) {
      return 0.0;
    }
    Arrays.sort(arr);
    int mid = arr.length / 2;
    if (arr.length % 2 == 0) {
      return (arr[mid - 1] + arr[mid]) / 2.0;
    } else {
      return arr[mid];
    }
  }

  /**
   * Simple container for a parameter combo
   */
  private record ParamCombo(double chromThreshold, double searchXWidth, double minRatio,
                            double minHeight, int minDataPoints) {

  }
}