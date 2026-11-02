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

package io.github.mzmine.modules.visualization.intensitymap;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapLabel;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapProjection;
import io.github.mzmine.modules.visualization.intensitymap.sampling.IntensityMapLayer;
import io.github.mzmine.modules.visualization.intensitymap.sampling.IntensityMapSampler;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.ProjectService;
import java.util.List;
import java.util.Set;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import testutils.GcxGcTestFeatures;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * The window of the 2D / 3D raw data plot of the compound dashboard around GCxGC features
 */
@TestInstance(Lifecycle.PER_CLASS)
class EmbeddedIntensityMapTest {

  private static final String TEST_FILE = "rawdatafiles/additional/gcxgc_pollen_3modulations.mzML";

  private IMSRawDataFile file;
  private List<ModularFeature> features;

  @BeforeAll
  void initialize() throws InterruptedException {
    MZmineTestUtil.startMzmineCore();
    MZmineTestUtil.cleanProject();
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.importFiles(List.of(TEST_FILE), 120));
    file = (IMSRawDataFile) ProjectService.getProject().getCurrentRawDataFiles().getFirst();
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
    Assertions.assertInstanceOf(TaskResult.FINISHED.class,
        MZmineTestUtil.callModuleWithTimeout(60, MassDetectionModule.class, detection));

    final ModularFeatureList flist = GcxGcTestFeatures.create(file, 10,
        new MZTolerance(0.005, 15));
    features = flist.getFeatures(file);
    Assertions.assertFalse(features.isEmpty());
  }

  @AfterAll
  void tearDown() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  void gcxgcFeaturesShowRetentionTimeAgainstSecondDimension() {
    final ParameterSet parameters = EmbeddedIntensityMap.parameters(file, features,
        IntensityMapProjection.TOP_VIEW, null);
    Assertions.assertNotNull(parameters);
    Assertions.assertEquals(IntensityMapDimensions.RT_MOBILITY,
        parameters.getValue(IntensityMapParameters.mode));
    final ScanSelection selection = parameters.getValue(IntensityMapParameters.scanSelection);
    final Range<Double> rt = selection.getScanRTRange();
    final Range<Double> secondDimension = selection.getScanMobilityRange();
    Assertions.assertNotNull(rt);
    Assertions.assertNotNull(secondDimension);
    // the window holds every feature, limited to the data of the file
    for (final ModularFeature feature : features) {
      final Range<Float> featureRt = feature.getRawDataPointsRTRange();
      Assertions.assertTrue(rt.contains((double) featureRt.lowerEndpoint())
          && rt.contains((double) featureRt.upperEndpoint()), rt + " " + featureRt);
      final Range<Float> feature2D = feature.getSecondDimensionRTRange();
      Assertions.assertTrue(secondDimension.contains((double) feature2D.lowerEndpoint())
          && secondDimension.contains((double) feature2D.upperEndpoint()),
          secondDimension + " " + feature2D);
    }
    Assertions.assertTrue(file.getDataMobilityRange().encloses(secondDimension));
    // all m/z like a TIC
    Assertions.assertEquals(IntensityMapSampler.dataMzRange(file),
        parameters.getValue(IntensityMapParameters.mzRange));
    // the stored defaults of the module are not changed
    Assertions.assertNotSame(parameters,
        ConfigService.getConfiguration().getModuleParameters(IntensityMap2DModule.class));
  }

  @Test
  void secondDimensionShowsTheSelectedIon() {
    final ModularFeature ion = features.getFirst();
    final Range<Double> ionRange = EmbeddedIntensityMap.ionMzRange(ion);
    // the data points of the ion with a small margin
    Assertions.assertTrue(ionRange.encloses(ion.getRawDataPointsMZRange()));
    Assertions.assertTrue(ionRange.upperEndpoint() - ionRange.lowerEndpoint()
        < ion.getRawDataPointsMZRange().upperEndpoint() - ion.getRawDataPointsMZRange()
        .lowerEndpoint() + 0.01);
    final ParameterSet parameters = EmbeddedIntensityMap.parameters(file, features,
        IntensityMapProjection.TOP_VIEW, ionRange);
    Assertions.assertNotNull(parameters);
    Assertions.assertEquals(ionRange, parameters.getValue(IntensityMapParameters.mzRange));
  }

  @Test
  void onlySelectedRowsGetLabels() {
    final ModularFeatureList flist = (ModularFeatureList) features.getFirst().getFeatureList();
    final List<IntensityMapLayer> layers = List.of(
        new IntensityMapLayer("layer-0", file, IntensityMapSampler.dataMzRange(file), Color.BLUE,
            true));
    final int all = IntensityMapLabels.of(flist, layers, IntensityMapDimensions.RT_MOBILITY, null,
        ConfigService.getGuiFormats()).size();
    Assertions.assertTrue(all > 1, "labels " + all);
    final FeatureListRow selected = flist.getRow(0);
    final List<IntensityMapLabel> labels = IntensityMapLabels.of(flist, layers,
        IntensityMapDimensions.RT_MOBILITY, null, ConfigService.getGuiFormats(),
        Set.of(selected));
    Assertions.assertEquals(1, labels.size());
    // no selection, no labels
    Assertions.assertTrue(IntensityMapLabels.of(flist, layers, IntensityMapDimensions.RT_MOBILITY,
        null, ConfigService.getGuiFormats(), Set.of()).isEmpty());
  }

  @Test
  void noFeaturesNoWindow() {
    Assertions.assertNull(
        EmbeddedIntensityMap.parameters(file, List.of(), IntensityMapProjection.PERSPECTIVE, null));
  }
}
