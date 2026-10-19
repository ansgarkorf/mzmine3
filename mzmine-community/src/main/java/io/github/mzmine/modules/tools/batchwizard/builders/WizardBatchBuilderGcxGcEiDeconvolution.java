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

import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureBuilderParameters;
import com.google.common.collect.Range;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.ResolvingDimension;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGcSecondDimensionSpectraModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGcSecondDimensionSpectraParameters;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.dataprocessing.align_gc.GCAlignerParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionAlgorithms;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionGCModule;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionGCParameters;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.twodgroupingandshapecorrelation.TwoDimensionalGroupingAndShapeCorrelationParameters;
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

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * GCxGC-EI spectral deconvolution workflow. GCxGC data is imported with each modulation as a frame
 * and the second column retention time as the second dimension of the frame, similar to LC-IMS
 * data. The second dimension has many more data points, so chromatograms are built and resolved
 * in the second dimension first, along the signals summed over all modulations (2D RT spectra).
 * They are then expanded into the modulations (2D features) and resolved in the first dimension.
 * EI fragments are then grouped into compounds by both retention times and the two-dimensional
 * feature shape. Alignment, gap filling and the duplicate filter also use the second dimension.
 */
public class WizardBatchBuilderGcxGcEiDeconvolution extends WizardBatchBuilderGcEiDeconvolution {

  // minimum height of 2D RT chromatograms as a multiple of the noise level of single scans
  protected static final double MIN_2D_HEIGHT_NOISE_FACTOR = 5d;
  // minimum intensity of consecutive 2D RT chromatogram points as a multiple of the scan noise
  protected static final double MIN_2D_GROUP_NOISE_FACTOR = 2d;

  protected final int min2DDataPoints;
  protected final @NotNull RTTolerance fwhm2D;
  protected final @NotNull RTTolerance intraSample2DTolerance;
  protected final @NotNull RTTolerance interSample2DTolerance;

  public WizardBatchBuilderGcxGcEiDeconvolution(@NotNull final WizardSequence steps) {
    // the first dimension parameters are shared with GC-EI and read by the super class
    super(steps);

    final Optional<? extends WizardStepParameters> params = steps.get(WizardPart.ION_INTERFACE);
    min2DDataPoints = getValue(params,
        IonInterfaceGcxGcElectronImpactWizardParameters.minNumberOf2DDataPoints);
    fwhm2D = getValue(params, IonInterfaceGcxGcElectronImpactWizardParameters.approximate2DFWHM);
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
    // decision (user): the second dimension has many more data points. Sum the signals of each
    // 2D RT over all modulations, build and resolve chromatograms in the second dimension first
    makeAndAddSecondDimensionSpectraStep(q);
    makeAndAddSecondDimensionChromatogramStep(q);
    makeAndAdd2DResolverStep(q);

    // then expand them into the modulations and resolve them in the first dimension
    makeAndAdd2DFeatureBuilderStep(q);
    if (rtSmoothing) {
      makeAndAddSmoothingStep(q, true, minRtDataPoints, false);
    }
    makeAndAddRtLocalMinResolver(q, null, minRtDataPoints, cropRtRange, rtFwhm, 100);

    makeSpectralDeconvolutionStep(q);
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
   * Detected signals of the single scans summed over the modulations into one spectrum per 2D RT.
   * The retention time crop is applied here, as all later steps use these modulations.
   */
  private void makeAndAddSecondDimensionSpectraStep(@NotNull final BatchQueue q) {
    final ParameterSet param = MZmineCore.getConfiguration()
        .getModuleParameters(GcxGcSecondDimensionSpectraModule.class).cloneParameterSet();
    param.setParameter(GcxGcSecondDimensionSpectraParameters.RAW_DATA_FILES,
        new RawDataFilesSelection(RawDataFilesSelectionType.BATCH_LAST_FILES));
    param.setParameter(GcxGcSecondDimensionSpectraParameters.RT_RANGE, cropRtRange != null,
        cropRtRange);
    param.setParameter(GcxGcSecondDimensionSpectraParameters.MZ_TOLERANCE, mzTolScans);
    param.setParameter(GcxGcSecondDimensionSpectraParameters.MIN_DETECTIONS, 2);
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(GcxGcSecondDimensionSpectraModule.class), param));
  }

  /**
   * Chromatograms along the 2D RT spectra. Their intensities are sums of single scans over few
   * modulations, so the thresholds are based on the single scan noise level.
   */
  private void makeAndAddSecondDimensionChromatogramStep(@NotNull final BatchQueue q) {
    final double scanNoise = getNoiseLevelForMsLevel(1, SelectedScanTypes.MOBLITY_SCANS);
    // the retention time crop was applied to the 2D RT spectra
    final ScanSelection scans = new ScanSelection(1, null, polarity.toScanPolaritySelection());
    final ParameterSet param = ADAPChromatogramBuilderParameters.create(
        new RawDataFilesSelection(RawDataFilesSelectionType.BATCH_LAST_FILES), scans,
        min2DDataPoints, mzTolScans, "eics", scanNoise * MIN_2D_GROUP_NOISE_FACTOR,
        scanNoise * MIN_2D_HEIGHT_NOISE_FACTOR, true);
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(ModularADAPChromatogramBuilderModule.class), param));
  }

  /**
   * Expands the resolved 2D RT chromatograms into the modulations
   */
  private void makeAndAdd2DFeatureBuilderStep(@NotNull final BatchQueue q) {
    final ParameterSet param = MZmineCore.getConfiguration()
        .getModuleParameters(GcxGc2DFeatureBuilderModule.class).cloneParameterSet();
    param.setParameter(GcxGc2DFeatureBuilderParameters.FEATURE_LISTS,
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS));
    param.setParameter(GcxGc2DFeatureBuilderParameters.MZ_TOLERANCE, mzTolScans);
    param.setParameter(GcxGc2DFeatureBuilderParameters.HANDLE_ORIGINAL,
        handleOriginalFeatureLists);
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(GcxGc2DFeatureBuilderModule.class), param));
  }

  /**
   * Local minimum resolver in the second dimension retention time along the 2D RT spectra
   */
  private void makeAndAdd2DResolverStep(@NotNull final BatchQueue q) {
    final ParameterSet param = MZmineCore.getConfiguration()
        .getModuleParameters(MinimumSearchFeatureResolverModule.class).cloneParameterSet();
    param.setParameter(MinimumSearchFeatureResolverParameters.PEAK_LISTS,
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS));
    param.setParameter(MinimumSearchFeatureResolverParameters.SUFFIX, "r2d");
    param.setParameter(MinimumSearchFeatureResolverParameters.handleOriginal,
        handleOriginalFeatureLists);
    param.setParameter(MinimumSearchFeatureResolverParameters.groupMS2Parameters, false);
    param.setParameter(MinimumSearchFeatureResolverParameters.dimension,
        ResolvingDimension.SECOND_DIMENSION_RT);
    param.setParameter(MinimumSearchFeatureResolverParameters.CHROMATOGRAPHIC_THRESHOLD_LEVEL,
        0.50);
    // 2D RT in minutes
    param.setParameter(MinimumSearchFeatureResolverParameters.SEARCH_RT_RANGE,
        (double) fwhm2D.getToleranceInMinutes());
    param.setParameter(MinimumSearchFeatureResolverParameters.MIN_RELATIVE_HEIGHT, 0d);
    // the height is measured on the 2D RT spectra, sums of single scans of few modulations
    param.setParameter(MinimumSearchFeatureResolverParameters.MIN_ABSOLUTE_HEIGHT,
        getNoiseLevelForMsLevel(1, SelectedScanTypes.MOBLITY_SCANS) * MIN_2D_HEIGHT_NOISE_FACTOR);
    param.setParameter(MinimumSearchFeatureResolverParameters.MIN_RATIO, 1.4d);
    // at most 10 s wide in the second dimension
    param.setParameter(MinimumSearchFeatureResolverParameters.PEAK_DURATION,
        Range.closed(0d, 10d / 60d));
    param.setParameter(MinimumSearchFeatureResolverParameters.MIN_NUMBER_OF_DATAPOINTS,
        min2DDataPoints);
    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(MinimumSearchFeatureResolverModule.class), param));
  }

  @Override
  protected void makeSpectralDeconvolutionStep(final BatchQueue q) {
    final ParameterSet param = MZmineCore.getConfiguration()
        .getModuleParameters(SpectralDeconvolutionGCModule.class).cloneParameterSet();

    param.setParameter(SpectralDeconvolutionGCParameters.FEATURE_LISTS,
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS));
    param.setParameter(SpectralDeconvolutionGCParameters.HANDLE_ORIGINAL,
        handleOriginalFeatureLists);
    param.setParameter(SpectralDeconvolutionGCParameters.SUFFIX, "decon");
    param.setParameter(SpectralDeconvolutionGCParameters.MZ_VALUES_TO_IGNORE, false);

    final var deconParams = param.getParameter(
            SpectralDeconvolutionGCParameters.SPECTRAL_DECONVOLUTION_ALGORITHM)
        .setOptionGetParameters(SpectralDeconvolutionAlgorithms.TWO_D_GROUPING_AND_SHAPE_CORRELATION);
    deconParams.setParameter(TwoDimensionalGroupingAndShapeCorrelationParameters.RT_TOLERANCE,
        intraSampleRtTol);
    deconParams.setParameter(
        TwoDimensionalGroupingAndShapeCorrelationParameters.SECOND_DIMENSION_TOLERANCE,
        intraSample2DTolerance);
    deconParams.setParameter(
        TwoDimensionalGroupingAndShapeCorrelationParameters.MIN_NUMBER_OF_SIGNALS,
        minNumberOfSignalsInDeconSpectra);
    deconParams.setParameter(TwoDimensionalGroupingAndShapeCorrelationParameters.MIN_R, 0.8);

    q.add(new MZmineProcessingStepImpl<>(
        MZmineCore.getModuleInstance(SpectralDeconvolutionGCModule.class), param));
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
