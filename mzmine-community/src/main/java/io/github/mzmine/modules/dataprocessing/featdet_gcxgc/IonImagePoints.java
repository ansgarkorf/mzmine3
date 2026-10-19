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

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * Detected signals of GCxGC single scans as primitive columns: m/z, intensity and the pixel
 * position in the ion image (modulation index, scan index within the modulation)
 */
class IonImagePoints {

  private final DoubleArrayList mzs;
  private final FloatArrayList intensities;
  private final IntArrayList modulations;
  private final IntArrayList scans;

  IonImagePoints(final int capacity) {
    mzs = new DoubleArrayList(capacity);
    intensities = new FloatArrayList(capacity);
    modulations = new IntArrayList(capacity);
    scans = new IntArrayList(capacity);
  }

  void add(final double mz, final float intensity, final int modulation, final int scan) {
    mzs.add(mz);
    intensities.add(intensity);
    modulations.add(modulation);
    scans.add(scan);
  }

  void addAll(@NotNull final IonImagePoints other) {
    mzs.addAll(other.mzs);
    intensities.addAll(other.intensities);
    modulations.addAll(other.modulations);
    scans.addAll(other.scans);
  }

  int size() {
    return mzs.size();
  }

  double mz(final int index) {
    return mzs.getDouble(index);
  }

  float intensity(final int index) {
    return intensities.getFloat(index);
  }

  /**
   * @return the index of the modulation in the processed modulations
   */
  int modulation(final int index) {
    return modulations.getInt(index);
  }

  /**
   * @return the scan index within the modulation (2D RT axis)
   */
  int scan(final int index) {
    return scans.getInt(index);
  }

  /**
   * @return the pixel key of the signal in the ion image
   */
  long pixel(final int index, final int scansPerModulation) {
    return pixel(modulation(index), scan(index), scansPerModulation);
  }

  static long pixel(final int modulation, final int scan, final int scansPerModulation) {
    return (long) modulation * scansPerModulation + scan;
  }
}
