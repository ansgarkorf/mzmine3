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

package io.github.mzmine.modules.tools.batchwizard.subparameters;

import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import io.github.mzmine.util.GcxGcUtils;
import com.google.common.collect.Range;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;

import org.jetbrains.annotations.NotNull;

/**
 * Comprehensive two-dimensional GC (GCxGC) with electron ionization. The first dimension parameters
 * are shared with {@link IonInterfaceGcElectronImpactWizardParameters} and refer to the first
 * column retention time (modulations). The second dimension is the retention time on the second
 * column in minutes.
 */
public final class IonInterfaceGcxGcElectronImpactWizardParameters extends
    IonInterfaceWizardParameters {

  public static final RTToleranceParameter approximate2DFWHM = GcxGcUtils.secondDimensionRTToleranceParameter(
      "Approximate 2D feature FWHM", """
      The approximate feature width in the second dimension (second column retention time, full-width-at-half-maximum, FWHM).""",
      new RTTolerance(0.1f, Unit.SECONDS));

  public static final IntegerParameter minNumberOf2DDataPoints = new IntegerParameter(
      "Minimum 2D data points", """
      Minimum number of data points of a feature in the second dimension (consecutive scans within a modulation).""",
      3, 1, Integer.MAX_VALUE);

  public static final RTToleranceParameter intraSample2DTolerance = GcxGcUtils.secondDimensionRTToleranceParameter(
      "2D RT tolerance (intra-sample)", """
      Second dimension retention time tolerance for multiple signals of the same compound in the same sample.
      Used to group EI fragments in the spectral deconvolution.""", new RTTolerance(0.05f, Unit.SECONDS));

  public static final RTToleranceParameter interSample2DTolerance = GcxGcUtils.secondDimensionRTToleranceParameter(
      "2D RT tolerance (sample-to-sample)", """
      Second dimension retention time tolerance for the same compound in different samples.
      Used to align multiple measurements.""", new RTTolerance(0.1f, Unit.SECONDS));

  public IonInterfaceGcxGcElectronImpactWizardParameters(
      @NotNull final IonInterfaceWizardParameterFactory preset) {
    super(WizardPart.ION_INTERFACE, preset,
        // first dimension, shared with GC-EI
        IonInterfaceGcElectronImpactWizardParameters.smoothing,
        IonInterfaceGcElectronImpactWizardParameters.RECALIBRATE_RETENTION_TIMES,
        IonInterfaceGcElectronImpactWizardParameters.cropRtRange,
        IonInterfaceGcElectronImpactWizardParameters.minNumberOfDataPoints,
        IonInterfaceGcElectronImpactWizardParameters.approximateChromatographicFWHM,
        IonInterfaceGcElectronImpactWizardParameters.intraSampleRTTolerance,
        IonInterfaceGcElectronImpactWizardParameters.interSampleRTTolerance,
        // second dimension
        minNumberOf2DDataPoints, approximate2DFWHM, intraSample2DTolerance,
        interSample2DTolerance);
  }

  public IonInterfaceGcxGcElectronImpactWizardParameters(
      @NotNull final IonInterfaceWizardParameterFactory preset, @NotNull final Range<Double> cropRt,
      final int minModulations, @NotNull final RTTolerance fwhm,
      @NotNull final RTTolerance intraSampleTolerance,
      @NotNull final RTTolerance interSampleTolerance, final int min2DDataPoints,
      @NotNull final RTTolerance fwhm2D, @NotNull final RTTolerance intraSample2DTol,
      @NotNull final RTTolerance interSample2DTol) {
    this(preset);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.smoothing, false);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.RECALIBRATE_RETENTION_TIMES, false);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.cropRtRange, cropRt);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.minNumberOfDataPoints,
        minModulations);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.approximateChromatographicFWHM,
        fwhm);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.intraSampleRTTolerance,
        intraSampleTolerance);
    setParameter(IonInterfaceGcElectronImpactWizardParameters.interSampleRTTolerance,
        interSampleTolerance);
    setParameter(minNumberOf2DDataPoints, min2DDataPoints);
    setParameter(approximate2DFWHM, fwhm2D);
    setParameter(intraSample2DTolerance, intraSample2DTol);
    setParameter(interSample2DTolerance, interSample2DTol);
  }
}
