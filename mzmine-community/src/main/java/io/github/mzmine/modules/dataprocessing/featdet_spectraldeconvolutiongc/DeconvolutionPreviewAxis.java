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

package io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.util.GcxGcUtils;
import java.text.NumberFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The y-axis of the spectral deconvolution preview. The x-axis is always the retention time.
 */
public enum DeconvolutionPreviewAxis {
  MZ, SECOND_DIMENSION;

  // box height of a feature in the m/z dimension
  private static final double MZ_BOX_HEIGHT = 0.5;

  /**
   * @return {@link #SECOND_DIMENSION} for GCxGC feature lists, otherwise {@link #MZ}
   */
  public static @NotNull DeconvolutionPreviewAxis forFeatureList(@Nullable FeatureList flist) {
    return GcxGcUtils.isGcxGc(flist) ? SECOND_DIMENSION : MZ;
  }

  /**
   * @return the y-value of the feature or null if not available
   */
  public @Nullable Double value(@NotNull Feature feature) {
    return switch (this) {
      case MZ -> feature.getMZ();
      case SECOND_DIMENSION -> {
        final Float secondDim = feature.getSecondDimensionRT();
        yield secondDim == null ? null : secondDim.doubleValue();
      }
    };
  }

  /**
   * @return the y-range of the feature box: the second dimension range of the feature or a fixed
   * height around the m/z. Null if the value is not available.
   */
  public @Nullable Range<Double> range(@NotNull Feature feature) {
    return switch (this) {
      case MZ -> Range.closed(feature.getMZ() - MZ_BOX_HEIGHT / 2, feature.getMZ() + MZ_BOX_HEIGHT / 2);
      case SECOND_DIMENSION -> {
        final Range<Float> range = feature.getSecondDimensionRTRange();
        yield range == null ? null
            : Range.closed(range.lowerEndpoint().doubleValue(), range.upperEndpoint().doubleValue());
      }
    };
  }

  public @NotNull String axisLabel() {
    final NumberFormats formats = ConfigService.getGuiFormats();
    return switch (this) {
      case MZ -> "m/z";
      case SECOND_DIMENSION -> formats.unit("2D retention time", "min");
    };
  }

  public @NotNull NumberFormat format() {
    final NumberFormats formats = ConfigService.getGuiFormats();
    return switch (this) {
      case MZ -> formats.mzFormat();
      case SECOND_DIMENSION -> GcxGcUtils.createSecondDimensionRTFormat();
    };
  }

  @Override
  public String toString() {
    return switch (this) {
      case MZ -> "m/z";
      case SECOND_DIMENSION -> "2D RT";
    };
  }
}
