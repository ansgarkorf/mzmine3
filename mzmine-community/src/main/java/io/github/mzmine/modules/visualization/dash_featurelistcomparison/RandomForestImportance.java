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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Repeated grouped validation and held-out permutation importance for a deterministic CART forest.
 */
final class RandomForestImportance {

  private static final int TREES = 64;
  private static final int REPEATS = 3;
  private static final int FOLDS = 3;
  private static final long SEED = 0x4d5a4d494e45L;

  private RandomForestImportance() {
  }

  /**
   * Legacy descriptive impurity score, retained for small algorithm checks.
   */
  static double @NotNull [] calculate(final double @NotNull [][] values,
      final int @NotNull [] labels) {
    if (values.length == 0 || values[0].length == 0 || labels.length != values.length) {
      return new double[0];
    }
    final double[] scores = train(values, labels, new Random(SEED), () -> false).impurity();
    final double total = Arrays.stream(scores).sum();
    if (total > 0d) {
      for (int i = 0; i < scores.length; i++) {
        scores[i] /= total;
      }
    }
    return scores;
  }

  static @NotNull Validation validate(final double @NotNull [][] values,
      final int @NotNull [] labels, @NotNull final List<String> units,
      @NotNull final BooleanSupplier canceled) {
    final int p = values.length == 0 ? 0 : values[0].length;
    final int classes = Arrays.stream(labels).max().orElse(-1) + 1;
    if (classes < 2 || p == 0 || units.size() != labels.length) {
      return Validation.unavailable(p, "No eligible shared components for RF.");
    }
    for (int label = 0; label < classes; label++) {
      final Set<String> unique = new TreeSet<>();
      for (int i = 0; i < labels.length; i++) {
        if (labels[i] == label && !units.get(i).isBlank()) {
          unique.add(units.get(i));
        }
      }
      if (unique.size() < FOLDS) {
        return Validation.unavailable(p,
            "RF needs at least three independent units in every comparison group.");
      }
    }
    final double[][] repeatedImportance = new double[REPEATS][p];
    final double[] accuracy = new double[REPEATS];
    for (int repeat = 0; repeat < REPEATS; repeat++) {
      final Random random = new Random(SEED + repeat * 7919L);
      final int[] assignment = groupedFolds(labels, units, random);
      for (int fold = 0; fold < FOLDS; fold++) {
        if (canceled.getAsBoolean()) {
          return Validation.unavailable(p, "Comparison superseded.");
        }
        final List<double[]> trainValues = new ArrayList<>();
        final List<Integer> trainLabels = new ArrayList<>();
        final List<double[]> testValues = new ArrayList<>();
        final List<Integer> testLabels = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
          if (assignment[i] == fold) {
            testValues.add(values[i]);
            testLabels.add(labels[i]);
          } else {
            trainValues.add(values[i]);
            trainLabels.add(labels[i]);
          }
        }
        final int[] trainY = trainLabels.stream().mapToInt(Integer::intValue).toArray();
        final int[] testY = testLabels.stream().mapToInt(Integer::intValue).toArray();
        if (Arrays.stream(trainY).distinct().count() != classes
            || Arrays.stream(testY).distinct().count() != classes) {
          return Validation.unavailable(p,
              "Sample groups cannot form three balanced held-out folds.");
        }
        final Forest forest = train(trainValues.toArray(double[][]::new), trainY, random, canceled);
        final double[][] testX = testValues.toArray(double[][]::new);
        final double baseline = balancedAccuracy(forest, testX, testY, classes, -1, null);
        accuracy[repeat] += baseline / FOLDS;
        final int[] shuffled = new int[testX.length];
        for (int feature = 0; feature < p; feature++) {
          if (canceled.getAsBoolean()) {
            return Validation.unavailable(p, "Comparison superseded.");
          }
          if (forest.impurity()[feature] == 0d) {
            continue;
          }
          for (int i = 0; i < shuffled.length; i++) {
            shuffled[i] = i;
          }
          for (int i = shuffled.length - 1; i > 0; i--) {
            final int j = random.nextInt(i + 1);
            final int temp = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = temp;
          }
          repeatedImportance[repeat][feature] +=
              (baseline - balancedAccuracy(forest, testX, testY, classes, feature, shuffled))
                  / FOLDS;
        }
      }
    }
    final double[] mean = new double[p];
    final double[] deviation = new double[p];
    final double[] stability = new double[p];
    for (final double[] importance : repeatedImportance) {
      final List<Integer> ranked = java.util.stream.IntStream.range(0, p).boxed()
          .filter(i -> importance[i] > 0d)
          .sorted((a, b) -> Double.compare(importance[b], importance[a])).limit(10).toList();
      for (int i = 0; i < p; i++) {
        mean[i] += importance[i] / REPEATS;
        if (ranked.contains(i)) {
          stability[i] += 1d / REPEATS;
        }
      }
    }
    for (int i = 0; i < p; i++) {
      for (final double[] importance : repeatedImportance) {
        deviation[i] += Math.pow(importance[i] - mean[i], 2d) / (REPEATS - 1d);
      }
      deviation[i] = Math.sqrt(deviation[i]);
    }
    final double score = Arrays.stream(accuracy).average().orElse(0d);
    final double chance = 1d / classes;
    final int unitCount = (int) units.stream().distinct().count();
    final double threshold = Math.min(0.99d,
        chance + Math.max(0.05d, 2d * Math.sqrt(chance * (1d - chance) / unitCount)));
    final boolean informative = score > threshold;
    final double spread = Math.sqrt(
        Arrays.stream(accuracy).map(value -> Math.pow(value - score, 2d)).sum() / (REPEATS - 1d));
    final String status = ("RF: %.0f%% ± %.0f%% held-out balanced accuracy (chance %.0f%%); "
        + "3 repeated grouped 3-fold splits, %d trees/fit, %d independent units. %s "
        + "Exploratory validation; study/method confounding is not controlled.").formatted(
        score * 100d, spread * 100d, chance * 100d, TREES, unitCount,
        informative ? "Ranked by held-out permutation importance."
            : "No clear predictive separation; use descriptive effects.");
    return new Validation(true, informative, score, spread, chance, mean, deviation, stability,
        status);
  }

  /**
   * A unit always occupies one fold even when represented in several project labels.
   */
  static int @NotNull [] groupedFolds(final int @NotNull [] labels,
      @NotNull final List<String> units, @NotNull final Random random) {
    final int classes = Arrays.stream(labels).max().orElseThrow() + 1;
    final Map<String, int[]> unitClasses = new LinkedHashMap<>();
    for (int i = 0; i < units.size(); i++) {
      unitClasses.computeIfAbsent(units.get(i), _ -> new int[classes])[labels[i]] = 1;
    }
    final List<String> order = new ArrayList<>(unitClasses.keySet());
    Collections.shuffle(order, random);
    final int[][] counts = new int[FOLDS][classes];
    final Map<String, Integer> assignment = new HashMap<>();
    for (final String unit : order) {
      final int[] membership = unitClasses.get(unit);
      final int start = random.nextInt(FOLDS);
      int best = start;
      int bestCount = Integer.MAX_VALUE;
      for (int offset = 0; offset < FOLDS; offset++) {
        final int fold = (offset + start) % FOLDS;
        int count = 0;
        for (int c = 0; c < classes; c++) {
          count += membership[c] * counts[fold][c];
        }
        if (count < bestCount) {
          bestCount = count;
          best = fold;
        }
      }
      assignment.put(unit, best);
      for (int c = 0; c < classes; c++) {
        counts[best][c] += membership[c];
      }
    }
    return units.stream().mapToInt(assignment::get).toArray();
  }

  private static @NotNull Forest train(final double @NotNull [][] x, final int @NotNull [] y,
      @NotNull final Random random, @NotNull final BooleanSupplier canceled) {
    final int classes = Arrays.stream(y).max().orElseThrow() + 1;
    final List<int[]> byClass = new ArrayList<>();
    for (int label = 0; label < classes; label++) {
      final int target = label;
      byClass.add(
          java.util.stream.IntStream.range(0, y.length).filter(i -> y[i] == target).toArray());
    }
    final double[] impurity = new double[x[0].length];
    final List<Node> trees = new ArrayList<>();
    for (int tree = 0; tree < TREES && !canceled.getAsBoolean(); tree++) {
      final int[] bootstrap = new int[y.length];
      for (int i = 0; i < bootstrap.length; i++) {
        final int[] candidates = byClass.get(i % classes);
        bootstrap[i] = candidates[random.nextInt(candidates.length)];
      }
      trees.add(grow(x, y, bootstrap, 0, classes, impurity, random));
    }
    return new Forest(trees, impurity);
  }

  private static @NotNull Node grow(final double @NotNull [][] x, final int @NotNull [] y,
      final int @NotNull [] samples, final int depth, final int classes,
      final double @NotNull [] importance, @NotNull final Random random) {
    final int[] counts = counts(y, samples, classes);
    int prediction = 0;
    for (int c = 1; c < classes; c++) {
      if (counts[c] > counts[prediction]) {
        prediction = c;
      }
    }
    final double parent = gini(counts, samples.length);
    if (depth >= 8 || samples.length < 4 || parent == 0d) {
      return new Node(-1, 0d, prediction, null, null);
    }
    Split best = null;
    final List<Integer> candidates = new ArrayList<>(
        java.util.stream.IntStream.range(0, x[0].length).boxed().toList());
    Collections.shuffle(candidates, random);
    final int count = Math.max(1, (int) Math.sqrt(x[0].length));
    int inspected = 0;
    for (final int feature : candidates) {
      // Constant candidates must not prevent an otherwise valid split.
      if (inspected++ >= count && best != null && best.gain() > 1e-12) {
        break;
      }
      final double[] sorted = Arrays.stream(samples).mapToDouble(i -> x[i][feature]).distinct()
          .sorted().toArray();
      final int stride = Math.max(1, (sorted.length - 1) / 8);
      for (int i = 0; i < sorted.length - 1; i += stride) {
        final double threshold = (sorted[i] + sorted[i + 1]) / 2d;
        final int[] left = Arrays.stream(samples).filter(s -> x[s][feature] <= threshold).toArray();
        final int[] right = Arrays.stream(samples).filter(s -> x[s][feature] > threshold).toArray();
        if (left.length == 0 || right.length == 0) {
          continue;
        }
        final double gain = parent -
            (left.length * gini(counts(y, left, classes), left.length) + right.length * gini(
                counts(y, right, classes), right.length)) / samples.length;
        if (best == null || gain > best.gain()) {
          best = new Split(feature, threshold, gain, left, right);
        }
      }
    }
    if (best == null || best.gain() <= 1e-12) {
      return new Node(-1, 0d, prediction, null, null);
    }
    importance[best.feature()] += best.gain() * samples.length;
    return new Node(best.feature(), best.threshold(), prediction,
        grow(x, y, best.left(), depth + 1, classes, importance, random),
        grow(x, y, best.right(), depth + 1, classes, importance, random));
  }

  private static int @NotNull [] counts(final int @NotNull [] labels, final int @NotNull [] samples,
      final int classes) {
    final int[] counts = new int[classes];
    for (final int sample : samples) {
      counts[labels[sample]]++;
    }
    return counts;
  }

  private static double gini(final int @NotNull [] counts, final int n) {
    double squared = 0d;
    for (final int count : counts) {
      squared += Math.pow((double) count / n, 2d);
    }
    return 1d - squared;
  }

  private static double balancedAccuracy(@NotNull final Forest forest, final double @NotNull [][] x,
      final int @NotNull [] y, final int classes, final int permutedFeature,
      final int @Nullable [] shuffled) {
    final int[] total = new int[classes];
    final int[] correct = new int[classes];
    for (int i = 0; i < x.length; i++) {
      final int[] votes = new int[classes];
      for (final Node tree : forest.trees()) {
        Node node = tree;
        while (node.feature() >= 0) {
          final double value =
              node.feature() == permutedFeature && shuffled != null ? x[shuffled[i]][node.feature()]
                  : x[i][node.feature()];
          node = value <= node.threshold() ? node.left() : node.right();
        }
        votes[node.prediction()]++;
      }
      int prediction = 0;
      for (int c = 1; c < classes; c++) {
        if (votes[c] > votes[prediction]) {
          prediction = c;
        }
      }
      total[y[i]]++;
      if (prediction == y[i]) {
        correct[y[i]]++;
      }
    }
    double balanced = 0d;
    for (int c = 0; c < classes; c++) {
      balanced += total[c] == 0 ? 0d : (double) correct[c] / total[c] / classes;
    }
    return balanced;
  }

  record Validation(boolean evaluated, boolean informative, double balancedAccuracy,
                    double accuracyDeviation, double chance, double @NotNull [] importance,
                    double @NotNull [] deviation, double @NotNull [] stability,
                    @NotNull String status) {

    static @NotNull Validation unavailable(final int features, @NotNull final String reason) {
      return new Validation(false, false, Double.NaN, Double.NaN, Double.NaN, new double[features],
          new double[features], new double[features], reason);
    }
  }

  private record Forest(@NotNull List<Node> trees, double @NotNull [] impurity) {

  }

  private record Node(int feature, double threshold, int prediction, @Nullable Node left,
                      @Nullable Node right) {

  }

  private record Split(int feature, double threshold, double gain, int @NotNull [] left,
                       int @NotNull [] right) {

  }
}
