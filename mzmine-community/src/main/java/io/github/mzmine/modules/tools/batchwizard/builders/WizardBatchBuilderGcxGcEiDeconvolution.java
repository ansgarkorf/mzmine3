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
import io.github.mzmine.modules.dataprocessing.align_gc.GCAlignerParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.dataprocessing.filter_duplicatefilter_gc_ei.GcEiDuplicateFilterModule;
import io.github.mzmine.modules.dataprocessing.filter_duplicatefilter_gc_ei.GcEiDuplicateFilterParameters;
import io.github.mzmine.modules.dataprocessing.filter_duplicatefilter_gc_ei.GcEiDuplicateHandling;
import io.github.mzmine.modules.dataprocessing.filter_duplicatefilter_gc_ei.GcEiDuplicateMzCheck;
import io.github.mzmine.modules.dataprocessing.gapfill_gc_ei.GcEiGapFillingModule;
import io.github.mzmine.modules.dataprocessing.gapfill_gc_ei.GcEiGapFillingParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.IonInterfaceGcxGcElectronImpactWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * GCxGC-EI spectral deconvolution workflow. GCxGC data is imported with each modulation as a frame
 * and the second column retention time as the second dimension of the frame, similar to LC-IMS
 * data. The GCxGC 2D feature finder (mzmine, closed source) detects 2D features directly in the ion
 * images (1D RT x 2D RT per m/z) and deconvolutes them into compounds by the 2D shapes of model
 * peaks (template deconvolution). Alignment, gap filling and the duplicate filter also use the
 * second dimension.
 */
public class WizardBatchBuilderGcxGcEiDeconvolution extends WizardBatchBuilderGcEiDeconvolution {

  // minimum shape correlation of an ion with the model peak of its compound
  private static final double MIN_SHAPE_CORRELATION = 0.8;
  // decision: features are separated if their apex is 1.5x the saddle, like the top/edge ratio of
  // the local minimum resolver for 3 data points
  private static final double MIN_TOP_SADDLE_RATIO = 1.5;
  // signals below 1 % of the apex (e.g., column bleed) are not part of a 2D feature
  private static final double EDGE_LEVEL = 0.01;
  // summed 2D RT profile must fit a single peak shape (tailing and fronting fit well)
  private static final double MIN_2D_SHAPE_QUALITY = 0.8;

  protected final int min2DDataPoints;
  protected final @NotNull RTTolerance intraSample2DTolerance;
  protected final @NotNull RTTolerance interSample2DTolerance;

  public WizardBatchBuilderGcxGcEiDeconvolution(@NotNull final WizardSequence steps) {
    // the first dimension parameters are shared with GC-EI and read by the super class
    super(steps);

    final Optional<? extends WizardStepParameters> params = steps.get(WizardPart.ION_INTERFACE);
    min2DDataPoints = getValue(params,
        IonInterfaceGcxGcElectronImpactWizardParameters.minNumberOf2DDataPoints);
    intraSample2DTolerance = getValue(params,
        IonInterfaceGcxGcElectronImpactWizardParameters.intraSample2DTolerance);
    interSample2DTolerance = getValue(params,
        IonInterfaceGcxGcElectronImpactWizardParameters.interSample2DTolerance);
  }

  @Override
  protected BatchQueue createQueueInternal() {
    final BatchQueue q = new BatchQueue();
    makeAndAddImportTask(q);
    // GCxGC mzML files contain the single scans of each modulation. Detect their signals with the
    // lower noise level of single scans. All scan types include the single scans of the
    // modulations.
    makeAndAddMassDetectionStep(q, 1, SelectedScanTypes.SCANS,
        getNoiseLevelForMsLevel(1, SelectedScanTypes.MOBLITY_SCANS));
    // 2D features and their deconvolution into compounds, no GC-EI spectral deconvolution
    make2DFeatureFinderStep(q);
    // decision: no feature shape filter, the first dimension shape of 3-6 modulations is not
    // reliable for a shape score

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
   * 2D features in the ion images, deconvoluted into compounds. The retention time crop is applied
   * here. The GCxGC 2D feature finder is part of mzmine (closed source) and registered in
   * {@link GcxGcFeatureFinderRegistry}.
   */
  private void make2DFeatureFinderStep(@NotNull final BatchQueue q) {
    final GcxGcFeatureFinderStepFactory finder = GcxGcFeatureFinderRegistry.get();
    if (finder == null) {
      throw new IllegalStateException(
          "The GCxGC 2D feature finder is not available in this mzmine version.");
    }
    // decision (user): the minimum feature height of the wizard, mass detection removes the noise
    q.add(finder.createStep(new GcxGcFeatureFinderSettings(
        new RawDataFilesSelection(RawDataFilesSelectionType.BATCH_LAST_FILES), cropRtRange,
        mzTolScans, minFeatureHeight, MIN_TOP_SADDLE_RATIO, MIN_2D_SHAPE_QUALITY, EDGE_LEVEL,
        minRtDataPoints, min2DDataPoints, intraSampleRtTol, intraSample2DTolerance,
        MIN_SHAPE_CORRELATION, minNumberOfSignalsInDeconSpectra, "decon")));
  }

  /**
   * GC-EI gap filling with co-elution checks in both dimensions
   */
  @Override
  protected void makeAndAddGcEiGapFillingStep(final BatchQueue q) {
    final ParameterSet param = GcEiGapFillingParameters.create(
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS), 3,
        interSampleRtTol, intraSampleRtTol, interSample2DTolerance, intraSample2DTolerance, "gaps",
        handleOriginalFeatureLists);
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(GcEiGapFillingModule.class), param));
  }

  /**
   * GC-EI duplicate filter, duplicates also need to be within the 2D tolerance
   */
  @Override
  protected void makeAndAddGcEiDuplicateFilterStep(final BatchQueue q) {
    final ParameterSet param = GcEiDuplicateFilterParameters.create(
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS),
        intraSampleRtTol, intraSample2DTolerance, mzTolScans,
        GcEiDuplicateMzCheck.QUANTIFIER_IN_BOTH_SPECTRA,
        GcEiDuplicateFilterParameters.DEFAULT_MIN_COSINE, GcEiDuplicateHandling.MERGE, "dup",
        handleOriginalFeatureLists);
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(GcEiDuplicateFilterModule.class), param));
  }

  @Override
  protected @NotNull ParameterSet createAlignmentParameters() {
    final ParameterSet param = super.createAlignmentParameters();
    param.setParameter(GCAlignerParameters.SECOND_DIMENSION_TOLERANCE, true,
        interSample2DTolerance);
    return param;
  }
}
