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

import io.github.mzmine.gui.chartbasics.simplechart.providers.IntervalWidthProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYDataProvider;
import io.github.mzmine.javafx.util.FxColorUtil;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Color;
import javafx.beans.property.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight provider for already-computed dashboard coordinates.
 */
final class StaticXYProvider implements PlotXYDataProvider, IntervalWidthProvider {

  private final String key;
  private final double[] domainValues;
  private final double[] rangeValues;
  private final String[] labels;
  private final String[] tooltips;
  private final Color color;
  private final double intervalWidth;

  StaticXYProvider(@NotNull final String key, final double @NotNull [] domainValues,
      final double @NotNull [] rangeValues, final String @Nullable [] labels,
      final String @Nullable [] tooltips, @NotNull final Color color, final double intervalWidth) {
    if (domainValues.length != rangeValues.length
        || labels != null && labels.length != domainValues.length
        || tooltips != null && tooltips.length != domainValues.length) {
      throw new IllegalArgumentException("Static series arrays must have equal lengths");
    }
    this.key = key;
    this.domainValues = domainValues.clone();
    this.rangeValues = rangeValues.clone();
    this.labels = labels == null ? null : labels.clone();
    this.tooltips = tooltips == null ? null : tooltips.clone();
    this.color = color;
    this.intervalWidth = intervalWidth;
  }

  @Override
  public void computeValues(@NotNull final Property<TaskStatus> status) {
  }

  @Override
  public double getDomainValue(final int index) {
    return domainValues[index];
  }

  @Override
  public double getRangeValue(final int index) {
    return rangeValues[index];
  }

  @Override
  public int getValueCount() {
    return domainValues.length;
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
  public @NotNull Comparable<?> getSeriesKey() {
    return key;
  }

  @Override
  public @Nullable String getLabel(final int index) {
    return labels == null ? null : labels[index];
  }

  @Override
  public @Nullable String getToolTipText(final int itemIndex) {
    return tooltips == null ? null : tooltips[itemIndex];
  }

  @Override
  public @NotNull Color getAWTColor() {
    return color;
  }

  @Override
  public @NotNull javafx.scene.paint.Color getFXColor() {
    return FxColorUtil.awtColorToFX(color);
  }

  @Override
  public double getIntervalWidth() {
    return intervalWidth;
  }
}
