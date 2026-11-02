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

package io.github.mzmine.util;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.modules.io.projectload.CachedIMSRawDataFile;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTToleranceParameter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javafx.collections.FXCollections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Comprehensive two-dimensional GC (GCxGC) data reuses the ion mobility data model internally, see
 * {@link io.github.mzmine.datamodel.GCxGCRawDataFile}. These checks separate GCxGC from ion mobility
 * data so that GCxGC users never see ion mobility terms.
 * <p>
 * decision: all checks are based on {@link MobilityType#GCXGC} and never on
 * {@code instanceof GCxGCRawDataFile}, because raw data files and frames are wrapped during project
 * load.
 */
public class GcxGcUtils {

  /**
   * Label of the second dimension retention time
   */
  public static final String SECOND_DIMENSION_RT = "2D RT";
  /**
   * Label of the first dimension retention time
   */
  public static final String FIRST_DIMENSION_RT = "1D RT";
  /**
   * 2D RT values in minutes are small, use more decimals than the RT format
   */
  public static final NumberFormat SECOND_DIMENSION_RT_EXPORT_FORMAT = new DecimalFormat(
      "0.00000");

  private GcxGcUtils() {
  }

  /**
   * @return format for 2D RT values in minutes in the GUI
   */
  public static @NotNull NumberFormat createSecondDimensionRTFormat() {
    return new DecimalFormat("0.0000");
  }

  /**
   * @return true if the file is GCxGC data
   */
  public static boolean isGcxGc(@Nullable final RawDataFile file) {
    return file instanceof IMSRawDataFile ims && ims.getMobilityType() == MobilityType.GCXGC;
  }

  /**
   * @return true if any raw data file of the feature list is GCxGC data
   */
  public static boolean isGcxGc(@Nullable final FeatureList flist) {
    return flist != null && flist.getRawDataFiles().stream().anyMatch(GcxGcUtils::isGcxGc);
  }

  /**
   * @return true if the scan is a modulation of GCxGC data (internally a frame)
   */
  public static boolean isModulation(@Nullable final Scan scan) {
    return scan instanceof Frame frame && frame.getMobilityType() == MobilityType.GCXGC;
  }

  /**
   * @return true for modulations and single scans of GCxGC data
   */
  public static boolean isGcxGcScan(@Nullable final Scan scan) {
    return isModulation(scan) || (
        scan instanceof MobilityScan ms && ms.getFrame().getMobilityType() == MobilityType.GCXGC);
  }

  /**
   * @return the GCxGC file, unwrapped if cached during project load, or null if the file is no
   * GCxGC file
   */
  public static @Nullable GCxGCRawDataFile asGcxGcFile(@Nullable final RawDataFile file) {
    final RawDataFile original =
        file instanceof CachedIMSRawDataFile cached ? cached.getOriginalFile() : file;
    return original instanceof GCxGCRawDataFile gcxgc ? gcxgc : null;
  }

  /**
   * @return true if the feature has a second dimension retention time trace (GCxGC)
   */
  public static boolean isGcxGcFeature(@Nullable final Feature feature) {
    if (feature == null || !(feature.getFeatureData() instanceof IonMobilogramTimeSeries series)) {
      return false;
    }
    final RawDataFile raw = feature.getRawDataFile();
    if (raw != null) {
      return isGcxGc(raw);
    }
    // the raw file may be set after the feature data, e.g., while copying features
    return series.getNumberOfValues() > 0 && isModulation(series.getSpectrum(0));
  }

  /**
   * @return the second separation dimension of the feature: 2D RT for GCxGC, otherwise mobility
   */
  public static @Nullable Float getSecondDimension(@NotNull final Feature feature) {
    return isGcxGc(feature.getRawDataFile()) ? feature.getSecondDimensionRT()
        : feature.getMobility();
  }

  /**
   * @return the second separation dimension range of the feature: 2D RT range for GCxGC, otherwise
   * mobility range
   */
  public static @Nullable Range<Float> getSecondDimensionRange(@NotNull final Feature feature) {
    return isGcxGc(feature.getRawDataFile()) ? feature.getSecondDimensionRTRange()
        : feature.getMobilityRange();
  }

  /**
   * @return a tolerance parameter for 2D RT values in minutes, entered in minutes or seconds
   */
  public static @NotNull RTToleranceParameter secondDimensionRTToleranceParameter(
      @NotNull final String name, @NotNull final String description,
      @NotNull final RTTolerance defaultValue) {
    return new RTToleranceParameter(name, description, defaultValue,
        FXCollections.observableArrayList(Unit.MINUTES, Unit.SECONDS));
  }
}
