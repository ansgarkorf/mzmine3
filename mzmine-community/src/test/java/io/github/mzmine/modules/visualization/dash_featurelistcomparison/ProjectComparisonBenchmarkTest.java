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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * Controlled comparisons, not validation of cross-method biological conclusions.
 */
class ProjectComparisonBenchmarkTest {

  @Test
  void preservesConventionalDirectionalFoldChange() {
    final var result = fixture(
        new double[][][]{{{100, 100}, {9900, 9900}}, {{1000, 1000}, {9000, 9000}}}, true, false);
    final var comparison = ProjectSelectionAnalysis.calculate(result, List.of(0, 1));
    final var difference = difference(comparison, 0);

    assertEquals(-Math.log(10d) / Math.log(2d), difference.signedLog2Effect(), 1e-10);
    assertEquals(-difference.signedLog2Effect(), difference.log2AbundanceRange(), 1e-10);
    assertEquals("Higher in B", difference.direction());
    assertFalse(comparison.randomForestAvailable());
    assertTrue(ComparisonFocus.HIGHER_B.accepts(difference));
    final var reversed = ProjectSelectionAnalysis.calculate(result, List.of(0, 1), List.of(1),
        () -> false);
    assertEquals(-difference.signedLog2Effect(), difference(reversed, 0).signedLog2Effect(), 1e-10);
  }

  @Test
  void distinguishesMissingSpectraFromMeasuredNonDetection() {
    final var result = fixture(new double[][][]{{{10, 0}, {90, 100}}, {null, {100, 100}}}, true,
        false);
    final var difference = difference(ProjectSelectionAnalysis.calculate(result, List.of(0, 1)), 0);

    assertEquals(0.5d, difference.prevalences().getFirst());
    assertTrue(Double.isNaN(difference.prevalences().get(1)));
    assertTrue(Double.isNaN(difference.signedLog2Effect()));
    assertTrue(Double.isNaN(difference.log2AbundanceRange()));
    assertEquals("No query MS2 match", difference.direction());
    assertFalse(ComparisonFocus.HIGHER_A.accepts(difference));
    assertTrue(ComparisonFocus.MISSING_MS2.accepts(difference));
  }

  @Test
  void mixedMethodDefaultUsesDetectionAndNeverRanksAbundances() {
    final var result = fixture(new double[][][]{{{10, 0}, {90, 100}}, {{90, 90}, {10, 10}}}, false,
        false);
    final var comparison = ProjectSelectionAnalysis.calculate(result, List.of(0, 1));
    final var difference = difference(comparison, 0);

    assertTrue(Double.isNaN(difference.signedLog2Effect()));
    assertEquals(-0.5d, difference.detectionDelta(), 1e-10);
    assertEquals("More detected in B", difference.direction());
    assertFalse(comparison.validation().evaluated());
    assertEquals(null, result.abundanceLandscape());
  }

  @Test
  void measuredZeroRetainsDetectionInterpretationWithoutAPseudocount() {
    assertEquals(Double.POSITIVE_INFINITY, ProjectSelectionAnalysis.log2Ratio(10, 0));
    assertEquals(Double.NEGATIVE_INFINITY, ProjectSelectionAnalysis.log2Ratio(0, 10));
    assertEquals(0d, ProjectSelectionAnalysis.log2Ratio(0, 0));
    assertTrue(Double.isNaN(ProjectSelectionAnalysis.log2Ratio(Double.NaN, 10)));
  }

  @Test
  void zeroTotalOrUnknownQuantificationDoesNotBecomeZeroEffect() {
    final var zero = fixture(new double[][][]{{{0, 0}, {0, 0}}, {{1, 1}, {1, 1}}}, true, false);
    final var unknown = fixture(
        new double[][][]{{{Double.NaN, Double.NaN}, {1, 1}}, {{1, 1}, {1, 1}}}, true, false);

    for (final var result : List.of(zero, unknown)) {
      final var difference = difference(ProjectSelectionAnalysis.calculate(result, List.of(0, 1)),
          0);
      assertTrue(Double.isNaN(result.profile(0).median(0)));
      assertTrue(Double.isNaN(difference.signedLog2Effect()));
      assertTrue(Double.isNaN(difference.log2AbundanceRange()));
      assertEquals("Abundance unavailable", difference.direction());
    }
    assertFalse(zero.abundanceLandscape().available());
  }

  @Test
  void selectionExcludesComponentsSeenOnlyOutsideTheSelection() {
    final var result = fixture(new double[][][]{{{1, 1}, null}, {{1, 1}, null}, {{1, 1}, {1, 1}}},
        false, false);

    final var comparison = ProjectSelectionAnalysis.calculate(result, List.of(0, 1));

    assertEquals(1, comparison.differences().size());
    assertEquals(0, comparison.differences().getFirst().componentIndex());
  }

  @Test
  void supportsMultipleReferenceProjectsAndHistoricalNoveltyFilter() {
    final var result = fixture(new double[][][]{{null, {1, 1}}, {null, {1, 1}}, {{1, 1}, {1, 1}}},
        false, false);
    final var comparison = ProjectSelectionAnalysis.calculate(result, List.of(0, 1, 2),
        List.of(0, 1), () -> false);

    assertEquals(List.of(0, 1), comparison.groupA());
    assertEquals(List.of(2), comparison.groupB());
    assertTrue(ComparisonFocus.NEW_TO_REFERENCE.accepts(difference(comparison, 0)));
    assertFalse(ComparisonFocus.NEW_TO_REFERENCE.accepts(difference(comparison, 1)));
    assertTrue(ComparisonFocus.RECURRENT.accepts(difference(comparison, 1)));
  }

  @Test
  void repeatedInjectionsCannotSatisfyIndependentSampleRequirement() {
    final double[][][] values = new double[2][2][20];
    for (int p = 0; p < 2; p++) {
      for (int s = 0; s < 20; s++) {
        values[p][0][s] = p == 0 ? 1 : 9;
        values[p][1][s] = 10 - values[p][0][s];
      }
    }
    final var result = fixture(values, true, true);
    final var comparison = ProjectSelectionAnalysis.calculate(result, List.of(0, 1), List.of(),
        true, () -> false);

    assertFalse(comparison.validation().evaluated());
    assertTrue(comparison.status().contains("three independent units"));
  }

  @Test
  void hundredProjectSelectionReportsRecurrenceWithoutTrainingAClassifier() {
    final double[][][] values = new double[100][5][2];
    for (int p = 0; p < values.length; p++) {
      for (int c = 0; c < 5; c++) {
        Arrays.fill(values[p][c], c + 1d);
      }
    }
    final var result = fixture(values, false, false);
    final var comparison = ProjectSelectionAnalysis.calculate(result,
        IntStream.range(0, 100).boxed().toList());

    assertEquals(5, comparison.differences().size());
    assertTrue(comparison.differences().stream().allMatch(d -> d.observedProjects() == 100));
    assertTrue(comparison.differences().stream().allMatch(d -> d.directDifferenceScore() == 0d));
    assertFalse(comparison.validation().evaluated());
  }

  @Test
  void groupedSplitsKeepTheSameIndependentUnitTogetherAcrossProjects() {
    final int[] labels = IntStream.range(0, 24).map(i -> i / 12).toArray();
    final List<String> units = IntStream.range(0, 24).mapToObj(i -> "unit-" + i % 12).toList();
    final int[] folds = RandomForestImportance.groupedFolds(labels, units, new Random(17));

    for (int i = 0; i < 12; i++) {
      assertEquals(folds[i], folds[i + 12]);
    }
    for (int fold = 0; fold < 3; fold++) {
      final int selected = fold;
      assertEquals(4, IntStream.range(0, 12).filter(i -> folds[i] == selected).count());
    }
  }

  @Test
  void heldOutForestRecoversStrongSignalAndRejectsIdenticalPairedProfiles() {
    final double[][] signal = new double[60][3];
    final double[][] nullValues = new double[60][3];
    final int[] labels = new int[60];
    final Random random = new Random(117);
    final List<String> units = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      labels[i] = i / 30;
      signal[i] = new double[]{labels[i] * 10d, random.nextDouble(), 1d};
      nullValues[i] = new double[]{i % 30, (i % 30) % 3, 1d};
      units.add("unit-" + i % 30);
    }
    final var strong = RandomForestImportance.validate(signal, labels, units, () -> false);
    final var identical = RandomForestImportance.validate(nullValues, labels, units, () -> false);

    assertTrue(strong.evaluated());
    assertTrue(strong.informative(), strong.status());
    assertTrue(strong.balancedAccuracy() > 0.9, strong.status());
    assertTrue(strong.importance()[0] > strong.importance()[1]);
    assertTrue(strong.importance()[0] > 0.2d);
    assertEquals(1d, strong.stability()[0], 1e-10);
    assertTrue(identical.evaluated());
    assertEquals(0.5d, identical.balancedAccuracy(), 1e-10);
    assertFalse(identical.informative());
  }

  private static @NotNull ProjectSelectionAnalysis.Difference difference(
      @NotNull final ProjectSelectionAnalysis.Result comparison, final int component) {
    return comparison.differences().stream().filter(d -> d.componentIndex() == component)
        .findFirst().orElseThrow();
  }

  /**
   * Values are project × component × sample; a null component means no matched MS2 evidence.
   */
  static @NotNull FeatureListComparisonResult fixture(final double @NotNull [][][] values,
      final boolean comparable, final boolean repeatedUnits) {
    return fixture(values, comparable, repeatedUnits, null);
  }

  static @NotNull FeatureListComparisonResult fixture(final double @NotNull [][][] values,
      final boolean comparable, final boolean repeatedUnits,
      @org.jetbrains.annotations.Nullable final ComparisonContrast contrast) {
    final int projects = values.length;
    final List<SpectralComponent> components = new ArrayList<>();
    final List<FeatureListSummary> summaries = new ArrayList<>();
    final List<ProjectSampleMetadata> metadata = new ArrayList<>();
    for (int c = 0; c < values[0].length; c++) {
      final List<SpectralComponent.Member> members = new ArrayList<>();
      final int[] counts = new int[projects];
      for (int p = 0; p < projects; p++) {
        if (values[p][c] == null) {
          continue;
        }
        final var feature = new SpectralFeature(c * projects + p, p, "Project " + p,
            PolarityType.POSITIVE, 1, c + 1, 300d + c,
            new DataPoint[]{new io.github.mzmine.datamodel.impl.SimpleDataPoint(100d, 10d),
                new io.github.mzmine.datamodel.impl.SimpleDataPoint(120d, 5d)}, values[p][c],
            List.of());
        members.add(new SpectralComponent.Member(feature, 1d));
        counts[p] = 1;
      }
      components.add(new SpectralComponent(c, members.getFirst().feature(), members, counts));
    }
    for (int p = 0; p < projects; p++) {
      final int project = p;
      final int count = (int) components.stream().filter(c -> c.isPresent(project)).count();
      final int samples = Arrays.stream(values[p]).filter(java.util.Objects::nonNull).findFirst()
          .orElse(new double[2]).length;
      summaries.add(new FeatureListSummary(p, "Project " + p, count, count, count, 0));
      final List<String> names = IntStream.range(0, samples).mapToObj(i -> "sample-" + i).toList();
      final List<String> units = IntStream.range(0, samples)
          .mapToObj(i -> repeatedUnits ? "P" + project + "-unit-" + i % 2 : "").toList();
      final List<java.util.Map<String, String>> attributes = IntStream.range(0, samples)
          .mapToObj(i -> {
            final java.util.Map<String, String> valuesByColumn = new java.util.LinkedHashMap<>();
            valuesByColumn.put("mzmine_sample_type", "sample");
            if (contrast != null) {
              valuesByColumn.put(contrast.metadataColumn(),
                  i < samples / 2 ? contrast.numerator() : contrast.denominator());
            }
            return java.util.Map.copyOf(valuesByColumn);
          }).toList();
      metadata.add(new ProjectSampleMetadata(p, names, units, 0, attributes));
    }
    final FeatureListPairSimilarity[][] matrix = new FeatureListPairSimilarity[projects][projects];
    for (int a = 0; a < projects; a++) {
      for (int b = 0; b < projects; b++) {
        int shared = 0;
        for (final var component : components) {
          if (component.isPresent(a) && component.isPresent(b)) {
            shared++;
          }
        }
        final int countA = summaries.get(a).usableSpectra();
        final int countB = summaries.get(b).usableSpectra();
        final int union = countA + countB - shared;
        matrix[a][b] = new FeatureListPairSimilarity(union == 0 ? 0d : (double) shared / union,
            shared, countA, countB, shared == 0 ? 0d : 1d);
      }
    }
    final var settings = new FeatureListComparisonSettings(new MZTolerance(0.01, 10),
        new MZTolerance(0.01, 10), 1, 0.8);
    final var clustering = HierarchicalProjectClustering.cluster(matrix);
    return new FeatureListComparisonResult(summaries, matrix, settings, components,
        clustering.projectOrder(), clustering.segments(), UmapProjector.project(matrix), metadata,
        ComparisonQuantification.AREA, comparable,
        IntStream.range(0, projects).mapToObj(ProjectMethodCoverage::unrestricted).toList(),
        contrast);
  }
}
