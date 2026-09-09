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

import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYZDataProvider;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Color;
import java.awt.Paint;
import javafx.beans.property.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.PaintScale;

/**
 * Dense, bounded display matrix. Missing spectral/quantitative evidence is explicitly gray.
 */
final class ComparisonMatrixProvider implements PlotXYZDataProvider {

  private final String name;
  private final double[][] values;
  private final String[][] tooltips;
  private final int columns;
  private final PaintScale scale;

  ComparisonMatrixProvider(@NotNull final String name, final double @NotNull [][] values,
      final String @NotNull [][] tooltips, final int columns, final boolean diverging) {
    this(name, values, tooltips, columns, createContinuousScale(diverging));
  }

  ComparisonMatrixProvider(@NotNull final String name, final double @NotNull [][] values,
      final String @NotNull [][] tooltips, final int columns, @NotNull final PaintScale scale) {
    this.name = name;
    this.values = values;
    this.tooltips = tooltips;
    this.columns = columns;
    this.scale = scale;
  }

  private static @NotNull PaintScale createContinuousScale(final boolean diverging) {
    final Color low = ConfigService.getDefaultColorPalette().getNegativeColorAWT();
    final Color high = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
    return new PaintScale() {
      @Override
      public double getLowerBound() {
        return diverging ? -4d : 0d;
      }

      @Override
      public double getUpperBound() {
        return diverging ? 4d : 1d;
      }

      @Override
      public @NotNull Paint getPaint(final double value) {
        if (!Double.isFinite(value)) {
          return new Color(155, 155, 155);
        }
        final double fraction = Math.min(1d, Math.abs(value) / (diverging ? 4d : 1d));
        final Color end = diverging && value < 0d ? low : high;
        final Color start = new Color(242, 242, 242);
        return new Color((int) (start.getRed() * (1d - fraction) + end.getRed() * fraction),
            (int) (start.getGreen() * (1d - fraction) + end.getGreen() * fraction),
            (int) (start.getBlue() * (1d - fraction) + end.getBlue() * fraction));
      }
    };
  }

  @Override
  public void computeValues(@NotNull final Property<TaskStatus> status) {
  }

  @Override
  public double getDomainValue(final int index) {
    return index % columns;
  }

  @Override
  public double getRangeValue(final int index) {
    return index / columns;
  }

  @Override
  public double getZValue(final int index) {
    return values[index / columns][index % columns];
  }

  @Override
  public int getValueCount() {
    return values.length * columns;
  }

  @Override
  public double getComputationFinishedPercentage() {
    return 1d;
  }

  @Override
  public boolean isComputed() {
    return true;
  }

  @Override
  public @NotNull Double getBoxHeight() {
    return 1d;
  }

  @Override
  public @NotNull Double getBoxWidth() {
    return 1d;
  }

  @Override
  public @NotNull Color getAWTColor() {
    return Color.GRAY;
  }

  @Override
  public @NotNull javafx.scene.paint.Color getFXColor() {
    return javafx.scene.paint.Color.GRAY;
  }

  @Override
  public @Nullable String getLabel(final int index) {
    return null;
  }

  @Override
  public @NotNull PaintScale getPaintScale() {
    return scale;
  }

  @Override
  public @NotNull Comparable<?> getSeriesKey() {
    return name;
  }

  @Override
  public @NotNull String getToolTipText(final int index) {
    return tooltips[index / columns][index % columns];
  }
}
