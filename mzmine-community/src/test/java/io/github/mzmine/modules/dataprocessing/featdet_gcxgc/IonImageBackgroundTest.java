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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Background subtraction along the first dimension of ion images
 */
class IonImageBackgroundTest {

  private static final int MODULATIONS = 60;

  @Test
  void removesRidgeAndKeepsPeak() {
    final IonImagePoints points = new IonImagePoints(200);
    // ridge at scan 10 in every modulation, a peak on top at modulation 30
    for (int m = 0; m < MODULATIONS; m++) {
      final double peak = 5000 * Math.exp(-0.5 * Math.pow((m - 30) / 1.5, 2));
      points.add(100, (float) (1000 + peak), m, 10);
    }
    final float[] corrected = subtract(points, 21);
    // the ridge is gone, the peak keeps its height above the ridge
    Assertions.assertEquals(0f, corrected[5], 1e-3f);
    Assertions.assertEquals(5000f, corrected[30], 1f);
  }

  @Test
  void noBackgroundWithMissingSignals() {
    final IonImagePoints points = new IonImagePoints(200);
    // signals in every second modulation only: sparse, no background
    for (int m = 0; m < MODULATIONS; m += 2) {
      points.add(100, 1000, m, 10);
    }
    final float[] corrected = subtract(points, 21);
    for (int i = 0; i < points.size(); i++) {
      Assertions.assertEquals(1000f, corrected[i]);
    }
  }

  @Test
  void windowZeroKeepsIntensities() {
    final IonImagePoints points = new IonImagePoints(10);
    for (int m = 0; m < MODULATIONS; m++) {
      points.add(100, 1000, m, 10);
    }
    final float[] corrected = subtract(points, 0);
    Assertions.assertEquals(1000f, corrected[0]);
  }

  private static float[] subtract(final IonImagePoints points, final int window) {
    final float[] corrected = new float[points.size()];
    IonImageBackground.subtract(points,
        new IntArrayList(IntStream.range(0, points.size()).toArray()), MODULATIONS, window,
        corrected);
    return corrected;
  }
}
