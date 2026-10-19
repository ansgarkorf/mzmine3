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

package io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.twodgroupingandshapecorrelation;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionAlgorithm;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionUtils;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Spectral deconvolution of GCxGC GC-EI data. Features are grouped into compounds by the first
 * dimension retention time (modulation), the second dimension retention time (stored as the
 * 2D RT of the feature) and the correlation of the two-dimensional feature shapes. Features are
 * processed from highest to lowest and the highest feature of each group is its representative.
 */
public class TwoDimensionalGroupingAndShapeCorrelationAlgorithm implements
    SpectralDeconvolutionAlgorithm {

  private final RTTolerance rtTolerance;
  private final RTTolerance secondDimensionTolerance;
  private final int minNumberOfSignals;
  private final double minR;

  // needs default constructor for config
  public TwoDimensionalGroupingAndShapeCorrelationAlgorithm() {
    this(null, null, 1, 0.8);
  }

  public TwoDimensionalGroupingAndShapeCorrelationAlgorithm(
      @NotNull final ParameterSet parameters) {
    this(parameters.getValue(TwoDimensionalGroupingAndShapeCorrelationParameters.RT_TOLERANCE),
        parameters.getValue(
            TwoDimensionalGroupingAndShapeCorrelationParameters.SECOND_DIMENSION_TOLERANCE),
        parameters.getValue(
            TwoDimensionalGroupingAndShapeCorrelationParameters.MIN_NUMBER_OF_SIGNALS),
        parameters.getValue(TwoDimensionalGroupingAndShapeCorrelationParameters.MIN_R));
  }

  public TwoDimensionalGroupingAndShapeCorrelationAlgorithm(final RTTolerance rtTolerance,
      final RTTolerance secondDimensionTolerance, final int minNumberOfSignals,
      final double minR) {
    this.rtTolerance = rtTolerance;
    this.secondDimensionTolerance = secondDimensionTolerance;
    this.minNumberOfSignals = minNumberOfSignals;
    this.minR = minR;
  }

  @Override
  public List<List<ModularFeature>> groupFeatures(final List<ModularFeature> input) {
    // sort a copy, the input list may be shared, e.g., by the preview
    final List<ModularFeature> features = new ArrayList<>(input);
    features.sort(Comparator.comparingDouble(ModularFeature::getHeight).reversed());

    // clusters are kept sorted by the RT of their representative for a fast candidate lookup
    final List<List<ModularFeature>> clusters = new ArrayList<>();
    final FloatArrayList clusterRts = new FloatArrayList();

    for (final ModularFeature feature : features) {
      final Range<Float> rtRange = rtTolerance.getToleranceRange(feature.getRT());

      List<ModularFeature> bestCluster = null;
      double highestCorrelation = Double.NEGATIVE_INFINITY;
      for (int i = lowerBound(clusterRts, rtRange.lowerEndpoint());
          i < clusters.size() && clusterRts.getFloat(i) <= rtRange.upperEndpoint(); i++) {
        final List<ModularFeature> cluster = clusters.get(i);
        final ModularFeature representative = cluster.getFirst();
        if (!isWithinSecondDimension(representative, feature)) {
          continue;
        }
        final double correlation = SpectralDeconvolutionUtils.calculateShapeCorrelation(
            representative, feature);
        if (correlation >= minR && correlation > highestCorrelation) {
          bestCluster = cluster;
          highestCorrelation = correlation;
        }
      }

      if (bestCluster != null) {
        bestCluster.add(feature);
      } else {
        // decision: a feature without a correlating compound is the start of a new compound. In
        // GCxGC, co-eluting compounds within the tolerances are expected and should not be lost.
        // Groups of noise features are removed by the minimum number of signals.
        final int insertAt = lowerBound(clusterRts, feature.getRT());
        final List<ModularFeature> newCluster = new ArrayList<>();
        newCluster.add(feature);
        clusters.add(insertAt, newCluster);
        clusterRts.add(insertAt, feature.getRT());
      }
    }

    // Remove clusters that do not meet the minimum number of signals
    clusters.removeIf(cluster -> cluster.size() < minNumberOfSignals);
    return clusters;
  }

  /**
   * @return true if the apex second dimension retention times are within the tolerance
   */
  private boolean isWithinSecondDimension(@NotNull final ModularFeature representative,
      @NotNull final ModularFeature feature) {
    final Float secondDim1 = representative.getSecondDimensionRT();
    final Float secondDim2 = feature.getSecondDimensionRT();
    // assumption: features without a second dimension (not resolved in 2D RT) are only grouped
    // by retention time and shape
    if (secondDim1 == null || secondDim2 == null) {
      return true;
    }
    return secondDimensionTolerance.checkWithinTolerance(secondDim1, secondDim2);
  }

  /**
   * @return the first index with a value >= the searched value
   */
  private static int lowerBound(@NotNull final FloatArrayList sorted, final float value) {
    int low = 0;
    int high = sorted.size();
    while (low < high) {
      final int mid = (low + high) >>> 1;
      if (sorted.getFloat(mid) < value) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  @Override
  public RTTolerance getRtTolerance() {
    return rtTolerance;
  }

  @Override
  public @Nullable RTTolerance getSecondDimensionTolerance() {
    return secondDimensionTolerance;
  }

  @Override
  public @NotNull String getName() {
    return "2D rt grouping and shape correlation";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return TwoDimensionalGroupingAndShapeCorrelationParameters.class;
  }
}
