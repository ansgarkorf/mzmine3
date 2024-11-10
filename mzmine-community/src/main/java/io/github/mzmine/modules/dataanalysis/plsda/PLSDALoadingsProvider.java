/*
 * Copyright (c) 2004-2024 The mzmine Development Team
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

package io.github.mzmine.modules.dataanalysis.plsda;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.annotations.MissingValueType;
import io.github.mzmine.gui.chartbasics.simplechart.providers.PlotXYZDataProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.SimpleXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.XYItemObjectProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.ZCategoryProvider;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.annotations.CompoundAnnotationUtils;
import io.github.mzmine.util.collections.SortOrder;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.awt.Color;
import java.util.Map;
import javafx.beans.property.Property;
import org.apache.commons.math3.linear.RealMatrix;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;

public class PLSDALoadingsProvider extends SimpleXYProvider implements PlotXYZDataProvider,
    ZCategoryProvider, XYItemObjectProvider<FeatureListRow> {

  private final PLSDARowsResult result;
  private final int loadingsIndexY;
  private final int loadingsIndexX;

  private int[] zCategories;
  private int numberOfCategories;
  private LookupPaintScale paintScale;
  private String[] legendNames;

  public PLSDALoadingsProvider(PLSDARowsResult result, String seriesKey, Color awt,
      int loadingsIndexX, int loadingsIndexY) {
    super(seriesKey, awt);
    this.result = result;
    this.loadingsIndexX = loadingsIndexX;
    this.loadingsIndexY = loadingsIndexY;
  }

  public PLSDALoadingsProvider(PLSDARowsResult result, String seriesKey, Color awt) {
    this(result, seriesKey, awt, 0, 1);
  }

  @Override
  public void computeValues(Property<TaskStatus> status) {
    RealMatrix loadingsMatrix = result.plsdaResult().getLoadingsMatrix();

    // Map each row to its best annotation type
    Map<FeatureListRow, DataType<?>> bestRowAnnotationType = CompoundAnnotationUtils.mapBestAnnotationTypesByPriority(
        result.rows(), true);

    // Only create order of actually existing annotation types + missing type
    Map<DataType<?>, Integer> typesInOrder = CompoundAnnotationUtils.rankUniqueAnnotationTypes(
        bestRowAnnotationType.values(), SortOrder.ASCENDING);
    numberOfCategories = typesInOrder.size();

    // Initialize arrays for plotting based on number of rows
    int numRows = result.rows().size();
    double[] domainData = new double[numRows];
    double[] rangeData = new double[numRows];
    zCategories = new int[numRows];

    System.out.println("Loadings Matrix Rows (Variables): " + numRows);

    // Populate data arrays for each row
    for (int i = 0; i < numRows; i++) {
      domainData[i] = loadingsMatrix.getEntry(i, loadingsIndexX);  // Row loading for Component X
      rangeData[i] = loadingsMatrix.getEntry(i, loadingsIndexY);   // Row loading for Component Y

      // Map row to its category for coloring
      FeatureListRow row = result.rows().get(i);
      DataType<?> bestTypeWithValue = bestRowAnnotationType.get(row);
      zCategories[i] = typesInOrder.getOrDefault(bestTypeWithValue,
          0); // Default to 0 if no mapping found
    }

    paintScale = new LookupPaintScale(0, numberOfCategories, Color.BLACK);
    final SimpleColorPalette colors = MZmineCore.getConfiguration().getDefaultColorPalette();
    for (int i = 0; i < numberOfCategories; i++) {
      paintScale.add(i, colors.getAWT(i));
    }

    setxValues(domainData);
    setyValues(rangeData);

    legendNames = typesInOrder.keySet().stream()
        .map(type -> type instanceof MissingValueType _ ? "Unknown" : type.getHeaderString())
        .toArray(String[]::new);

    // Debugging output for row check
    System.out.println("Total Rows Processed: " + numRows);
    System.out.println("Total Rows in Loadings Plot: " + domainData.length);
  }


  //@Override
  public void computeValues1(Property<TaskStatus> status) {
    RealMatrix loadingsMatrix = result.plsdaResult().getLoadingsMatrix();

    // Map each row to its best annotation type
    Map<FeatureListRow, DataType<?>> bestRowAnnotationType = CompoundAnnotationUtils.mapBestAnnotationTypesByPriority(
        result.rows(), true);

    // only create order of actually existing annotation types + missing type
    // ASCENDING will put MissingType first so that it is always black
    Map<DataType<?>, Integer> typesInOrder = CompoundAnnotationUtils.rankUniqueAnnotationTypes(
        bestRowAnnotationType.values(), SortOrder.ASCENDING);
    numberOfCategories = typesInOrder.size();

    // Initialize arrays for plotting
    int numEntries = loadingsMatrix.getColumnDimension();
    double[] domainData = new double[numEntries];
    double[] rangeData = new double[numEntries];
    zCategories = new int[numEntries];

    System.out.println("Loadings Matrix Columns: " + numEntries);

    // Populate data arrays
    for (int i = 0; i < numEntries; i++) {
      domainData[i] = loadingsMatrix.getEntry(loadingsIndexX, i);
      rangeData[i] = loadingsMatrix.getEntry(loadingsIndexY, i);

      FeatureListRow row = result.rows().get(i);
      final DataType<?> bestTypeWithValue = bestRowAnnotationType.get(row);
      zCategories[i] = typesInOrder.get(bestTypeWithValue);
    }

    paintScale = new LookupPaintScale(0, numberOfCategories, Color.BLACK);
    final SimpleColorPalette colors = MZmineCore.getConfiguration().getDefaultColorPalette();
    for (int i = 0; i < numberOfCategories; i++) {
      paintScale.add(i, colors.getAWT(i));
    }

    setxValues(domainData);
    setyValues(rangeData);

    legendNames = typesInOrder.keySet().stream()
        .map(type -> type instanceof MissingValueType _ ? "Unknown" : type.getHeaderString())
        .toArray(String[]::new);

    // Debugging output for row check
    System.out.println("Total Rows Processed: " + result.rows().size());
    System.out.println("Total Rows in Loadings Plot: " + domainData.length);
  }


  @Override
  public @Nullable PaintScale getPaintScale() {
    return paintScale;
  }

  @Override
  public double getZValue(int index) {
    return zCategories[index];
  }

  @Override
  public @Nullable Double getBoxHeight() {
    return 5d;
  }

  @Override
  public @Nullable Double getBoxWidth() {
    return 5d;
  }

  @Override
  public int getNumberOfCategories() {
    return numberOfCategories;
  }

  @Override
  public String getLegendLabel(int category) {
    return legendNames[category];
  }

  @Override
  public FeatureListRow getItemObject(int item) {
    return result.rows().get(item);
  }

  @Override
  public String getToolTipText(int itemIndex) {
    return getItemObject(itemIndex).toString();
  }
}
