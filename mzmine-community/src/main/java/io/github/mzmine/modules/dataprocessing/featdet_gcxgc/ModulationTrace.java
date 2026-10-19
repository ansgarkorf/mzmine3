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

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonMobilitySeries;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Collects the signals of one GCxGC 2D RT chromatogram from the single scans of consecutive
 * modulations. Scans must be offered in acquisition order (modulation, then scan index). Keeps the
 * signal closest to the chromatogram m/z per single scan.
 */
class ModulationTrace {

  private final @NotNull Range<Double> mzRange;
  private final double centerMz;
  private final List<MobilityScan> scans = new ArrayList<>();
  private final DoubleArrayList mzs = new DoubleArrayList();
  private final DoubleArrayList intensities = new DoubleArrayList();

  ModulationTrace(@NotNull final Range<Double> mzRange, final double centerMz) {
    this.mzRange = mzRange;
    this.centerMz = centerMz;
  }

  /**
   * Adds the signal if it is closer to the center m/z than a signal of the same scan.
   */
  void offer(@NotNull final MobilityScan scan, final double mz, final double intensity) {
    final int last = scans.size() - 1;
    if (last >= 0 && scans.get(last) == scan) {
      if (Math.abs(mz - centerMz) < Math.abs(mzs.getDouble(last) - centerMz)) {
        mzs.set(last, mz);
        intensities.set(last, intensity);
      }
      return;
    }
    scans.add(scan);
    mzs.add(mz);
    intensities.add(intensity);
  }

  @NotNull Range<Double> getMzRange() {
    return mzRange;
  }

  int getNumberOfScans() {
    return scans.size();
  }

  /**
   * Appends the signals of another trace of the same chromatogram from later modulations.
   */
  void addAll(@NotNull final ModulationTrace later) {
    scans.addAll(later.scans);
    mzs.addAll(later.mzs);
    intensities.addAll(later.intensities);
  }

  /**
   * @param modulations output: the modulations of the returned series in the same order
   * @return one series of single scans per modulation with signals
   */
  @NotNull List<IonMobilitySeries> toSeriesPerModulation(@NotNull final List<Frame> modulations) {
    final List<IonMobilitySeries> series = new ArrayList<>();
    final double[] allMzs = mzs.toDoubleArray();
    final double[] allIntensities = intensities.toDoubleArray();
    int start = 0;
    for (int i = 1; i <= scans.size(); i++) {
      if (i < scans.size() && scans.get(i).getFrame() == scans.get(start).getFrame()) {
        continue;
      }
      // IonMobilitySeries are kept in memory until they are added to the time series
      series.add(new SimpleIonMobilitySeries(null, Arrays.copyOfRange(allMzs, start, i),
          Arrays.copyOfRange(allIntensities, start, i),
          new ArrayList<>(scans.subList(start, i))));
      modulations.add(scans.get(start).getFrame());
      start = i;
    }
    return series;
  }
}
