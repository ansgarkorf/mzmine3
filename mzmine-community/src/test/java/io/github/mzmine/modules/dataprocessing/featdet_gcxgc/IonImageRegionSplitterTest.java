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

import io.github.mzmine.modules.dataprocessing.featdet_gcxgc.IonImageWatershedTest.Peak;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Splitting watershed regions by their summed profiles
 */
class IonImageRegionSplitterTest {

  @Test
  void splitsLongLowAreaNextToSharpPeak() {
    // sharp intense peak at scan 20 in 3 modulations, a low second compound at scan 32 over 10
    // modulations: per signal a shoulder, summed over the modulations a second 2D peak
    final IonImagePoints points = IonImageWatershedTest.image(new Peak(1e5, 3, 20, 0.7, 1.5),
        new Peak(1.5e4, 6, 32, 3, 2.5));
    final List<int[]> parts = split(points);
    Assertions.assertEquals(2, parts.size());
    // each part holds one apex
    final List<Integer> apexScans = parts.stream().map(part -> points.scan(
        Arrays.stream(part).boxed().max((a, b) -> Float.compare(points.intensity(a),
            points.intensity(b))).orElseThrow())).sorted().toList();
    Assertions.assertEquals(List.of(20, 32), apexScans);
  }

  @Test
  void splitsCompoundsAtDifferentRetentionTimes() {
    // same 2D RT, apexes 6 modulations apart
    final IonImagePoints points = IonImageWatershedTest.image(new Peak(1e5, 3, 25, 1, 2),
        new Peak(5e4, 9, 25, 1, 2));
    Assertions.assertEquals(2, split(points).size());
  }

  @Test
  void keepsBumpInTail() {
    // tailing 2D peak with a small bump in the tail: one peak shape
    final IonImagePoints points = new IonImagePoints(200);
    for (int m = 3; m <= 6; m++) {
      final double height = 1e5 * Math.exp(-0.5 * Math.pow((m - 4.5) / 1.2, 2));
      for (int s = 15; s < 70; s++) {
        double shape = s < 20 ? Math.exp(-0.5 * Math.pow((s - 20) / 1.5, 2))
            : Math.exp(-(s - 20) / 12d);
        if (s >= 40 && s <= 43) {
          shape *= 1.6;
        }
        points.add(100, (float) (height * shape), m, s);
      }
    }
    Assertions.assertEquals(1, split(points).size());
  }

  @Test
  void doesNotCutFewModulations() {
    // 3 modulations with a dip in the middle at the same 2D RT: too few modulations to cut
    final IonImagePoints points = new IonImagePoints(100);
    final double[] heights = {1e5, 4e4, 9e4};
    for (int m = 0; m < 3; m++) {
      for (int s = 15; s < 35; s++) {
        points.add(100, (float) (heights[m] * Math.exp(-0.5 * Math.pow((s - 25) / 2d, 2))), m,
            s);
      }
    }
    Assertions.assertEquals(1, split(points).size());
  }

  @Test
  void mainPeakKeepsTheApexSide() {
    // a main peak and a narrow second peak in one modulation: the main peak side is kept
    final IonImagePoints points = new IonImagePoints(64);
    for (int s = 10; s < 40; s++) {
      final double v = 1e5 * Math.exp(-0.5 * Math.pow((s - 20) / 2d, 2))
          + 3e4 * Math.exp(-0.5 * Math.pow((s - 30) / 1d, 2));
      points.add(100, (float) v, 0, s);
    }
    final int[] main = IonImageRegionSplitter.mainPeak(points,
        IntStream.range(0, points.size()).toArray(), IonImageWatershedTest.raw(points), 1.5);
    Assertions.assertTrue(main.length < points.size());
    Assertions.assertTrue(Arrays.stream(main).allMatch(p -> points.scan(p) < 28));
    Assertions.assertTrue(Arrays.stream(main).anyMatch(p -> points.scan(p) == 20));
  }

  @Test
  void keepsSinglePeak() {
    final IonImagePoints points = IonImageWatershedTest.image(new Peak(1e5, 5, 25, 1, 3));
    Assertions.assertEquals(1, split(points).size());
  }

  private static @NotNull List<int[]> split(@NotNull final IonImagePoints points) {
    return IonImageRegionSplitter.split(points, IntStream.range(0, points.size()).toArray(),
        IonImageWatershedTest.raw(points), 1.5, 2, 3);
  }
}
