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

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.dataprocessing.featdet_imsexpander.ImsExpanderModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.dataprocessing.featdet_mobilityscanmerger.MobilityScanMergerModule;
import io.github.mzmine.modules.tools.batchwizard.subparameters.MassDetectorWizardOptions;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.util.scans.ScanUtils;
import java.util.List;
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
 * GCxGC data in mzmine community on a small GCxGC file (3 modulations of 200 scans): mass
 * detection on the single scans and the scan labels. The GCxGC 2D feature finder is part of mzmine
 * (closed source) and tested there. GCxGC reuses the ion mobility processing internally, but users
 * must not see ion mobility modules or data types.
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class GcxGcModulesTest {

  private static final String TEST_FILE = "rawdatafiles/additional/gcxgc_pollen_3modulations.mzML";
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
  void massDetectionOnTheSingleScans() throws InterruptedException {
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

    for (final Frame modulation : file.getFrames()) {
      Assertions.assertNotNull(modulation.getMobilityScan(0).getMassList());
    }
    Assertions.assertTrue(file.getAppliedMethods().stream()
        .noneMatch(m -> IMS_MODULES.contains(m.getModule().getClass())));
  }

  @Test
  @Order(2)
  void scanLabelsUseModulations() {
    final Frame modulation = file.getFrame(0);
    final String modulationLabel = ScanUtils.scanToString(modulation);
    final String scanLabel = ScanUtils.scanToString(modulation.getMobilityScan(10));
    Assertions.assertTrue(modulationLabel.startsWith("Modulation #"), modulationLabel);
    Assertions.assertTrue(scanLabel.startsWith("Scan #"), scanLabel);
    for (final String label : List.of(modulationLabel, scanLabel,
        modulation.getMobilityScan(10).getScanDefinition())) {
      Assertions.assertFalse(label.toLowerCase().matches(".*(frame|mobility).*"), label);
    }
  }
}
