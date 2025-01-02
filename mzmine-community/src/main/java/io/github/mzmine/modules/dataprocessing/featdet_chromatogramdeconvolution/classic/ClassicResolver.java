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
import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic.ClassicResolverParameters.MIN_ABSOLUTE_HEIGHT;
import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic.ClassicResolverParameters.MIN_RATIO;
import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic.ClassicResolverParameters.MIN_RELATIVE_HEIGHT;
import static io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic.ClassicResolverParameters.SEARCH_RT_RANGE;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.AbstractResolver;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ClassicResolver extends AbstractResolver {

  private final ParameterSet parameters;
  private final double searchXWidth;
  private final double minRatio;
  private final int minDataPoints;
  private final double minAbsoluteHeight;
  private final double minRelativeHeight;

  double[] xBuffer;
  double[] yBuffer;


  public ClassicResolver(ParameterSet parameterSet, ModularFeatureList flist) {
    super(parameterSet, flist);
    this.parameters = parameterSet;
    minDataPoints = parameters.getParameter(MIN_NUMBER_OF_DATAPOINTS).getValue();
    searchXWidth = parameters.getParameter(SEARCH_RT_RANGE).getValue();
    minRatio = parameters.getParameter(MIN_RATIO).getValue();
    minAbsoluteHeight = parameters.getParameter(MIN_ABSOLUTE_HEIGHT).getValue();
    minRelativeHeight = parameters.getParameter(MIN_RELATIVE_HEIGHT).getValue();
  }


  @Override
  public @NotNull Class<? extends MZmineModule> getModuleClass() {
    return ClassicResolverModule.class;
  }

  /**
   * Dynamically calculate a threshold for the entire chromatogram. For example, we can use median +
   * k * MAD (Median Absolute Deviation). This is just one of many possible dynamic strategies.
   */
  private double calculateDynamicThreshold(double[] intensities) {
    // Copy intensities so we don't mutate the original array when sorting
    double[] copy = Arrays.copyOf(intensities, intensities.length);

    // 1) Calculate median
    double median = median(copy);

    // 2) Calculate MAD
    double mad = medianAbsoluteDeviation(copy, median);

    // 3) Define threshold as median + factor * MAD
    // The factor (e.g., 2 or 3) is somewhat arbitrary; adjust to desired sensitivity
    return median + 2.0 * mad;
  }

  private double median(double[] data) {
    Arrays.sort(data);
    int mid = data.length / 2;
    if (data.length % 2 == 0) {
      return (data[mid - 1] + data[mid]) / 2.0;
    } else {
      return data[mid];
    }
  }

  private double medianAbsoluteDeviation(double[] data, double median) {
    double[] deviations = new double[data.length];
    for (int i = 0; i < data.length; i++) {
      deviations[i] = Math.abs(data[i] - median);
    }
    return median(deviations);
  }

  /**
   * @param x domain values (e.g., RT or mobility), strictly monotonically increasing
   * @param y intensity values for each domain point
   * @return list of resolved peak ranges
   */
  @Override
  @NotNull
  public List<Range<Double>> resolve(double[] x, double[] y) {
    if (x.length != y.length) {
      throw new AssertionError("Lengths of x and y arrays must match.");
    }

    final int valueCount = x.length;
    List<Range<Double>> resolved = new ArrayList<>();

    if (valueCount == 0) {
      return resolved;
    }

    // Instead of user-set threshold, compute dynamic threshold for entire array
    double dynamicThreshold = calculateDynamicThreshold(y);

    // Remove all data points below dynamic threshold
    double maxY = 0;
    for (int i = 0; i < y.length; i++) {
      if (y[i] < dynamicThreshold) {
        y[i] = 0.0;
      }
      if (y[i] > maxY) {
        maxY = y[i];
      }
    }

    // The final minHeight depends on max signal now that noise is zeroed
    final double minHeight = Math.max(minAbsoluteHeight, minRelativeHeight * maxY);
    final int lastScan = valueCount - 1;

    // Main local-minimum search logic
    startSearch:
    for (int currentRegionStart = 0; currentRegionStart < lastScan - 2; currentRegionStart++) {

      // Need at least two consecutive non-zero data points to consider a start
      if (y[currentRegionStart] == 0.0 || y[currentRegionStart + 1] == 0.0) {
        continue;
      }

      double currentRegionHeight = y[currentRegionStart];

      endSearch:
      for (int currentRegionEnd = currentRegionStart + 1; currentRegionEnd < valueCount;
          currentRegionEnd++) {

        // Update height of current region
        currentRegionHeight = Math.max(currentRegionHeight, y[currentRegionEnd]);

        // If next intensity is 0 or we've reached the end, finalize the region
        if (currentRegionEnd == lastScan || y[currentRegionEnd + 1] == 0.0) {

          // Intensities at the edges
          final double peakMinLeft = y[currentRegionStart];
          final double peakMinRight = y[currentRegionEnd];

          // Attempt to finalize a peak
          currentRegionStart = tryToFinalizePeak(x, y, currentRegionEnd, currentRegionStart,
              currentRegionHeight, minHeight, peakMinLeft, peakMinRight, resolved);
          continue startSearch;
        }

        // If we've reached minimum required width
        if (x[currentRegionEnd] - x[currentRegionStart] >= searchXWidth) {

          // Simple check for local left side
          final Range<Double> checkRange = Range.closed(x[currentRegionEnd] - searchXWidth,
              x[currentRegionEnd] + searchXWidth);

          // Check left side
          for (int i = currentRegionEnd - 1; i > 0; i--) {
            if (!checkRange.contains(x[i])) {
              break;
            }
            if (y[i] < y[currentRegionEnd]) {
              // Not a minimum => break the loop
              continue endSearch;
            }
          }

          // Check right side
          for (int i = currentRegionEnd + 1; i < valueCount; i++) {
            if (!checkRange.contains(x[i])) {
              break;
            }
            if (y[i] < y[currentRegionEnd]) {
              continue endSearch;
            }
          }

          final double peakMinLeft = y[currentRegionStart];
          final double peakMinRight = y[currentRegionEnd];

          // Ratio check
          if (currentRegionHeight >= peakMinRight * minRatio) {
            currentRegionStart = tryToFinalizePeak(x, y, currentRegionEnd, currentRegionStart,
                currentRegionHeight, minHeight, peakMinLeft, peakMinRight, resolved);
            continue startSearch;
          }
        }
      }
    }
    return resolved;
  }

  /**
   * Attempt to finalize peak and return updated region start
   */
  private int tryToFinalizePeak(double[] x, double[] y, int currentRegionEnd,
      int currentRegionStart, double currentRegionHeight, double minHeight, double peakMinLeft,
      double peakMinRight, List<Range<Double>> resolved) {

    final int numberOfDataPoints = currentRegionEnd - currentRegionStart + 1;

    // Check shape
    if (checkPeakShape(x, numberOfDataPoints, currentRegionHeight, minHeight, peakMinLeft,
        peakMinRight)) {
      final Range<Double> range = adjustStartAndEnd(x, y, currentRegionStart, currentRegionEnd);
      resolved.add(range);
    }

    // Move region start to just before currentRegionEnd
    currentRegionStart = currentRegionEnd - 1;
    return currentRegionStart;
  }

  private boolean checkPeakShape(double[] x, int numberOfDataPoints, double currentRegionHeight,
      double minHeight, double peakMinLeft, double peakMinRight) {
    return numberOfDataPoints >= minDataPoints && currentRegionHeight >= minHeight
        && currentRegionHeight >= peakMinLeft * minRatio
        && currentRegionHeight >= peakMinRight * minRatio;
  }

  /**
   * Include zero-intensity neighbors if relevant
   */
  private static @NotNull Range<Double> adjustStartAndEnd(double[] x, double[] y,
      int currentRegionStart, int currentRegionEnd) {

    int start = currentRegionStart;
    if (y[currentRegionStart] != 0 && currentRegionStart > 0 && y[currentRegionStart - 1] == 0.0) {
      start = currentRegionStart - 1;
    }

    int end = currentRegionEnd;
    if (y[currentRegionEnd] != 0 && currentRegionEnd < y.length - 1
        && y[currentRegionEnd + 1] == 0.0) {
      end = currentRegionEnd + 1;
    }

    return Range.closed(x[start], x[end]);
  }
}