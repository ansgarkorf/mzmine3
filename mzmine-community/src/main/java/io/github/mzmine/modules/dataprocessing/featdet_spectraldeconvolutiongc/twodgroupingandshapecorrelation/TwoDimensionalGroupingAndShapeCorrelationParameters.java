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

import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;

import java.text.DecimalFormat;

/**
 * Grouping of GCxGC features into compounds by first and second dimension retention time and the
 * two-dimensional feature shape.
 */
public class TwoDimensionalGroupingAndShapeCorrelationParameters extends SimpleParameterSet {

  public static final RTToleranceParameter RT_TOLERANCE = new RTToleranceParameter(
      "1D retention time tolerance", """
      Maximum allowed difference of the first dimension retention time (apex modulation).
      Should be at least one modulation period, as the apex of co-eluting ions may be detected in
      neighboring modulations.""", new RTTolerance(0.06f, Unit.MINUTES));

  public static final RTToleranceParameter SECOND_DIMENSION_TOLERANCE = GcxGcUtils.secondDimensionRTToleranceParameter(
      "2D retention time tolerance", """
      Maximum allowed difference of the second dimension retention time (second column) of the
      feature apexes.""", new RTTolerance(0.05f, Unit.SECONDS));

  public static final IntegerParameter MIN_NUMBER_OF_SIGNALS = new IntegerParameter(
      "Minimum signals in pseudo spectrum",
      "Minimum number of deconvoluted signals in pseudo spectrum", 10, true, 1, 5000);

  public static final DoubleParameter MIN_R = new DoubleParameter("Minimum shape similarity", """
      Minimum shape similarity (pearson correlation) of the two-dimensional feature shape
      (modulations x second dimension) to be added to the pseudo spectrum.""",
      new DecimalFormat("0.00"), 0.80, 0d, 1d);

  public TwoDimensionalGroupingAndShapeCorrelationParameters() {
    super(RT_TOLERANCE, SECOND_DIMENSION_TOLERANCE, MIN_NUMBER_OF_SIGNALS, MIN_R);
  }
}
