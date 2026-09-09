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

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.features.correlation.SpectralSimilarity;
import io.github.mzmine.modules.dataprocessing.group_spectral_networking.modified_cosine.ModifiedCosineSpectralNetworkingTask;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.scans.similarity.Weights;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Calculates a symmetric project-level score from individual MS2 spectrum matches.
 */
final class FeatureListComparisonCalculator {

  private FeatureListComparisonCalculator() {
  }

  static @NotNull FeatureListPairSimilarity compare(@NotNull List<SpectralFeature> spectraA,
      @NotNull List<SpectralFeature> spectraB, @NotNull FeatureListComparisonSettings settings) {
    if (spectraA.isEmpty() || spectraB.isEmpty()) {
      return new FeatureListPairSimilarity(0d, 0, spectraA.size(), spectraB.size(), 0d);
    }

    final MZTolerance precursorTolerance = settings.precursorMzTolerance();
    final List<SpectralMatch> candidates = new ArrayList<>();
    int firstCandidateB = 0;

    for (int indexA = 0; indexA < spectraA.size(); indexA++) {
      final SpectralFeature spectrumA = spectraA.get(indexA);
      final var precursorRange = precursorTolerance.getToleranceRange(spectrumA.precursorMz());
      while (firstCandidateB < spectraB.size()
          && spectraB.get(firstCandidateB).precursorMz() < precursorRange.lowerEndpoint()) {
        firstCandidateB++;
      }
      for (int indexB = firstCandidateB; indexB < spectraB.size(); indexB++) {
        final SpectralFeature spectrumB = spectraB.get(indexB);
        if (spectrumB.precursorMz() > precursorRange.upperEndpoint()) {
          break;
        }
        if (!precursorTolerance.checkWithinTolerance(spectrumA.precursorMz(),
            spectrumB.precursorMz())) {
          continue;
        }
        final double cosine = cosine(spectrumA, spectrumB, settings);
        if (cosine >= settings.minimumCosineSimilarity()) {
          candidates.add(new SpectralMatch(indexA, indexB, cosine));
        }
      }
    }

    candidates.sort(Comparator.comparingDouble(SpectralMatch::cosineSimilarity).reversed());
    final boolean[] matchedA = new boolean[spectraA.size()];
    final boolean[] matchedB = new boolean[spectraB.size()];
    int matchedSpectra = 0;
    double cosineSum = 0d;
    for (final SpectralMatch candidate : candidates) {
      if (matchedA[candidate.indexA()] || matchedB[candidate.indexB()]) {
        continue;
      }
      matchedA[candidate.indexA()] = true;
      matchedB[candidate.indexB()] = true;
      matchedSpectra++;
      cosineSum += candidate.cosineSimilarity();
    }

    final int unionSize = spectraA.size() + spectraB.size() - matchedSpectra;
    final double jaccard = unionSize == 0 ? 0d : (double) matchedSpectra / unionSize;
    final double meanCosine =
        matchedSpectra == 0 ? 0d : Math.max(0d, Math.min(1d, cosineSum / matchedSpectra));
    return new FeatureListPairSimilarity(jaccard, matchedSpectra, spectraA.size(), spectraB.size(),
        meanCosine);
  }

  static double cosine(@NotNull final SpectralFeature spectrumA,
      @NotNull final SpectralFeature spectrumB,
      @NotNull final FeatureListComparisonSettings settings) {
    final SpectralSimilarity similarity = ModifiedCosineSpectralNetworkingTask.createMS2Sim(
        settings.fragmentMzTolerance(), spectrumA.dataPoints(), spectrumB.dataPoints(),
        settings.minimumMatchedSignals(), Weights.SQRT);
    return similarity == null ? 0d : Math.max(0d, Math.min(1d, similarity.cosine()));
  }

  record SpectralFeature(int globalIndex, int projectIndex, @NotNull String projectName,
                         @NotNull PolarityType polarity, @Nullable Integer precursorCharge,
                         int rowId, double precursorMz, @NotNull DataPoint[] dataPoints,
                         @NotNull double[] sampleAbundances,
                         @NotNull List<CompoundAnnotationEvidence> annotations,
                         boolean @NotNull [] sampleDetections) {

    SpectralFeature {
      annotations = List.copyOf(annotations);
      sampleAbundances = sampleAbundances.clone();
      sampleDetections = sampleDetections.clone();
    }

    SpectralFeature(final int globalIndex, final int projectIndex,
        @NotNull final String projectName, @NotNull final PolarityType polarity,
        @Nullable final Integer precursorCharge, final int rowId, final double precursorMz,
        @NotNull final DataPoint[] dataPoints, final double @NotNull [] sampleAbundances,
        @NotNull final List<CompoundAnnotationEvidence> annotations) {
      this(globalIndex, projectIndex, projectName, polarity, precursorCharge, rowId, precursorMz,
          dataPoints, sampleAbundances, annotations, detections(sampleAbundances));
    }

    private static boolean @NotNull [] detections(final double @NotNull [] abundances) {
      final boolean[] detected = new boolean[abundances.length];
      for (int i = 0; i < abundances.length; i++) {
        detected[i] = abundances[i] > 0d;
      }
      return detected;
    }

    SpectralFeature(final int rowId, final double precursorMz,
        @NotNull final DataPoint[] dataPoints) {
      this(-1, -1, "", PolarityType.UNKNOWN, null, rowId, precursorMz, dataPoints, new double[0],
          List.of());
    }
  }

  private record SpectralMatch(int indexA, int indexB, double cosineSimilarity) {

  }
}
