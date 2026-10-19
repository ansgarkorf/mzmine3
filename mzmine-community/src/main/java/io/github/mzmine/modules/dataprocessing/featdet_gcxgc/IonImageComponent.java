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

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A compound in the template deconvolution of GCxGC ion images. All ions of a compound elute with
 * the same 2D shape, so the shape of its model peak (a clean, intense single peak of one m/z) is
 * the template for all other ions.
 */
final class IonImageComponent {

  private final @NotNull IonImageFeature model;
  private final long[] pixels;
  private final float[] template;
  private final Long2FloatOpenHashMap lookup;
  private final List<IonImageFeature> members = new ArrayList<>();

  IonImageComponent(@NotNull final IonImageFeature model, final int scansPerModulation) {
    this.model = model;
    final int n = model.size();
    pixels = new long[n];
    template = new float[n];
    lookup = new Long2FloatOpenHashMap(n);
    final float height = model.height();
    for (int i = 0; i < n; i++) {
      pixels[i] = model.pixel(i, scansPerModulation);
      template[i] = height > 0 ? model.corrected(i) / height : 0f;
      lookup.put(pixels[i], template[i]);
    }
    members.add(model);
  }

  @NotNull IonImageFeature model() {
    return model;
  }

  int apexModulation() {
    return model.apexModulation();
  }

  int apexScan() {
    return model.apexScan();
  }

  /**
   * @return the normalized template intensity at a pixel, 0 outside the template
   */
  float templateAt(final long pixel) {
    return lookup.get(pixel);
  }

  /**
   * @return true if the template has an intensity at any signal of the feature
   */
  boolean overlaps(@NotNull final IonImageFeature feature, final int scansPerModulation) {
    for (int i = 0; i < feature.size(); i++) {
      if (lookup.get(feature.pixel(i, scansPerModulation)) > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Pearson correlation of the corrected intensities of the feature and the template over the
   * union of their pixels, missing pixels count as zero. Both are sorted by pixel.
   */
  double correlation(@NotNull final IonImageFeature feature, final int scansPerModulation) {
    double sx = 0;
    double sy = 0;
    double sxx = 0;
    double syy = 0;
    double sxy = 0;
    int n = 0;
    int i = 0;
    int j = 0;
    while (i < feature.size() || j < pixels.length) {
      final long fp = i < feature.size() ? feature.pixel(i, scansPerModulation) : Long.MAX_VALUE;
      final long tp = j < pixels.length ? pixels[j] : Long.MAX_VALUE;
      double x = 0;
      double y = 0;
      if (fp <= tp) {
        x = feature.corrected(i++);
      }
      if (tp <= fp) {
        y = template[j++];
      }
      sx += x;
      sy += y;
      sxx += x * x;
      syy += y * y;
      sxy += x * y;
      n++;
    }
    final double cov = sxy - sx * sy / n;
    final double vx = sxx - sx * sx / n;
    final double vy = syy - sy * sy / n;
    return vx <= 0 || vy <= 0 ? 0 : cov / Math.sqrt(vx * vy);
  }

  /**
   * Members are added after all components were found, from one thread
   */
  void addMember(@NotNull final IonImageFeature feature) {
    members.add(feature);
  }

  /**
   * @return the model first, then the other ions
   */
  @NotNull List<IonImageFeature> members() {
    return members;
  }
}
