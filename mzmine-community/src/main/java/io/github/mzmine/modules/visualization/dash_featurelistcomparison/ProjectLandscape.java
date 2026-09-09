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

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A common distance definition for the heatmap, clustering, and embedding.
 */
record ProjectLandscape(@NotNull String label, @NotNull FeatureListPairSimilarity[][] matrix,
                        int @NotNull [] order,
                        @NotNull List<HierarchicalProjectClustering.Segment> dendrogram,
                        double @NotNull [][] umap) {

  static @NotNull ProjectLandscape abundance(@NotNull final List<ComparisonProfile> profiles,
      final int components) {
    final int n = profiles.size();
    final FeatureListPairSimilarity[][] matrix = new FeatureListPairSimilarity[n][n];
    boolean complete = true;
    for (int a = 0; a < n; a++) {
      for (int b = a; b < n; b++) {
        double distance = 0d;
        double total = 0d;
        int shared = 0;
        for (int c = 0; c < components; c++) {
          final double x = profiles.get(a).median(c);
          final double y = profiles.get(b).median(c);
          if (Double.isFinite(x) && Double.isFinite(y)) {
            distance += Math.abs(x - y);
            total += x + y;
            shared++;
          }
        }
        complete &= total > 0d;
        final double similarity = total > 0d ? Math.max(0d, 1d - distance / total) : 0d;
        matrix[a][b] = new FeatureListPairSimilarity(similarity, shared, shared, shared, 0d);
        matrix[b][a] = matrix[a][b];
      }
    }
    // A missing comparison cannot be represented honestly by a zero distance or by dissimilarity.
    if (!complete) {
      return new ProjectLandscape("Relative abundance unavailable: no common quantified signal",
          matrix, new int[0], List.of(), new double[0][0]);
    }
    final var clustering = HierarchicalProjectClustering.cluster(matrix);
    return new ProjectLandscape("Relative abundance · 1 − Bray–Curtis", matrix,
        clustering.projectOrder(), clustering.segments(), UmapProjector.project(matrix));
  }

  boolean available() {
    return order.length == matrix.length;
  }

  @Override
  public @NotNull String toString() {
    return label;
  }
}
