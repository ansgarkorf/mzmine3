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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Splits watershed regions by their summed profiles. The watershed decides per signal, so a long
 * but low area next to a compound (e.g., a second compound eluting over many modulations at a
 * close 2D RT) is merged as a shoulder, although its summed 2D RT trace is a separate peak. Each
 * region is split at the valley of its summed 2D RT profile (sum per scan index) or of its first
 * dimension profile (sum per modulation) if a second apex is at least the minimum top/valley ratio
 * above the valley, like the local minimum resolver. Split parts are split again.
 * <p>
 * decision: both parts of a split must still be a peak: along the 2D RT at least the minimum
 * consecutive 2D data points, so noise bumps on a peak top or in a tail are not cut off. The first
 * dimension has only few modulations per compound (user): both parts need the minimum number of
 * modulations, so a compound is never cut into fragments. A shape fit over the whole profile is
 * not used to decide splits: it is dominated by the main peak and kept small compounds next to
 * intense ones merged (Biogluc: 4.5x more two-apex features).
 */
final class IonImageRegionSplitter {

  /**
   * assumption: a second apex below this fraction of the main apex is noise of the tail
   */
  static final double MIN_SECOND_APEX_FRACTION = 0.05;

  private IonImageRegionSplitter() {
  }

  /**
   * @param points    all signals
   * @param region    signal indices of one region
   * @param intensity intensities by signal index (background corrected)
   * @param minRatio       minimum ratio of the second apex to the valley
   * @param minModulations minimum modulations of each part of a split in the first dimension
   * @param min2DDataPoints minimum 2D RT scans of each part of a split in the second dimension
   * @return the split regions or the region itself
   */
  static @NotNull List<int[]> split(@NotNull final IonImagePoints points,
      final int @NotNull [] region, final float @NotNull [] intensity, final double minRatio,
      final int minModulations, final int min2DDataPoints) {
    final List<int[]> result = new ArrayList<>();
    final Deque<int[]> todo = new ArrayDeque<>();
    todo.push(region);
    while (!todo.isEmpty()) {
      final int[] current = todo.pop();
      int[][] parts = splitAtValley(points, current, intensity, minRatio, true,
          min2DDataPoints);
      if (parts == null) {
        parts = splitAtValley(points, current, intensity, minRatio, false, minModulations);
      }
      if (parts == null) {
        result.add(current);
      } else {
        todo.push(parts[0]);
        todo.push(parts[1]);
      }
    }
    return result;
  }

  /**
   * Keeps only the peak around the most intense signal: splits at valleys like
   * {@link #split} without minimum part sizes and keeps the part with the apex. Used when a region
   * cannot be split into peaks and does not look like one peak, so that the main peak is kept
   * instead of discarding the whole region.
   *
   * @return the signal indices of the main peak, the region itself if there is no valley
   */
  static int @NotNull [] mainPeak(@NotNull final IonImagePoints points,
      final int @NotNull [] region, final float @NotNull [] intensity, final double minRatio) {
    int[] current = region;
    while (true) {
      int[][] parts = splitAtValley(points, current, intensity, minRatio, true, 1);
      if (parts == null) {
        parts = splitAtValley(points, current, intensity, minRatio, false, 1);
      }
      if (parts == null) {
        return current;
      }
      int apex = current[0];
      for (final int point : current) {
        if (intensity[point] > intensity[apex]) {
          apex = point;
        }
      }
      final int apexPoint = apex;
      current = Arrays.stream(parts[0]).anyMatch(p -> p == apexPoint) ? parts[0] : parts[1];
    }
  }

  /**
   * @param secondDimension true to split along the 2D RT (scan index), false along the
   *                        modulations
   * @return both parts or null if the profile has a single apex
   */
  private static int @Nullable [][] splitAtValley(@NotNull final IonImagePoints points,
      final int @NotNull [] region, final float @NotNull [] intensity, final double minRatio,
      final boolean secondDimension, final int minPartSize) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (final int point : region) {
      final int coordinate = coordinate(points, point, secondDimension);
      min = Math.min(min, coordinate);
      max = Math.max(max, coordinate);
    }
    final int length = max - min + 1;
    if (length < 3) {
      return null;
    }
    final double[] profile = new double[length];
    for (final int point : region) {
      profile[coordinate(points, point, secondDimension) - min] += intensity[point];
    }
    // light smoothing against single scan noise
    final double[] smoothed = new double[length];
    for (int i = 0; i < length; i++) {
      smoothed[i] = (profile[Math.max(0, i - 1)] + 2 * profile[i] + profile[Math.min(length - 1,
          i + 1)]) / 4;
    }
    int top = 0;
    for (int i = 1; i < length; i++) {
      if (smoothed[i] > smoothed[top]) {
        top = i;
      }
    }

    // the highest second apex that is prominent enough on either side of the top
    int bestValley = -1;
    double bestApex = 0;
    for (final int direction : new int[]{-1, 1}) {
      int valley = top;
      for (int i = top + direction; i >= 0 && i < length; i += direction) {
        if (smoothed[i] < smoothed[valley]) {
          valley = i;
        }
        final boolean localMax = smoothed[i] >= smoothed[i - direction] && (i + direction < 0
            || i + direction >= length || smoothed[i] >= smoothed[i + direction]);
        if (localMax && smoothed[i] > bestApex
            && smoothed[i] >= MIN_SECOND_APEX_FRACTION * smoothed[top]
            && smoothed[i] >= minRatio * smoothed[valley]) {
          bestApex = smoothed[i];
          bestValley = valley;
        }
      }
    }
    if (bestValley < 0) {
      return null;
    }

    // the valley is the last coordinate of the lower part
    final int border = min + bestValley;
    final int[] lower = Arrays.stream(region)
        .filter(point -> coordinate(points, point, secondDimension) <= border).toArray();
    final int[] upper = Arrays.stream(region)
        .filter(point -> coordinate(points, point, secondDimension) > border).toArray();
    if (lower.length == 0 || upper.length == 0) {
      return null;
    }
    if (countCoordinates(points, lower, secondDimension) < minPartSize
        || countCoordinates(points, upper, secondDimension) < minPartSize) {
      // both parts must still be a peak in this dimension
      return null;
    }
    return new int[][]{lower, upper};
  }

  private static int countCoordinates(@NotNull final IonImagePoints points,
      final int @NotNull [] region, final boolean secondDimension) {
    return (int) Arrays.stream(region).map(point -> coordinate(points, point, secondDimension))
        .distinct().count();
  }

  private static int coordinate(@NotNull final IonImagePoints points, final int point,
      final boolean secondDimension) {
    return secondDimension ? points.scan(point) : points.modulation(point);
  }
}
