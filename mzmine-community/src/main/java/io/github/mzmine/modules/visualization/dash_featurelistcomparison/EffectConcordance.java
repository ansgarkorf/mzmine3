/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.mzmine.modules.visualization.dash_featurelistcomparison;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-level response effects and a random-effects historical summary.
 */
record EffectConcordance(@NotNull String status, @NotNull List<ComponentEffect> components,
                         @Nullable ComparisonContrast contrast, int queryProject,
                         @NotNull List<Integer> historicalProjects) {

  EffectConcordance {
    components = List.copyOf(components);
    historicalProjects = List.copyOf(historicalProjects);
  }

  static @NotNull EffectConcordance calculate(@NotNull final FeatureListComparisonResult result,
      @NotNull final ComparisonEvidence evidence,
      @NotNull final List<ProjectSelectionAnalysis.Difference> differences,
      @NotNull final BooleanSupplier canceled) {
    final ComparisonContrast contrast = result.responseContrast();
    if (contrast == null) {
      return unavailable(
          "Map an optional historic response contrast to enable effect concordance.");
    }
    if (!result.comparableAbundances()) {
      return unavailable("Effect concordance requires comparable relative abundances.");
    }
    if (evidence.query().size() != 1 || evidence.reference().size() < 2) {
      return unavailable(
          "Select one query project and at least two historical reference projects.");
    }
    final int query = evidence.query().getFirst();
    final List<ComponentEffect> effects = new ArrayList<>();
    for (final var difference : differences) {
      if (canceled.getAsBoolean()) {
        break;
      }
      final ProjectEffect queryEffect = projectEffect(result, difference.componentIndex(), query,
          contrast);
      if (queryEffect == null) {
        continue;
      }
      final List<ProjectEffect> historical = evidence.reference().stream()
          .map(project -> projectEffect(result, difference.componentIndex(), project, contrast))
          .filter(java.util.Objects::nonNull).toList();
      if (historical.size() < 2) {
        continue;
      }
      effects.add(pool(difference.component(), queryEffect, historical));
    }
    return new EffectConcordance(effects.isEmpty()
        ? "No components have one query effect and at least two estimable historical effects."
        : "%d components with query and random-effects historical estimates · %s".formatted(
            effects.size(), contrast.label()), effects, contrast, query, evidence.reference());
  }

  private static @NotNull EffectConcordance unavailable(@NotNull final String status) {
    return new EffectConcordance(status, List.of(), null, -1, List.of());
  }

  boolean available() {
    return !components.isEmpty() && contrast != null;
  }

  @Nullable ComponentEffect component(final int id) {
    return components.stream().filter(effect -> effect.component().id() == id).findFirst()
        .orElse(null);
  }

  private static @Nullable ProjectEffect projectEffect(
      @NotNull final FeatureListComparisonResult result, final int component, final int project,
      @NotNull final ComparisonContrast contrast) {
    if (!result.profile(project).supported(component)) {
      return null;
    }
    final ProjectSampleMetadata metadata = result.sampleMetadata(project);
    if (!metadata.hasIndependentUnits()) {
      return null;
    }
    final double[] values = result.profile(project).values(component);
    final Map<UnitGroup, List<Double>> repeated = new LinkedHashMap<>();
    for (int sample = 0; sample < metadata.sampleNames().size(); sample++) {
      final String condition = metadata.attributes().get(sample).get(contrast.metadataColumn());
      final int group = contrast.numerator().equals(condition) ? 1
          : contrast.denominator().equals(condition) ? 0 : -1;
      if (group < 0 || sample >= values.length || !Double.isFinite(values[sample])) {
        continue;
      }
      repeated.computeIfAbsent(new UnitGroup(metadata.independentUnits().get(sample), group),
          _ -> new ArrayList<>()).add(values[sample]);
    }
    final double[] numerator = repeated.entrySet().stream()
        .filter(entry -> entry.getKey().group == 1).mapToDouble(entry -> ComparisonProfile.median(
            entry.getValue().stream().mapToDouble(Double::doubleValue).toArray())).toArray();
    final double[] denominator = repeated.entrySet().stream()
        .filter(entry -> entry.getKey().group == 0).mapToDouble(entry -> ComparisonProfile.median(
            entry.getValue().stream().mapToDouble(Double::doubleValue).toArray())).toArray();
    if (numerator.length < 2 || denominator.length < 2) {
      return null;
    }
    final double varianceA = variance(numerator);
    final double varianceB = variance(denominator);
    final int degrees = numerator.length + denominator.length - 2;
    final double pooledVariance =
        ((numerator.length - 1d) * varianceA + (denominator.length - 1d) * varianceB) / degrees;
    if (!(pooledVariance > 0d)) {
      return null;
    }
    final double correction = 1d - 3d / (4d * (numerator.length + denominator.length) - 9d);
    final double effect =
        correction * (mean(numerator) - mean(denominator)) / Math.sqrt(pooledVariance);
    final double effectVariance =
        (double) (numerator.length + denominator.length) / (numerator.length * denominator.length)
            + effect * effect / (2d * degrees);
    return new ProjectEffect(project, effect, effectVariance, numerator.length, denominator.length);
  }

  private static @NotNull ComponentEffect pool(@NotNull final SpectralComponent component,
      @NotNull final ProjectEffect query, @NotNull final List<ProjectEffect> historical) {
    double weightSum = 0d;
    double weightedEffect = 0d;
    double squaredWeightSum = 0d;
    for (final ProjectEffect effect : historical) {
      final double weight = 1d / effect.variance();
      weightSum += weight;
      squaredWeightSum += weight * weight;
      weightedEffect += weight * effect.effect();
    }
    final double fixed = weightedEffect / weightSum;
    double q = 0d;
    for (final ProjectEffect effect : historical) {
      q += 1d / effect.variance() * Math.pow(effect.effect() - fixed, 2d);
    }
    final double c = weightSum - squaredWeightSum / weightSum;
    final double tauSquared = c <= 0d ? 0d : Math.max(0d, (q - (historical.size() - 1d)) / c);
    weightSum = 0d;
    weightedEffect = 0d;
    for (final ProjectEffect effect : historical) {
      final double weight = 1d / (effect.variance() + tauSquared);
      weightSum += weight;
      weightedEffect += weight * effect.effect();
    }
    final double pooled = weightedEffect / weightSum;
    final double pooledVariance = 1d / weightSum;
    final double iSquared = q <= 0d ? 0d : Math.max(0d, (q - (historical.size() - 1d)) / q);
    return new ComponentEffect(component, query, historical, pooled, pooledVariance, tauSquared,
        iSquared);
  }

  private static double mean(final double @NotNull [] values) {
    return java.util.Arrays.stream(values).average().orElseThrow();
  }

  private static double variance(final double @NotNull [] values) {
    final double mean = mean(values);
    return java.util.Arrays.stream(values).map(value -> Math.pow(value - mean, 2d)).sum() / (
        values.length - 1d);
  }

  record ProjectEffect(int project, double effect, double variance, int numeratorUnits,
                       int denominatorUnits) {

    double standardError() {
      return Math.sqrt(variance);
    }

    double lower95() {
      return effect - 1.96d * standardError();
    }

    double upper95() {
      return effect + 1.96d * standardError();
    }
  }

  record ComponentEffect(@NotNull SpectralComponent component, @NotNull ProjectEffect query,
                         @NotNull List<ProjectEffect> historical, double pooledHistorical,
                         double pooledVariance, double tauSquared, double iSquared) {

    ComponentEffect {
      historical = List.copyOf(historical);
    }

    double pooledStandardError() {
      return Math.sqrt(pooledVariance);
    }

    double pooledLower95() {
      return pooledHistorical - 1.96d * pooledStandardError();
    }

    double pooledUpper95() {
      return pooledHistorical + 1.96d * pooledStandardError();
    }
  }

  private record UnitGroup(@NotNull String unit, int group) {

  }
}
