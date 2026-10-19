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

import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.parameters.impl.SimpleParameterSet;



/**
 * Second dimension tolerances of the GC-EI gap filling for GCxGC data.
 */
public class GcEiGapFillingSecondDimensionParameters extends SimpleParameterSet {

  public static final RTToleranceParameter SAMPLE_TO_SAMPLE_TOLERANCE = GcxGcUtils.secondDimensionRTToleranceParameter(
      "2D RT tolerance (sample-to-sample)", """
      Maximum allowed difference between the second dimension of the row (second column retention time)
      and the second dimension apex of the quantifier peak in a sample with a gap.
      Usually the same tolerance as used for the alignment.""", new RTTolerance(0.1f, Unit.SECONDS));

  public static final RTToleranceParameter INTRA_SAMPLE_COELUTION_TOLERANCE = GcxGcUtils.secondDimensionRTToleranceParameter(
      "2D co-elution tolerance (intra-sample)", """
      Maximum allowed difference between the second dimension apex of the quantifier peak and the apices
      of the other top signals in the same sample. Usually the same tolerance as used for the deconvolution.""",
      new RTTolerance(0.1f, Unit.SECONDS));

  public GcEiGapFillingSecondDimensionParameters() {
    super(SAMPLE_TO_SAMPLE_TOLERANCE, INTRA_SAMPLE_COELUTION_TOLERANCE);
  }
}
