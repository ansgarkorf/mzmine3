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
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * An updated local-minimum-based feature resolver that optimizes its parameters GLOBALLY across
 * multiple chromatograms, focusing on:
 * 1) Good peak shape metrics (asymmetry, zigzag, Gaussian fit, etc.)
 * 2) Maximizing the number of detected features at decent quality
 * <p>
 * We remove any multi-peak splitting logic. Each local-min region = 1 feature.
 */
public class AutoGlobalMinimumSearchResolver extends AbstractResolver {

  private static final Logger logger = Logger.getLogger(
      AutoGlobalMinimumSearchResolver.class.getName());


  // The globally best parameters
  private double bestChromThreshold;
  private double bestSearchXWidth;
  private double bestMinRatio;
  private double bestMinAbsoluteHeight;
  private int bestMinDataPoints;

  private boolean isCalibrated = false;

  public AutoGlobalMinimumSearchResolver(ParameterSet parameterSet, ModularFeatureList flist) {
    super(parameterSet, flist);
  }

  @Override
  public @NotNull Class<? extends MZmineModule> getModuleClass() {
    // your module class
    return AutoMinimumSearchResolverModule.class;
  }

  /**
   * If MZmine calls this without calibration, fallback. Otherwise, use best parameters.
   */
  @Override
  @NotNull
  public List<Range<Double>> resolve(double[] x, double[] y) {
    if (!isCalibrated) {
      logger.warning("Resolver called without calibration. Using fallback parameters.");
      return fallbackResolve(x, y);
    }
    ParamCombo best = new ParamCombo(bestChromThreshold, bestSearchXWidth, bestMinRatio,
        bestMinAbsoluteHeight, bestMinDataPoints);
    logger.info(String.format("Using best parameters for resolving: %s", best));
    return detectPeaksWithCombo(x, y, best);
  }

  /**
   * The method to calibrate parameters across ALL chromatograms. We do a grid search, but in the
   * final scoring we factor in both (avg. shape quality) and (# of detected features).
   */
  public void calibrateGlobalParameters(List<double[]> allX, List<double[]> allY) {
    if (allX == null || allY == null || allX.size() != allY.size() || allX.isEmpty()) {
      System.err.println("Invalid calibration data");
      return;
    }

    // 1) Build grid of parameter combos
    List<ParamCombo> combos = generateParameterGrid(allX, allY);
    logger.info(
        String.format("Generated %d parameter combinations for grid search.", combos.size()));
    // 2) Evaluate combos => pick best
    double bestScore = Double.NEGATIVE_INFINITY;
    ParamCombo best = null;

    for (ParamCombo pc : combos) {
      double totalScore = 0.0;
      int countChrom = 0;

      for (int i = 0; i < allX.size(); i++) {
        double[] x = allX.get(i);
        double[] y = allY.get(i);
        if (x.length < 3) {
          continue;
        }

        double score = testParametersOnChrom(x, y, pc);
        if (score > -9999.0) {
          totalScore += score;
          countChrom++;
        }
      }

      if (countChrom > 0) {
        double avgScore = totalScore / countChrom;
        logger.fine(String.format("Combo %s: Avg Score = %.3f", pc, avgScore));

        if (avgScore > bestScore) {
          bestScore = avgScore;
          best = pc;
        }
      }
    }

    if (best == null) {
      System.err.println("No valid combos => fallback");
      return;
    }

    // 3) Store
    bestChromThreshold = best.chromThreshold;
    bestSearchXWidth = best.searchXWidth;
    bestMinRatio = best.minRatio;
    bestMinAbsoluteHeight = best.minHeight;
    bestMinDataPoints = best.minDataPoints;
    isCalibrated = true;

    System.out.printf("Global param search => best combo: %s, bestScore=%.3f%n", best, bestScore);
  }

  /**
   * Once calibrated, apply best to each chromatogram if you like
   */
  public List<List<Range<Double>>> applyBestParameters(List<double[]> allX, List<double[]> allY) {
    List<List<Range<Double>>> results = new ArrayList<>();
    if (!isCalibrated) {
      return results;
    }
    ParamCombo best = new ParamCombo(bestChromThreshold, bestSearchXWidth, bestMinRatio,
        bestMinAbsoluteHeight, bestMinDataPoints);
    for (int i = 0; i < allX.size(); i++) {
      double[] x = allX.get(i);
      double[] y = allY.get(i);
      if (x.length < 3) {
        results.add(new ArrayList<>());
        continue;
      }
      List<Range<Double>> peaks = detectPeaksWithCombo(x, y, best);
      // No splitting logic
      results.add(peaks);
    }
    return results;
  }

  private List<Range<Double>> fallbackResolve(double[] x, double[] y) {
    // Minimal defaults
    ParamCombo defaults = new ParamCombo(0.0, (x[x.length - 1] - x[0]) * 0.1, 1.2, 1000.0, 3);
    return detectPeaksWithCombo(x, y, defaults);
  }

  /**
   * testParametersOnChrom => detect peaks => measure a combined score that accounts for both
   * average shape quality and number of features.
   */
  private double testParametersOnChrom(double[] x, double[] y, ParamCombo pc) {
    logger.info(String.format("Testing parameter combo: %s", pc));

    double[] yCopy = Arrays.copyOf(y, y.length);
    for (int i = 0; i < yCopy.length; i++) {
      if (yCopy[i] < pc.chromThreshold) {
        yCopy[i] = 0.0;
      }
    }

    List<Range<Double>> peaks = detectPeaksWithCombo(x, yCopy, pc);
    if (peaks.isEmpty()) {
      logger.info("No peaks detected for this combo.");
      return -9999.0;
    }

    double totalQ = 0.0;
    for (Range<Double> rg : peaks) {
      double quality = computeFeatureQuality(x, yCopy, rg);
      totalQ += quality;
      logger.finest(String.format("Peak %s: Quality = %.3f", rg, quality));
    }

    double avgQuality = totalQ / peaks.size();
    double alpha = 0.05;
    double score = avgQuality + alpha * peaks.size();

    logger.info(String.format("Combo %s: Avg Quality = %.3f, Total Features = %d, Score = %.3f", pc,
        avgQuality, peaks.size(), score));
    return score;
  }

  /**
   * Basic local-min detection with param combo. No multi-peak splitting.
   */
  private List<Range<Double>> detectPeaksWithCombo(double[] x, double[] y, ParamCombo pc) {
    List<Range<Double>> peaks = new ArrayList<>();
    final int n = x.length;

    outerLoop:
    for (int start = 0; start < n - 2; start++) {
      if (y[start] == 0.0 || y[start + 1] == 0.0) {
        continue;
      }
      double regionTop = y[start];

      innerLoop:
      for (int end = start + 1; end < n; end++) {
        regionTop = Math.max(regionTop, y[end]);

        // finalize if next=0 or last
        if (end == n - 1 || y[end + 1] == 0.0) {
          double leftEdge = y[start];
          double rightEdge = y[end];
          int newStart = tryToFinalizePeak(x, y, start, end, regionTop, pc, leftEdge, rightEdge,
              peaks);
          start = newStart;
          continue outerLoop;
        }

        // if we exceed searchXWidth => local-min boundary
        if ((x[end] - x[start]) >= pc.searchXWidth) {
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
   * Try to finalize a peak [start..end]. If it passes shape constraints, store it.
   */
  private int tryToFinalizePeak(double[] x, double[] y, int start, int end, double apex,
      ParamCombo pc, double leftEdge, double rightEdge, List<Range<Double>> peaks) {
    int numPoints = end - start + 1;
    if (numPoints >= pc.minDataPoints && apex >= pc.minHeight) {
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
   * Just expand to neighbors if we want. No multi-splitting.
   */
  private Range<Double> adjustStartAndEnd(double[] x, double[] y, int s, int e) {
    int start = s;
    if (start > 0 && y[start] != 0 && y[start - 1] == 0) {
      start--;
    }
    int end = e;
    if (end < y.length - 1 && y[end] != 0 && y[end + 1] == 0) {
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

  /**
   * Computes the quality score of a detected feature based on peak shape metrics.
   * <p>
   * Metrics used:
   * - Asymmetry Score
   * - Zigzag Penalty
   * - Gaussian Fit (R²)
   * - Kurtosis
   * - Tailing Factor
   *
   * @param x  The x-coordinates (e.g., retention times) of the chromatogram.
   * @param y  The y-coordinates (e.g., intensities) of the chromatogram.
   * @param rg The range of the peak (start and end in x).
   * @return A quality score for the peak. Higher scores indicate better quality.
   */
  private double computeFeatureQuality(double[] x, double[] y, Range<Double> rg) {
    int s = findClosestIndex(x, rg.lowerEndpoint());
    int e = findClosestIndex(x, rg.upperEndpoint());
    if (e <= s) {
      return 0.0;
    }

    // Peak characteristics
    double apexVal = 0.0;
    int apexIdx = s;
    for (int i = s; i <= e; i++) {
      if (y[i] > apexVal) {
        apexVal = y[i];
        apexIdx = i;
      }
    }
    if (apexVal <= 0) {
      return 0.0;
    }

    // 1. Asymmetry
    double leftVal = y[s];
    double rightVal = y[e];
    double meanEdge = (leftVal + rightVal) / 2.0;
    double asym = (meanEdge > 0) ? (apexVal / meanEdge) : 1.0;
    double asymmetryScore = 2.0 - Math.abs(asym - 1.0); // Symmetry => higher score

    // 2. Zigzag Penalty
    int zigzagCount = 0;
    double prevSlope = 0.0;
    for (int i = s + 1; i <= e; i++) {
      double slope = y[i] - y[i - 1];
      if (prevSlope != 0.0 && slope * prevSlope < 0.0) {
        zigzagCount++;
      }
      prevSlope = slope;
    }
    double zigzagPenalty = zigzagCount * 0.2;

    // 3. Gaussian Fit (R²)
    double r2Gauss = fitGaussian(x, y, s, e);

    // 4. Kurtosis
    double kurtosis = computeKurtosis(y, s, e);
    double kurtosisScore = Math.min(1.0, kurtosis / 10.0); // Normalize kurtosis to [0, 1]

    // 5. Tailing Factor
    double tailingFactor = computeTailingFactor(y, apexIdx, s, e);
    double tailingPenalty = Math.max(0.0, tailingFactor - 1.2); // Penalize tailing >1.2

    // Combine metrics
    double finalScore =
        (asymmetryScore + 0.5 * r2Gauss + kurtosisScore) - (zigzagPenalty + tailingPenalty);

    return Math.max(0.0, finalScore); // Ensure score is non-negative
  }

  /**
   * Computes the kurtosis of a peak within a given range.
   *
   * @param y     The intensity values of the chromatogram.
   * @param start The start index of the peak.
   * @param end   The end index of the peak.
   * @return The kurtosis of the peak. A value near 3 indicates a Gaussian-like peak.
   */
  private double computeKurtosis(double[] y, int start, int end) {
    if (end <= start + 1) {
      return 0.0; // Not enough points for meaningful kurtosis
    }

    double sum = 0.0, sumSq = 0.0, sumQuad = 0.0;
    int n = end - start + 1;

    for (int i = start; i <= end; i++) {
      double yi = y[i];
      sum += yi;
      sumSq += yi * yi;
      sumQuad += yi * yi * yi * yi;
    }

    double mean = sum / n;
    double variance = (sumSq / n) - (mean * mean);

    if (variance <= 0) {
      return 0.0; // Variance must be positive
    }

    double kurtosis = (sumQuad / n) / (variance * variance);
    return kurtosis;
  }

  /**
   * Computes the tailing factor of a peak, which measures asymmetry.
   *
   * @param y       The intensity values of the chromatogram.
   * @param apexIdx The index of the peak apex.
   * @param start   The start index of the peak.
   * @param end     The end index of the peak.
   * @return The tailing factor. A value close to 1 indicates symmetry; values >1 indicate tailing.
   */
  private double computeTailingFactor(double[] y, int apexIdx, int start, int end) {
    if (apexIdx <= start || apexIdx >= end) {
      return 1.0; // Apex is at boundary; no tailing factor
    }

    double leftWidth = 0.0;
    double rightWidth = 0.0;

    // Find the width to the left of the apex
    for (int i = apexIdx; i >= start; i--) {
      if (y[i] <= y[start] / 2.0) {
        leftWidth = Math.abs(i - apexIdx);
        break;
      }
    }

    // Find the width to the right of the apex
    for (int i = apexIdx; i <= end; i++) {
      if (y[i] <= y[end] / 2.0) {
        rightWidth = Math.abs(i - apexIdx);
        break;
      }
    }

    if (leftWidth == 0 || rightWidth == 0) {
      return 1.0; // Symmetrical if one side width cannot be measured
    }

    return Math.max(leftWidth, rightWidth) / Math.min(leftWidth, rightWidth);
  }

  private double fitGaussian(double[] x, double[] y, int startIdx, int endIdx) {
    // minimal code
    int length = endIdx - startIdx + 1;
    if (length < 3) {
      return 0.0;
    }
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

    // naive polynomial fit => R^2
    double[] arrX = X.stream().mapToDouble(Double::doubleValue).toArray();
    double[] arrLogY = logY.stream().mapToDouble(Double::doubleValue).toArray();

    double[][] design = new double[arrX.length][3];
    for (int i = 0; i < arrX.length; i++) {
      design[i][0] = 1.0;
      design[i][1] = arrX[i];
      design[i][2] = arrX[i] * arrX[i];
    }
    double[] coeff = solveLeastSquares(design, arrLogY);
    double ssRes = 0.0, ssTot = 0.0;
    double mean = 0.0;
    for (double v : arrLogY) {
      mean += v;
    }
    mean /= arrLogY.length;

    for (int i = 0; i < arrX.length; i++) {
      double pred = coeff[0] + coeff[1] * arrX[i] + coeff[2] * arrX[i] * arrX[i];
      double diffRes = arrLogY[i] - pred;
      ssRes += diffRes * diffRes;
      double diffTot = arrLogY[i] - mean;
      ssTot += diffTot * diffTot;
    }
    if (ssTot < 1e-12) {
      return 0.0;
    }
    double r2 = 1.0 - ssRes / ssTot;
    if (r2 < 0) {
      r2 = 0;
    }
    if (r2 > 1) {
      r2 = 1;
    }
    return r2;
  }

  private double[] solveLeastSquares(double[][] A, double[] b) {
    // normal eqn for 3 param => 3x3
    double[][] xtx = new double[3][3];
    double[] xty = new double[3];
    int n = A.length;
    for (int i = 0; i < n; i++) {
      double[] row = A[i];
      double val = b[i];
      for (int r = 0; r < 3; r++) {
        xty[r] += row[r] * val;
        for (int c = 0; c < 3; c++) {
          xtx[r][c] += row[r] * row[c];
        }
      }
    }
    return solve3x3(xtx, xty);
  }

  private double[] solve3x3(double[][] M, double[] b) {
    double det = det3(M);
    if (Math.abs(det) < 1e-12) {
      return new double[]{0, 0, 0};
    }
    double[] x = new double[3];
    for (int i = 0; i < 3; i++) {
      double[][] Mi = copyMat(M);
      for (int r = 0; r < 3; r++) {
        Mi[r][i] = b[r];
      }
      x[i] = det3(Mi) / det;
    }
    return x;
  }

  private double det3(double[][] m) {
    return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2]
        - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
  }

  private double[][] copyMat(double[][] src) {
    double[][] cpy = new double[src.length][src[0].length];
    for (int r = 0; r < src.length; r++) {
      System.arraycopy(src[r], 0, cpy[r], 0, src[r].length);
    }
    return cpy;
  }

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

  // ---------------------------------------------------------
  // ParamCombo + parameter grid
  // ---------------------------------------------------------
  private List<ParamCombo> generateParameterGrid(List<double[]> allX, List<double[]> allY) {
    // Compute a baseline from the lowest 10% of intensities for dynamic range adjustment
    List<Double> intensities = new ArrayList<>();
    for (double[] arr : allY) {
      for (double val : arr) {
        intensities.add(val);
      }
    }
    double[] merged = intensities.stream().mapToDouble(Double::doubleValue).toArray();
    Arrays.sort(merged);
    int cut = (int) (merged.length * 0.1);
    if (cut < 1) {
      cut = 1;
    }
    double baseline = median(Arrays.copyOf(merged, cut));

    // Find maximum chromatogram span among all inputs
    double maxSpan = 0.0;
    for (double[] xx : allX) {
      if (xx.length < 2) {
        continue;
      }
      double span = xx[xx.length - 1] - xx[0];
      if (span > maxSpan) {
        maxSpan = span;
      }
    }

    // Adjusted parameter ranges for a larger grid
    double[] chromThresholds = {0.0, baseline * 0.1, baseline * 0.2, baseline * 0.5, baseline,
        baseline * 2, baseline * 5};
    double[] searchXWidths = {0.01 * maxSpan, 0.02 * maxSpan, 0.05 * maxSpan, 0.1 * maxSpan,
        0.2 * maxSpan, 0.3 * maxSpan};
    double[] minRatios = {1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1};
    double[] minHeights = {0.0, baseline * 0.5, baseline, baseline * 2, baseline * 5};
    int[] minDataPoints = {3, 5, 7, 10, 15};

    // Build the parameter combinations
    List<ParamCombo> combos = new ArrayList<>();
    for (double chromThreshold : chromThresholds) {
      for (double searchXWidth : searchXWidths) {
        for (double minRatio : minRatios) {
          for (double minHeight : minHeights) {
            for (int minDataPoint : minDataPoints) {
              combos.add(
                  new ParamCombo(chromThreshold, searchXWidth, minRatio, minHeight, minDataPoint));
            }
          }
        }
      }
    }

    System.out.printf("Generated %d parameter combinations for grid search.%n", combos.size());
    return combos;
  }

  private double median(double[] arr) {
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

  private record ParamCombo(double chromThreshold, double searchXWidth, double minRatio,
                            double minHeight, int minDataPoints) {

  }
}