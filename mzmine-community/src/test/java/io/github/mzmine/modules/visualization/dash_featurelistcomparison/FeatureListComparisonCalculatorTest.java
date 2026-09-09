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
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.DataPointSorter;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class FeatureListComparisonCalculatorTest {

  private static final FeatureListComparisonSettings SETTINGS = new FeatureListComparisonSettings(
      new MZTolerance(0.01, 10), new MZTolerance(0.01, 10), 3, 0.7);

  @Test
  void identicalSpectraProduceCompleteOverlap() {
    final SpectralFeature spectrumA = spectrum(1, 500d, 50d, 70d, 90d);
    final SpectralFeature spectrumB = spectrum(2, 500.002d, 50d, 70d, 90d);

    final FeatureListPairSimilarity result = FeatureListComparisonCalculator.compare(
        List.of(spectrumA), List.of(spectrumB), SETTINGS);

    assertEquals(1d, result.jaccardSimilarity(), 1e-12);
    assertEquals(1, result.matchedSpectra());
    assertEquals(1d, result.meanMatchedCosineSimilarity(), 1e-12);
  }

  @Test
  void unrelatedFragmentsDoNotMatch() {
    final SpectralFeature spectrumA = spectrum(1, 500d, 50d, 70d, 90d);
    final SpectralFeature spectrumB = spectrum(2, 500.002d, 150d, 170d, 190d);

    final FeatureListPairSimilarity result = FeatureListComparisonCalculator.compare(
        List.of(spectrumA), List.of(spectrumB), SETTINGS);

    assertEquals(0d, result.jaccardSimilarity(), 1e-12);
    assertEquals(0, result.matchedSpectra());
  }

  @Test
  void oneSpectrumCannotMatchMultipleSpectra() {
    final SpectralFeature spectrumA1 = spectrum(1, 500d, 50d, 70d, 90d);
    final SpectralFeature spectrumA2 = spectrum(2, 500.004d, 50d, 70d, 90d);
    final SpectralFeature spectrumB = spectrum(3, 500.002d, 50d, 70d, 90d);

    final FeatureListPairSimilarity result = FeatureListComparisonCalculator.compare(
        List.of(spectrumA1, spectrumA2), List.of(spectrumB), SETTINGS);

    assertEquals(0.5d, result.jaccardSimilarity(), 1e-12);
    assertEquals(1, result.matchedSpectra());
    assertEquals(1, result.unmatchedSpectraA());
    assertEquals(0, result.unmatchedSpectraB());
  }

  @Test
  void precursorToleranceBlocksOtherwiseIdenticalSpectra() {
    final SpectralFeature spectrumA = spectrum(1, 500d, 50d, 70d, 90d);
    final SpectralFeature spectrumB = spectrum(2, 501d, 50d, 70d, 90d);

    final FeatureListPairSimilarity result = FeatureListComparisonCalculator.compare(
        List.of(spectrumA), List.of(spectrumB), SETTINGS);

    assertEquals(0, result.matchedSpectra());
  }

  private static @NotNull SpectralFeature spectrum(final int rowId, final double precursorMz,
      final double... fragmentMzs) {
    final DataPoint[] dataPoints = new DataPoint[fragmentMzs.length];
    for (int i = 0; i < fragmentMzs.length; i++) {
      dataPoints[i] = new SimpleDataPoint(fragmentMzs[i], fragmentMzs.length - i);
    }
    Arrays.sort(dataPoints, DataPointSorter.DEFAULT_INTENSITY);
    return new SpectralFeature(rowId, precursorMz, dataPoints);
  }
}
