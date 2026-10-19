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

package io.github.mzmine.modules.dataprocessing.gapfill_gc_ei;

import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.ResolvingDimension;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureFinderModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureFinderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The feature detection settings that created the features of a feature list, extracted from its
 * applied methods. GC-EI gap filling extracts chromatograms with the same m/z tolerance and scans
 * and applies the same smoothing, so that gap-filled features are comparable to detected
 * features.
 *
 * @param mzTolerance         m/z tolerance of the feature detection
 * @param scanSelection       scans of the feature detection, used without selected scans
 * @param smoothingParameters parameters of the last smoothing step in the retention time
 *                            dimension, or null if there was none
 * @param minConsecutiveScans re-detected signals need at least this number of consecutive data
 *                            points (scans or GCxGC modulations) around their apex
 */
public record GcEiFeatureFindingSettings(@NotNull MZTolerance mzTolerance,
                                         @NotNull ScanSelection scanSelection,
                                         @Nullable ParameterSet smoothingParameters,
                                         int minConsecutiveScans) {

  /**
   * Extracts the latest feature detection call (chromatogram builder or GCxGC 2D feature finder)
   * and the latest smoothing call after it.
   *
   * @param methods applied methods of a feature list, oldest first
   * @throws IllegalStateException if the feature detection step is missing
   */
  public static @NotNull GcEiFeatureFindingSettings fromAppliedMethods(
      @NotNull final List<FeatureListAppliedMethod> methods) {
    final int chromIndex = lastIndexOf(methods, ModularADAPChromatogramBuilderModule.class, 0);
    final int finderIndex = lastIndexOf(methods, GcxGc2DFeatureFinderModule.class, 0);
    if (chromIndex < 0 && finderIndex < 0) {
      throw new IllegalStateException("""
          GC-EI gap filling uses the m/z tolerance of the original chromatogram building, \
          but no "Chromatogram builder" step was found in the processing history of the feature list.""");
    }
    if (finderIndex > chromIndex) {
      // GCxGC 2D features found in the ion images, both dimensions at once
      final ParameterSet finder = methods.get(finderIndex).getParameters();
      final int smoothingIndex = lastIndexOf(methods, SmoothingModule.class, finderIndex + 1);
      return new GcEiFeatureFindingSettings(
          finder.getValue(GcxGc2DFeatureFinderParameters.MZ_TOLERANCE), new ScanSelection(1),
          smoothingIndex >= 0 ? methods.get(smoothingIndex).getParameters() : null,
          finder.getValue(GcxGc2DFeatureFinderParameters.MIN_MODULATIONS));
    }

    final ParameterSet chromatogramParameters = methods.get(chromIndex).getParameters();
    // GCxGC: chromatograms are built along the 2D RT and expanded into the modulations by the 2D
    // feature builder. The first dimension starts there.
    final int builder2DIndex = lastIndexOf(methods, GcxGc2DFeatureBuilderModule.class,
        chromIndex + 1);
    final int firstDimensionIndex = builder2DIndex >= 0 ? builder2DIndex : chromIndex;

    // assumption: GC-EI workflows smooth at most once, usually before resolving
    final int smoothingIndex = lastIndexOf(methods, SmoothingModule.class,
        firstDimensionIndex + 1);
    final ParameterSet smoothingParameters =
        smoothingIndex >= 0 ? methods.get(smoothingIndex).getParameters() : null;

    int minConsecutiveScans = chromatogramParameters.getValue(
        ADAPChromatogramBuilderParameters.minimumConsecutiveScans);
    if (builder2DIndex >= 0) {
      // decision: the chromatogram builder counted 2D RT scans, use the minimum number of
      // modulations of the retention time resolver after the expansion
      final Integer minModulations = minDataPointsOfLastRtResolver(methods, builder2DIndex + 1);
      minConsecutiveScans = minModulations != null ? minModulations : 1;
    }
    return new GcEiFeatureFindingSettings(
        chromatogramParameters.getValue(ADAPChromatogramBuilderParameters.mzTolerance),
        chromatogramParameters.getValue(ADAPChromatogramBuilderParameters.scanSelection),
        smoothingParameters, minConsecutiveScans);
  }

  /**
   * @return the minimum number of data points of the last resolver in the retention time
   * dimension at or after from, or null if there is none
   */
  private static @Nullable Integer minDataPointsOfLastRtResolver(
      @NotNull final List<FeatureListAppliedMethod> methods, final int from) {
    for (int i = methods.size() - 1; i >= from; i--) {
      final FeatureListAppliedMethod method = methods.get(i);
      if (method != null && method.getParameters() instanceof GeneralResolverParameters params
          && params.getValue(GeneralResolverParameters.dimension)
          == ResolvingDimension.RETENTION_TIME) {
        return params.getValue(GeneralResolverParameters.MIN_NUMBER_OF_DATAPOINTS);
      }
    }
    return null;
  }

  /**
   * @return index of the last method of the module class at or after from, or -1
   */
  private static int lastIndexOf(@NotNull final List<FeatureListAppliedMethod> methods,
      @NotNull final Class<? extends MZmineModule> module, final int from) {
    for (int i = methods.size() - 1; i >= from; i--) {
      final FeatureListAppliedMethod method = methods.get(i);
      if (method != null && module.isInstance(method.getModule())) {
        return i;
      }
    }
    return -1;
  }
}
