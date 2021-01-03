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

import org.jfree.data.xy.AbstractXYZDataset;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.util.FormulaUtils;

/**
 * XYZDataset for Kendrick mass plots
 * 
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class KendrickMassPlotDataset extends AbstractXYZDataset {

  private static final long serialVersionUID = 1L;

  private FeatureListRow[] selectedRows;
  private String xAxisKMBase;
  private String zAxisKMBase;
  private String customYAxisKMBase;
  private String customXAxisKMBase;
  private String customZAxisKMBase;
  private String bubbleSizeLabel;
  private double[] xValues;
  private double[] yValues;
  private double[] zValues;
  private double[] bubbleSizeValues;
  private ParameterSet parameters;

  public KendrickMassPlotDataset(ParameterSet parameters) {
    this.parameters = parameters;
    init();
  }

  private void init() {
    FeatureList featureList = parameters.getParameter(KendrickMassPlotParameters.featureList)
        .getValue().getMatchingFeatureLists()[0];

    this.selectedRows = parameters.getParameter(KendrickMassPlotParameters.selectedRows)
        .getMatchingRows(featureList);

    this.customYAxisKMBase =
        parameters.getParameter(KendrickMassPlotParameters.yAxisCustomKendrickMassBase).getValue();

    if (parameters.getParameter(KendrickMassPlotParameters.xAxisCustomKendrickMassBase)
        .getValue()) {
      this.customXAxisKMBase =
          parameters.getParameter(KendrickMassPlotParameters.xAxisCustomKendrickMassBase)
              .getEmbeddedParameter().getValue();
    } else {
      this.xAxisKMBase = parameters.getParameter(KendrickMassPlotParameters.xAxisValues).getValue();
    }

    if (parameters.getParameter(KendrickMassPlotParameters.zAxisCustomKendrickMassBase)
        .getValue()) {
      this.customZAxisKMBase =
          parameters.getParameter(KendrickMassPlotParameters.zAxisCustomKendrickMassBase)
              .getEmbeddedParameter().getValue();
    } else {
      this.zAxisKMBase = parameters.getParameter(KendrickMassPlotParameters.zAxisValues).getValue();
    }

    this.bubbleSizeLabel =
        parameters.getParameter(KendrickMassPlotParameters.bubbleSize).getValue();

    calculateXValues();
    calculateYValues();
    calculateZValues();
    calculateBubbleSizeValues();
  }

  private void calculateXValues() {
    xValues = new double[selectedRows.length];
    if (parameters.getParameter(KendrickMassPlotParameters.xAxisCustomKendrickMassBase)
        .getValue()) {
      for (int i = 0; i < selectedRows.length; i++) {
        xValues[i] =
            Math.ceil(selectedRows[i].getAverageMZ() * getKendrickMassFactor(customXAxisKMBase))
                - selectedRows[i].getAverageMZ() * getKendrickMassFactor(customXAxisKMBase);
      }
    } else {
      for (int i = 0; i < selectedRows.length; i++) {

        // simply plot m/z values as x axis
        if (xAxisKMBase.equals("m/z")) {
          xValues[i] = selectedRows[i].getAverageMZ();
        }

        // plot Kendrick masses as x axis
        else if (xAxisKMBase.equals("KM")) {
          xValues[i] = selectedRows[i].getAverageMZ() * getKendrickMassFactor(customYAxisKMBase);
        }
      }
    }
  }

  private void calculateYValues() {
    yValues = new double[selectedRows.length];
    for (int i = 0; i < selectedRows.length; i++) {
      yValues[i] =
          Math.ceil((selectedRows[i].getAverageMZ()) * getKendrickMassFactor(customYAxisKMBase))
              - (selectedRows[i].getAverageMZ()) * getKendrickMassFactor(customYAxisKMBase);
    }
  }

  private void calculateZValues() {
    zValues = new double[selectedRows.length];
    if (parameters.getParameter(KendrickMassPlotParameters.zAxisCustomKendrickMassBase)
        .getValue() == true) {
      for (int i = 0; i < selectedRows.length; i++) {
        zValues[i] =
            Math.ceil((selectedRows[i].getAverageMZ()) * getKendrickMassFactor(customZAxisKMBase))
                - (selectedRows[i].getAverageMZ()) * getKendrickMassFactor(customZAxisKMBase);
      }
    } else
      for (int i = 0; i < selectedRows.length; i++) {
        zValues[i] = addFeatureCharacteristic(zAxisKMBase, selectedRows[i]);
      }
  }

  private void calculateBubbleSizeValues() {
    bubbleSizeValues = new double[selectedRows.length];
    for (int i = 0; i < selectedRows.length; i++) {
      bubbleSizeValues[i] = addFeatureCharacteristic(bubbleSizeLabel, selectedRows[i]);
    }
  }

  private double addFeatureCharacteristic(String label, FeatureListRow selectedRow) {
    switch (label) {
      case ("Retention time"):
        return selectedRow.getAverageRT();
      case ("Intensity"):
        return selectedRow.getAverageHeight();
      case ("Area"):
        return selectedRow.getAverageArea();
      case ("Tailing factor"):
        return selectedRow.getBestFeature().getTailingFactor();
      case ("Asymmetry factor"):
        return selectedRow.getBestFeature().getAsymmetryFactor();
      case ("FWHM"):
        return selectedRow.getBestFeature().getFWHM();
      case ("m/z"):
        return selectedRow.getBestFeature().getMZ();
      default:
        return 0.0;
    }
  }

  public ParameterSet getParameters() {
    return parameters;
  }

  public void setParameters(ParameterSet parameters) {
    this.parameters = parameters;
  }

  @Override
  public int getItemCount(int series) {
    return selectedRows.length;
  }

  @Override
  public Number getX(int series, int item) {
    return xValues[item];
  }

  @Override
  public Number getY(int series, int item) {
    return yValues[item];
  }

  @Override
  public Number getZ(int series, int item) {
    return zValues[item];
  }

  public double getBubbleSize(int item) {
    return bubbleSizeValues[item];
  }

  public double[] getBubbleSizeValues() {
    return bubbleSizeValues;
  }

  public void setxValues(double[] values) {
    xValues = values;
  }

  public void setyValues(double[] values) {
    yValues = values;
  }

  public void setzValues(double[] values) {
    zValues = values;
  }

  public void setBubbleSize(double[] bubbleSize) {
    this.bubbleSizeValues = bubbleSize;
  }

  @Override
  public int getSeriesCount() {
    return 1;
  }

  public Comparable<?> getRowKey(int row) {
    return selectedRows[row].toString();
  }

  @Override
  public Comparable<?> getSeriesKey(int series) {
    return getRowKey(series);
  }

  private double getKendrickMassFactor(String formula) {
    double exactMassFormula = FormulaUtils.calculateExactMass(formula);
    return ((int) ((exactMassFormula) + 0.5d)) / (exactMassFormula);
  }

}
