/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.visualization.kendrickmassplot;

import java.awt.Color;
import java.awt.Font;
import java.util.Arrays;
import java.util.logging.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.gui.chartbasics.chartutils.NameItemLabelGenerator;
import io.github.mzmine.gui.chartbasics.chartutils.ScatterPlotToolTipGenerator;
import io.github.mzmine.gui.chartbasics.chartutils.XYBubbleSizeRenderer;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScale;
import io.github.mzmine.gui.chartbasics.chartutils.paintscales.PaintScaleFactory;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.visualization.intensityplot.IntensityPlotParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.dialogs.FeatureOverviewWindow;
import javafx.application.Platform;
import javafx.scene.input.MouseButton;

/**
 * Task to create a Kendrick mass plot of selected features of a selected feature list
 *
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class KendrickMassPlotTask extends AbstractTask {

  static final Font legendFont = new Font("SansSerif", Font.PLAIN, 12);
  static final Font titleFont = new Font("SansSerif", Font.PLAIN, 12);

  private Logger logger = Logger.getLogger(this.getClass().getName());

  private ParameterSet parameters;
  private JFreeChart chart;
  private FeatureList featureList;
  private String title;
  private String xAxisLabel;
  private String yAxisLabel;
  private String zAxisLabel;
  private PaintScale paintScaleParameter;
  private FeatureListRow rows[];
  private int totalSteps = 3, appliedSteps = 0;

  public KendrickMassPlotTask(ParameterSet parameters) {
    featureList = parameters.getParameter(KendrickMassPlotParameters.featureList).getValue()
        .getMatchingFeatureLists()[0];

    this.parameters = parameters;

    title = "Kendrick mass plot [" + featureList + "]";

    if (parameters.getParameter(KendrickMassPlotParameters.xAxisCustomKendrickMassBase)
        .getValue() == true) {
      xAxisLabel =
          "KMD (" + parameters.getParameter(KendrickMassPlotParameters.xAxisCustomKendrickMassBase)
              .getEmbeddedParameter().getValue() + ")";
    } else {
      xAxisLabel = parameters.getParameter(KendrickMassPlotParameters.xAxisValues).getValue();
    }

    yAxisLabel = "KMD ("
        + parameters.getParameter(KendrickMassPlotParameters.yAxisCustomKendrickMassBase).getValue()
        + ")";

    if (parameters.getParameter(KendrickMassPlotParameters.zAxisCustomKendrickMassBase)
        .getValue() == true) {
      zAxisLabel =
          "KMD (" + parameters.getParameter(KendrickMassPlotParameters.zAxisCustomKendrickMassBase)
              .getEmbeddedParameter().getValue() + ")";
    } else {
      zAxisLabel = parameters.getParameter(KendrickMassPlotParameters.zAxisValues).getValue();
    }

    paintScaleParameter = parameters.getParameter(KendrickMassPlotParameters.paintScale).getValue();

    rows =
        parameters.getParameter(IntensityPlotParameters.selectedRows).getMatchingRows(featureList);

  }

  @Override
  public String getTaskDescription() {
    return "Create Kendrick mass plot for " + featureList;
  }

  @Override
  public double getFinishedPercentage() {
    return totalSteps == 0 ? 0 : (double) appliedSteps / totalSteps;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    logger.info("Create Kendrick mass plot of " + featureList);
    // Task canceled?
    if (isCanceled()) {
      return;
    }

    chart = createKendrickMassPlot();
    chart.setBackgroundPaint(Color.white);

    // create chartViewer
    EChartViewer chartViewer = new EChartViewer(chart, true, true, true, true, false);

    // add chart mouse listener for feature overview
    addMouseListener(chartViewer);


    // set title properties
    TextTitle chartTitle = chart.getTitle();
    chartTitle.setMargin(5, 0, 0, 0);
    chartTitle.setFont(titleFont);
    LegendTitle legend = chart.getLegend();
    legend.setVisible(false);

    // Create Kendrick mass plot Tab
    Platform.runLater(() -> {
      KendrickMassPlotTab newTab = new KendrickMassPlotTab(parameters, chartViewer);
      MZmineCore.getDesktop().addTab(newTab);
    });

    setStatus(TaskStatus.FINISHED);
    logger.info("Finished creating Kendrick mass plot of " + featureList);
  }

  private void addMouseListener(EChartViewer chartViewer) {
    chartViewer.addChartMouseListener(new ChartMouseListenerFX() {

      @Override
      public void chartMouseMoved(ChartMouseEventFX event) {}

      @Override
      public void chartMouseClicked(ChartMouseEventFX event) {
        XYPlot plot = (XYPlot) chart.getPlot();
        double xValue = plot.getDomainCrosshairValue();
        double yValue = plot.getRangeCrosshairValue();
        if (plot.getDataset() instanceof KendrickMassPlotDataset) {
          KendrickMassPlotDataset dataset = (KendrickMassPlotDataset) plot.getDataset();
          double[] xValues = new double[dataset.getItemCount(0)];
          for (int i = 0; i < xValues.length; i++) {
            if ((event.getTrigger().getButton().equals(MouseButton.PRIMARY))
                && (event.getTrigger().getClickCount() == 2)
                && (dataset.getX(0, i).doubleValue() == xValue
                    && dataset.getY(0, i).doubleValue() == yValue)) {
              new FeatureOverviewWindow(rows[i]);
            }
          }
        }
      }
    });
  }

  /**
   * create Kendrick mass plot
   */
  private JFreeChart createKendrickMassPlot() {

    logger.info("Creating new Kendrick Plot");
    appliedSteps++;
    // load dataset
    KendrickMassPlotDataset dataset = new KendrickMassPlotDataset(parameters);
    PaintScale paintScale = preparePaintScale(dataset);
    PaintScaleFactory paintScaleFactoy = new PaintScaleFactory();
    paintScaleFactoy.createColorsForPaintScale(paintScale);

    // create chart
    chart = ChartFactory.createScatterPlot(title, xAxisLabel, yAxisLabel, dataset,
        PlotOrientation.VERTICAL, true, true, true);
    XYPlot plot = chart.getXYPlot();

    // set axis
    NumberAxis domain = (NumberAxis) plot.getDomainAxis();
    NumberAxis range = (NumberAxis) plot.getRangeAxis();
    range.setRange(0, 1);
    if (xAxisLabel.contains("KMD")) {
      domain.setRange(0, 1);
    }
    // set renderer
    XYBubbleSizeRenderer renderer = new XYBubbleSizeRenderer();
    appliedSteps++;

    // Set paint scale
    renderer.setPaintScale(paintScale);

    ScatterPlotToolTipGenerator tooltipGenerator =
        new ScatterPlotToolTipGenerator(xAxisLabel, yAxisLabel, zAxisLabel, rows);
    renderer.setSeriesToolTipGenerator(0, tooltipGenerator);

    // set item label generator
    NameItemLabelGenerator generator = new NameItemLabelGenerator(rows);
    renderer.setDefaultItemLabelGenerator(generator);
    renderer.setDefaultItemLabelsVisible(false);
    renderer.setDefaultItemLabelFont(legendFont);
    renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.CENTER,
        TextAnchor.TOP_RIGHT, TextAnchor.TOP_RIGHT, -45), true);

    plot.setRenderer(renderer);
    plot.setBackgroundPaint(Color.white);
    plot.setRangeGridlinePaint(Color.white);
    plot.setAxisOffset(new RectangleInsets(5, 5, 5, 5));
    plot.setOutlinePaint(Color.black);
    plot.setBackgroundPaint(Color.white);
    plot.setDomainCrosshairPaint(Color.GRAY);
    plot.setRangeCrosshairPaint(Color.GRAY);
    plot.setDomainCrosshairVisible(true);
    plot.setRangeCrosshairVisible(true);

    // Paint scale legend
    if (paintScale.getLowerBound() != 0.0 && paintScale.getUpperBound() != 1.0) {
      addPaintScaleLegend(paintScale);
    }

    return chart;
  }

  private PaintScale preparePaintScale(KendrickMassPlotDataset dataset) {
    // copy and sort z-Values for min and max of the paint scale
    Double[] copyZValues = new Double[dataset.getItemCount(0)];
    for (int i = 0; i < dataset.getItemCount(0); i++) {
      copyZValues[i] = dataset.getZValue(0, i);
    }
    Arrays.sort(copyZValues);
    double min = copyZValues[0];
    double max = copyZValues[copyZValues.length - 1];
    if (min >= max) {
      return createPaintScale(0, 1);
    } else {
      return createPaintScale(min, max);
    }
  }

  private PaintScale createPaintScale(double min, double max) {
    Range<Double> zValueRange = Range.closed(min, max);
    return new PaintScale(paintScaleParameter.getPaintScaleColorStyle(),
        paintScaleParameter.getPaintScaleBoundStyle(), zValueRange);
  }

  private void addPaintScaleLegend(PaintScale paintScale) {
    NumberAxis scaleAxis = new NumberAxis(zAxisLabel);
    scaleAxis.setRange(paintScale.getLowerBound(), paintScale.getUpperBound());
    scaleAxis.setAxisLinePaint(Color.white);
    scaleAxis.setTickMarkPaint(Color.white);
    PaintScaleLegend legend = new PaintScaleLegend(paintScale, scaleAxis);

    legend.setStripOutlineVisible(false);
    legend.setAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
    legend.setAxisOffset(5.0);
    legend.setMargin(new RectangleInsets(5, 5, 5, 5));
    legend.setFrame(new BlockBorder(Color.white));
    legend.setPadding(new RectangleInsets(10, 10, 10, 10));
    legend.setStripWidth(10);
    legend.setPosition(RectangleEdge.LEFT);
    legend.getAxis().setLabelFont(legendFont);
    legend.getAxis().setTickLabelFont(legendFont);
    chart.addSubtitle(legend);
  }

}
