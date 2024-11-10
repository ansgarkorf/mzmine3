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

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataanalysis.utils.StatisticUtils;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ImputationFunction;
import io.github.mzmine.modules.dataanalysis.utils.scaling.ScalingFunction;
import io.github.mzmine.modules.visualization.projectmetadata.SampleTypeFilter;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public class PLSDAUtils {

  private static final Logger logger = Logger.getLogger(PLSDAUtils.class.getName());

  /**
   * Calculates the PLS-DA decomposition for X and Y matrices.
   *
   * @param XData matrix of predictor variables.
   * @param YData matrix of response variables.
   * @return A PLSDA result.
   */
  public static PLSDAResult quickPLSDA(RealMatrix XData, RealMatrix YData,
      ScalingFunction scalingFunction) {

    logger.finest(() -> "Performing scaling and centering on X and Y data");
    final RealMatrix scaledX = StatisticUtils.centerAndScale(XData, scalingFunction, false);
    final RealMatrix scaledY = StatisticUtils.centerAndScale(YData, scalingFunction, false);

    logger.finest(() -> "Performing singular value decomposition on X and Y matrices for PLS-DA");
    SingularValueDecomposition svdX = new SingularValueDecomposition(scaledX);
    SingularValueDecomposition svdY = new SingularValueDecomposition(scaledY);

    return new PLSDAResult(svdX, svdY);
  }

  /**
   * Performs PLS-DA on a list of feature list rows with response variables.
   *
   * @param rows             The feature rows.
   * @param measure          The abundance measure.
   * @param sampleTypeFilter Filters samples by type.
   * @return A PLSDA result mapped to the rows used.
   */
  public static PLSDARowsResult performPLSDAOnRows(List<FeatureListRow> rows,
      AbundanceMeasure measure, ScalingFunction scalingFunction,
      ImputationFunction imputationFunction, SampleTypeFilter sampleTypeFilter,
      MetadataColumn<?> metadataColumn) {

    List<RawDataFile> files = rows.stream().flatMap(row -> row.getRawDataFiles().stream())
        .distinct().filter(sampleTypeFilter::matches)
        .filter(file -> MZmineCore.getProjectMetadata().getValue(metadataColumn, file) != null)
        .toList();

    if (files.isEmpty() || metadataColumn == null) {
      return null;
    }

    // Predictor matrix
    RealMatrix XData = StatisticUtils.createDatasetFromRows(rows, files, measure);

    // Response matrix
    RealMatrix YData = StatisticUtils.createResponseMatrixBasedOnMetadata(files, metadataColumn);

    StatisticUtils.imputeMissingValues(XData, true, imputationFunction);
    StatisticUtils.imputeMissingValues(YData, true, imputationFunction);

    PLSDAResult plsdaResult = quickPLSDA(XData, YData, scalingFunction);
    return new PLSDARowsResult(plsdaResult, rows, files);
  }
}
