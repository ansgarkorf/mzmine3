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

package io.github.mzmine.modules.dataanalysis.compounddashboard;

import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapProjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The plot left of the chromatograms in the compound dashboard. The toolbar button cycles through
 * the values in their order.
 */
public enum CompoundDashboardSidePlot {
  /**
   * The 4D feature plot of the feature list
   */
  FEATURE_PLOT("Feature plot"),
  /**
   * The 2D visualizer of the selected raw data file around the selected compound
   */
  MAP_2D("2D plot"),
  /**
   * The 3D visualizer of the selected raw data file around the selected compound
   */
  MAP_3D("3D plot"),
  HIDDEN("Hidden");

  private final @NotNull String label;

  CompoundDashboardSidePlot(@NotNull final String label) {
    this.label = label;
  }

  public @NotNull String label() {
    return label;
  }

  /**
   * @return the next plot of the toolbar button
   */
  public @NotNull CompoundDashboardSidePlot next() {
    return values()[(ordinal() + 1) % values().length];
  }

  /**
   * @return the projection of the raw data visualizer, null if this is no raw data plot
   */
  public @Nullable IntensityMapProjection projection() {
    return switch (this) {
      case MAP_2D -> IntensityMapProjection.TOP_VIEW;
      case MAP_3D -> IntensityMapProjection.PERSPECTIVE;
      case FEATURE_PLOT, HIDDEN -> null;
    };
  }
}
