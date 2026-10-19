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

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

/**
 * Background of the ion image of one m/z channel of GCxGC data: column bleed and long tailing
 * signals form ridges along the first dimension at almost the same 2D RT. For each 2D scan index,
 * a signal that is present in all modulations of the window around it has a background equal to
 * the lowest of these signals (rolling minimum along the first dimension). Missing signals count as
 * zero, so sparse channels have no background and 2D features narrower than the window keep their
 * shape.
 */
final class IonImageBackground {

  private IonImageBackground() {
  }

  /**
   * @param points         all signals
   * @param channel        signal indices of one m/z channel
   * @param numModulations number of modulations, size of the first dimension axis
   * @param window         window width in modulations, 0 disables the background subtraction
   * @param corrected      output: background corrected intensities by signal index
   */
  static void subtract(@NotNull final IonImagePoints points, @NotNull final IntList channel,
      final int numModulations, final int window, final float @NotNull [] corrected) {
    final int n = channel.size();
    final int halfWidth = window / 2;
    final int width = 2 * halfWidth + 1;
    // decision: the window always spans its full width, shorter runs have no background
    if (window <= 1 || numModulations < width) {
      for (int i = 0; i < n; i++) {
        final int point = channel.getInt(i);
        corrected[point] = points.intensity(point);
      }
      return;
    }
    // signals by 2D scan index, then modulation
    final int[] sorted = channel.toIntArray();
    IntArrays.quickSort(sorted, (a, b) -> {
      final int scan = Integer.compare(points.scan(a), points.scan(b));
      return scan != 0 ? scan : Integer.compare(points.modulation(a), points.modulation(b));
    });

    // per 2D scan index (column): one value per modulation (most intense signal of a pixel)
    final int[] modulations = new int[n];
    final float[] values = new float[n];
    final int[] minQueue = new int[n];
    int start = 0;
    while (start < n) {
      final int scan = points.scan(sorted[start]);
      int end = start;
      int count = 0;
      while (end < n && points.scan(sorted[end]) == scan) {
        final int point = sorted[end];
        final int modulation = points.modulation(point);
        if (count > 0 && modulations[count - 1] == modulation) {
          values[count - 1] = Math.max(values[count - 1], points.intensity(point));
        } else {
          modulations[count] = modulation;
          values[count] = points.intensity(point);
          count++;
        }
        end++;
      }

      // sliding window minimum over the modulations of this column
      int head = 0;
      int tail = 0;
      int lower = 0;
      int upper = 0;
      for (int k = start; k < end; k++) {
        final int point = sorted[k];
        final int modulation = points.modulation(point);
        // shifted at the first and last modulations to keep the full width
        final int windowStart = Math.clamp(modulation - halfWidth, 0, numModulations - width);
        final int windowEnd = windowStart + width - 1;
        while (upper < count && modulations[upper] <= windowEnd) {
          while (tail > head && values[minQueue[tail - 1]] >= values[upper]) {
            tail--;
          }
          minQueue[tail++] = upper;
          upper++;
        }
        while (lower < count && modulations[lower] < windowStart) {
          lower++;
        }
        while (head < tail && minQueue[head] < lower) {
          head++;
        }
        // background only if every modulation of the window has a signal
        final boolean complete = upper - lower == width;
        final float background = complete && head < tail ? values[minQueue[head]] : 0f;
        corrected[point] = Math.max(0f, points.intensity(point) - background);
      }
      start = end;
    }
  }
}
