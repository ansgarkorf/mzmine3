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

package io.github.mzmine.modules.tools.batchwizard.builders;

import com.google.common.collect.Range;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Settings of the GCxGC 2D feature finder step of the batch wizard
 *
 * @param files              the raw data files
 * @param rtRange            the first dimension retention time range or null for all modulations
 * @param mzTolerance        m/z tolerance of the m/z channels
 * @param minHeight          minimum apex height of a 2D feature
 * @param minRatio           minimum top/saddle ratio
 * @param min2DShape         minimum 2D peak shape (R²)
 * @param edgeLevel          feature edge level as a fraction of the apex
 * @param minModulations     minimum modulations of a 2D feature
 * @param min2DDataPoints    minimum consecutive 2D data points around the apex
 * @param rtTolerance        1D RT tolerance of the ions of a compound
 * @param secondDimTolerance 2D RT tolerance of the ions of a compound
 * @param minCorrelation     minimum shape similarity of an ion with its compound
 * @param minSignals         minimum ions of a compound
 * @param suffix             suffix of the deconvoluted feature list
 */
public record GcxGcFeatureFinderSettings(@NotNull RawDataFilesSelection files,
                                         @Nullable Range<Double> rtRange,
                                         @NotNull MZTolerance mzTolerance, double minHeight,
                                         double minRatio, double min2DShape, double edgeLevel,
                                         int minModulations, int min2DDataPoints,
                                         @NotNull RTTolerance rtTolerance,
                                         @NotNull RTTolerance secondDimTolerance,
                                         double minCorrelation, int minSignals,
                                         @NotNull String suffix) {

}
