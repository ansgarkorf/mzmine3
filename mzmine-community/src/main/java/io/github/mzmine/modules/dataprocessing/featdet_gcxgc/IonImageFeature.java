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

import org.jetbrains.annotations.NotNull;

/**
 * A 2D feature of one m/z channel in pixel form before it becomes a mzmine feature: its signals
 * sorted by modulation and scan index. Used to deconvolute compounds before the features are
 * created.
 */
final class IonImageFeature {

  private final int channel;
  private final int[] modulations;
  private final int[] scans;
  private final double[] mzs;
  private final float[] intensities;
  private final float[] corrected;
  private final int apex;

  /**
   * @param channel     id of the m/z channel, unique within the file
   * @param modulations modulation index of each signal, sorted with the scans
   * @param scans       scan index within the modulation of each signal
   * @param mzs         m/z of each signal
   * @param intensities measured intensity of each signal
   * @param corrected   background corrected intensity of each signal
   */
  IonImageFeature(final int channel, final int @NotNull [] modulations, final int @NotNull [] scans,
      final double @NotNull [] mzs, final float @NotNull [] intensities,
      final float @NotNull [] corrected) {
    this.channel = channel;
    this.modulations = modulations;
    this.scans = scans;
    this.mzs = mzs;
    this.intensities = intensities;
    this.corrected = corrected;
    int top = 0;
    for (int i = 1; i < corrected.length; i++) {
      if (corrected[i] > corrected[top]) {
        top = i;
      }
    }
    apex = top;
  }

  int channel() {
    return channel;
  }

  int size() {
    return modulations.length;
  }

  int modulation(final int i) {
    return modulations[i];
  }

  int scan(final int i) {
    return scans[i];
  }

  double mz(final int i) {
    return mzs[i];
  }

  float intensity(final int i) {
    return intensities[i];
  }

  float corrected(final int i) {
    return corrected[i];
  }

  long pixel(final int i, final int scansPerModulation) {
    return IonImagePoints.pixel(modulations[i], scans[i], scansPerModulation);
  }

  /**
   * @return background corrected apex intensity
   */
  float height() {
    return corrected[apex];
  }

  int apexModulation() {
    return modulations[apex];
  }

  int apexScan() {
    return scans[apex];
  }

  /**
   * @return the corrected intensities summed per 2D scan index (2D RT profile)
   */
  double @NotNull [] secondDimensionProfile() {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (final int scan : scans) {
      min = Math.min(min, scan);
      max = Math.max(max, scan);
    }
    final double[] profile = new double[max - min + 1];
    for (int i = 0; i < scans.length; i++) {
      profile[scans[i] - min] += corrected[i];
    }
    return profile;
  }

  /**
   * @return how well the 2D RT profile fits a single peak, see {@link UnimodalShape}
   */
  double shapeQuality() {
    return UnimodalShape.rSquared(secondDimensionProfile());
  }

  /**
   * @return a feature with the same signals and the given corrected intensities, the measured
   * intensities are scaled with them, signals without intensity are removed
   */
  @NotNull IonImageFeature withCorrected(final float @NotNull [] newCorrected) {
    int n = 0;
    for (final float v : newCorrected) {
      if (v > 0) {
        n++;
      }
    }
    final int[] m = new int[n];
    final int[] s = new int[n];
    final double[] z = new double[n];
    final float[] raw = new float[n];
    final float[] corr = new float[n];
    int k = 0;
    for (int i = 0; i < newCorrected.length; i++) {
      if (newCorrected[i] <= 0) {
        continue;
      }
      m[k] = modulations[i];
      s[k] = scans[i];
      z[k] = mzs[i];
      // the measured intensity gets the same share as the corrected intensity
      raw[k] = corrected[i] > 0 ? intensities[i] * newCorrected[i] / corrected[i]
          : newCorrected[i];
      corr[k] = newCorrected[i];
      k++;
    }
    return new IonImageFeature(channel, m, s, z, raw, corr);
  }
}
