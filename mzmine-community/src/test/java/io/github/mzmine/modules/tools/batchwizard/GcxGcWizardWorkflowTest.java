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

package io.github.mzmine.modules.tools.batchwizard;

import io.github.mzmine.datamodel.features.types.FeatureShapeSecondDimensionType;
import io.github.mzmine.datamodel.features.types.FeatureShapeIonMobilityRetentionTimeHeatMapType;
import io.github.mzmine.datamodel.features.types.FeatureShapeMobilogramType;
import io.github.mzmine.datamodel.features.types.MobilityUnitType;
import io.github.mzmine.datamodel.features.types.numbers.CCSType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityRangeType;
import io.github.mzmine.datamodel.features.types.numbers.SecondDimensionRTType;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGcSecondDimensionSpectraModule;
import io.github.mzmine.modules.dataprocessing.featdet_imsexpander.ImsExpanderModule;
import io.github.mzmine.modules.dataprocessing.featdet_mobilityscanmerger.MobilityScanMergerModule;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;
import io.github.mzmine.modules.dataprocessing.align_gc.GCConsensusAlignerPostProcessor;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.modules.dataprocessing.gapfill_gc_ei.GcEiGapFillingModule;
import io.github.mzmine.modules.dataprocessing.gapfill_gc_ei.GcEiGapFillingParameters;
import io.github.mzmine.modules.dataprocessing.gapfill_gc_ei.GcEiGapFillingTask;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.PseudoSpectrum;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.compoundlist.CompoundList;
import io.github.mzmine.modules.batchmode.BatchModeModule;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.batchmode.BatchTask;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionUtils;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.twodgroupingandshapecorrelation.TwoDimensionalGroupingAndShapeCorrelationAlgorithm;
import io.github.mzmine.modules.tools.batchwizard.subparameters.DataImportWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.FilterWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassSpectrometerWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WorkflowGcElectronImpactWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.custom_parameters.WizardMassDetectorNoiseLevels;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.AnnotationWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.CustomizationWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.DataImportWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.FilterWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonMobilityWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.MassSpectrometerWizardParameterFactory;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.GcxGc2DFeatureFinderModule;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.workflows.WorkflowDeconvolution;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.workflows.WorkflowGcxGc2DFeatureFinder;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.workflows.WorkflowGcxGc2DTemplateDeconvolution;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import testutils.MZmineTestUtil;

/**
 * Runs the GCxGC-EI wizard workflow on local GCxGC mzML files. Skipped unless the environment
 * variable {@code GCXGC_TEST_FILE} points to one or more files (separated by the path separator),
 * as the files are too large for the repository.
 */
class GcxGcWizardWorkflowTest {

  private static final Logger logger = Logger.getLogger(GcxGcWizardWorkflowTest.class.getName());

  @Test
  void bothGcxGcWorkflowsCreateTheirQueues() {
    MZmineTestUtil.startMzmineCore();
    final File[] files = {new File("gcxgc.mzML")};
    final List<String> projection = new WorkflowDeconvolution().getBatchBuilder(
            createSequence(files, new WorkflowDeconvolution())).createQueue().stream()
        .map(step -> step.getModule().getName()).toList();
    Assertions.assertTrue(projection.contains(GcxGcSecondDimensionSpectraModule.NAME));
    Assertions.assertFalse(projection.contains(GcxGc2DFeatureFinderModule.NAME));

    final WorkflowGcxGc2DFeatureFinder finderWorkflow = new WorkflowGcxGc2DFeatureFinder();
    final WizardSequence steps = createSequence(files, finderWorkflow);
    // the wizard keeps the workflow of the parameters
    Assertions.assertInstanceOf(WorkflowGcxGc2DFeatureFinder.class,
        steps.get(WizardPart.WORKFLOW).orElseThrow().getFactory());
    final List<String> finder = finderWorkflow.getBatchBuilder(steps).createQueue().stream()
        .map(step -> step.getModule().getName()).toList();
    Assertions.assertTrue(finder.contains(GcxGc2DFeatureFinderModule.NAME));
    Assertions.assertFalse(finder.contains(GcxGcSecondDimensionSpectraModule.NAME));
    Assertions.assertFalse(finder.contains(GcxGc2DFeatureBuilderModule.NAME));
    // the same steps after the feature detection
    Assertions.assertEquals(projection.subList(projection.indexOf("GC-EI spectral deconvolution"),
        projection.size()), finder.subList(finder.indexOf("GC-EI spectral deconvolution"),
        finder.size()));
  }

  @Test
  void templateWorkflowSkipsTheSpectralDeconvolution() {
    MZmineTestUtil.startMzmineCore();
    final WorkflowGcxGc2DTemplateDeconvolution workflow = new WorkflowGcxGc2DTemplateDeconvolution();
    final List<String> steps = workflow.getBatchBuilder(
            createSequence(new File[]{new File("gcxgc.mzML")}, workflow)).createQueue().stream()
        .map(step -> step.getModule().getName()).toList();
    Assertions.assertTrue(steps.contains(GcxGc2DFeatureFinderModule.NAME));
    Assertions.assertFalse(steps.contains("GC-EI spectral deconvolution"));
    Assertions.assertTrue(steps.contains("GC aligner"));
  }

  @Test
  void runGcxGcTemplateDeconvolutionWorkflow() {
    runWorkflow(new WorkflowGcxGc2DTemplateDeconvolution());
  }

  @Test
  void runGcxGcDeconvolutionWorkflow() {
    runWorkflow(new WorkflowDeconvolution());
  }

  @Test
  void runGcxGc2DFeatureFinderWorkflow() {
    runWorkflow(new WorkflowGcxGc2DFeatureFinder());
  }

  private void runWorkflow(final WorkflowWizardParameterFactory workflow) {
    final String paths = System.getenv("GCXGC_TEST_FILE");
    Assumptions.assumeTrue(paths != null, "Set GCXGC_TEST_FILE to GCxGC mzML files");
    final File[] files = Arrays.stream(paths.split(File.pathSeparator)).map(File::new)
        .toArray(File[]::new);
    Assumptions.assumeTrue(Arrays.stream(files).allMatch(File::exists),
        "Set GCXGC_TEST_FILE to existing GCxGC mzML files");
    MZmineTestUtil.startMzmineCore();

    final WizardSequence steps = createSequence(files, workflow);
    final BatchQueue queue = workflow.getBatchBuilder(steps).createQueue();
    logger.info(() -> "GCxGC batch steps: " + queue.stream()
        .map(step -> step.getModule().getName()).toList());

    final MZmineProject project = new MZmineProjectImpl();
    ProjectService.getProjectManager().setCurrentProject(project);
    final long start = System.currentTimeMillis();
    final BatchTask task = BatchModeModule.runBatchQueue(queue, project, null, null, null, null,
        Instant.now(), null, null);
    Assertions.assertNotNull(task);
    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus(), task.getErrorMessage());
    logger.info("GCxGC batch finished in %d s".formatted(
        (System.currentTimeMillis() - start) / 1000));

    for (final FeatureList flist : project.getCurrentFeatureLists()) {
      logger.info("Feature list %s: %d rows".formatted(flist.getName(), flist.getNumberOfRows()));
    }
    logDiagnostics(project);
    verifyNoIonMobilityTerms(project, files[0]);
    final ModularFeatureList last = (ModularFeatureList) project.getCurrentFeatureLists()
        .getLast();
    final CompoundList compounds = last.getCompoundList();
    Assertions.assertNotNull(compounds, "No compounds after the 2D deconvolution");
    Assertions.assertTrue(compounds.size() > 0);
    logger.info("Compounds: " + compounds.size());

    // the most intense compounds with first and second dimension retention time
    last.getRows().stream().filter(row -> getPseudoSpectrum(row) != null)
        .sorted(Comparator.comparingDouble(FeatureListRow::getMaxHeight).reversed()).limit(25)
        .forEach(row -> logger.info(
            "RT1 %.3f min, 2D RT %.5f min, m/z %.4f, height %.0f, signals %d".formatted(
                row.getAverageRT(), row.getAverageSecondDimensionRT(), row.getAverageMZ(),
                row.getMaxHeight(), getPseudoSpectrum(row).getNumberOfDataPoints())));
    Assertions.assertTrue(last.getRows().stream().allMatch(row -> row.getAverageSecondDimensionRT() != null),
        "All rows need a second dimension retention time");
    if (files.length > 1) {
      final long alignedInAll = compounds.getRows().stream()
          .filter(c -> c.getPreferredRow().getNumberOfFeatures() == files.length).count();
      logger.info("Compounds aligned in all %d samples: %d of %d".formatted(files.length,
          alignedInAll, compounds.size()));
      verifySecondDimensionReExtraction(last);
      verifySecondDimensionGapFilling(project, last);
    }
  }

  /**
   * GCxGC reuses the ion mobility processing internally, users must never see ion mobility
   * modules, columns or export headers
   */
  private static void verifyNoIonMobilityTerms(final MZmineProject project, final File file) {
    final List<Class<?>> imsModules = List.of(ImsExpanderModule.class,
        MobilityScanMergerModule.class);
    for (final RawDataFile raw : project.getCurrentRawDataFiles()) {
      Assertions.assertTrue(raw.getAppliedMethods().stream()
          .noneMatch(m -> imsModules.contains(m.getModule().getClass())), "IMS module on raw file");
    }
    final List<Class<?>> imsTypes = List.of(
        io.github.mzmine.datamodel.features.types.numbers.MobilityType.class,
        MobilityRangeType.class, MobilityUnitType.class, CCSType.class,
        FeatureShapeMobilogramType.class, FeatureShapeIonMobilityRetentionTimeHeatMapType.class);
    for (final FeatureList flist : project.getCurrentFeatureLists()) {
      Assertions.assertTrue(flist.getAppliedMethods().stream()
              .noneMatch(m -> imsModules.contains(m.getModule().getClass())),
          "IMS module in " + flist.getName());
      for (final var type : Stream.concat(flist.getRowTypes().stream(),
          flist.getFeatureTypes().stream()).toList()) {
        Assertions.assertFalse(imsTypes.contains(type.getClass()),
            "Ion mobility type %s in %s".formatted(type.getHeaderString(), flist.getName()));
      }
    }
    final FeatureList last = project.getCurrentFeatureLists().getLast();
    Assertions.assertTrue(last.hasRowType(SecondDimensionRTType.class));
    Assertions.assertTrue(last.hasRowType(FeatureShapeSecondDimensionType.class));

    // the compound table export
    final File csv = new File(new File(System.getProperty("java.io.tmpdir"), "gcxgc_test"),
        FileAndPathUtil.eraseFormat(file).getName() + "_compound_table.csv");
    Assertions.assertTrue(csv.exists(), "missing " + csv);
    try {
      final String header = Files.readAllLines(csv.toPath()).getFirst();
      Assertions.assertFalse(header.toLowerCase().matches(".*(mobility|ccs|mobilogram).*"),
          header);
      Assertions.assertTrue(header.contains("rt_2d"), header);
    } catch (IOException e) {
      Assertions.fail(e);
    }
  }

  /**
   * Feature shapes after each step and the compound sizes of the 2D grouping without minimum
   * signals
   */
  private static void logDiagnostics(final MZmineProject project) {
    for (final FeatureList flist : project.getCurrentFeatureLists()) {
      final List<ModularFeature> features = flist.getFeatures(flist.getRawDataFile(0));
      final IntSummaryStatistics frames = features.stream()
          .mapToInt(f -> f.getFeatureData().getNumberOfValues()).summaryStatistics();
      final DoubleSummaryStatistics heights = features.stream()
          .mapToDouble(ModularFeature::getHeight).summaryStatistics();
      final DoubleSummaryStatistics widths2D = features.stream()
          .filter(f -> f.getSecondDimensionRTRange() != null).mapToDouble(
              f -> f.getSecondDimensionRTRange().upperEndpoint() - f.getSecondDimensionRTRange().lowerEndpoint())
          .summaryStatistics();
      logger.info(
          "%s: %d features, modulations %s, height avg %.0f max %.0f, 2D width avg %.5f min".formatted(
              flist.getName(), features.size(), frames, heights.getAverage(), heights.getMax(),
              widths2D.getAverage()));
    }
    // the 2D features, the input of the deconvolution
    final FeatureList resolved2D = project.getCurrentFeatureLists().stream()
        .filter(f -> f.getName().endsWith("2D r") || f.getName().endsWith("2D features")
            || f.getName().endsWith("2D features decon"))
        .findFirst().orElse(null);
    if (resolved2D == null) {
      return;
    }
    final List<ModularFeature> features = new ArrayList<>(
        resolved2D.getFeatures(resolved2D.getRawDataFile(0)));
    final var groups = new TwoDimensionalGroupingAndShapeCorrelationAlgorithm(
        new RTTolerance(0.06f, Unit.MINUTES), new RTTolerance(0.05f, Unit.SECONDS), 1, 0.8).groupFeatures(
        features);
    logger.info("2D groups without min signals: %d, sizes %s".formatted(groups.size(),
        groups.stream().map(List::size).sorted(Comparator.reverseOrder()).limit(30).toList()));
    final var loose = new TwoDimensionalGroupingAndShapeCorrelationAlgorithm(
        new RTTolerance(0.06f, Unit.MINUTES), new RTTolerance(0.05f, Unit.SECONDS), 1, 0.0).groupFeatures(
        new ArrayList<>(features));
    // correlation of all features to their representative without a correlation limit
    final List<Double> correlations = loose.stream().filter(g -> g.size() > 1).flatMap(
        g -> g.stream().skip(1).map(
            f -> SpectralDeconvolutionUtils.calculateShapeCorrelation(g.getFirst(), f))).sorted()
        .toList();
    if (!correlations.isEmpty()) {
      logger.info("Correlations to representative (r>=0): n %d, quartiles %.2f %.2f %.2f".formatted(
          correlations.size(), correlations.get(correlations.size() / 4),
          correlations.get(correlations.size() / 2), correlations.get(correlations.size() * 3 / 4)));
    }
  }

  /**
   * The GC aligner and the duplicate filter re-extract features at another m/z. GCxGC features need
   * to keep their second dimension. Re-extraction at the same m/z reproduces the feature.
   */
  private static void verifySecondDimensionReExtraction(final ModularFeatureList flist) {
    final List<ModularFeature> features = flist.getRows().stream()
        .filter(row -> getPseudoSpectrum(row) != null)
        .map(row -> (ModularFeature) row.getBestFeature()).limit(10).toList();
    for (final ModularFeature feature : features) {
      final ModularFeature extracted = GCConsensusAlignerPostProcessor.extractNewFeature(flist,
          feature, new MZTolerance(0.005, 20).getToleranceRange(feature.getMZ()));
      Assertions.assertNotNull(extracted);
      Assertions.assertTrue(GcxGcUtils.isGcxGcFeature(extracted), "keeps the second dimension");
      Assertions.assertEquals(feature.getSecondDimensionRT(), extracted.getSecondDimensionRT(),
          0.02 / 60);
      // the extraction uses the closest signal to the m/z center per scan
      Assertions.assertEquals(feature.getHeight(), extracted.getHeight(),
          feature.getHeight() * 0.1);
    }
  }

  /**
   * Removes the features of the second sample from the most intense rows and fills these gaps with
   * the 2D GC-EI gap filling. The refilled features need to match the removed features in both
   * dimensions.
   */
  private static void verifySecondDimensionGapFilling(final MZmineProject project,
      final ModularFeatureList flist) {
    final RawDataFile file = flist.getRawDataFile(1);
    final List<FeatureListRow> rows = flist.getRows().stream()
        .filter(row -> row.getFeature(file) != null && getPseudoSpectrum(row) != null)
        .sorted(Comparator.comparingDouble(FeatureListRow::getMaxHeight).reversed()).limit(40)
        .toList();
    final Map<FeatureListRow, Feature> removed = new HashMap<>();
    for (final FeatureListRow row : rows) {
      removed.put(row, row.getFeature(file));
      row.removeFeature(file);
    }

    final ParameterSet params = GcEiGapFillingParameters.create(
        new FeatureListsSelection(flist), 3, new RTTolerance(0.1f, Unit.MINUTES),
        new RTTolerance(0.06f, Unit.MINUTES), new RTTolerance(0.1f, Unit.SECONDS),
        new RTTolerance(0.1f, Unit.SECONDS), "gaps", OriginalFeatureListOption.PROCESS_IN_PLACE);
    final GcEiGapFillingTask task = new GcEiGapFillingTask(project, flist, null, Instant.now(),
        params, GcEiGapFillingModule.class);
    task.run();
    Assertions.assertEquals(TaskStatus.FINISHED, task.getStatus(), task.getErrorMessage());

    int filled = 0;
    int matching = 0;
    final List<Double> heightErrors = new ArrayList<>();
    for (final FeatureListRow row : rows) {
      final Feature refilled = row.getFeature(file);
      if (refilled == null) {
        continue;
      }
      filled++;
      Assertions.assertNotNull(refilled.getSecondDimensionRT(), "gap-filled features keep the 2D RT");
      final Feature original = removed.get(row);
      // one modulation (3 s) in the first dimension
      final boolean sameApex = Math.abs(original.getRT() - refilled.getRT()) < 0.051
          && Math.abs(original.getSecondDimensionRT() - refilled.getSecondDimensionRT()) < 0.03 / 60;
      if (sameApex) {
        matching++;
        heightErrors.add(
            (double) Math.abs(refilled.getHeight() - original.getHeight()) / original.getHeight());
      } else {
        logger.info("Refilled apex differs: m/z %.4f RT %.3f -> %.3f, 2D %.5f -> %.5f".formatted(
            original.getMZ(), original.getRT(), refilled.getRT(), original.getSecondDimensionRT(),
            refilled.getSecondDimensionRT()));
      }
    }
    Collections.sort(heightErrors);
    final double medianHeightError = heightErrors.isEmpty() ? 1 : heightErrors.get(
        heightErrors.size() / 2);
    logger.info(
        "2D gap filling refilled %d of %d removed features, %d at the same apex, median height error %.3f".formatted(
            filled, rows.size(), matching, medianHeightError));
    Assertions.assertTrue(matching >= rows.size() * 0.8,
        "Refilled at the same apex: " + matching + " of " + rows.size());
    Assertions.assertTrue(medianHeightError < 0.2);
  }

  private static PseudoSpectrum getPseudoSpectrum(FeatureListRow row) {
    for (final Scan scan : row.getAllFragmentScans()) {
      if (scan instanceof PseudoSpectrum ps) {
        return ps;
      }
    }
    return null;
  }

  private static WizardSequence createSequence(final File[] files,
      final WorkflowWizardParameterFactory workflowFactory) {
    final WizardSequence steps = new WizardSequence();
    final WizardStepParameters dataImport = DataImportWizardParameterFactory.values()[0].create();
    dataImport.setParameter(DataImportWizardParameters.fileNames, files);
    steps.set(WizardPart.DATA_IMPORT, dataImport);
    steps.set(WizardPart.ION_INTERFACE, IonInterfaceWizardParameterFactory.GCXGC_EI.create());
    steps.set(WizardPart.IMS, IonMobilityWizardParameterFactory.NO_IMS.create());

    // Pegasus HRT: low intensities in the single scans at 200 Hz, mass detection uses 1/5 of MS1 noise
    final WizardStepParameters ms = MassSpectrometerWizardParameterFactory.QTOF.create();
    ms.setParameter(MassSpectrometerWizardParameters.massDetectorOption,
        new WizardMassDetectorNoiseLevels(MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL, 300,
            300));
    ms.setParameter(MassSpectrometerWizardParameters.minimumFeatureHeight, 5000d);
    steps.set(WizardPart.MS, ms);
    final WizardStepParameters filter = FilterWizardParameterFactory.values()[0].create();
    // keep the intermediate feature lists to report each step
    filter.setParameter(FilterWizardParameters.handleOriginalFeatureLists,
        OriginalFeatureListOption.KEEP);
    steps.set(WizardPart.FILTER, filter);
    steps.set(WizardPart.ANNOTATION, AnnotationWizardParameterFactory.values()[0].create());
    final WizardStepParameters workflow = workflowFactory.create();
    workflow.setParameter(WorkflowGcElectronImpactWizardParameters.applySpectralNetworking, false);
    workflow.setParameter(WorkflowGcElectronImpactWizardParameters.exportGnps, false);
    workflow.setParameter(WorkflowGcElectronImpactWizardParameters.exportAnnotationGraphics,
        false);
    // compound table, MSP and batch file for manual inspection
    final File exportDir = new File(System.getProperty("java.io.tmpdir"), "gcxgc_test");
    exportDir.mkdirs();
    workflow.setParameter(WorkflowGcElectronImpactWizardParameters.exportPath, true,
        new File(exportDir, files[0].getName()));
    steps.set(WizardPart.WORKFLOW, workflow);
    steps.set(WizardPart.CUSTOMIZATION, CustomizationWizardParameterFactory.values()[0].create());
    return steps;
  }
}
