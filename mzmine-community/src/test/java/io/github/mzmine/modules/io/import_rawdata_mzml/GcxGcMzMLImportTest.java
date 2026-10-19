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

package io.github.mzmine.modules.io.import_rawdata_mzml;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.Modulation;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.visualization.intensitymap.IntensityMapDataSource;
import io.github.mzmine.modules.visualization.intensitymap.IntensityMapDimensions;
import io.github.mzmine.modules.visualization.intensitymap.IntensityMapParameters;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapGrid;
import io.github.mzmine.modules.visualization.intensitymap.sampling.IntensityMapSampler;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.project.ProjectService;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;
import testutils.MZmineTestUtil;
import testutils.TaskResult;

/**
 * GCxGC mzML (ChromaTOF export) with first and second column elution times per scan. The test file
 * contains 3 modulations (3 s modulation period, 200 Hz) cut down to the first 200 scans each.
 */
@TestInstance(Lifecycle.PER_CLASS)
class GcxGcMzMLImportTest {

  private static final String TEST_FILE = "rawdatafiles/additional/gcxgc_pollen_3modulations.mzML";

  private IMSRawDataFile file;

  @BeforeAll
  void initialize() throws InterruptedException {
    MZmineTestUtil.startMzmineCore();
    MZmineTestUtil.cleanProject();
    final TaskResult result = MZmineTestUtil.importFiles(List.of(TEST_FILE), 120);
    Assertions.assertInstanceOf(TaskResult.FINISHED.class, result);
    file = (IMSRawDataFile) ProjectService.getProject().getCurrentRawDataFiles().stream()
        .filter(IMSRawDataFile.class::isInstance).findFirst()
        .orElseThrow(() -> new IllegalStateException("GCxGC file was not imported as 2D data"));
  }

  @AfterAll
  void tearDown() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  void modulationsAreFrames() {
    Assertions.assertEquals(MobilityType.GCXGC, file.getMobilityType());
    Assertions.assertInstanceOf(GCxGCRawDataFile.class, file);
    Assertions.assertTrue(GcxGcUtils.isGcxGc(file));
    Assertions.assertTrue(file.getFrames().stream().allMatch(Modulation.class::isInstance));
    Assertions.assertEquals(3, file.getNumberOfFrames());
    final List<? extends Frame> frames = file.getFrames();
    // first column elution time of modulation 400 is 300 s + 400 * 3 s
    Assertions.assertEquals(1500f / 60f, frames.get(0).getRetentionTime(), 1E-4);
    Assertions.assertEquals(1503f / 60f, frames.get(1).getRetentionTime(), 1E-4);
    Assertions.assertEquals(1506f / 60f, frames.get(2).getRetentionTime(), 1E-4);
  }

  @Test
  void secondColumnTimeIsSecondDimension() {
    for (final Frame frame : file.getFrames()) {
      Assertions.assertEquals(MobilityType.GCXGC, frame.getMobilityType());
      Assertions.assertEquals(200, frame.getNumberOfMobilityScans());
      // second column elution time in seconds, ascending with the scan number
      // 2D RT in minutes
      Assertions.assertEquals(0d, frame.getMobilityScan(0).getMobility(), 1E-8);
      Assertions.assertEquals(0.005d / 60, frame.getMobilityScan(1).getMobility(), 1E-8);
      Assertions.assertEquals(0.995d / 60, frame.getMobilityScan(199).getMobility(), 1E-8);
      Assertions.assertTrue(frame.getMobilityScan(10).getNumberOfDataPoints() > 0);
    }
  }

  @Test
  void opensAsFirstVsSecondDimensionRetentionTime() {
    Assertions.assertEquals(IntensityMapDimensions.RT_MOBILITY,
        IntensityMapSampler.resolveMode(file, IntensityMapDimensions.AUTOMATIC));

    final ParameterSet parameters = new IntensityMapParameters().cloneParameterSet();
    parameters.getParameter(IntensityMapParameters.dataFile)
        .setValue(RawDataFilesSelectionType.SPECIFIC_FILES, new RawDataFile[]{file});
    parameters.setParameter(IntensityMapParameters.mode, IntensityMapDimensions.AUTOMATIC);
    parameters.setParameter(IntensityMapParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(IntensityMapParameters.dataSource, IntensityMapDataSource.RAW);
    // frames have no merged spectra yet, the m/z range comes from the single scans
    final Range<Double> mzRange = IntensityMapSampler.dataMzRange(file);
    Assertions.assertTrue(mzRange.upperEndpoint() - mzRange.lowerEndpoint() > 100);
    parameters.setParameter(IntensityMapParameters.mzRange, mzRange);

    final IntensityMapGrid grid = IntensityMapSampler.sample(file, parameters,
        new IntensityMapSampler.Progress() {
          @Override
          public boolean canceled() {
            return false;
          }

          @Override
          public void advance(final int completed, final int total) {
          }
        });
    Assertions.assertTrue(grid.xLabel().startsWith("1D retention time"), grid.xLabel());
    Assertions.assertTrue(grid.yLabel().startsWith("2D RT"), grid.yLabel());
    double total = 0;
    for (int x = 0; x < grid.width(); x++) {
      for (int y = 0; y < grid.height(); y++) {
        total += grid.intensity(x, y);
      }
    }
    Assertions.assertTrue(total > 0);
  }
}
