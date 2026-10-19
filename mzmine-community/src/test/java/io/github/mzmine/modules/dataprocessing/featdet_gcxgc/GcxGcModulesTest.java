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

import io.github.mzmine.datamodel.features.types.FeatureShapeSecondDimensionType;
import com.google.common.collect.Range;
import io.github.mzmine.modules.dataprocessing.filter_groupms2.GroupMS2SubParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.ResolvingDimension;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.SecondDimensionSpectrum;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.FeatureShapeIonMobilityRetentionTimeHeatMapType;
import io.github.mzmine.datamodel.features.types.FeatureShapeMobilogramType;
import io.github.mzmine.datamodel.features.types.MobilityUnitType;
import io.github.mzmine.datamodel.features.types.numbers.CCSType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityRangeType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.SecondDimensionRTRangeType;
import io.github.mzmine.datamodel.features.types.numbers.SecondDimensionRTType;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_imsexpander.ImsExpanderModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.gapfill_gc_ei.GcEiFeatureFindingSettings;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.dataprocessing.featdet_mobilityscanmerger.MobilityScanMergerModule;
import io.github.mzmine.modules.io.projectsave.RawDataSavingUtils;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.util.FeatureUtils;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.ParsingUtils;
import io.github.mzmine.util.scans.ScanUtils;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * The GCxGC modules on a small GCxGC file (3 modulations of 200 scans): chromatograms are built and
 * resolved along the 2D RT first, then expanded into the modulations and resolved in the retention
 * time. GCxGC reuses the ion mobility processing internally, but users must not see ion mobility
 * modules or data types.
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class GcxGcModulesTest {

  private static final String TEST_FILE = "rawdatafiles/additional/gcxgc_pollen_3modulations.mzML";
  private static final MZTolerance MZ_TOLERANCE = new MZTolerance(0.005, 15);
  private static final List<Class<? extends MZmineModule>> IMS_MODULES = List.of(
      MobilityScanMergerModule.class, ImsExpanderModule.class);

  private IMSRawDataFile file;

  @BeforeAll
  void initialize() throws InterruptedException {
    MZmineTestUtil.startMzmineCore();
    MZmineTestUtil.cleanProject();
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.importFiles(List.of(TEST_FILE), 120));
    file = (IMSRawDataFile) ProjectService.getProject().getCurrentRawDataFiles().getFirst();
  }

  @AfterAll
  void tearDown() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  @Order(1)
  void secondDimensionSpectraSumTheModulations() throws InterruptedException {
    // regular mass detection on all scan types detects the signals of the single scans
    final ParameterSet detection = new MassDetectionParameters().cloneParameterSet();
    detection.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{file}));
    final var detector = MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL.createMassDetectorParameters(
        10);
    detection.getParameter(MassDetectionParameters.massDetector)
        .setValue(detector.value(), detector.parameters());
    detection.setParameter(MassDetectionParameters.scanSelection, new ScanSelection());
    detection.setParameter(MassDetectionParameters.scanTypes, SelectedScanTypes.SCANS);
    detection.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, MassDetectionModule.class, detection));

    final ParameterSet params = new GcxGcSecondDimensionSpectraParameters().cloneParameterSet();
    params.setParameter(GcxGcSecondDimensionSpectraParameters.RAW_DATA_FILES,
        new RawDataFilesSelection(new RawDataFile[]{file}));
    params.setParameter(GcxGcSecondDimensionSpectraParameters.RT_RANGE, false);
    params.setParameter(GcxGcSecondDimensionSpectraParameters.MZ_TOLERANCE, MZ_TOLERANCE);
    // every signal is kept, so the sums can be compared
    params.setParameter(GcxGcSecondDimensionSpectraParameters.MIN_DETECTIONS, 1);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, GcxGcSecondDimensionSpectraModule.class,
            params));

    // one spectrum per 2D scan index, sorted by 2D RT
    final List<SecondDimensionSpectrum> spectra = GcxGcUtils.getSecondDimensionSpectra(file);
    final int numScans = file.getFrames().stream().mapToInt(Frame::getNumberOfMobilityScans).max()
        .orElseThrow();
    Assertions.assertEquals(numScans, spectra.size());
    for (int i = 0; i < spectra.size(); i++) {
      final SecondDimensionSpectrum spectrum = spectra.get(i);
      Assertions.assertEquals(i, spectrum.getScanNumber());
      // the merged signals are the mass list
      Assertions.assertNotNull(spectrum.getMassList());
      Assertions.assertEquals(spectrum.getNumberOfDataPoints(),
          spectrum.getMassList().getNumberOfDataPoints());
      Assertions.assertEquals(file.getFrame(0).getMobilityScan(i).getMobility(),
          spectrum.getRetentionTime(), 1e-5);
      if (i > 0) {
        Assertions.assertTrue(spectrum.getRetentionTime() > spectra.get(i - 1).getRetentionTime());
      }
    }
    // the summed spectrum keeps the total intensity of the scans with this index
    final int index = mostIntenseIndex(spectra);
    double expected = 0;
    for (final Frame modulation : file.getFrames()) {
      final var massList = modulation.getMobilityScan(index).getMassList();
      for (int dp = 0; dp < massList.getNumberOfDataPoints(); dp++) {
        expected += massList.getIntensityValue(dp);
      }
    }
    Assertions.assertEquals(expected, spectra.get(index).getTIC(), expected * 1e-6);
    Assertions.assertEquals(Range.closed(file.getFrame(0).getRetentionTime(),
            file.getFrames().getLast().getRetentionTime()),
        ((GCxGCRawDataFile) file).getSecondDimensionSpectraRTRange());

    // mass detection and the GCxGC module are in the processing history and replayed on load
    Assertions.assertTrue(file.getAppliedMethods().stream()
        .anyMatch(m -> m.getModule() instanceof GcxGcSecondDimensionSpectraModule));
    Assertions.assertTrue(file.getAppliedMethods().stream()
        .noneMatch(m -> IMS_MODULES.contains(m.getModule().getClass())));
    final var replay = RawDataSavingUtils.makeBatchQueue(List.of(file));
    Assertions.assertTrue(
        replay.stream().anyMatch(step -> step.getModule() instanceof MassDetectionModule));
    Assertions.assertTrue(replay.stream()
        .anyMatch(step -> step.getModule() instanceof GcxGcSecondDimensionSpectraModule));
  }

  @Test
  @Order(2)
  void scanLabelsUseModulations() {
    final Frame modulation = file.getFrame(0);
    final String modulationLabel = ScanUtils.scanToString(modulation);
    final String scanLabel = ScanUtils.scanToString(modulation.getMobilityScan(10));
    final String spectrumLabel = ScanUtils.scanToString(
        GcxGcUtils.getSecondDimensionSpectra(file).get(10));
    Assertions.assertTrue(modulationLabel.startsWith("Modulation #"), modulationLabel);
    Assertions.assertTrue(scanLabel.startsWith("Scan #"), scanLabel);
    Assertions.assertTrue(spectrumLabel.startsWith("2D RT spectrum #10"), spectrumLabel);
    for (final String label : List.of(modulationLabel, scanLabel, spectrumLabel,
        modulation.getMobilityScan(10).getScanDefinition())) {
      Assertions.assertFalse(label.toLowerCase().matches(".*(frame|mobility).*"), label);
    }
  }

  @Test
  @Order(3)
  void featuresAreDetectedInTheSecondDimensionFirst() throws InterruptedException {
    // chromatograms along the 2D RT spectra
    final ParameterSet adap = ADAPChromatogramBuilderParameters.create(
        new RawDataFilesSelection(new RawDataFile[]{file}), new ScanSelection(1), 3, MZ_TOLERANCE,
        "eics", 20, 50, true);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, ModularADAPChromatogramBuilderModule.class,
            adap));
    final ModularFeatureList chromatograms = lastList();
    Assertions.assertTrue(chromatograms.getNumberOfRows() > 0);
    Assertions.assertTrue(chromatograms.getSeletedScans(file).stream()
        .allMatch(SecondDimensionSpectrum.class::isInstance));
    for (final ModularFeature feature : chromatograms.getFeatures(file)) {
      Assertions.assertTrue(GcxGcUtils.isSecondDimensionChromatogram(feature));
      Assertions.assertFalse(GcxGcUtils.isGcxGcFeature(feature));
      // no first dimension retention time before the expansion
      Assertions.assertNull(feature.getRT());
      Assertions.assertNotNull(feature.getSecondDimensionRT());
      Assertions.assertTrue(feature.getSecondDimensionRT() < 1d / 60);
    }
    Assertions.assertTrue(chromatograms.hasRowType(FeatureShapeSecondDimensionType.class));
    assertNoIonMobilityTypes(chromatograms);

    // the retention time dimension needs the expansion into the modulations first
    Assertions.assertInstanceOf(TaskResult.ERROR.class,
        MZmineTestUtil.callModuleWithTimeout(60, MinimumSearchFeatureResolverModule.class,
            resolverParameters(chromatograms, ResolvingDimension.RETENTION_TIME)));
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, MinimumSearchFeatureResolverModule.class,
            resolverParameters(chromatograms, ResolvingDimension.SECOND_DIMENSION_RT)));
    final ModularFeatureList resolved2D = lastList();
    Assertions.assertTrue(resolved2D.getNumberOfRows() > 0);
    Assertions.assertTrue(resolved2D.getFeatures(file).stream()
        .allMatch(GcxGcUtils::isSecondDimensionChromatogram));

    // expand into the modulations
    final ParameterSet builder = new GcxGc2DFeatureBuilderParameters().cloneParameterSet();
    builder.setParameter(GcxGc2DFeatureBuilderParameters.FEATURE_LISTS,
        new FeatureListsSelection(resolved2D));
    builder.setParameter(GcxGc2DFeatureBuilderParameters.MZ_TOLERANCE, MZ_TOLERANCE);
    builder.setParameter(GcxGc2DFeatureBuilderParameters.HANDLE_ORIGINAL,
        OriginalFeatureListOption.KEEP);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, GcxGc2DFeatureBuilderModule.class, builder));
    final ModularFeatureList features2D = lastList();
    Assertions.assertTrue(features2D.getNumberOfRows() > 0);
    Assertions.assertTrue(features2D.getSeletedScans(file).stream()
        .allMatch(GcxGcUtils::isModulation));
    for (final ModularFeature feature : features2D.getFeatures(file)) {
      Assertions.assertTrue(GcxGcUtils.isGcxGcFeature(feature));
      Assertions.assertNotNull(feature.getRT());
      // the 2D RT stays within the resolved 2D RT range of the chromatogram, the summed 2D trace
      // has a zero data point at each edge
      final var chromatogram = resolved2D.findRowByID(feature.getRow().getID()).getFeature(file);
      final Range<Float> range2D = chromatogram.getSecondDimensionRTRange();
      final float step = (float) scanStep() * 1.01f;
      Assertions.assertTrue(Range.closed(range2D.lowerEndpoint() - step,
              range2D.upperEndpoint() + step).encloses(feature.getSecondDimensionRTRange()),
          feature + " " + range2D + " " + feature.getSecondDimensionRTRange());
    }

    // the mobility dimension is rejected for GCxGC features
    Assertions.assertInstanceOf(TaskResult.ERROR.class,
        MZmineTestUtil.callModuleWithTimeout(60, MinimumSearchFeatureResolverModule.class,
            resolverParameters(features2D, ResolvingDimension.MOBILITY)));
    // the retention time dimension accepts the 2D features. The test file has only 3 modulations,
    // too few for the local minimum search, the Biogluc workflow test resolves real data
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, MinimumSearchFeatureResolverModule.class,
            MinimumSearchFeatureResolverParameters.create(new FeatureListsSelection(features2D),
                "r", OriginalFeatureListOption.KEEP, false, new GroupMS2SubParameters(),
                ResolvingDimension.RETENTION_TIME, 0d, 0.05, 0d, 50, 1d, Range.closed(0d, 10d),
                2)));

    final ModularFeatureList resolved = features2D;
    Assertions.assertTrue(resolved.hasRowType(SecondDimensionRTType.class));
    Assertions.assertTrue(resolved.hasFeatureType(SecondDimensionRTRangeType.class));
    // second shape column next to the RT shapes
    Assertions.assertTrue(resolved.hasRowType(FeatureShapeSecondDimensionType.class));
    assertNoIonMobilityTypes(resolved);
    Assertions.assertTrue(resolved.getAppliedMethods().stream()
        .anyMatch(m -> m.getModule() instanceof GcxGc2DFeatureBuilderModule));
    Assertions.assertTrue(resolved.getAppliedMethods().stream()
        .noneMatch(m -> IMS_MODULES.contains(m.getModule().getClass())));

    for (final ModularFeature feature : resolved.getFeatures(file)) {
      Assertions.assertTrue(GcxGcUtils.isGcxGcFeature(feature));
      Assertions.assertFalse(FeatureUtils.isImsFeature(feature));
      Assertions.assertNotNull(feature.getRT());
      Assertions.assertNotNull(feature.getSecondDimensionRT());
      // 2D RT in minutes within the 1 s of the scans in the test file
      Assertions.assertTrue(feature.getSecondDimensionRT() < 1d / 60);
      Assertions.assertNull(feature.getMobility());
    }
    // row bindings
    Assertions.assertNotNull(resolved.getRow(0).getAverageSecondDimensionRT());
    Assertions.assertNotNull(resolved.getRow(0).getSecondDimensionRTRange());
  }

  @Test
  @Order(4)
  void secondDimensionSpectraAreIndexedAfterAllScans() {
    final List<Scan> indexed = GcxGcUtils.getScansForIndexing(file);
    final List<SecondDimensionSpectrum> spectra = GcxGcUtils.getSecondDimensionSpectra(file);
    Assertions.assertEquals(file.getScans().size() + spectra.size(), indexed.size());
    Assertions.assertSame(file.getScans().getFirst(), indexed.getFirst());
    Assertions.assertSame(spectra.getFirst(), indexed.get(file.getScans().size()));

    // scan references as saved in projects
    final List<Scan> scans = List.of(spectra.get(3), spectra.get(7));
    final String saved = ParsingUtils.scanListToStrings(scans).get(file);
    Assertions.assertEquals(scans, ParsingUtils.stringToScanList(saved, file));
    final int[] indices = ParsingUtils.getIndicesOfSubListElements(scans, indexed);
    Assertions.assertEquals(scans, ParsingUtils.getSublistFromIndices(indexed, indices));
  }

  @Test
  @Order(5)
  void featureFinderFindsFeaturesInIonImages() throws InterruptedException {
    final ParameterSet params = new GcxGc2DFeatureFinderParameters().cloneParameterSet();
    params.setParameter(GcxGc2DFeatureFinderParameters.RAW_DATA_FILES,
        new RawDataFilesSelection(new RawDataFile[]{file}));
    params.setParameter(GcxGc2DFeatureFinderParameters.RT_RANGE, false);
    params.setParameter(GcxGc2DFeatureFinderParameters.MZ_TOLERANCE, MZ_TOLERANCE);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_HEIGHT, 100d);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_RATIO, 1.5);
    params.setParameter(GcxGc2DFeatureFinderParameters.EDGE_LEVEL, 0.01);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_MODULATIONS, 2);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_2D_DATA_POINTS, 3);
    params.setParameter(GcxGc2DFeatureFinderParameters.SUFFIX, "2D features");
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, GcxGc2DFeatureFinderModule.class, params));

    final ModularFeatureList features = lastList();
    Assertions.assertTrue(features.getName().endsWith("2D features"));
    Assertions.assertTrue(features.getNumberOfRows() > 0);
    Assertions.assertTrue(features.getSeletedScans(file).stream()
        .allMatch(GcxGcUtils::isModulation));
    Assertions.assertTrue(features.hasRowType(FeatureShapeSecondDimensionType.class));
    assertNoIonMobilityTypes(features);
    for (final ModularFeature feature : features.getFeatures(file)) {
      Assertions.assertTrue(GcxGcUtils.isGcxGcFeature(feature));
      Assertions.assertNotNull(feature.getRT());
      Assertions.assertNotNull(feature.getSecondDimensionRT());
      Assertions.assertTrue(feature.getHeight() >= 100f);
      Assertions.assertTrue(feature.getFeatureData().getNumberOfValues() >= 2);
    }
    // GC-EI gap filling takes the m/z tolerance and minimum modulations from the finder
    final var settings = GcEiFeatureFindingSettings.fromAppliedMethods(
        features.getAppliedMethods());
    Assertions.assertEquals(MZ_TOLERANCE, settings.mzTolerance());
    Assertions.assertEquals(2, settings.minConsecutiveScans());
  }

  @Test
  @Order(6)
  void templateDeconvolutionCreatesCompounds() throws InterruptedException {
    final ParameterSet params = new GcxGc2DFeatureFinderParameters().cloneParameterSet();
    params.setParameter(GcxGc2DFeatureFinderParameters.RAW_DATA_FILES,
        new RawDataFilesSelection(new RawDataFile[]{file}));
    params.setParameter(GcxGc2DFeatureFinderParameters.RT_RANGE, false);
    params.setParameter(GcxGc2DFeatureFinderParameters.MZ_TOLERANCE, MZ_TOLERANCE);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_HEIGHT, 100d);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_MODULATIONS, 2);
    params.setParameter(GcxGc2DFeatureFinderParameters.MIN_2D_DATA_POINTS, 3);
    params.setParameter(GcxGc2DFeatureFinderParameters.SUFFIX, "2D features");
    params.setParameter(GcxGc2DFeatureFinderParameters.TEMPLATE_DECONVOLUTION, true);
    final ParameterSet templates = params.getParameter(
        GcxGc2DFeatureFinderParameters.TEMPLATE_DECONVOLUTION).getEmbeddedParameters();
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.MIN_SIGNALS, 1);
    templates.setParameter(GcxGcTemplateDeconvolutionParameters.SUFFIX, "decon");
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, GcxGc2DFeatureFinderModule.class, params));

    // the deconvoluted list with compounds instead of the 2D feature list
    final ModularFeatureList decon = lastList();
    // the earlier test created a list of the same name
    Assertions.assertTrue(decon.getName().endsWith("decon"), decon.getName());
    Assertions.assertNotNull(decon.getCompoundList());
    Assertions.assertTrue(decon.getCompoundList().size() > 0);
    Assertions.assertTrue(decon.getFeatures(file).stream().allMatch(GcxGcUtils::isGcxGcFeature));
  }

  /**
   * @return the 2D RT difference of two scans in minutes
   */
  private double scanStep() {
    return file.getFrame(0).getMobilityScan(1).getMobility() - file.getFrame(0).getMobilityScan(0)
        .getMobility();
  }

  private static int mostIntenseIndex(@NotNull final List<SecondDimensionSpectrum> spectra) {
    int index = 0;
    for (int i = 1; i < spectra.size(); i++) {
      if (spectra.get(i).getTIC() > spectra.get(index).getTIC()) {
        index = i;
      }
    }
    return index;
  }

  /**
   * Local minimum resolver in the given dimension, 2D RT in minutes
   */
  private static @NotNull ParameterSet resolverParameters(@NotNull final ModularFeatureList flist,
      @NotNull final ResolvingDimension dimension) {
    return MinimumSearchFeatureResolverParameters.create(new FeatureListsSelection(flist), "r2d",
        OriginalFeatureListOption.KEEP, false, new GroupMS2SubParameters(), dimension, 0.5, 0.1 / 60, 0d, 50, 1.4,
        Range.closed(0d, 10d / 60), 3);
  }

  private static void assertNoIonMobilityTypes(@NotNull final FeatureList flist) {
    final List<Class<?>> imsTypes = List.of(MobilityType.class, MobilityRangeType.class,
        MobilityUnitType.class, CCSType.class, FeatureShapeMobilogramType.class,
        FeatureShapeIonMobilityRetentionTimeHeatMapType.class);
    final List<DataType> types = Stream.concat(flist.getRowTypes().stream(),
        flist.getFeatureTypes().stream()).toList();
    for (final DataType type : types) {
      Assertions.assertFalse(imsTypes.contains(type.getClass()), type.getHeaderString());
    }
  }

  private static @NotNull ModularFeatureList lastList() {
    return (ModularFeatureList) ProjectService.getProject().getCurrentFeatureLists().getLast();
  }
}
