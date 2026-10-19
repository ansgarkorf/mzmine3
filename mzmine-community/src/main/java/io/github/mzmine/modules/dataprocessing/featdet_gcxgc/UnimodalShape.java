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
 * Peak shape quality without a peak model: the closest unimodal profile (rising up to the apex,
 * falling after it) by isotonic regression on both sides of the apex. Gaussian, tailing, fronting,
 * broad and noisy ("fuzzy") peaks fit well, profiles with two apexes or scattered noise do not.
 */
final class UnimodalShape {

  private UnimodalShape() {
  }

  /**
   * @param profile intensities along one dimension
   * @return coefficient of determination of the unimodal fit, 1 for profiles with fewer than 3
   * values or without variance
   */
  static double rSquared(final double @NotNull [] profile) {
    final int n = profile.length;
    if (n < 3) {
      return 1;
    }
    int apex = 0;
    double mean = 0;
    for (int i = 0; i < n; i++) {
      mean += profile[i];
      if (profile[i] > profile[apex]) {
        apex = i;
      }
    }
    mean /= n;
    final double[] fit = new double[n];
    // rising part up to the apex, falling part from the apex
    isotonic(profile, 0, apex + 1, true, fit);
    isotonic(profile, apex, n, false, fit);

    double residual = 0;
    double total = 0;
    for (int i = 0; i < n; i++) {
      residual += (profile[i] - fit[i]) * (profile[i] - fit[i]);
      total += (profile[i] - mean) * (profile[i] - mean);
    }
    return total <= 0 ? 1 : 1 - residual / total;
  }

  /**
   * Pool adjacent violators: least squares fit that is non-decreasing (or non-increasing) on
   * [from, to)
   */
  private static void isotonic(final double @NotNull [] y, final int from, final int to,
      final boolean increasing, final double @NotNull [] fit) {
    final int n = to - from;
    final double[] sums = new double[n];
    final int[] counts = new int[n];
    int blocks = 0;
    for (int i = from; i < to; i++) {
      sums[blocks] = y[i];
      counts[blocks] = 1;
      blocks++;
      // merge blocks while the order is violated
      while (blocks > 1) {
        final double last = sums[blocks - 1] / counts[blocks - 1];
        final double previous = sums[blocks - 2] / counts[blocks - 2];
        if (increasing ? previous <= last : previous >= last) {
          break;
        }
        sums[blocks - 2] += sums[blocks - 1];
        counts[blocks - 2] += counts[blocks - 1];
        blocks--;
      }
    }
    int index = from;
    for (int b = 0; b < blocks; b++) {
      final double value = sums[b] / counts[b];
      for (int c = 0; c < counts[b]; c++) {
        fit[index++] = value;
      }
    }
  }
}
