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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.MathUtils;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The second dimension of a GCxGC row to gap-fill.
 *
 * @param secondDim  the second dimension (second column retention time) of the row
 * @param leftWidth  median distance of the lower 2D edge to the 2D apex of the features
 * @param rightWidth median distance of the upper 2D edge to the 2D apex of the features
 */
public record GcEiGapFillSecondDimension(float secondDim, float leftWidth, float rightWidth) {

  /**
   * @param features the features to derive the 2D feature shape from
   * @return the second dimension or null if the features have no second dimension
   */
  public static @Nullable GcEiGapFillSecondDimension fromFeatures(
      @NotNull final List<? extends Feature> features) {
    final double[] secondDims = new double[features.size()];
    final double[] leftWidths = new double[features.size()];
    final double[] rightWidths = new double[features.size()];
    for (int i = 0; i < features.size(); i++) {
      final Feature feature = features.get(i);
      final Float secondDim = feature.getSecondDimensionRT();
      final Range<Float> range = feature.getSecondDimensionRTRange();
      if (!GcxGcUtils.isGcxGcFeature(feature) || secondDim == null || range == null) {
        return null;
      }
      secondDims[i] = secondDim;
      leftWidths[i] = secondDim - range.lowerEndpoint();
      rightWidths[i] = range.upperEndpoint() - secondDim;
    }
    if (features.isEmpty()) {
      return null;
    }
    return new GcEiGapFillSecondDimension((float) MathUtils.calcMedian(secondDims),
        (float) MathUtils.calcMedian(leftWidths), (float) MathUtils.calcMedian(rightWidths));
  }
}
