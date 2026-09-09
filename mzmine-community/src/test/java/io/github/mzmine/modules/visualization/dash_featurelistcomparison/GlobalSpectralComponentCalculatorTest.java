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

package io.github.mzmine.modules.visualization.dash_featurelistcomparison;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.DataPointSorter;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class GlobalSpectralComponentCalculatorTest {

  private static final FeatureListComparisonSettings SETTINGS = new FeatureListComparisonSettings(
      new MZTolerance(0.01, 10), new MZTolerance(0.01, 10), 3, 0.7);

  @Test
  void oneGlobalAssignmentDrivesEveryProjectPair() {
    final SpectralFeature projectA = spectrum(0, 0, "A", 1, 500d, 50d, 70d, 90d);
    final SpectralFeature projectB = spectrum(1, 1, "B", 2, 500.002d, 50d, 70d, 90d);
    final SpectralFeature projectC = spectrum(2, 2, "C", 3, 500.003d, 150d, 170d, 190d);

    final GlobalSpectralComponentCalculator.Result result = GlobalSpectralComponentCalculator.calculate(
        List.of(projectC, projectB, projectA), 3, SETTINGS, () -> false);

    assertEquals(2, result.components().size());
    assertEquals(2,
        result.components().stream().mapToInt(SpectralComponent::prevalence).max().orElseThrow());
    assertEquals(1d, result.similarities()[0][1].jaccardSimilarity(), 1e-12);
    assertEquals(0d, result.similarities()[0][2].jaccardSimilarity(), 1e-12);
  }

  private static @NotNull SpectralFeature spectrum(final int globalIndex, final int projectIndex,
      @NotNull final String projectName, final int rowId, final double precursorMz,
      final double... fragmentMzs) {
    final DataPoint[] dataPoints = new DataPoint[fragmentMzs.length];
    for (int i = 0; i < fragmentMzs.length; i++) {
      dataPoints[i] = new SimpleDataPoint(fragmentMzs[i], fragmentMzs.length - i);
    }
    Arrays.sort(dataPoints, DataPointSorter.DEFAULT_INTENSITY);
    return new SpectralFeature(globalIndex, projectIndex, projectName, PolarityType.POSITIVE, 1,
        rowId, precursorMz, dataPoints, new double[]{1d, 2d}, List.of());
  }
}
