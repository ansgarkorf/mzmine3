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

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.features.Feature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One measurement unit throughout a comparison; unavailable values are never replaced by height.
 */
public enum ComparisonQuantification {
  AREA, HEIGHT;

  double abundance(@Nullable final Feature feature) {
    if (feature == null || feature.getFeatureStatus() == FeatureStatus.UNKNOWN) {
      return 0d;
    }
    final Float value = switch (this) {
      case AREA -> feature.getArea();
      case HEIGHT -> feature.getHeight();
    };
    return value != null && Float.isFinite(value) && value >= 0f ? value : Double.NaN;
  }

  @Override
  public @NotNull String toString() {
    return switch (this) {
      case AREA -> "Peak area";
      case HEIGHT -> "Peak height";
    };
  }
}

