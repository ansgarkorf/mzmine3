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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;

/**
 * Descriptive spectral recurrence. Unmatched projects are not measured chemical absences.
 */
record ComparisonEvidence(@NotNull List<Integer> reference, @NotNull List<Integer> query,
                          int excludedReferences, @NotNull List<RecurrencePoint> points,
                          @NotNull List<RecurrenceBin> bins, @NotNull List<Pattern> patterns) {

  ComparisonEvidence {
    reference = List.copyOf(reference);
    query = List.copyOf(query);
    points = List.copyOf(points);
    bins = List.copyOf(bins);
    patterns = List.copyOf(patterns);
  }

  static @NotNull ComparisonEvidence calculate(@NotNull final FeatureListComparisonResult result,
      @NotNull final List<Integer> projects, @NotNull final List<Integer> groupA,
      @NotNull final List<Integer> groupB,
      @NotNull final List<ProjectSelectionAnalysis.Difference> differences,
      @NotNull final BooleanSupplier canceled) {
    // A selected pair has a defined A/B comparison. Otherwise, unselected projects are the context.
    final List<Integer> referenceInput =
        groupA.isEmpty() ? IntStream.range(0, result.size()).filter(p -> !projects.contains(p))
            .boxed().toList() : groupA;
    final List<Integer> query = groupA.isEmpty() ? projects : groupB;
    final List<Integer> reference = referenceInput.stream()
        .filter(p -> result.summaries().get(p).usableSpectra() > 0).toList();
    final List<RecurrencePoint> points = new ArrayList<>();
    final Map<List<Integer>, List<Integer>> patterns = new LinkedHashMap<>();
    final Map<BinKey, List<RecurrencePoint>> binned = new LinkedHashMap<>();
    for (final var difference : differences) {
      if (canceled.getAsBoolean()) {
        break;
      }
      final SpectralComponent component = difference.component();
      final List<Integer> matches = projects.stream().filter(component::isPresent).toList();
      patterns.computeIfAbsent(matches, _ -> new ArrayList<>()).add(component.id());
      if (reference.isEmpty() || query.isEmpty()) {
        continue;
      }
      final int queryMatches = (int) query.stream().filter(component::isPresent).count();
      final double[] detection = query.stream().filter(component::isPresent)
          .mapToDouble(p -> result.profile(p).detection(difference.componentIndex()))
          .filter(Double::isFinite).toArray();
      if (detection.length == 0) {
        continue; // Never place missing query evidence at detection = zero.
      }
      final Map<ProjectMethodCoverage.EvidenceState, Long> referenceStates = reference.stream().map(
              project -> result.methodCoverage(project)
                  .state(component, result.summaries().get(project).usableSpectra() > 0))
          .collect(Collectors.groupingBy(state -> state, Collectors.counting()));
      final int referenceMatches = referenceStates.getOrDefault(
          ProjectMethodCoverage.EvidenceState.MATCHED, 0L).intValue();
      final int eligibleReferences = referenceMatches + referenceStates.getOrDefault(
          ProjectMethodCoverage.EvidenceState.COMPATIBLE_NO_MATCH, 0L).intValue();
      if (eligibleReferences == 0) {
        continue;
      }
      final double meanDetection = java.util.Arrays.stream(detection).average().orElseThrow();
      final RecurrencePoint point = new RecurrencePoint(component.id(), referenceMatches,
          eligibleReferences,
          referenceStates.getOrDefault(ProjectMethodCoverage.EvidenceState.OUTSIDE_METHOD_SCOPE, 0L)
              .intValue(),
          referenceStates.getOrDefault(ProjectMethodCoverage.EvidenceState.UNKNOWN, 0L).intValue(),
          queryMatches, detection.length, meanDetection);
      points.add(point);
      final BinKey key = new BinKey(referenceMatches, (int) Math.round(meanDetection * 100d),
          detection.length < query.size());
      binned.computeIfAbsent(key, _ -> new ArrayList<>()).add(point);
    }
    final List<Pattern> grouped = patterns.entrySet().stream()
        .map(e -> new Pattern(e.getKey(), Set.copyOf(e.getValue()))).sorted(
            Comparator.comparingInt((Pattern p) -> p.components().size()).reversed().thenComparing(
                    Comparator.comparingInt((Pattern p) -> p.projects().size()).reversed())
                .thenComparing(p -> p.projects().toString())).toList();
    final List<RecurrenceBin> bins = binned.entrySet().stream().map(
        e -> new RecurrenceBin(e.getKey().referenceMatches(), e.getKey().detectionPercent() / 100d,
            e.getKey().partialQueryCoverage(),
            e.getValue().stream().map(RecurrencePoint::componentId).collect(Collectors.toSet()),
            e.getValue().stream().mapToInt(RecurrencePoint::queryMatches).min().orElse(0),
            e.getValue().stream().mapToInt(RecurrencePoint::queryMatches).max().orElse(0))).sorted(
        Comparator.comparingInt(RecurrenceBin::referenceMatches)
            .thenComparingDouble(RecurrenceBin::detection)
            .thenComparing(RecurrenceBin::partialQueryCoverage)).toList();
    return new ComparisonEvidence(reference, query, referenceInput.size() - reference.size(),
        points, bins, grouped);
  }

  record RecurrencePoint(int componentId, int referenceMatches, int eligibleReferences,
                         int outsideScopeReferences, int unknownReferences, int queryMatches,
                         int measuredQueryProjects, double detection) {

    double prevalence() {
      return eligibleReferences == 0 ? Double.NaN : (double) referenceMatches / eligibleReferences;
    }

  }

  record RecurrenceBin(int referenceMatches, double detection, boolean partialQueryCoverage,
                       @NotNull Set<Integer> components, int minimumQueryMatches,
                       int maximumQueryMatches) {

    RecurrenceBin {
      components = Set.copyOf(components);
    }
  }

  /**
   * Exact intersections of MS2-support sets, not chemical families or biological modules.
   */
  record Pattern(@NotNull List<Integer> projects, @NotNull Set<Integer> components) {

    Pattern {
      projects = List.copyOf(projects);
      components = Set.copyOf(components);
    }
  }

  private record BinKey(int referenceMatches, int detectionPercent, boolean partialQueryCoverage) {

  }
}
