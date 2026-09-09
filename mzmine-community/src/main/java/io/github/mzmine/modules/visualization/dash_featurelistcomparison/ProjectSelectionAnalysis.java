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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Directional descriptive contrasts with independent-unit validation of predictive rankings.
 */
final class ProjectSelectionAnalysis {

  private ProjectSelectionAnalysis() {
  }

  static @NotNull Result calculate(@NotNull final FeatureListComparisonResult result,
      @NotNull final List<Integer> selectedProjects) {
    return calculate(result, selectedProjects, List.of(), () -> false);
  }

  static @NotNull Result calculate(@NotNull final FeatureListComparisonResult result,
      @NotNull final List<Integer> selectedProjects, @NotNull final List<Integer> reference,
      @NotNull final BooleanSupplier canceled) {
    return calculate(result, selectedProjects, reference, false, canceled);
  }

  static @NotNull Result calculate(@NotNull final FeatureListComparisonResult result,
      @NotNull final List<Integer> selectedProjects, @NotNull final List<Integer> reference,
      final boolean useRandomForest, @NotNull final BooleanSupplier canceled) {
    final List<Integer> projects = selectedProjects.stream().distinct().sorted().toList();
    final List<Integer> groupA =
        reference.isEmpty() ? projects.size() == 2 ? List.of(projects.getFirst()) : List.of()
            : projects.stream().filter(reference::contains).toList();
    final List<Integer> groupB = groupA.isEmpty() ? List.of()
        : projects.stream().filter(project -> !groupA.contains(project)).toList();
    final boolean pair = !groupA.isEmpty() && !groupB.isEmpty();
    final List<Difference> differences = new ArrayList<>();
    for (int c = 0; c < result.components().size(); c++) {
      if (canceled.getAsBoolean()) {
        break;
      }
      final SpectralComponent component = result.components().get(c);
      final List<Double> detections = new ArrayList<>();
      final List<Double> medians = new ArrayList<>();
      int observed = 0;
      for (final int project : projects) {
        final ComparisonProfile profile = result.profile(project);
        detections.add(profile.detection(c));
        medians.add(profile.median(c));
        if (profile.supported(c)) {
          observed++;
        }
      }
      if (observed == 0) {
        continue;
      }
      final boolean complete = observed == projects.size();
      final double minimum = medians.stream().mapToDouble(Double::doubleValue)
          .filter(Double::isFinite).min().orElse(Double.NaN);
      final double maximum = medians.stream().mapToDouble(Double::doubleValue)
          .filter(Double::isFinite).max().orElse(Double.NaN);
      final double detectionRange = range(detections);
      final double range =
          result.comparableAbundances() && complete && medians.stream().allMatch(Double::isFinite)
              ? log2Ratio(maximum, minimum) : Double.NaN;
      final double signedEffect = result.comparableAbundances() && complete && pair ? log2Ratio(
          groupMedian(projects, medians, groupA), groupMedian(projects, medians, groupB))
          : Double.NaN;
      final double detectionDelta =
          complete && pair ? groupMean(projects, detections, groupA) - groupMean(projects,
              detections, groupB) : Double.NaN;
      final double abundanceScore = Double.isNaN(range) ? 0d
          : Double.isInfinite(range) ? 1d : Math.abs(range) / (1d + Math.abs(range));
      final double score = !complete ? 1d - (double) observed / projects.size()
          : Math.max(Double.isFinite(detectionRange) ? detectionRange : 0d, abundanceScore);
      final String direction;
      if (!complete) {
        direction = !pair ? "Missing MS2 evidence"
            : groupA.stream().noneMatch(component::isPresent) ? "No reference MS2 match"
                : groupB.stream().noneMatch(component::isPresent) ? "No query MS2 match"
                    : "Partial MS2 coverage";
      } else if (!result.comparableAbundances()) {
        direction = pair && Double.isFinite(detectionDelta) && Math.abs(detectionDelta) > 1e-9 ?
            detectionDelta > 0d ? "More detected in A" : "More detected in B"
            : detectionRange > 0d ? "Detection differs" : "Similar detection";
      } else if (pair && !Double.isNaN(signedEffect)) {
        direction = Math.abs(signedEffect) < 1e-9 ? "Similar abundance"
            : signedEffect > 0d ? "Higher in A" : "Higher in B";
      } else {
        direction = projects.size() == 1 ? "Single project"
            : Double.isNaN(range) ? "Abundance unavailable"
                : range > 0d ? "Abundance differs" : "Similar abundance";
      }
      differences.add(
          new Difference(component, List.copyOf(detections), List.copyOf(medians), observed, range,
              0d, score, component.annotationConsensus(projects), signedEffect, detectionDelta,
              direction, 0d, 0d, c));
    }

    final ForestResult forest =
        useRandomForest ? validate(result, projects, groupA, pair, differences, canceled)
            : ForestResult.unavailable(differences.size(), "RF off; showing descriptive evidence.");
    final List<Difference> ranked = new ArrayList<>(differences.size());
    for (int i = 0; i < differences.size(); i++) {
      final Difference d = differences.get(i);
      ranked.add(
          new Difference(d.component(), d.prevalences(), d.meanAbundances(), d.observedProjects(),
              d.log2AbundanceRange(), forest.importance()[i], d.directDifferenceScore(),
              d.annotationConsensus(), d.signedLog2Effect(), d.detectionDelta(), d.direction(),
              forest.deviation()[i], forest.stability()[i], d.componentIndex()));
    }
    ranked.sort(forest.validation().informative() ? Comparator.comparingDouble(
            Difference::randomForestImportance).reversed()
        .thenComparing(Comparator.comparingDouble(Difference::directDifferenceScore).reversed())
        : Comparator.comparingDouble(Difference::directDifferenceScore).reversed());
    final String design =
        pair ? "A: " + projectNames(result, groupA) + " · B: " + projectNames(result, groupB) + ". "
            : "Across " + projects.size() + " projects. ";
    final String quantification = result.comparableAbundances() ? result.quantification()
        + "; project medians of relative MS2-row signal. "
        : "Detection comparison; abundance comparability not enabled. ";
    final ComparisonEvidence evidence = ComparisonEvidence.calculate(result, projects, groupA,
        groupB, ranked, canceled);
    return new Result(projects, List.copyOf(ranked),
        design + quantification + forest.validation().status(), forest.validation().informative(),
        groupA, groupB, forest.validation(), result.comparableAbundances(), evidence,
        EffectConcordance.calculate(result, evidence, ranked, canceled));
  }

  /**
   * Median ratios have no arbitrary pseudocount; a zero denominator is shown as detection-only.
   */
  static double log2Ratio(final double a, final double b) {
    if (!Double.isFinite(a) || !Double.isFinite(b) || a < 0d || b < 0d) {
      return Double.NaN;
    }
    if (a == b) {
      return 0d;
    }
    if (b == 0d) {
      return Double.POSITIVE_INFINITY;
    }
    if (a == 0d) {
      return Double.NEGATIVE_INFINITY;
    }
    return Math.log(a / b) / Math.log(2d);
  }

  private static double range(@NotNull final List<Double> values) {
    if (values.stream().anyMatch(value -> !Double.isFinite(value))) {
      return Double.NaN;
    }
    return values.stream().mapToDouble(Double::doubleValue).max().orElse(0d) - values.stream()
        .mapToDouble(Double::doubleValue).min().orElse(0d);
  }

  private static double groupMedian(@NotNull final List<Integer> projects,
      @NotNull final List<Double> values, @NotNull final List<Integer> group) {
    final double[] selected = group.stream()
        .mapToDouble(project -> values.get(projects.indexOf(project))).toArray();
    return Arrays.stream(selected).allMatch(Double::isFinite) ? ComparisonProfile.median(selected)
        : Double.NaN;
  }

  private static double groupMean(@NotNull final List<Integer> projects,
      @NotNull final List<Double> values, @NotNull final List<Integer> group) {
    return group.stream().mapToDouble(project -> values.get(projects.indexOf(project))).average()
        .orElse(Double.NaN);
  }

  private static @NotNull String projectNames(@NotNull final FeatureListComparisonResult result,
      @NotNull final List<Integer> projects) {
    return projects.stream().limit(3).map(p -> result.summaries().get(p).name())
        .collect(java.util.stream.Collectors.joining(", ")) + (projects.size() > 3
        ? " (+%d more)".formatted(projects.size() - 3) : "");
  }

  private static @NotNull ForestResult validate(@NotNull final FeatureListComparisonResult result,
      @NotNull final List<Integer> projects, @NotNull final List<Integer> groupA,
      final boolean pair, @NotNull final List<Difference> differences,
      @NotNull final BooleanSupplier canceled) {
    final int size = differences.size();
    if (!result.comparableAbundances() || projects.size() < 2) {
      return ForestResult.unavailable(size,
          "RF requires comparable abundances and multiple groups.");
    }
    if (projects.stream().anyMatch(p -> !result.sampleMetadata(p).hasIndependentUnits())) {
      return ForestResult.unavailable(size,
          "RF unavailable: define independent sample IDs or declare independent raw files.");
    }
    final List<Integer> candidates = new ArrayList<>();
    for (int i = 0; i < differences.size(); i++) {
      final Difference d = differences.get(i);
      if (d.sharedByAll() && projects.stream().allMatch(
          project -> Arrays.stream(result.profile(project).values(d.componentIndex()))
              .allMatch(Double::isFinite))) {
        final double[] values = projects.stream().flatMapToDouble(
            project -> Arrays.stream(result.profile(project).values(d.componentIndex()))).toArray();
        if (Arrays.stream(values).distinct().limit(2).count() > 1) {
          candidates.add(i);
        }
      }
    }
    final int sampleCount = projects.stream()
        .mapToInt(p -> result.sampleMetadata(p).sampleNames().size()).sum();
    if ((long) candidates.size() * sampleCount > 2_000_000L) {
      return ForestResult.unavailable(size,
          "RF exceeds the interactive size limit; use a smaller project selection.");
    }
    final Map<UnitLabel, List<double[]>> repeated = new LinkedHashMap<>();
    for (int i = 0; i < projects.size(); i++) {
      final int project = projects.get(i);
      final int label = pair ? groupA.contains(project) ? 0 : 1 : i;
      final ProjectSampleMetadata metadata = result.sampleMetadata(project);
      final double[][] projectValues = candidates.stream()
          .map(index -> result.profile(project).values(differences.get(index).componentIndex()))
          .toArray(double[][]::new);
      for (int sample = 0; sample < metadata.sampleNames().size(); sample++) {
        final double[] x = new double[candidates.size()];
        for (int c = 0; c < x.length; c++) {
          x[c] = Math.log1p(projectValues[c][sample]);
        }
        repeated.computeIfAbsent(new UnitLabel(metadata.independentUnits().get(sample), label),
            _ -> new ArrayList<>()).add(x);
      }
    }
    final List<String> units = new ArrayList<>();
    final int[] labels = new int[repeated.size()];
    final double[][] values = new double[repeated.size()][candidates.size()];
    int row = 0;
    for (final var entry : repeated.entrySet()) {
      units.add(entry.getKey().unit());
      labels[row] = entry.getKey().label();
      for (int c = 0; c < candidates.size(); c++) {
        final int column = c;
        values[row][c] = ComparisonProfile.median(
            entry.getValue().stream().mapToDouble(x -> x[column]).toArray());
      }
      row++;
    }
    final var validation = RandomForestImportance.validate(values, labels, units, canceled);
    final double[] importance = new double[size];
    final double[] deviation = new double[size];
    final double[] stability = new double[size];
    for (int c = 0; c < candidates.size(); c++) {
      importance[candidates.get(c)] = validation.importance()[c];
      deviation[candidates.get(c)] = validation.deviation()[c];
      stability[candidates.get(c)] = validation.stability()[c];
    }
    return new ForestResult(validation, importance, deviation, stability);
  }

  record Result(@NotNull List<Integer> selectedProjects, @NotNull List<Difference> differences,
                @NotNull String status, boolean randomForestAvailable,
                @NotNull List<Integer> groupA, @NotNull List<Integer> groupB,
                @NotNull RandomForestImportance.Validation validation, boolean abundanceEnabled,
                @NotNull ComparisonEvidence evidence,
                @NotNull EffectConcordance effectConcordance) {

    @NotNull Result withDifferences(@NotNull final List<Difference> filtered) {
      return new Result(selectedProjects, List.copyOf(filtered), status, randomForestAvailable,
          groupA, groupB, validation, abundanceEnabled, evidence, effectConcordance);
    }

  }

  record Difference(@NotNull SpectralComponent component, @NotNull List<Double> prevalences,
                    @NotNull List<Double> meanAbundances, int observedProjects,
                    double log2AbundanceRange, double randomForestImportance,
                    double directDifferenceScore,
                    @NotNull CompoundAnnotationConsensus annotationConsensus,
                    double signedLog2Effect, double detectionDelta, @NotNull String direction,
                    double importanceDeviation, double rankStability, int componentIndex) {

    boolean sharedByAll() {
      return observedProjects == prevalences.size();
    }

    boolean exclusiveToOne() {
      return prevalences.size() > 1 && observedProjects == 1;
    }

    boolean missingEvidence() {
      return observedProjects < prevalences.size();
    }
  }

  private record UnitLabel(@NotNull String unit, int label) {

  }

  private record ForestResult(@NotNull RandomForestImportance.Validation validation,
                              double @NotNull [] importance, double @NotNull [] deviation,
                              double @NotNull [] stability) {

    private static @NotNull ForestResult unavailable(final int features,
        @NotNull final String status) {
      return new ForestResult(RandomForestImportance.Validation.unavailable(features, status),
          new double[features], new double[features], new double[features]);
    }
  }
}
