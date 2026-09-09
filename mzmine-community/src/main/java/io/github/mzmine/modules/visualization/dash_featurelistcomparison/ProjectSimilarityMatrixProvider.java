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

import com.google.common.collect.Range;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleTransform;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYZDataProvider;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.awt.Color;
import javafx.beans.property.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.PaintScale;

/**
 * Adapts the precomputed symmetric similarity matrix to the SimpleChart XYZ API.
 */
public class ProjectSimilarityMatrixProvider implements PlotXYZDataProvider {

  private final FeatureListComparisonResult result;
  private final PaintScale paintScale;
  private final int[] projectOrder;
  private final ProjectLandscape landscape;

  public ProjectSimilarityMatrixProvider(@NotNull FeatureListComparisonResult result) {
    this(result, result.compositionLandscape());
  }

  ProjectSimilarityMatrixProvider(@NotNull final FeatureListComparisonResult result,
      @NotNull final ProjectLandscape landscape) {
    this.result = result;
    this.landscape = landscape;
    projectOrder = landscape.order();
    paintScale = ConfigService.getConfiguration().getDefaultPaintScalePalette()
        .toPaintScale(PaintScaleTransform.LINEAR, Range.closed(0d, 1d));
  }

  @Override
  public void computeValues(@NotNull Property<TaskStatus> status) {
    // Values are already computed by FeatureListComparisonTask.
  }

  @Override
  public double getDomainValue(final int index) {
    return index % result.size();
  }

  @Override
  public double getRangeValue(final int index) {
    return index / result.size();
  }

  @Override
  public int getValueCount() {
    return result.size() * result.size();
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
  public double getZValue(final int index) {
    final int indexA = projectOrder[index / result.size()];
    final int indexB = projectOrder[index % result.size()];
    return landscape.matrix()[indexA][indexB].jaccardSimilarity();
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
    return Color.BLACK;
  }

  @Override
  public @NotNull javafx.scene.paint.Color getFXColor() {
    return javafx.scene.paint.Color.BLACK;
  }

  @Override
  public @Nullable String getLabel(final int index) {
    return null;
  }

  @Override
  public @NotNull PaintScale getPaintScale() {
    return paintScale;
  }

  @Override
  public @NotNull Comparable<?> getSeriesKey() {
    return "MS2 spectral overlap";
  }

  @Override
  public @NotNull String getToolTipText(final int itemIndex) {
    final int indexA = projectOrder[itemIndex / result.size()];
    final int indexB = projectOrder[itemIndex % result.size()];
    final FeatureListSummary summaryA = result.summaries().get(indexA);
    final FeatureListSummary summaryB = result.summaries().get(indexB);
    final FeatureListPairSimilarity similarity = landscape.matrix()[indexA][indexB];
    return "%s ↔ %s\n%s: %.1f%%\nShared components used: %d".formatted(summaryA.name(),
        summaryB.name(), landscape.label(), similarity.jaccardSimilarity() * 100d,
        similarity.matchedSpectra());
  }
}
