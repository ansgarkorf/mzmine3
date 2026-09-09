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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Average-linkage clustering of projects from component Jaccard distance.
 */
final class HierarchicalProjectClustering {

  private HierarchicalProjectClustering() {
  }

  static @NotNull Result cluster(@NotNull final FeatureListPairSimilarity[][] similarities) {
    final int size = similarities.length;
    if (size == 0) {
      return new Result(new int[0], List.of());
    }
    final List<Node> active = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      active.add(Node.leaf(i));
    }
    while (active.size() > 1) {
      int bestA = 0;
      int bestB = 1;
      double bestDistance = Double.POSITIVE_INFINITY;
      for (int a = 0; a < active.size(); a++) {
        for (int b = a + 1; b < active.size(); b++) {
          final double distance = averageDistance(active.get(a), active.get(b), similarities);
          if (distance < bestDistance) {
            bestDistance = distance;
            bestA = a;
            bestB = b;
          }
        }
      }
      Node left = active.get(bestA);
      Node right = active.get(bestB);
      if (left.minimumLeaf() > right.minimumLeaf()) {
        final Node swap = left;
        left = right;
        right = swap;
      }
      active.remove(bestB);
      active.remove(bestA);
      active.add(new Node(left, right, bestDistance));
      active.sort(Comparator.comparingInt(Node::minimumLeaf));
    }

    final Node root = active.getFirst();
    final List<Integer> orderList = new ArrayList<>(size);
    root.appendLeaves(orderList);
    final int[] order = orderList.stream().mapToInt(Integer::intValue).toArray();
    final Map<Integer, Double> leafPositions = new HashMap<>();
    for (int position = 0; position < order.length; position++) {
      leafPositions.put(order[position], (double) position);
    }
    final List<Segment> segments = new ArrayList<>();
    addSegments(root, leafPositions, segments);
    return new Result(order, List.copyOf(segments));
  }

  private static double averageDistance(@NotNull final Node a, @NotNull final Node b,
      @NotNull final FeatureListPairSimilarity[][] similarities) {
    double sum = 0d;
    int pairs = 0;
    for (final int leafA : a.leaves) {
      for (final int leafB : b.leaves) {
        sum += 1d - similarities[leafA][leafB].jaccardSimilarity();
        pairs++;
      }
    }
    return sum / pairs;
  }

  private static double addSegments(@NotNull final Node node,
      @NotNull final Map<Integer, Double> leafPositions, @NotNull final List<Segment> segments) {
    if (node.isLeaf()) {
      return leafPositions.get(node.leaves[0]);
    }
    final double leftY = addSegments(node.left, leafPositions, segments);
    final double rightY = addSegments(node.right, leafPositions, segments);
    final double leftDistance = node.left.distance;
    final double rightDistance = node.right.distance;
    segments.add(new Segment(leftDistance, leftY, node.distance, leftY));
    segments.add(new Segment(rightDistance, rightY, node.distance, rightY));
    segments.add(new Segment(node.distance, leftY, node.distance, rightY));
    return (leftY + rightY) / 2d;
  }

  record Result(@NotNull int[] projectOrder, @NotNull List<Segment> segments) {

    Result {
      projectOrder = projectOrder.clone();
      segments = List.copyOf(segments);
    }

    @Override
    public int @NotNull [] projectOrder() {
      return projectOrder.clone();
    }
  }

  record Segment(double x1, double y1, double x2, double y2) {

  }

  private static final class Node {

    private final Node left;
    private final Node right;
    private final int[] leaves;
    private final double distance;

    private Node(@NotNull final Node left, @NotNull final Node right, final double distance) {
      this.left = left;
      this.right = right;
      this.distance = distance;
      leaves = Arrays.copyOf(left.leaves, left.leaves.length + right.leaves.length);
      System.arraycopy(right.leaves, 0, leaves, left.leaves.length, right.leaves.length);
    }

    private Node(final int leaf) {
      left = null;
      right = null;
      distance = 0d;
      leaves = new int[]{leaf};
    }

    private static @NotNull Node leaf(final int index) {
      return new Node(index);
    }

    private boolean isLeaf() {
      return leaves.length == 1;
    }

    private int minimumLeaf() {
      return Arrays.stream(leaves).min().orElseThrow();
    }

    private void appendLeaves(@NotNull final List<Integer> output) {
      if (isLeaf()) {
        output.add(leaves[0]);
        return;
      }
      left.appendLeaves(output);
      right.appendLeaves(output);
    }
  }
}
