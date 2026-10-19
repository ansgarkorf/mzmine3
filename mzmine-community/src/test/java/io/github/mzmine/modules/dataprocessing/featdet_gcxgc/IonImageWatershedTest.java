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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Watershed segmentation of synthetic ion images (modulation x scan)
 */
class IonImageWatershedTest {

  private static final int MODULATIONS = 12;
  private static final int SCANS = 60;

  /**
   * Gaussian peak: apex height, modulation and scan of the apex, widths (sigma) in modulations
   * and scans
   */
  record Peak(double height, double modulation, double scan, double sigmaModulation,
                      double sigmaScan) {

    double intensity(final int m, final int s) {
      final double dm = (m - modulation) / sigmaModulation;
      final double ds = (s - scan) / sigmaScan;
      return height * Math.exp(-0.5 * (dm * dm + ds * ds));
    }
  }

  @Test
  void separatesPeaksWithDeepSaddle() {
    // neighboring modulations, far apart in 2D RT
    final List<int[]> regions = segment(image(new Peak(1e5, 4, 20, 0.8, 3),
        new Peak(5e4, 5, 35, 0.8, 3)), 1.5);
    Assertions.assertEquals(2, regions.size());
    // the most intense region first, apexes in their own region
    Assertions.assertTrue(regions.get(0).length > 0 && regions.get(1).length > 0);
  }

  @Test
  void separatesCoelutingCompoundsInSecondDimension() {
    // same modulations, 2D apexes 10 scans apart: separated only in the second dimension, the
    // saddle is about 5 times lower than the lower apex
    final List<int[]> regions = segment(image(new Peak(1e5, 5, 25, 1, 2),
        new Peak(3e4, 5, 35, 1, 2)), 1.5);
    Assertions.assertEquals(2, regions.size());
  }

  @Test
  void mergesShallowSaddle() {
    // apexes 4 scans apart with sigma 3: shoulder without a prominent saddle
    final List<int[]> regions = segment(image(new Peak(1e5, 5, 25, 1, 3),
        new Peak(9e4, 5, 29, 1, 3)), 1.5);
    Assertions.assertEquals(1, regions.size());
  }

  @Test
  void mergesLowerPeaksBelowMinimumHeight() {
    final IonImagePoints points = image(new Peak(1e5, 4, 20, 0.8, 3), new Peak(500, 8, 45, 0.8, 3));
    final List<int[]> regions = IonImageWatershed.segment(points, sortedChannel(points), raw(points), SCANS,
        1000, 1.5, 0);
    Assertions.assertEquals(1, regions.size());
  }

  @Test
  void edgeLevelTrimsLowSignals() {
    final IonImagePoints points = image(new Peak(1e5, 5, 25, 1, 3));
    final int all = IonImageWatershed.segment(points, sortedChannel(points), raw(points), SCANS, 100, 1.5, 0)
        .getFirst().length;
    final int[] trimmed = IonImageWatershed.segment(points, sortedChannel(points), raw(points), SCANS, 100,
        1.5, 0.1).getFirst();
    Assertions.assertTrue(trimmed.length < all);
    for (final int point : trimmed) {
      Assertions.assertTrue(points.intensity(point) >= 1e4);
    }
  }

  @Test
  void doesNotBridgeMissingScans() {
    // scattered noise: pairs of signals separated by single missing scans in one modulation
    final IonImagePoints points = new IonImagePoints(16);
    for (final int s : new int[]{10, 11, 13, 14, 16, 17}) {
      points.add(100, s % 3 == 0 ? 6f : 5f, 0, s);
    }
    Assertions.assertEquals(3,
        IonImageWatershed.segment(points, sortedChannel(points), raw(points), SCANS, 1, 1.5, 0).size());
  }

  @Test
  void keepsTheMostIntenseSignalOfAPixel() {
    final IonImagePoints points = image(new Peak(1e4, 5, 25, 1, 3));
    // a second, lower signal in the apex pixel
    points.add(100.001, 10, 5, 25);
    final int[] region = IonImageWatershed.segment(points, sortedChannel(points), raw(points), SCANS, 100, 1.5,
        0).getFirst();
    Assertions.assertTrue(Arrays.stream(region).noneMatch(p -> points.intensity(p) == 10f));
  }

  private static @NotNull List<int[]> segment(@NotNull final IonImagePoints points,
      final double minRatio) {
    return IonImageWatershed.segment(points, sortedChannel(points), raw(points), SCANS, 1000, minRatio, 0);
  }

  /**
   * Sum of the peaks, signals above an intensity of 1 (detected signals)
   */
  static @NotNull IonImagePoints image(@NotNull final Peak... peaks) {
    final IonImagePoints points = new IonImagePoints(MODULATIONS * SCANS);
    for (int m = 0; m < MODULATIONS; m++) {
      for (int s = 0; s < SCANS; s++) {
        double intensity = 0;
        for (final Peak peak : peaks) {
          intensity += peak.intensity(m, s);
        }
        if (intensity > 1) {
          points.add(100, (float) intensity, m, s);
        }
      }
    }
    return points;
  }

  static float @NotNull [] raw(@NotNull final IonImagePoints points) {
    final float[] intensities = new float[points.size()];
    for (int i = 0; i < intensities.length; i++) {
      intensities[i] = points.intensity(i);
    }
    return intensities;
  }

  static @NotNull IntArrayList sortedChannel(@NotNull final IonImagePoints points) {
    return new IntArrayList(IntStream.range(0, points.size()).boxed()
        .sorted(Comparator.comparingDouble(i -> -points.intensity(i))).toList());
  }
}
