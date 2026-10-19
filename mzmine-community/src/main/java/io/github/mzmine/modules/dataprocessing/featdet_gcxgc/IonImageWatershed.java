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

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Segments the ion image of one m/z channel of GCxGC data (modulation x scan index within the
 * modulation) into 2D features with a watershed by flooding from the top: signals are visited from
 * the most to the least intense. A signal without visited neighbors starts a new region. A signal
 * touching several regions is their saddle: a region whose apex is not at least the minimum
 * top/saddle ratio above the saddle (or below the minimum height) is merged into the highest
 * region, otherwise the regions stay separate and the signal joins the region of its most intense
 * neighbor. This is the two-dimensional analog of the top/edge ratio of the local minimum
 * resolver (topological persistence), without a search range in minutes. Pixels are connected to
 * their 8 direct neighbors (neighboring modulations and scans).
 */
final class IonImageWatershed {

  private static final int DUPLICATE = -1;

  private IonImageWatershed() {
  }

  /**
   * @param points             all signals
   * @param channel            indices of the signals of one m/z channel with an intensity above
   *                           zero, sorted by descending intensity
   * @param intensity          intensities by signal index (background corrected)
   * @param scansPerModulation scans per modulation, size of the 2D RT axis
   * @param minHeight          minimum apex intensity of a region
   * @param minRatio           minimum ratio of the apex intensity to the saddle intensity to keep
   *                           two regions separate
   * @param edgeLevel          signals below this fraction of the apex intensity are not part of
   *                           the region
   * @return the signal indices of each region, regions in order of their apex intensity
   */
  static @NotNull List<int[]> segment(@NotNull final IonImagePoints points,
      @NotNull final IntList channel, final float @NotNull [] intensity,
      final int scansPerModulation, final double minHeight,
      final double minRatio, final double edgeLevel) {
    final int n = channel.size();
    final int[] parent = new int[n];
    final float[] apex = new float[n];
    final Long2IntOpenHashMap visited = new Long2IntOpenHashMap(n);
    visited.defaultReturnValue(-1);
    final IntArrayList roots = new IntArrayList(4);

    for (int i = 0; i < n; i++) {
      final int point = channel.getInt(i);
      final int modulation = points.modulation(point);
      final int scan = points.scan(point);
      final long pixel = IonImagePoints.pixel(modulation, scan, scansPerModulation);
      if (visited.containsKey(pixel)) {
        // a more intense signal of this channel in the same scan was visited before
        parent[i] = DUPLICATE;
        continue;
      }
      final float pointIntensity = intensity[point];

      // regions of the visited neighbors and the most intense neighbor
      roots.clear();
      int steepest = -1;
      float steepestIntensity = Float.NEGATIVE_INFINITY;
      for (int dm = -1; dm <= 1; dm++) {
        final int neighborModulation = modulation + dm;
        if (neighborModulation < 0) {
          continue;
        }
        // decision: only direct neighbors, bridging missing scans chained scattered noise signals
        // into features
        for (int ds = -1; ds <= 1; ds++) {
          final int neighborScan = scan + ds;
          if ((dm == 0 && ds == 0) || neighborScan < 0 || neighborScan >= scansPerModulation) {
            continue;
          }
          final int neighbor = visited.get(
              IonImagePoints.pixel(neighborModulation, neighborScan, scansPerModulation));
          if (neighbor < 0) {
            continue;
          }
          final int root = find(parent, neighbor);
          if (!roots.contains(root)) {
            roots.add(root);
          }
          final float neighborIntensity = intensity[channel.getInt(neighbor)];
          if (neighborIntensity > steepestIntensity) {
            steepestIntensity = neighborIntensity;
            steepest = neighbor;
          }
        }
      }
      visited.put(pixel, i);

      if (roots.isEmpty()) {
        // local maximum
        parent[i] = i;
        apex[i] = pointIntensity;
        continue;
      }

      // this signal is the saddle between all touching regions
      int top = roots.getInt(0);
      for (int r = 1; r < roots.size(); r++) {
        if (apex[roots.getInt(r)] > apex[top]) {
          top = roots.getInt(r);
        }
      }
      for (int r = 0; r < roots.size(); r++) {
        final int root = roots.getInt(r);
        // decision: regions that are too low or not prominent enough are part of the highest one
        if (root != top && (apex[root] < minHeight || apex[root] < minRatio * pointIntensity)) {
          parent[root] = top;
        }
      }
      parent[i] = find(parent, steepest);
    }

    // collect the regions, trimmed to the edge level of their apex
    final Int2ObjectLinkedOpenHashMap<IntArrayList> regions = new Int2ObjectLinkedOpenHashMap<>();
    for (int i = 0; i < n; i++) {
      if (parent[i] == DUPLICATE) {
        continue;
      }
      final int root = find(parent, i);
      final float regionApex = apex[root];
      final int point = channel.getInt(i);
      if (regionApex < minHeight || intensity[point] < edgeLevel * regionApex) {
        continue;
      }
      regions.computeIfAbsent(root, _ -> new IntArrayList()).add(point);
    }
    final List<int[]> result = new ArrayList<>(regions.size());
    for (final IntArrayList region : regions.values()) {
      result.add(region.toIntArray());
    }
    return result;
  }

  /**
   * Root of the region with path halving
   */
  private static int find(final int @NotNull [] parent, int index) {
    while (parent[index] != index) {
      parent[index] = parent[parent[index]];
      index = parent[index];
    }
    return index;
  }
}
