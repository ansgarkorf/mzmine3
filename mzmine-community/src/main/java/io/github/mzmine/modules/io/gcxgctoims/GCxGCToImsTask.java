/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.io.gcxgctoims;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.impl.BuildingMobilityScan;
import io.github.mzmine.datamodel.impl.SimpleFrame;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GCxGCToImsTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(GCxGCToImsTask.class.getName());

  private final ParameterSet parameters;
  private final RawDataFile file;
  private IMSRawDataFileImpl imsFile;
  private int processedScans = 0;
  private final int totalScans;
  private final MZmineProject project;

  public GCxGCToImsTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      @NotNull ParameterSet parameters, RawDataFile file, MZmineProject project) {
    super(storage, moduleCallDate);
    this.parameters = parameters;

    this.file = file;

    totalScans = file.getNumOfScans();
    this.project = project;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    final Map<Float, List<Scan>> groupedScans = partitionScans(file.getScans());

    if (groupedScans.isEmpty()) {
      setErrorMessage("Raw file does not contain information on two-dimensional separation.");
      setStatus(TaskStatus.ERROR);
      return;
    }

    imsFile = new IMSRawDataFileImpl(file.getName() + " ims", file.getAbsolutePath(),
        file.getMemoryMapStorage(), file.getColor());

    final List<Entry<Float, List<Scan>>> scanSegments = groupedScans.entrySet().stream()
        .sorted(Comparator.comparingDouble(Entry::getKey)).toList();

    for (int i = 0; i < scanSegments.size(); i++) {
      if (isCanceled()) {
        return;
      }
      List<Scan> gcScans = scanSegments.get(i).getValue();
      final Scan firstScan = gcScans.getFirst();
      float rt = firstScan.getRetentionTime();

      final double[] mobilities = createMobilityValues(scanSegments.get(i).getValue());

      final SimpleFrame frame = new SimpleFrame(imsFile, i + 1, 1, rt, null, null,
          firstScan.getSpectrumType(), firstScan.getPolarity(), firstScan.getScanDefinition(),
          firstScan.getScanningMZRange(), MobilityType.GCxGC, null, null);

      List<BuildingMobilityScan> mobScans = new ArrayList<>();
      for (int mobScanCounter = 0; mobScanCounter < gcScans.size(); mobScanCounter++) {
        final Scan gcScan = gcScans.get(mobScanCounter);

        final double[] mzs = new double[gcScan.getNumberOfDataPoints()];
        final double[] intensities = new double[gcScan.getNumberOfDataPoints()];
        gcScan.getMzValues(mzs);
        gcScan.getIntensityValues(intensities);

        BuildingMobilityScan mobScan = new BuildingMobilityScan(mobScanCounter, mzs, intensities);
        mobScans.add(mobScan);

        processedScans++;
      }
      frame.setMobilities(mobilities);
      frame.setMobilityScans(mobScans, false);

      try {
        imsFile.addScan(frame);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    imsFile.getAppliedMethods().addAll(file.getAppliedMethods());
    imsFile.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(GCxGCToImsModule.class, parameters,
            getModuleCallDate()));
    project.removeFile(file);
    project.addFile(imsFile);
    setStatus(TaskStatus.FINISHED);
  }

  private double @NotNull [] createMobilityValues(List<Scan> scans) {
    final double[] mobilities = new double[scans.size()];
    for (int i = 0; i < mobilities.length; i++) {
      mobilities[i] = scans.get(i).getTwoDRt().dimensionTwo();
    }
    return mobilities;
  }

  @Override
  public String getTaskDescription() {
    return "Creating pseudo-IMS file for GCxGC for %s".formatted(file.getName());
  }

  @Override
  public double getFinishedPercentage() {
    return (double) processedScans / totalScans;
  }

  private Map<Float, List<Scan>> partitionScans(List<Scan> scans) {
    return scans.stream().filter(scan -> scan.getTwoDRt() != null)
        .collect(Collectors.groupingBy(scan -> scan.getTwoDRt().dimensionOne()));
  }

}
