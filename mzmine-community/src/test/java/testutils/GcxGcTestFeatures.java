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

package testutils;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.impl.IonMobilogramTimeSeriesFactory;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonMobilitySeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.util.DataTypeUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates GCxGC 2D features for tests of mzmine community. The GCxGC 2D feature finder is part of
 * mzmine (closed source), so these features are extracted around the most intense signals of the
 * single scans: all modulations, a window of scans around the apex scan and the closest signal
 * within the m/z tolerance per scan. Mass detection on the single scans needs to run before.
 */
public final class GcxGcTestFeatures {

  // the 2D RT window around the apex scan
  private static final int SCAN_WINDOW = 15;
  // apexes of different features need at least this m/z difference
  private static final double MIN_MZ_DISTANCE = 0.5;

  private GcxGcTestFeatures() {
  }

  /**
   * Creates a feature list with 2D features of the most intense signals and adds it to the project
   *
   * @param file        the GCxGC file
   * @param numFeatures the maximum number of features
   * @param mzTolerance the m/z tolerance of the signals of a feature
   * @return the feature list
   */
  public static @NotNull ModularFeatureList create(@NotNull final IMSRawDataFile file,
      final int numFeatures, @NotNull final MZTolerance mzTolerance) {
    final List<Frame> modulations = file.getFrames().stream().map(Frame.class::cast).toList();
    final ModularFeatureList flist = new ModularFeatureList(file.getName() + " 2D features", null,
        file);
    DataTypeUtils.addDefaultChromatographicTypeColumns(flist);
    DataTypeUtils.addDefaultGcxGcTypeColumns(flist);
    flist.setSelectedScans(file, modulations);

    final BinningMobilogramDataAccess binning = EfficientDataAccess.of(file,
        BinningMobilogramDataAccess.getRecommendedBinWidth(file));
    int id = 1;
    for (final GcxGcTestApex apex : findApexes(modulations, numFeatures)) {
      final Range<Double> mzRange = mzTolerance.getToleranceRange(apex.mz());
      final List<IonMobilitySeries> series = new ArrayList<>();
      final List<Frame> frames = new ArrayList<>();
      for (final Frame modulation : modulations) {
        final IonMobilitySeries trace = extract(modulation, apex.scan(), mzRange, apex.mz());
        if (trace != null) {
          series.add(trace);
          frames.add(modulation);
        }
      }
      final ModularFeature feature = new ModularFeature(flist, file,
          IonMobilogramTimeSeriesFactory.of(null, series, binning, frames),
          FeatureStatus.DETECTED);
      flist.addRow(new ModularFeatureListRow(flist, id++, feature));
    }
    ProjectService.getProject().addFeatureList(flist);
    return flist;
  }

  /**
   * @return the closest signals to the m/z per scan within the scan window, or null if there are
   * none
   */
  private static @Nullable IonMobilitySeries extract(@NotNull final Frame modulation, final int apexScan,
      @NotNull final Range<Double> mzRange, final double mz) {
    final List<MobilityScan> scans = new ArrayList<>();
    final List<Double> mzs = new ArrayList<>();
    final List<Double> intensities = new ArrayList<>();
    final int last = Math.min(modulation.getNumberOfMobilityScans() - 1, apexScan + SCAN_WINDOW);
    for (int s = Math.max(0, apexScan - SCAN_WINDOW); s <= last; s++) {
      final MobilityScan scan = modulation.getMobilityScan(s);
      final MassList massList = scan.getMassList();
      int best = -1;
      for (int i = 0; massList != null && i < massList.getNumberOfDataPoints(); i++) {
        if (mzRange.contains(massList.getMzValue(i)) && (best < 0
            || Math.abs(massList.getMzValue(i) - mz) < Math.abs(massList.getMzValue(best) - mz))) {
          best = i;
        }
      }
      if (best >= 0) {
        scans.add(scan);
        mzs.add(massList.getMzValue(best));
        intensities.add(massList.getIntensityValue(best));
      }
    }
    if (scans.isEmpty()) {
      return null;
    }
    return new SimpleIonMobilitySeries(null,
        mzs.stream().mapToDouble(Double::doubleValue).toArray(),
        intensities.stream().mapToDouble(Double::doubleValue).toArray(), scans);
  }

  /**
   * @return the most intense signals of the single scans with distinct m/z
   */
  private static @NotNull List<GcxGcTestApex> findApexes(@NotNull final List<Frame> modulations,
      final int numFeatures) {
    final List<GcxGcTestApex> signals = new ArrayList<>();
    for (final Frame modulation : modulations) {
      for (int s = 0; s < modulation.getNumberOfMobilityScans(); s++) {
        final MassList massList = modulation.getMobilityScan(s).getMassList();
        for (int i = 0; massList != null && i < massList.getNumberOfDataPoints(); i++) {
          signals.add(
              new GcxGcTestApex(massList.getMzValue(i), massList.getIntensityValue(i), s));
        }
      }
    }
    signals.sort(Comparator.comparingDouble(GcxGcTestApex::intensity).reversed());
    final List<GcxGcTestApex> apexes = new ArrayList<>();
    for (final GcxGcTestApex signal : signals) {
      if (apexes.size() >= numFeatures) {
        break;
      }
      if (apexes.stream().allMatch(a -> Math.abs(a.mz() - signal.mz()) > MIN_MZ_DISTANCE)) {
        apexes.add(signal);
      }
    }
    return apexes;
  }
}
