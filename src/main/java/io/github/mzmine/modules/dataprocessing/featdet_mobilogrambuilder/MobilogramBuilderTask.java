package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.StorableFrame;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;

/**
 * Worker task of the mobilogram builder
 */
public class MobilogramBuilderTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(MobilogramBuilderTask.class.getName());

  private final Set<Frame> frames;
  private final MZTolerance mzTolerance;
  private final String massList;
  private final int totalFrames;
  private final int minPeaks;
  private final boolean addDpFromRaw;
  private final ScanSelection scanSelection;
  private int processedFrames;

  public MobilogramBuilderTask(Set<Frame> frames, ParameterSet parameters) {
    this.mzTolerance = parameters.getParameter(MobilogramBuilderParameters.mzTolerance).getValue();
    this.massList = parameters.getParameter(MobilogramBuilderParameters.massList).getValue();
    this.minPeaks = parameters.getParameter(MobilogramBuilderParameters.minPeaks).getValue();
    this.addDpFromRaw = parameters.getParameter(MobilogramBuilderParameters.addRawDp).getValue();
    this.scanSelection =
        parameters.getParameter(MobilogramBuilderParameters.scanSelection).getValue();
    // this.frames = frames;
    this.frames = (Set<Frame>) scanSelection.getMachtingScans((frames));

    totalFrames = (this.frames.size() != 0) ? this.frames.size() : 1;
    setStatus(TaskStatus.WAITING);
  }

  @Override
  public String getTaskDescription() {
    return "Detecting mobilograms for frames. " + processedFrames + "/" + totalFrames;
  }

  @Override
  public double getFinishedPercentage() {
    return (double) processedFrames / totalFrames;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    for (Frame frame : frames) {

      if (isCanceled()) {
        return;
      }

      if (!(frame instanceof StorableFrame) || !scanSelection.matches(frame)) {
        continue;
      }

      Set<Scan> eligibleScans = frame.getMobilityScans().stream().filter(scanSelection::matches)
          .collect(Collectors.toSet());
      Set<IMobilogram> mobilograms = calculateMobilogramsForScans(eligibleScans);

      if (addDpFromRaw) {
        addDataPointsFromRaw(mobilograms, frame.getMobilityScans());
      }
      printDuplicateStatistics(mobilograms);
      // mobilograms.forEach(mob -> ((SimpleMobilogram) mob).fillMissingScanNumsWithZero());
      mobilograms.forEach(mob -> ((SimpleMobilogram) mob).fillEdgesWithZeros(3));

      processedFrames++;
      frame.getMobilograms().clear(); // remove previous mobilograms
      frame.getMobilograms().addAll(mobilograms);
    }

    setStatus(TaskStatus.FINISHED);
  }

  protected Set<IMobilogram> calculateMobilogramsForScans(Set<Scan> scans) {
    MobilityType mobilityType = null;
    if (scans.isEmpty()) {
      return Collections.emptySet();
    } else {
      for (Scan scan : scans) {
        if (scan.getMassList(massList) == null) {
          return Collections.emptySet();
        } else {
          mobilityType = scan.getMobilityType();
          break;
        }
      }
    }

    // int numDp = 0;
    //
    // for (Scan scan : scans) {
    // numDp += scan.getMassList(massList).getDataPoints().length;
    // }
    final SortedSet<MobilityDataPoint> allDps =
        new TreeSet<>(Comparator.comparing(MobilityDataPoint::getIntensity));

    for (Scan scan : scans) {
      Arrays.stream(scan.getMassList(massList).getDataPoints())
          .forEach(dp -> allDps.add(new MobilityDataPoint(dp.getMZ(), dp.getIntensity(),
              scan.getMobility(), scan.getScanNumber())));
    }

    // sort by highest dp, we assume that that measurement was the most accurate
    // allDps.sort(Comparator.comparingDouble(MobilityDataPoint::getIntensity));

    Set<IMobilogram> mobilograms = new HashSet<>();

    for (MobilityDataPoint allDp : allDps) {
      final MobilityDataPoint baseDp = allDp;
      final double baseMz = baseDp.getMZ();
      // allDps.remove(allDp);

      final SimpleMobilogram mobilogram = new SimpleMobilogram(mobilityType);
      mobilogram.addDataPoint(baseDp);

      // go through all dps and add mzs within tolerance
      for (MobilityDataPoint dp : allDps) {
        if (mzTolerance.checkWithinTolerance(baseMz, dp.getMZ())
            && !mobilogram.containsDpForScan(dp.getScanNum())) {
          mobilogram.addDataPoint(dp);
          // itemsToRemove.add(dp);
        }
      }

      // allDps.removeAll(itemsToRemove);
      // itemsToRemove.clear();

      if (mobilogram.getDataPoints().size() > minPeaks) {
        mobilogram.calc();
        mobilograms.add(mobilogram);
      }
    }

    SortedSet<IMobilogram> sortedMobilograms =
        new TreeSet<>(Comparator.comparingDouble(IMobilogram::getMZ));
    sortedMobilograms.addAll(mobilograms);
    return sortedMobilograms;
  }

  private void addDataPointsFromRaw(Set<IMobilogram> mobilograms, Set<Scan> rawScans) {
    // rawScans are actually StorableScans so data points are stored on the hard disc. We preload
    // everything here at once
    int numDp = 0;
    for (Scan scan : rawScans) {
      numDp += scan.getDataPoints().length;
    }
    final List<MobilityDataPoint> allDps = new ArrayList<>(numDp);
    for (Scan scan : rawScans) {
      Arrays.stream(scan.getDataPoints()).forEach(dp -> allDps.add(new MobilityDataPoint(dp.getMZ(),
          dp.getIntensity(), scan.getMobility(), scan.getScanNumber())));
    }
    // if we sort here, we can use break conditions later
    allDps.sort(Comparator.comparingDouble(MobilityDataPoint::getMZ));

    for (IMobilogram mobilogram : mobilograms) {
      Date start = new Date();
      // todo maybe mobilogram.getMZRange()?
      double lowerMzLimit = mzTolerance.getToleranceRange(mobilogram.getMZ()).lowerEndpoint();
      double upperMzLimit = mzTolerance.getToleranceRange(mobilogram.getMZ()).upperEndpoint();

      int lowerStartIndex = -1;
      int upperStopIndex = allDps.size() - 1;
      for (int i = 0; i < allDps.size(); i++) {
        if (allDps.get(i).getMZ() >= lowerMzLimit) {
          lowerStartIndex = i;
          break;
        }
      }
      if (lowerStartIndex == -1) {
        continue;
      }
      for (int i = lowerStartIndex; i < allDps.size(); i++) {
        if (allDps.get(i).getMZ() >= upperMzLimit) {
          upperStopIndex = i;
          break;
        }
      }

      // all dps within mztolerance and not already in the mobilogram
      Date preSearch = new Date();
      List<MobilityDataPoint> eligibleDps = allDps.subList(lowerStartIndex, upperStopIndex + 1)
          .stream().filter(dp -> !mobilogram.getScanNumbers().contains(dp.getScanNum()))
          .collect(Collectors.toList());
      Date done = new Date();

      final long full = done.getTime() - start.getTime();
      final long search = done.getTime() - preSearch.getTime();

      logger
          .info(() -> "adding " + eligibleDps.size() + " dp to " + mobilogram.representativeString()
              + " - full: " + full + " ms - search " + search + " ms");

      for (MobilityDataPoint dp : eligibleDps) {
        ((SimpleMobilogram) mobilogram).addDataPoint(dp);
      }
      ((SimpleMobilogram) mobilogram).calc();
    }
    // todo compare if other entries from that scan would have been better
  }

  private void printDuplicateStatistics(Set<IMobilogram> mobilograms) {

    List<IMobilogram> copyMobilograms = new ArrayList<>(mobilograms);

    for (IMobilogram baseMob : mobilograms) {
      copyMobilograms.remove(baseMob);
      for (IMobilogram copyMob : copyMobilograms) {
        if (baseMob.getMZRange().isConnected(copyMob.getMobilityRange())) {
          int overlapCounter = 0;
          for (MobilityDataPoint dp : baseMob.getDataPoints()) {
            if (copyMob.getDataPoints().contains(dp)) {
              overlapCounter++;
            }
          }
          logger.info(baseMob.representativeString() + " and " + copyMob.representativeString()
              + " share " + overlapCounter + " data points");
        }
      }
    }
  }
}
