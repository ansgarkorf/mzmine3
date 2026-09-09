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
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result consumed by the feature-list comparison dashboard.
 */
public final class FeatureListComparisonResult {

  private final List<FeatureListSummary> summaries;
  private final FeatureListPairSimilarity[][] similarities;
  private final FeatureListComparisonSettings settings;
  private final List<SpectralComponent> components;
  private final int[] clusteredProjectOrder;
  private final List<HierarchicalProjectClustering.Segment> dendrogramSegments;
  private final double[][] projectUmapCoordinates;
  private final List<ProjectSampleMetadata> sampleMetadata;
  private final List<ProjectMethodCoverage> methodCoverage;
  private final @Nullable ComparisonContrast responseContrast;
  private final ComparisonQuantification quantification;
  private final boolean comparableAbundances;
  private final List<ComparisonProfile> profiles;
  private final ProjectLandscape compositionLandscape;
  private final @Nullable ProjectLandscape abundanceLandscape;

  public FeatureListComparisonResult(@NotNull List<FeatureListSummary> summaries,
      @NotNull FeatureListPairSimilarity[][] similarities,
      @NotNull FeatureListComparisonSettings settings, @NotNull List<SpectralComponent> components,
      final int @NotNull [] clusteredProjectOrder,
      @NotNull List<HierarchicalProjectClustering.Segment> dendrogramSegments,
      final double @NotNull [][] projectUmapCoordinates,
      @NotNull List<ProjectSampleMetadata> sampleMetadata) {
    this(summaries, similarities, settings, components, clusteredProjectOrder, dendrogramSegments,
        projectUmapCoordinates, sampleMetadata, ComparisonQuantification.AREA, false,
        java.util.stream.IntStream.range(0, summaries.size())
            .mapToObj(ProjectMethodCoverage::unrestricted).toList(), null);
  }

  public FeatureListComparisonResult(@NotNull final List<FeatureListSummary> summaries,
      @NotNull final FeatureListPairSimilarity[][] similarities,
      @NotNull final FeatureListComparisonSettings settings,
      @NotNull final List<SpectralComponent> components,
      final int @NotNull [] clusteredProjectOrder,
      @NotNull final List<HierarchicalProjectClustering.Segment> dendrogramSegments,
      final double @NotNull [][] projectUmapCoordinates,
      @NotNull final List<ProjectSampleMetadata> sampleMetadata,
      @NotNull final ComparisonQuantification quantification, final boolean comparableAbundances) {
    this(summaries, similarities, settings, components, clusteredProjectOrder, dendrogramSegments,
        projectUmapCoordinates, sampleMetadata, quantification, comparableAbundances,
        java.util.stream.IntStream.range(0, summaries.size())
            .mapToObj(ProjectMethodCoverage::unrestricted).toList(), null);
  }

  public FeatureListComparisonResult(@NotNull final List<FeatureListSummary> summaries,
      @NotNull final FeatureListPairSimilarity[][] similarities,
      @NotNull final FeatureListComparisonSettings settings,
      @NotNull final List<SpectralComponent> components,
      final int @NotNull [] clusteredProjectOrder,
      @NotNull final List<HierarchicalProjectClustering.Segment> dendrogramSegments,
      final double @NotNull [][] projectUmapCoordinates,
      @NotNull final List<ProjectSampleMetadata> sampleMetadata,
      @NotNull final ComparisonQuantification quantification, final boolean comparableAbundances,
      @NotNull final List<ProjectMethodCoverage> methodCoverage) {
    this(summaries, similarities, settings, components, clusteredProjectOrder, dendrogramSegments,
        projectUmapCoordinates, sampleMetadata, quantification, comparableAbundances,
        methodCoverage, null);
  }

  public FeatureListComparisonResult(@NotNull final List<FeatureListSummary> summaries,
      @NotNull final FeatureListPairSimilarity[][] similarities,
      @NotNull final FeatureListComparisonSettings settings,
      @NotNull final List<SpectralComponent> components,
      final int @NotNull [] clusteredProjectOrder,
      @NotNull final List<HierarchicalProjectClustering.Segment> dendrogramSegments,
      final double @NotNull [][] projectUmapCoordinates,
      @NotNull final List<ProjectSampleMetadata> sampleMetadata,
      @NotNull final ComparisonQuantification quantification, final boolean comparableAbundances,
      @NotNull final List<ProjectMethodCoverage> methodCoverage,
      @Nullable final ComparisonContrast responseContrast) {
    if (similarities.length != summaries.size()) {
      throw new IllegalArgumentException("Similarity matrix must match summary count");
    }
    for (final FeatureListPairSimilarity[] row : similarities) {
      if (row.length != summaries.size()) {
        throw new IllegalArgumentException("Similarity matrix must be square");
      }
    }
    this.summaries = List.copyOf(summaries);
    this.similarities = copyMatrix(similarities);
    this.settings = settings;
    this.components = List.copyOf(components);
    this.clusteredProjectOrder = clusteredProjectOrder.clone();
    this.dendrogramSegments = List.copyOf(dendrogramSegments);
    this.projectUmapCoordinates = copyCoordinates(projectUmapCoordinates);
    this.sampleMetadata = List.copyOf(sampleMetadata);
    if (methodCoverage.size() != summaries.size()) {
      throw new IllegalArgumentException("Method coverage must match summary count");
    }
    this.methodCoverage = List.copyOf(methodCoverage);
    this.responseContrast = responseContrast;
    this.quantification = quantification;
    this.comparableAbundances = comparableAbundances;
    profiles = java.util.stream.IntStream.range(0, summaries.size()).mapToObj(
        project -> new ComparisonProfile(components, project,
            sampleMetadata.get(project).sampleNames().size())).toList();
    compositionLandscape = new ProjectLandscape("MS2 composition · Jaccard", this.similarities,
        this.clusteredProjectOrder, this.dendrogramSegments, this.projectUmapCoordinates);
    abundanceLandscape =
        comparableAbundances ? ProjectLandscape.abundance(profiles, components.size()) : null;
  }

  public @NotNull List<FeatureListSummary> summaries() {
    return summaries;
  }

  public @NotNull FeatureListPairSimilarity similarity(final int indexA, final int indexB) {
    return similarities[indexA][indexB];
  }

  public int size() {
    return summaries.size();
  }

  public @NotNull FeatureListComparisonSettings settings() {
    return settings;
  }

  public @NotNull List<SpectralComponent> components() {
    return components;
  }

  public int @NotNull [] clusteredProjectOrder() {
    return clusteredProjectOrder.clone();
  }

  public @NotNull List<HierarchicalProjectClustering.Segment> dendrogramSegments() {
    return dendrogramSegments;
  }

  public double @NotNull [] projectUmapCoordinate(final int projectIndex) {
    return projectUmapCoordinates[projectIndex].clone();
  }

  public @NotNull ProjectSampleMetadata sampleMetadata(final int projectIndex) {
    return sampleMetadata.get(projectIndex);
  }

  public @NotNull ProjectMethodCoverage methodCoverage(final int projectIndex) {
    return methodCoverage.get(projectIndex);
  }

  public @Nullable ComparisonContrast responseContrast() {
    return responseContrast;
  }

  @NotNull ComparisonProfile profile(final int projectIndex) {
    return profiles.get(projectIndex);
  }

  public @NotNull ComparisonQuantification quantification() {
    return quantification;
  }

  public boolean comparableAbundances() {
    return comparableAbundances;
  }

  @NotNull ProjectLandscape compositionLandscape() {
    return compositionLandscape;
  }

  @Nullable ProjectLandscape abundanceLandscape() {
    return abundanceLandscape;
  }

  private static @NotNull FeatureListPairSimilarity[][] copyMatrix(
      @NotNull FeatureListPairSimilarity[][] source) {
    final FeatureListPairSimilarity[][] copy = new FeatureListPairSimilarity[source.length][];
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i].clone();
    }
    return copy;
  }

  private static double @NotNull [][] copyCoordinates(final double @NotNull [][] source) {
    final double[][] copy = new double[source.length][];
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i].clone();
    }
    return copy;
  }
}
