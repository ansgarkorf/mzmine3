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
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;

/**
 * Builds 2D features of GCxGC data from features resolved in the first dimension
 */
public class GcxGc2DFeatureBuilderParameters extends SimpleParameterSet {

  public static final FeatureListsParameter FEATURE_LISTS = new FeatureListsParameter();

  public static final MZToleranceParameter MZ_TOLERANCE = new MZToleranceParameter("m/z tolerance",
      "Signals of the single scans within this tolerance around the chromatogram m/z are extracted.",
      0.005, 15);

  public static final OriginalFeatureListHandlingParameter HANDLE_ORIGINAL = new OriginalFeatureListHandlingParameter(
      false);

  public GcxGc2DFeatureBuilderParameters() {
    super(new Parameter[]{FEATURE_LISTS, MZ_TOLERANCE, HANDLE_ORIGINAL});
  }
}
