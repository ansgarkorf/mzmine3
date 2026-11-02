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

package io.github.mzmine.datamodel.features.types.graphicalnodes;

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.modifiers.GraphicalColumType;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.series.SummedMobilogramXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.gui.preferences.UnitFormat;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.RangeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.jfree.data.Range;

/**
 * The second dimension retention time (2D RT) shapes of all features of a GCxGC row, the summed 2D
 * RT trace of each sample. Like {@link MobilogramFeatureShapeCell} for ion mobility data.
 */
public class SecondDimensionFeatureShapeCell extends ChartCell<SimpleXYChart<?>> {

  public SecondDimensionFeatureShapeCell(final int id) {
    super(id);
  }

  @Override
  protected int getMinCellHeight() {
    return GraphicalColumType.DEFAULT_GRAPHICAL_CELL_HEIGHT;
  }

  @Override
  protected double getMinCellWidth() {
    return GraphicalColumType.DEFAULT_GRAPHICAL_CELL_WIDTH;
  }

  @Override
  protected void updateItem(final Object o, final boolean visible) {
    // always need to call super.updateItem
    super.updateItem(o, visible);

    if (!isValidCell() || cellHasNoData()) {
      // datasets removed and plot cleared in XyChartCell.updateItem
      return;
    }

    final ModularFeatureListRow row = getTableRow().getItem();
    final IMSRawDataFile gcxgcFile = (IMSRawDataFile) row.getRawDataFiles().stream()
        .filter(GcxGcUtils::isGcxGc).findAny().orElse(null);
    if (gcxgcFile == null) {
      return;
    }

    double maxIntensity = 1d;
    final List<DatasetAndRenderer> datasets = new ArrayList<>();
    for (final ModularFeature f : row.getFeatures()) {
      if (GcxGcUtils.isGcxGcFeature(f)) {
        final var trace = ((IonMobilogramTimeSeries) f.getFeatureData()).getSummedMobilogram();
        datasets.add(new DatasetAndRenderer(
            new ColoredXYDataset(new SummedMobilogramXYProvider(f), RunOption.THIS_THREAD),
            new ColoredXYLineRenderer()));
        for (int i = 0; i < trace.getNumberOfDataPoints(); i++) {
          maxIntensity = Math.max(trace.getIntensity(i), maxIntensity);
        }
      }
    }

    // the 2D RT range of the row with three widths on each side
    final Range defaultRange;
    final com.google.common.collect.Range<Float> secondDimRange = row.getSecondDimensionRTRange();
    final Float secondDim = row.getAverageSecondDimensionRT();
    final com.google.common.collect.Range<Double> dataRange = gcxgcFile.getDataMobilityRange();
    if (secondDimRange != null && secondDim != null && !Float.isNaN(secondDim)) {
      final Float length = RangeUtils.rangeLength(secondDimRange);
      defaultRange = new Range(Math.max(secondDim - 3 * length, dataRange.lowerEndpoint()),
          Math.min(secondDim + 3 * length, dataRange.upperEndpoint()));
    } else {
      defaultRange = new Range(dataRange.lowerEndpoint(), dataRange.upperEndpoint());
    }

    final Range intensityRange = new Range(0, maxIntensity * 1.2);
    getChart().applyWithNotifyChanges(false, () -> {
      getChart().setDatasetsAndRenderers(datasets);
      try {
        getChart().getXYPlot().getRangeAxis().setRange(intensityRange);
        getChart().getXYPlot().getRangeAxis().setDefaultAutoRange(intensityRange);
        getChart().getXYPlot().getDomainAxis().setRange(defaultRange);
        getChart().getXYPlot().getDomainAxis().setDefaultAutoRange(defaultRange);
      } catch (NullPointerException | NoSuchElementException e) {
        // error in jfreechart draw method
      }
    });
  }

  @Override
  protected SimpleXYChart<?> createChart() {
    final UnitFormat uf = MZmineCore.getConfiguration().getUnitFormat();
    final SimpleXYChart<?> chart = new SimpleXYChart<>(
        uf.format(GcxGcUtils.SECOND_DIMENSION_RT, "min"), uf.format("Intensity", "a.u."));
    chart.setRangeAxisNumberFormatOverride(MZmineCore.getConfiguration().getIntensityFormat());
    chart.setDomainAxisNumberFormatOverride(GcxGcUtils.createSecondDimensionRTFormat());
    chart.setLegendItemsVisible(false);
    chart.setPrefWidth(GraphicalColumType.DEFAULT_GRAPHICAL_CELL_WIDTH);
    return chart;
  }
}
