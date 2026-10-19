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

import java.util.Random;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Chromatographic peak shapes (Gaussian, tailing, fronting, broad, fuzzy, split) and non-peaks
 */
class UnimodalShapeTest {

  private static final int N = 80;

  @Test
  void peakShapesFitWell() {
    // Gaussian
    assertAbove(0.99, profile(i -> gauss(i, 40, 4)));
    // moderate and severe tailing: exponential decay after a fast rise
    assertAbove(0.99, profile(i -> i < 30 ? gauss(i, 30, 2) : Math.exp(-(i - 30) / 6d)));
    assertAbove(0.99, profile(i -> i < 20 ? gauss(i, 20, 1.5) : Math.exp(-(i - 20) / 25d)));
    // fronting: mirrored tailing
    assertAbove(0.99, profile(i -> i > 50 ? gauss(i, 50, 2) : Math.exp(-(50 - i) / 8d)));
    // broad "fat" peak
    assertAbove(0.99, profile(i -> gauss(i, 40, 15)));
    // "fuzzy": tailing peak with 5 % noise
    final Random random = new Random(1);
    assertAbove(0.95, profile(
        i -> (i < 30 ? gauss(i, 30, 3) : Math.exp(-(i - 30) / 10d)) * (1
            + 0.05 * random.nextGaussian())));
    // "split" peak with a shallow valley (one compound)
    assertAbove(0.95, profile(i -> gauss(i, 36, 3) + 0.9 * gauss(i, 44, 3)));
  }

  @Test
  void twoPeaksAndNoiseFitPoorly() {
    // user example: sharp peak and a lower, broader second peak with a deep valley
    assertBelow(0.95, profile(i -> gauss(i, 25, 2) + 0.55 * gauss(i, 45, 4)));
    // scattered noise
    final Random random = new Random(2);
    assertBelow(0.8, profile(i -> random.nextDouble() < 0.5 ? 0 : random.nextDouble()));
  }

  @Test
  void shortProfilesAreAPeak() {
    Assertions.assertEquals(1, UnimodalShape.rSquared(new double[]{1, 5}));
  }

  private static double gauss(final int i, final double center, final double sigma) {
    return Math.exp(-0.5 * Math.pow((i - center) / sigma, 2));
  }

  private static double @NotNull [] profile(@NotNull final IntToDoubleFunction f) {
    return IntStream.range(0, N).mapToDouble(f).toArray();
  }

  private static void assertAbove(final double min, final double @NotNull [] profile) {
    final double r2 = UnimodalShape.rSquared(profile);
    Assertions.assertTrue(r2 >= min, "R2 " + r2);
  }

  private static void assertBelow(final double max, final double @NotNull [] profile) {
    final double r2 = UnimodalShape.rSquared(profile);
    Assertions.assertTrue(r2 < max, "R2 " + r2);
  }
}
