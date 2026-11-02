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

package io.github.mzmine.modules.visualization.pseudospectrumvisualizer;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.ProjectService;
import java.util.Comparator;
import java.util.List;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import testutils.GcxGcTestFeatures;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * The 2D RT traces of the pseudo spectrum visualizer on a small GCxGC file
 */
@TestInstance(Lifecycle.PER_CLASS)
class SecondDimensionDatasetsBuilderTest {

  private static final String TEST_FILE = "rawdatafiles/additional/gcxgc_pollen_3modulations.mzML";
  private static final MZTolerance MZ_TOLERANCE = new MZTolerance(0.005, 15);

  private List<ModularFeature> features;

  @BeforeAll
  void initialize() throws InterruptedException {
    MZmineTestUtil.startMzmineCore();
    MZmineTestUtil.cleanProject();
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.importFiles(List.of(TEST_FILE), 120));
    final IMSRawDataFile file = (IMSRawDataFile) ProjectService.getProject()
        .getCurrentRawDataFiles().getFirst();
    final RawDataFilesSelection files = new RawDataFilesSelection(new RawDataFile[]{file});

    final ParameterSet detection = new MassDetectionParameters().cloneParameterSet();
    detection.setParameter(MassDetectionParameters.dataFiles, files);
    final var detector = MassDetectorWizardOptions.ABSOLUTE_NOISE_LEVEL.createMassDetectorParameters(
        10);
    detection.getParameter(MassDetectionParameters.massDetector)
        .setValue(detector.value(), detector.parameters());
    detection.setParameter(MassDetectionParameters.scanSelection, new ScanSelection());
    detection.setParameter(MassDetectionParameters.scanTypes, SelectedScanTypes.SCANS);
    detection.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    run(MassDetectionModule.class, detection);

    GcxGcTestFeatures.create(file, 10, MZ_TOLERANCE);
    features = lastList().getFeatures(file).stream()
        .sorted(Comparator.comparingDouble(ModularFeature::getHeight).reversed()).toList();
    Assertions.assertTrue(features.size() > 2);
  }

  @AfterAll
  void tearDown() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  void featureTracesAreSecondDimensionRT() {
    final ModularFeature main = features.getFirst();
    final List<DatasetAndRenderer> datasets = SecondDimensionDatasetsBuilder.fromFeatures(main,
        features.subList(1, 3), Color.BLUE);
    Assertions.assertEquals(3, datasets.size());
    assertWithin(datasets.getFirst(), main.getSecondDimensionRTRange());
  }

  @Test
  void extractsAllSignalsInSecondDimension() {
    final ModularFeature main = features.getFirst();
    final double[] mzs = features.stream().limit(5).mapToDouble(ModularFeature::getMZ).toArray();
    final GcxGcPseudoSpectrumDatasets extracted = SecondDimensionDatasetsBuilder.extract(main, mzs,
        MZ_TOLERANCE, Color.BLUE, () -> false);
    final List<DatasetAndRenderer> datasets = extracted.secondDimensionDatasets();
    // the main feature and at least its own m/z
    Assertions.assertTrue(datasets.size() >= 2, "datasets: " + datasets.size());
    // the same signals along the retention time, the modulations have no spectra of their own
    Assertions.assertEquals(datasets.size(), extracted.rtDatasets().size());
    for (final DatasetAndRenderer dataset : extracted.rtDatasets()) {
      assertWithin(dataset, main.getRawDataPointsRTRange());
    }
    final Range<Float> range = main.getSecondDimensionRTRange();
    final float width = range.upperEndpoint() - range.lowerEndpoint();
    // summed traces have a zero data point at each edge, one scan (5 ms) outside the window
    final float scanStep = 0.006f / 60;
    for (final DatasetAndRenderer dataset : datasets) {
      assertWithin(dataset, Range.closed(range.lowerEndpoint() - width - scanStep,
          range.upperEndpoint() + width + scanStep));
    }
  }

  @Test
  void noTracesWithoutSecondDimension() {
    // a feature without 2D RT trace
    final ModularFeature feature = Mockito.mock(ModularFeature.class);
    Assertions.assertTrue(
        SecondDimensionDatasetsBuilder.fromFeatures(feature, List.of(), Color.BLUE).isEmpty());
  }

  /**
   * 2D RT values in minutes within the range
   */
  private static void assertWithin(final DatasetAndRenderer dataset, final Range<Float> range) {
    final var data = dataset.dataset();
    Assertions.assertTrue(data.getItemCount(0) > 0);
    for (int i = 0; i < data.getItemCount(0); i++) {
      final double x = data.getXValue(0, i);
      Assertions.assertTrue(range.contains((float) x), x + " not in " + range);
    }
  }

  private static ModularFeatureList lastList() {
    return (ModularFeatureList) ProjectService.getProject().getCurrentFeatureLists().getLast();
  }

  private static void run(final Class<? extends io.github.mzmine.modules.MZmineRunnableModule> module,
      final ParameterSet parameters) throws InterruptedException {
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, module, parameters));
  }
}
