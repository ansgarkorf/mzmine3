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

import io.github.mzmine.datamodel.features.correlation.SpectralSimilarity;
import io.github.mzmine.modules.dataprocessing.group_spectral_networking.modified_cosine.ModifiedCosineSpectralNetworkingTask;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.SpectralComponent.Member;
import io.github.mzmine.util.scans.similarity.Weights;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Creates one deterministic, global set of spectral components. The fixed representative avoids
 * transitive single-link chaining, while a precursor-sorted candidate window keeps the expensive
 * spectral comparisons local.
 */
final class GlobalSpectralComponentCalculator {

  private static final int MAX_CACHED_SCORES = 2_000_000;

  private GlobalSpectralComponentCalculator() {
  }

  static @NotNull Result calculate(@NotNull final List<SpectralFeature> inputSpectra,
      final int numberOfProjects, @NotNull final FeatureListComparisonSettings settings,
      @NotNull final BooleanSupplier canceled) {
    final List<SpectralFeature> spectra = inputSpectra.stream().sorted(
            Comparator.comparingDouble(SpectralFeature::precursorMz)
                .thenComparing(SpectralFeature::projectName).thenComparingInt(SpectralFeature::rowId))
        .toList();
    final ScoreCache scoreCache = new ScoreCache(settings);
    final List<ComponentBuilder> builders = new ArrayList<>();

    for (final SpectralFeature spectrum : spectra) {
      if (canceled.getAsBoolean()) {
        return new Result(List.of(), new FeatureListPairSimilarity[0][0]);
      }
      ComponentBuilder bestComponent = null;
      double bestScore = settings.minimumCosineSimilarity();
      for (int i = builders.size() - 1; i >= 0; i--) {
        final ComponentBuilder component = builders.get(i);
        final SpectralFeature representative = component.representative;
        if (representative.precursorMz() < settings.precursorMzTolerance()
            .getToleranceRange(spectrum.precursorMz()).lowerEndpoint()) {
          break;
        }
        if (!settings.precursorMzTolerance()
            .checkWithinTolerance(spectrum.precursorMz(), representative.precursorMz())) {
          continue;
        }
        if (!compatibleIonization(spectrum, representative)) {
          continue;
        }
        final double score = scoreCache.score(spectrum, representative);
        if (score >= bestScore) {
          bestScore = score;
          bestComponent = component;
        }
      }
      if (bestComponent == null) {
        builders.add(new ComponentBuilder(spectrum, numberOfProjects));
      } else {
        bestComponent.add(spectrum, bestScore);
      }
    }

    final List<SpectralComponent> components = new ArrayList<>(builders.size());
    for (int i = 0; i < builders.size(); i++) {
      components.add(builders.get(i).build(i));
    }
    final FeatureListPairSimilarity[][] similarities = createSimilarityMatrix(components,
        numberOfProjects, scoreCache);
    return new Result(List.copyOf(components), similarities);
  }

  private static @NotNull FeatureListPairSimilarity[][] createSimilarityMatrix(
      @NotNull final List<SpectralComponent> components, final int numberOfProjects,
      @NotNull final ScoreCache scoreCache) {
    final FeatureListPairSimilarity[][] matrix = new FeatureListPairSimilarity[numberOfProjects][numberOfProjects];
    final int[] projectComponentCounts = new int[numberOfProjects];
    for (final SpectralComponent component : components) {
      for (int project = 0; project < numberOfProjects; project++) {
        if (component.isPresent(project)) {
          projectComponentCounts[project]++;
        }
      }
    }

    for (int projectA = 0; projectA < numberOfProjects; projectA++) {
      final int countA = projectComponentCounts[projectA];
      final double diagonal = countA == 0 ? 0d : 1d;
      matrix[projectA][projectA] = new FeatureListPairSimilarity(diagonal, countA, countA, countA,
          diagonal);
      for (int projectB = projectA + 1; projectB < numberOfProjects; projectB++) {
        int shared = 0;
        double cosineSum = 0d;
        for (final SpectralComponent component : components) {
          if (!component.isPresent(projectA) || !component.isPresent(projectB)) {
            continue;
          }
          shared++;
          cosineSum += maximumPairScore(component, projectA, projectB, scoreCache);
        }
        final int countB = projectComponentCounts[projectB];
        final int union = countA + countB - shared;
        final double jaccard = union == 0 ? 0d : (double) shared / union;
        final double meanCosine = shared == 0 ? 0d : cosineSum / shared;
        matrix[projectA][projectB] = new FeatureListPairSimilarity(jaccard, shared, countA, countB,
            meanCosine);
        matrix[projectB][projectA] = new FeatureListPairSimilarity(jaccard, shared, countB, countA,
            meanCosine);
      }
    }
    return matrix;
  }

  private static double maximumPairScore(@NotNull final SpectralComponent component,
      final int projectA, final int projectB, @NotNull final ScoreCache scoreCache) {
    double maximum = 0d;
    for (final Member memberA : component.members()) {
      if (memberA.feature().projectIndex() != projectA) {
        continue;
      }
      for (final Member memberB : component.members()) {
        if (memberB.feature().projectIndex() == projectB) {
          maximum = Math.max(maximum, scoreCache.score(memberA.feature(), memberB.feature()));
        }
      }
    }
    return maximum;
  }

  private static boolean compatibleIonization(@NotNull final SpectralFeature spectrumA,
      @NotNull final SpectralFeature spectrumB) {
    final boolean polaritiesDefined =
        spectrumA.polarity().getSign() != 0 && spectrumB.polarity().getSign() != 0;
    if (polaritiesDefined && spectrumA.polarity() != spectrumB.polarity()) {
      return false;
    }
    return spectrumA.precursorCharge() == null || spectrumB.precursorCharge() == null
        || spectrumA.precursorCharge().equals(spectrumB.precursorCharge());
  }

  record Result(@NotNull List<SpectralComponent> components,
                @NotNull FeatureListPairSimilarity[][] similarities) {

  }

  private static final class ComponentBuilder {

    private final SpectralFeature representative;
    private final List<Member> members = new ArrayList<>();
    private final int[] projectMemberCounts;

    private ComponentBuilder(@NotNull final SpectralFeature representative,
        final int numberOfProjects) {
      this.representative = representative;
      projectMemberCounts = new int[numberOfProjects];
      add(representative, 1d);
    }

    private void add(@NotNull final SpectralFeature spectrum, final double cosine) {
      members.add(new Member(spectrum, cosine));
      projectMemberCounts[spectrum.projectIndex()]++;
    }

    private @NotNull SpectralComponent build(final int id) {
      return new SpectralComponent(id, representative, members, projectMemberCounts);
    }
  }

  private static final class ScoreCache extends LinkedHashMap<Long, Double> {

    private final FeatureListComparisonSettings settings;

    private ScoreCache(@NotNull final FeatureListComparisonSettings settings) {
      super(16_384, 0.75f, true);
      this.settings = settings;
    }

    private double score(@NotNull final SpectralFeature spectrumA,
        @NotNull final SpectralFeature spectrumB) {
      if (spectrumA.globalIndex() == spectrumB.globalIndex()) {
        return 1d;
      }
      final int low = Math.min(spectrumA.globalIndex(), spectrumB.globalIndex());
      final int high = Math.max(spectrumA.globalIndex(), spectrumB.globalIndex());
      final long key = ((long) low << 32) | (high & 0xffffffffL);
      final Double cached = get(key);
      if (cached != null) {
        return cached;
      }
      final SpectralSimilarity similarity = ModifiedCosineSpectralNetworkingTask.createMS2Sim(
          settings.fragmentMzTolerance(), spectrumA.dataPoints(), spectrumB.dataPoints(),
          settings.minimumMatchedSignals(), Weights.SQRT);
      final double score =
          similarity == null ? 0d : Math.max(0d, Math.min(1d, similarity.cosine()));
      put(key, score);
      return score;
    }

    @Override
    protected boolean removeEldestEntry(@NotNull final Map.Entry<Long, Double> eldest) {
      return size() > MAX_CACHED_SCORES;
    }
  }
}
