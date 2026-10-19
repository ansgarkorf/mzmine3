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
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.ranges.RTRangeParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;

/**
 * Sums the detected signals (mass lists) of each second dimension scan index over all modulations
 * of a GCxGC file. Mass detection needs to run before.
 */
public class GcxGcSecondDimensionSpectraParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter RAW_DATA_FILES = new RawDataFilesParameter();

  public static final OptionalParameter<RTRangeParameter> RT_RANGE = new OptionalParameter<>(
      new RTRangeParameter("Retention time (1D)", """
          Only modulations within this first dimension retention time range (in minutes) are \
          summed and used for feature detection.""", false, null), false);

  public static final MZToleranceParameter MZ_TOLERANCE = new MZToleranceParameter("m/z tolerance",
      "Signals of the same 2D RT scan within this tolerance are summed over the modulations.",
      0.005, 15);

  public static final IntegerParameter MIN_DETECTIONS = new IntegerParameter(
      "Minimum modulations", """
      Minimum number of modulations with a signal at the m/z at the same 2D RT.
      Signals detected in fewer modulations are removed from the 2D RT spectrum.""", 2, 1,
      Integer.MAX_VALUE);

  public GcxGcSecondDimensionSpectraParameters() {
    super(new Parameter[]{RAW_DATA_FILES, RT_RANGE, MZ_TOLERANCE, MIN_DETECTIONS});
  }
}
