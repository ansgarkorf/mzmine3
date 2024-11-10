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

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 * A PLS-DA result class capturing covariance between X and Y matrices, with flexible component
 * selection for plotting.
 */
public record PLSDAResult(SingularValueDecomposition svdX, SingularValueDecomposition svdY) {

  /**
   * Retrieves the loadings matrix from X, retaining all features.
   */
  public RealMatrix getLoadingsMatrix() {
    return svdX.getV();
  }

  /**
   * Retrieves specific components for scores matrix, allowing custom selection of domain and
   * range.
   */
  public RealMatrix getScoresMatrix(int domainColIndex, int rangeColIndex) {
    final RealMatrix scoresX = svdX.getU();
    final RealMatrix scoresY = svdY.getU();
    RealMatrix selectedScores = new Array2DRowRealMatrix(scoresX.getRowDimension(), 2);

    selectedScores.setColumnVector(0, scoresX.getColumnVector(domainColIndex));
    selectedScores.setColumnVector(1, scoresY.getColumnVector(rangeColIndex));
    return selectedScores;
  }

  /**
   * Retrieves specific components for loadings matrix, allowing custom selection of domain and
   * range.
   */
  public RealMatrix getLoadingsMatrix(int domainColIndex, int rangeColIndex) {
    final RealMatrix loadings = getLoadingsMatrix();
    RealMatrix selectedLoadings = new Array2DRowRealMatrix(loadings.getRowDimension(), 2);

    selectedLoadings.setColumnVector(0, loadings.getColumnVector(domainColIndex));
    selectedLoadings.setColumnVector(1, loadings.getColumnVector(rangeColIndex));
    return selectedLoadings;
  }

  /**
   * Retrieves component contributions based on singular values from X.
   */
  public float[] getComponentContributions(int components) {
    double[] singularValues = svdX.getSingularValues();
    double totalVariance = 0;
    for (double value : singularValues) {
      totalVariance += value * value;
    }

    components = Math.min(components, singularValues.length);
    float[] contributions = new float[components];
    for (int i = 0; i < components; i++) {
      contributions[i] = (float) ((singularValues[i] * singularValues[i]) / totalVariance);
    }
    return contributions;
  }

  public int componentCount() {
    return svdX.getU().getRowDimension();
  }
}
