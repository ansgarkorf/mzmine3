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

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureFinderModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureFinderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGcTemplateDeconvolutionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import org.jetbrains.annotations.NotNull;

/**
 * GCxGC-EI spectral deconvolution workflow with the GCxGC 2D feature finder: the 2D features are
 * found directly in the ion images (1D RT x 2D RT per m/z) instead of building and resolving
 * chromatograms in both dimensions. All other steps and parameters are the same as in
 * {@link WizardBatchBuilderGcxGcEiDeconvolution}, so both workflows can be compared.
 */
public class WizardBatchBuilderGcxGcEi2DFeatureFinder extends
    WizardBatchBuilderGcxGcEiDeconvolution {

  // minimum shape correlation of an ion with its compound, like the 2D deconvolution
  private static final double MIN_SHAPE_CORRELATION = 0.8;

  // decision: features are separated if their apex is 1.5x the saddle, like the top/edge ratio of
  // the local minimum resolver for 3 data points
  private static final double MIN_TOP_SADDLE_RATIO = 1.5;
  // signals below 1 % of the apex (e.g., column bleed) are not part of a 2D feature
  private static final double EDGE_LEVEL = 0.01;
  // summed 2D RT profile must fit a single peak shape (tailing and fronting fit well)
  private static final double MIN_2D_SHAPE_QUALITY = 0.8;

  private final boolean templateDeconvolution;

  public WizardBatchBuilderGcxGcEi2DFeatureFinder(@NotNull final WizardSequence steps) {
    this(steps, false);
  }

  /**
   * @param templateDeconvolution true to deconvolute the compounds in the 2D feature finder by
   *                              the shapes of model peaks instead of the GC-EI spectral
   *                              deconvolution
   */
  public WizardBatchBuilderGcxGcEi2DFeatureFinder(@NotNull final WizardSequence steps,
      final boolean templateDeconvolution) {
    super(steps);
    this.templateDeconvolution = templateDeconvolution;
  }

  @Override
  protected BatchQueue createQueueInternal() {
    final BatchQueue q = new BatchQueue();
    makeAndAddImportTask(q);
    // signals of the single scans with the lower noise level of single scans
    makeAndAddMassDetectionStep(q, 1, SelectedScanTypes.SCANS,
        getNoiseLevelForMsLevel(1, SelectedScanTypes.MOBLITY_SCANS));
    make2DFeatureFinderStep(q);
    if (!templateDeconvolution) {
      makeSpectralDeconvolutionStep(q);
    }
    if (recalibrateRetentionTime) {
      makeAndAddRetentionTimeCalibration(q, mzTolInterSample, interSampleRtTol,
          handleOriginalFeatureLists);
    }
    makeAndAddAlignmentStep(q);
    makeAndAddRowFilterStep(q);
    makeAndAddGcEiGapFillingStep(q);
    makeAndAddGcEiDuplicateFilterStep(q);
    if (applySpectralNetworking) {
      makeAndAddSpectralNetworkingSteps(q, isExportActive, exportPath, true);
    }
    makeAndAddLibrarySearchMS1Step(q, false);
    makeAndAddIonNotationStep(q);
    makeAndAddGcEiExportSteps(q);
    return q;
  }

  /**
   * 2D features in the ion images
   */
  private void make2DFeatureFinderStep(@NotNull final BatchQueue q) {
    final ParameterSet param = MZmineCore.getConfiguration()
        .getModuleParameters(GcxGc2DFeatureFinderModule.class).cloneParameterSet();
    param.setParameter(GcxGc2DFeatureFinderParameters.RAW_DATA_FILES,
        new RawDataFilesSelection(RawDataFilesSelectionType.BATCH_LAST_FILES));
    param.setParameter(GcxGc2DFeatureFinderParameters.RT_RANGE, cropRtRange != null,
        cropRtRange);
    param.setParameter(GcxGc2DFeatureFinderParameters.MZ_TOLERANCE, mzTolScans);
    // decision (user): the minimum feature height of the wizard, mass detection removes the noise
    param.setParameter(GcxGc2DFeatureFinderParameters.MIN_HEIGHT, minFeatureHeight);
    param.setParameter(GcxGc2DFeatureFinderParameters.MIN_RATIO, MIN_TOP_SADDLE_RATIO);
    param.setParameter(GcxGc2DFeatureFinderParameters.MIN_2D_SHAPE_QUALITY,
        MIN_2D_SHAPE_QUALITY);
    param.setParameter(GcxGc2DFeatureFinderParameters.EDGE_LEVEL, EDGE_LEVEL);
    param.setParameter(GcxGc2DFeatureFinderParameters.MIN_MODULATIONS, minRtDataPoints);
    param.setParameter(GcxGc2DFeatureFinderParameters.MIN_2D_DATA_POINTS, min2DDataPoints);
    param.setParameter(GcxGc2DFeatureFinderParameters.SUFFIX, "2D features");
    param.setParameter(GcxGc2DFeatureFinderParameters.TEMPLATE_DECONVOLUTION,
        templateDeconvolution);
    final ParameterSet templates = param.getParameter(
        GcxGc2DFeatureFinderParameters.TEMPLATE_DECONVOLUTION).getEmbeddedParameters();
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.RT_TOLERANCE, intraSampleRtTol);
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.SECOND_DIMENSION_TOLERANCE,
        intraSample2DTolerance);
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.MIN_CORRELATION,
        MIN_SHAPE_CORRELATION);
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.MIN_SIGNALS,
        minNumberOfSignalsInDeconSpectra);
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.SUFFIX, "decon");
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(GcxGc2DFeatureFinderModule.class), param));
  }
}
