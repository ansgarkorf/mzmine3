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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class ProjectStructureAlgorithmsTest {

  @Test
  void clusteringPlacesTheMostSimilarPairNextToEachOther() {
    final FeatureListPairSimilarity[][] matrix = matrix(
        new double[][]{{1d, 0.9d, 0.1d, 0.2d}, {0.9d, 1d, 0.15d, 0.2d}, {0.1d, 0.15d, 1d, 0.8d},
            {0.2d, 0.2d, 0.8d, 1d}});

    final HierarchicalProjectClustering.Result result = HierarchicalProjectClustering.cluster(
        matrix);
    final int[] order = result.projectOrder();

    assertEquals(4, order.length);
    assertEquals(9, result.segments().size());
    assertEquals(1, Math.abs(indexOf(order, 0) - indexOf(order, 1)));
    assertEquals(1, Math.abs(indexOf(order, 2) - indexOf(order, 3)));
  }

  @Test
  void umapIsFiniteAndDeterministic() {
    final FeatureListPairSimilarity[][] matrix = matrix(
        new double[][]{{1d, 0.9d, 0.1d}, {0.9d, 1d, 0.2d}, {0.1d, 0.2d, 1d}});

    final double[][] first = UmapProjector.project(matrix);
    final double[][] second = UmapProjector.project(matrix);

    for (int i = 0; i < first.length; i++) {
      assertArrayEquals(first[i], second[i], 1e-12);
      assertTrue(Arrays.stream(first[i]).allMatch(Double::isFinite));
    }
  }

  @Test
  void randomForestRanksTheSeparatingComponentHighest() {
    final double[][] values = {{0d, 0d, 0.1d}, {0d, 0d, 0.2d}, {0d, 0d, 0.15d}, {10d, 0d, 0.1d},
        {11d, 0d, 0.2d}, {12d, 0d, 0.15d}};
    final int[] labels = {0, 0, 0, 1, 1, 1};

    final double[] importance = RandomForestImportance.calculate(values, labels);

    assertTrue(importance[0] > importance[1]);
    assertTrue(importance[0] > importance[2]);
    assertEquals(1d, Arrays.stream(importance).sum(), 1e-12);
  }

  @Test
  void randomForestSupportsMultipleSelectedProjects() {
    final double[][] values = {{0d, 1d}, {0.1d, 1d}, {5d, 1d}, {5.1d, 1d}, {10d, 1d}, {10.1d, 1d}};
    final int[] labels = {0, 0, 1, 1, 2, 2};

    final double[] importance = RandomForestImportance.calculate(values, labels);

    assertTrue(importance[0] > 0.99d);
    assertEquals(0d, importance[1], 1e-12);
  }

  private static int indexOf(final int[] values, final int target) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == target) {
        return i;
      }
    }
    return -1;
  }

  private static @NotNull FeatureListPairSimilarity[][] matrix(
      final double @NotNull [][] similarities) {
    final FeatureListPairSimilarity[][] matrix = new FeatureListPairSimilarity[similarities.length][similarities.length];
    for (int i = 0; i < similarities.length; i++) {
      for (int j = 0; j < similarities.length; j++) {
        final int shared = i == j ? 1 : similarities[i][j] > 0d ? 1 : 0;
        matrix[i][j] = new FeatureListPairSimilarity(similarities[i][j], shared, 1, 1,
            similarities[i][j]);
      }
    }
    return matrix;
  }
}
