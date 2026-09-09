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

import java.util.Arrays;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.jetbrains.annotations.NotNull;

/**
 * Deterministic two-dimensional UMAP implementation for the small project-distance matrix.
 */
final class UmapProjector {

  private static final double UMAP_A = 1.576943460;
  private static final double UMAP_B = 0.895060879;

  private UmapProjector() {
  }

  static double @NotNull [][] project(@NotNull final FeatureListPairSimilarity[][] similarities) {
    final int size = similarities.length;
    if (size == 0) {
      return new double[0][2];
    }
    if (size == 1) {
      return new double[][]{{0d, 0d}};
    }
    final double[][] distances = new double[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = i + 1; j < size; j++) {
        final double distance = 1d - similarities[i][j].jaccardSimilarity();
        distances[i][j] = distance;
        distances[j][i] = distance;
      }
    }
    final double[][] fuzzyGraph = fuzzyGraph(distances, Math.min(15, size - 1));
    final double[][] coordinates = classicalMds(distances);
    optimize(coordinates, fuzzyGraph, 350);
    standardize(coordinates);
    return coordinates;
  }

  private static double @NotNull [][] fuzzyGraph(final double @NotNull [][] distances,
      final int neighbors) {
    final int size = distances.length;
    final double[][] directed = new double[size][size];
    for (int i = 0; i < size; i++) {
      final Integer[] order = new Integer[size - 1];
      int write = 0;
      for (int j = 0; j < size; j++) {
        if (i != j) {
          order[write++] = j;
        }
      }
      final int row = i;
      Arrays.sort(order, (a, b) -> Double.compare(distances[row][a], distances[row][b]));
      final double rho = distances[i][order[0]];
      double low = 1e-6;
      double high = 64d;
      final double target = Math.log(neighbors) / Math.log(2d);
      for (int iteration = 0; iteration < 48; iteration++) {
        final double sigma = (low + high) / 2d;
        double sum = 0d;
        for (int k = 0; k < neighbors; k++) {
          sum += Math.exp(-Math.max(0d, distances[i][order[k]] - rho) / sigma);
        }
        if (sum > target) {
          high = sigma;
        } else {
          low = sigma;
        }
      }
      final double sigma = (low + high) / 2d;
      for (int k = 0; k < neighbors; k++) {
        final int j = order[k];
        directed[i][j] = Math.exp(-Math.max(0d, distances[i][j] - rho) / sigma);
      }
    }
    final double[][] graph = new double[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = i + 1; j < size; j++) {
        final double union = directed[i][j] + directed[j][i] - directed[i][j] * directed[j][i];
        graph[i][j] = union;
        graph[j][i] = union;
      }
    }
    return graph;
  }

  private static double @NotNull [][] classicalMds(final double @NotNull [][] distances) {
    final int size = distances.length;
    final double[][] centered = new double[size][size];
    final double[] rowMeans = new double[size];
    double totalMean = 0d;
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        final double squared = distances[i][j] * distances[i][j];
        rowMeans[i] += squared / size;
        totalMean += squared / (size * (double) size);
      }
    }
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        centered[i][j] =
            -0.5d * (distances[i][j] * distances[i][j] - rowMeans[i] - rowMeans[j] + totalMean);
      }
    }
    final RealMatrix matrix = MatrixUtils.createRealMatrix(centered);
    final EigenDecomposition decomposition = new EigenDecomposition(matrix);
    final Integer[] eigenOrder = new Integer[size];
    for (int i = 0; i < size; i++) {
      eigenOrder[i] = i;
    }
    Arrays.sort(eigenOrder, (a, b) -> Double.compare(decomposition.getRealEigenvalue(b),
        decomposition.getRealEigenvalue(a)));
    final double[][] coordinates = new double[size][2];
    for (int dimension = 0; dimension < Math.min(2, size); dimension++) {
      final int eigenIndex = eigenOrder[dimension];
      final double scale = Math.sqrt(Math.max(0d, decomposition.getRealEigenvalue(eigenIndex)));
      final double[] vector = decomposition.getEigenvector(eigenIndex).toArray();
      for (int i = 0; i < size; i++) {
        coordinates[i][dimension] = vector[i] * scale;
      }
    }
    return coordinates;
  }

  private static void optimize(final double @NotNull [][] coordinates,
      final double @NotNull [][] graph, final int epochs) {
    final int size = coordinates.length;
    for (int epoch = 0; epoch < epochs; epoch++) {
      final double learningRate = 0.8d * (1d - (double) epoch / epochs) + 0.02d;
      for (int i = 0; i < size; i++) {
        for (int j = i + 1; j < size; j++) {
          double dx = coordinates[i][0] - coordinates[j][0];
          double dy = coordinates[i][1] - coordinates[j][1];
          double distanceSquared = dx * dx + dy * dy;
          if (distanceSquared < 1e-9) {
            dx = ((i + 1) * 0.00017d) - ((j + 1) * 0.00011d);
            dy = ((i + 1) * 0.00013d) + ((j + 1) * 0.00007d);
            distanceSquared = dx * dx + dy * dy;
          }
          final double powered = Math.pow(distanceSquared, UMAP_B);
          final double attraction =
              -2d * UMAP_A * UMAP_B * Math.pow(distanceSquared, UMAP_B - 1d) / (1d
                  + UMAP_A * powered);
          final double repulsion =
              2d * UMAP_B / ((0.001d + distanceSquared) * (1d + UMAP_A * powered));
          final double coefficient = Math.max(-4d,
              Math.min(4d, graph[i][j] * attraction + (1d - graph[i][j]) * 0.015d * repulsion));
          final double moveX = learningRate * coefficient * dx;
          final double moveY = learningRate * coefficient * dy;
          coordinates[i][0] += moveX;
          coordinates[i][1] += moveY;
          coordinates[j][0] -= moveX;
          coordinates[j][1] -= moveY;
        }
      }
    }
  }

  private static void standardize(final double @NotNull [][] coordinates) {
    for (int dimension = 0; dimension < 2; dimension++) {
      double mean = 0d;
      for (final double[] coordinate : coordinates) {
        mean += coordinate[dimension] / coordinates.length;
      }
      double variance = 0d;
      for (final double[] coordinate : coordinates) {
        coordinate[dimension] -= mean;
        variance += coordinate[dimension] * coordinate[dimension] / coordinates.length;
      }
      final double scale = Math.sqrt(variance);
      if (scale > 0d) {
        for (final double[] coordinate : coordinates) {
          coordinate[dimension] /= scale;
        }
      }
    }
  }
}
