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

import static io.github.mzmine.modules.visualization.dash_featurelistcomparison.ProjectComparisonBenchmarkTest.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ComparisonEvidenceTest {

  @Test
  void queryDetectionIsConditionalOnAvailableEvidenceNotZeroImputed() {
    final var result = fixture(
        new double[][][]{{{1, 1}, {1, 1}}, {{1, 1}, {1, 1}}, {{1, 0}, {1, 1}}, {null, {1, 1}}},
        false, false);
    final var analysis = ProjectSelectionAnalysis.calculate(result, List.of(0, 1, 2, 3),
        List.of(0, 1), () -> false);
    final var point = analysis.evidence().points().stream().filter(p -> p.componentId() == 0)
        .findFirst().orElseThrow();

    assertEquals(2, point.referenceMatches());
    assertEquals(2, point.eligibleReferences());
    assertEquals(1, point.queryMatches());
    assertEquals(1, point.measuredQueryProjects());
    assertEquals(0.5d, point.detection(), 1e-12);
    assertEquals(2, analysis.evidence().query().size());
    assertTrue(
        analysis.evidence().bins().stream().filter(b -> b.components().contains(0)).findFirst()
            .orElseThrow().partialQueryCoverage());
  }

  @Test
  void referenceOnlyComponentsAreNotPlottedAsZeroQueryDetection() {
    final var result = fixture(new double[][][]{{{1, 1}, {1, 1}}, {null, {1, 1}}}, false, false);
    final var analysis = ProjectSelectionAnalysis.calculate(result, List.of(0, 1));

    assertTrue(analysis.evidence().points().stream().noneMatch(p -> p.componentId() == 0));
    assertEquals(2, analysis.differences().size()); // Still inspectable in the feature table.
  }

  @Test
  void singleQueryUsesUnselectedProjectsAsContext() {
    final var result = fixture(new double[][][]{{{1, 1}}, {{1, 1}}, {{1, 1}}}, false, false);
    final var evidence = ProjectSelectionAnalysis.calculate(result, List.of(2)).evidence();

    assertEquals(List.of(0, 1), evidence.reference());
    assertEquals(List.of(2), evidence.query());
    assertEquals(2, evidence.points().getFirst().referenceMatches());
  }

  @Test
  void noHistoricalMatchFilterUsesTheSameContextAsSingleQueryRecurrence() {
    final var result = fixture(new double[][][]{{{1, 1}, null}, {{1, 1}, {1, 1}}}, false, false);
    final var model = new FeatureListComparisonDashboardModel(result);
    model.setSelectedProjects(List.of(1));
    model.analysisProperty().set(ProjectSelectionAnalysis.calculate(result, List.of(1)));
    model.focusProperty().set(ComparisonFocus.NEW_TO_REFERENCE);

    assertEquals(1, model.visibleDifferences().size());
    assertEquals(1, model.visibleDifferences().getFirst().component().id());
  }

  @Test
  void emptyReferenceSpectraAreExcludedFromTheDenominator() {
    final var result = fixture(new double[][][]{{{1, 1}}, {null}, {{1, 1}}}, false, false);
    final var evidence = ProjectSelectionAnalysis.calculate(result, List.of(0, 1, 2), List.of(0, 1),
        () -> false).evidence();

    assertEquals(List.of(0), evidence.reference());
    assertEquals(1, evidence.excludedReferences());
    assertEquals(1, evidence.points().getFirst().referenceMatches());
  }

  @Test
  void identicalCoordinatesAggregateEveryComponentWithoutDroppingMembership() {
    final var result = fixture(new double[][][]{{{1, 1}, {1, 1}, {1, 1}}, {{1, 1}, {1, 1}, {1, 1}}},
        false, false);
    final var evidence = ProjectSelectionAnalysis.calculate(result, List.of(0, 1)).evidence();

    assertEquals(3, evidence.points().size());
    assertEquals(1, evidence.bins().size());
    assertEquals(Set.of(0, 1, 2), evidence.bins().getFirst().components());
    assertEquals(1, evidence.patterns().size());
    assertEquals(Set.of(0, 1, 2), evidence.patterns().getFirst().components());
  }

  @Test
  void exactPatternsPartitionComponentsAndSupportMoreThan64Projects() {
    final double[][][] data = new double[100][3][2];
    for (int p = 0; p < 100; p++) {
      data[p][0] = new double[]{1, 1};
      data[p][1] = p == 99 ? null : new double[]{1, 1};
      data[p][2] = p == 99 ? null : new double[]{1, 1};
    }
    final var result = fixture(data, false, false);
    final var evidence = ProjectSelectionAnalysis.calculate(result,
        IntStream.range(0, 100).boxed().toList()).evidence();

    assertEquals(2, evidence.patterns().size());
    assertEquals(Set.of(1, 2), evidence.patterns().getFirst().components());
    assertEquals(99, evidence.patterns().getFirst().projects().size());
    assertEquals(List.of(), evidence.reference());
    assertEquals(List.of(), evidence.bins());
  }

  @Test
  void sharedScopeFiltersFeatureViewsAndClearsWhenProjectContextChanges() {
    final var result = fixture(new double[][][]{{{1, 1}, {1, 1}}, {{1, 1}, {1, 1}}}, false, false);
    final var model = new FeatureListComparisonDashboardModel(result);
    model.analysisProperty().set(ProjectSelectionAnalysis.calculate(result, List.of(0, 1)));
    model.componentScopeProperty().set(new ComponentScope(Set.of(1), "one feature"));

    assertEquals(1, model.visibleDifferences().size());
    assertEquals(1, model.visibleDifferences().getFirst().component().id());
    model.focusProperty().set(ComparisonFocus.MISSING_MS2);
    assertTrue(model.visibleDifferences().isEmpty());
    model.setSelectedProjects(List.of(1));
    assertNull(model.componentScopeProperty().get());
  }

  @Test
  void forestIsExplicitlyOptInEvenWhenAbundancesAreEnabled() {
    final var result = fixture(new double[][][]{{{1, 1}, {9, 9}}, {{9, 9}, {1, 1}}}, true, true);
    final var analysis = ProjectSelectionAnalysis.calculate(result, List.of(0, 1));

    assertFalse(analysis.validation().evaluated());
    assertTrue(analysis.status().contains("RF off"));
    assertFalse(new FeatureListComparisonDashboardModel(result).randomForestProperty().get());
  }

  @Test
  void responseConcordanceRequiresAndPoolsRepeatedProjectContrasts() {
    final var contrast = new ComparisonContrast("condition", "response", "baseline");
    final var result = fixture(
        new double[][][]{{{9, 8, 2, 1}, {91, 92, 98, 99}}, {{8, 9, 1, 2}, {92, 91, 99, 98}},
            {{10, 9, 2, 1}, {90, 91, 98, 99}}}, true, true, contrast);
    final var analysis = ProjectSelectionAnalysis.calculate(result, List.of(0, 1, 2), List.of(0, 1),
        () -> false);

    assertTrue(analysis.effectConcordance().available());
    assertEquals(2, analysis.effectConcordance().historicalProjects().size());
    assertTrue(analysis.effectConcordance().component(0).pooledHistorical() > 0d);
    assertTrue(analysis.effectConcordance().component(0).query().effect() > 0d);
  }

  @Test
  void methodCoverageSeparatesCompatibleOutsideAndUnknownEvidence() {
    final var result = fixture(new double[][][]{{{1, 1}, {1, 1}}, {null, {1, 1}}}, false, false);
    final SpectralComponent component = result.components().getFirst();

    assertEquals(ProjectMethodCoverage.EvidenceState.COMPATIBLE_NO_MATCH,
        new ProjectMethodCoverage(1, List.of(new ProjectMethodCoverage.Window(250d, 350d,
            Set.of(io.github.mzmine.datamodel.PolarityType.POSITIVE)))).state(component, true));
    assertEquals(ProjectMethodCoverage.EvidenceState.OUTSIDE_METHOD_SCOPE,
        new ProjectMethodCoverage(1, List.of(new ProjectMethodCoverage.Window(50d, 150d,
            Set.of(io.github.mzmine.datamodel.PolarityType.POSITIVE)))).state(component, true));
    assertEquals(ProjectMethodCoverage.EvidenceState.UNKNOWN,
        ProjectMethodCoverage.unknown(1).state(component, true));
  }
}
