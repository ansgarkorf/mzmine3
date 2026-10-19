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

package io.github.mzmine.modules.dataprocessing.featdet_gcxgc;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import io.github.mzmine.util.GcxGcUtils;
import java.text.DecimalFormat;

/**
 * Template deconvolution of the GCxGC 2D feature finder, see
 * {@link IonImageTemplateDeconvolution}
 */
public class GcxGcTemplateDeconvolutionParameters extends SimpleParameterSet {

  public static final RTToleranceParameter RT_TOLERANCE = new RTToleranceParameter(
      "1D retention time tolerance",
      "Maximum 1D RT difference between the apexes of the ions of a compound.",
      new RTTolerance(0.06f, Unit.MINUTES));

  public static final RTToleranceParameter SECOND_DIMENSION_TOLERANCE = GcxGcUtils.secondDimensionRTToleranceParameter(
      "2D retention time tolerance",
      "Maximum 2D RT difference between the apexes of the ions of a compound.",
      new RTTolerance(0.1f, Unit.SECONDS));

  public static final DoubleParameter MIN_CORRELATION = new DoubleParameter(
      "Minimum shape similarity", """
      Minimum Pearson correlation of the 2D shape of an ion with the shape of the compound \
      (its model peak). Ions that overlap several compounds are shared between them by a fit of \
      their shapes.""", new DecimalFormat("0.00"), 0.8, 0d, 1d);

  public static final IntegerParameter MIN_SIGNALS = new IntegerParameter(
      "Minimum signals in pseudo spectrum", "Minimum number of ions (m/z) of a compound.", 8, 1,
      Integer.MAX_VALUE);

  public static final StringParameter SUFFIX = new StringParameter("Suffix",
      "Suffix of the deconvoluted feature list", "decon");

  public GcxGcTemplateDeconvolutionParameters() {
    super(new Parameter[]{RT_TOLERANCE, SECOND_DIMENSION_TOLERANCE, MIN_CORRELATION, MIN_SIGNALS,
        SUFFIX});
  }
}
