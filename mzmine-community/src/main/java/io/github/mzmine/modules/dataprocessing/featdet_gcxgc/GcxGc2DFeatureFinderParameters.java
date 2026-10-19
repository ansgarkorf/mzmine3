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

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.ranges.RTRangeParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import java.text.DecimalFormat;

/**
 * Finds 2D features in the ion images of GCxGC data, see {@link GcxGc2DFeatureFinderModule}
 */
public class GcxGc2DFeatureFinderParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter RAW_DATA_FILES = new RawDataFilesParameter();

  public static final OptionalParameter<RTRangeParameter> RT_RANGE = new OptionalParameter<>(
      new RTRangeParameter("Retention time (1D)", """
          Only modulations within this first dimension retention time range (in minutes) are \
          used.""", false, null), false);

  public static final MZToleranceParameter MZ_TOLERANCE = new MZToleranceParameter("m/z tolerance",
      """
          Signals within this tolerance form one ion image (m/z channel). Channels are started at \
          the most intense signals and do not overlap.""", 0.005, 15);

  public static final DoubleParameter MIN_HEIGHT = new DoubleParameter("Minimum height", """
      Minimum intensity of the most intense single scan signal (apex) of a 2D feature. Only \
      signals of at least this height start new m/z channels.""",
      MZmineCore.getConfiguration().getIntensityFormat(), 1000d, 0d, null);

  public static final DoubleParameter MIN_RATIO = new DoubleParameter(
      "Minimum top/saddle ratio", """
      Two 2D features of the same ion image are separated if the apex of the lower one is at \
      least this factor more intense than the lowest signal (saddle) between them. Features are \
      also split if their summed 2D RT or 1D RT profile has a second apex of this factor above \
      the valley. Lower ratios split more, higher ratios merge noise maxima into their \
      features.""",
      new DecimalFormat("0.00"), 1.5d, 1d, null);

  public static final PercentParameter EDGE_LEVEL = new PercentParameter("Feature edge level", """
      Signals below this fraction of the apex intensity are not part of the 2D feature. Keeps \
      baseline signals (e.g., column bleed) out of the features.""", 0.01, 0d, 1d);

  public static final DoubleParameter MIN_2D_SHAPE_QUALITY = new DoubleParameter(
      "Minimum 2D peak shape", """
      Minimum fit (R², 0 to 1) of the summed 2D RT profile of a feature to a single peak shape \
      (rising to the apex, falling after it). Gaussian, tailing, fronting and broad peaks fit \
      well, scattered noise and two merged peaks do not. The first dimension has too few \
      modulations for a shape requirement.""", new DecimalFormat("0.00"), 0.8, 0d, 1d);

  public static final IntegerParameter BACKGROUND_WINDOW = new IntegerParameter(
      "Background window (modulations)", """
      Column bleed and long tailing signals form ridges along the first dimension. A signal that \
      is present in all modulations of this window around it at the same 2D RT scan has a \
      background equal to the lowest of these signals, which is subtracted for the detection. \
      Choose a window wider than the widest compound in the first dimension. 0 disables the \
      background subtraction.""", 21, 0, Integer.MAX_VALUE);

  public static final IntegerParameter MIN_MODULATIONS = new IntegerParameter(
      "Minimum modulations", "Minimum number of modulations with signals of a 2D feature.", 2, 1,
      Integer.MAX_VALUE);

  public static final IntegerParameter MIN_2D_DATA_POINTS = new IntegerParameter(
      "Minimum 2D data points", """
      Minimum number of consecutive single scans with signals around the apex in the apex \
      modulation of a 2D feature.""", 3, 1, Integer.MAX_VALUE);

  public static final OptionalModuleParameter<GcxGcTemplateDeconvolutionParameters> TEMPLATE_DECONVOLUTION = new OptionalModuleParameter<>(
      "Template deconvolution", """
      Deconvolutes compounds directly from the 2D features: clean, intense single peaks define \
      the 2D shape of a compound, ions of other m/z with the same shape are its signals, and ions \
      that overlap several compounds are shared between them by a fit of their shapes. Creates \
      the deconvoluted feature list with compounds (pseudo spectra) instead of the 2D feature \
      list; the GC-EI spectral deconvolution is not needed afterwards.""",
      new GcxGcTemplateDeconvolutionParameters(), false);

  public static final StringParameter SUFFIX = new StringParameter("Suffix",
      "Suffix of the new feature list", "2D features");

  public GcxGc2DFeatureFinderParameters() {
    super(new Parameter[]{RAW_DATA_FILES, RT_RANGE, MZ_TOLERANCE, MIN_HEIGHT, MIN_RATIO,
        MIN_2D_SHAPE_QUALITY, EDGE_LEVEL, BACKGROUND_WINDOW, MIN_MODULATIONS,
        MIN_2D_DATA_POINTS, TEMPLATE_DECONVOLUTION, SUFFIX});
  }
}
