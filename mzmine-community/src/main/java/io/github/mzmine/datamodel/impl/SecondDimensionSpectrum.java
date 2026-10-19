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

package io.github.mzmine.datamodel.impl;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.GCxGCRawDataFile;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.impl.masslist.ScanPointerMassList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The signals of one second dimension scan index of GCxGC data summed over all modulations (first
 * dimension). The retention time of this spectrum is the second dimension retention time (2D RT)
 * in minutes, the scan number is the scan index within a modulation. Chromatograms are built along
 * these spectra in the second dimension. Created by the GCxGC 2D RT spectra module and stored in the
 * {@link GCxGCRawDataFile}, not in {@link GCxGCRawDataFile#getScans()}.
 */
public class SecondDimensionSpectrum extends SimpleScan {

  public static final String SCAN_DEFINITION = "GCxGC 2D RT spectrum";

  /**
   * @param scanIndex          the scan index within a modulation
   * @param secondDimensionRT  the 2D RT in minutes
   * @param mzValues           centroided and merged m/z values
   * @param intensityValues    summed intensities
   * @param polarity           polarity of the merged scans
   * @param scanMZRange        scanning m/z range of the merged scans
   */
  public SecondDimensionSpectrum(@NotNull final GCxGCRawDataFile dataFile, final int scanIndex,
      final float secondDimensionRT, final double @NotNull [] mzValues,
      final double @NotNull [] intensityValues, @NotNull final PolarityType polarity,
      @Nullable final Range<Double> scanMZRange) {
    super(dataFile, scanIndex, 1, secondDimensionRT, null, mzValues, intensityValues,
        MassSpectrumType.CENTROIDED, polarity, SCAN_DEFINITION, scanMZRange);
    // the merged signals are already detected signals
    addMassList(new ScanPointerMassList(this));
  }

  /**
   * @return the second dimension retention time in minutes, same as {@link #getRetentionTime()}
   */
  public float getSecondDimensionRT() {
    return getRetentionTime();
  }
}
