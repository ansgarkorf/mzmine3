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

package io.github.mzmine.datamodel.features.types;

import com.google.common.util.concurrent.AtomicDouble;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.graphicalnodes.FeatureShapeIonMobilityRetentionTimeHeatMapChart;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.modules.visualization.intensitymap.IntensityMap3DModule;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapProjection;
import io.github.mzmine.util.GcxGcUtils;
import java.util.List;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The shape of a GCxGC 2D feature: first against second dimension retention time
 */
public class FeatureShapeGcxGc2DType extends LinkedGraphicalType {

  @NotNull
  @Override
  public final String getUniqueID() {
    // Never change the ID for compatibility during saving/loading of type
    return "feature_shape_gcxgc_2d_feature";
  }

  @NotNull
  @Override
  public String getHeaderString() {
    return "2D feature";
  }

  @Override
  public @Nullable Node createCellContent(final ModularFeatureListRow row, final Boolean cellData,
      final RawDataFile raw, final AtomicDouble progress) {
    if (row == null || (cellData != null && !cellData) || !GcxGcUtils.isGcxGc(raw)) {
      return null;
    }
    final ModularFeature feature = row.getFeature(raw);
    if (feature == null || feature.getFeatureStatus() == FeatureStatus.UNKNOWN
        || !GcxGcUtils.isGcxGcFeature(feature)) {
      return null;
    }
    // the chart labels the axes as first and second dimension retention time for GCxGC
    return new FeatureShapeIonMobilityRetentionTimeHeatMapChart(feature, progress);
  }

  @Override
  public double getPrefColumnWidth() {
    return LARGE_GRAPHICAL_CELL_WIDTH;
  }

  @Nullable
  @Override
  public Runnable getDoubleClickAction(final @Nullable FeatureTableFX table,
      @NotNull final ModularFeatureListRow row, @NotNull final List<RawDataFile> file,
      final DataType<?> superType, @Nullable final Object value) {
    final ModularFeature feature = file.isEmpty() ? null : row.getFeature(file.getFirst());
    if (feature == null) {
      return null;
    }
    return () -> IntensityMap3DModule.showFeatures(List.of(feature),
        IntensityMapProjection.TOP_VIEW);
  }
}
