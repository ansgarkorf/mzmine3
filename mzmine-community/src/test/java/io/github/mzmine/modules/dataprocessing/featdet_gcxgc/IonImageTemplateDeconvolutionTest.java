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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Template deconvolution of synthetic GCxGC compounds
 */
class IonImageTemplateDeconvolutionTest {

  private static final int SCANS = 80;
  private static final int SHARED_CHANNEL = 99;

  @Test
  void nnlsRecoversContributions() {
    final double[] t1 = {0, 1, 4, 1, 0, 0, 0};
    final double[] t2 = {0, 0, 0, 1, 4, 1, 0};
    final double[] y = new double[7];
    for (int i = 0; i < 7; i++) {
      y[i] = 3 * t1[i] + 0.5 * t2[i];
    }
    final double[] a = IonImageTemplateDeconvolution.nnls(new double[][]{t1, t2}, y);
    Assertions.assertEquals(3, a[0], 1e-6);
    Assertions.assertEquals(0.5, a[1], 1e-6);
    // negative contributions are clipped
    final double[] b = IonImageTemplateDeconvolution.nnls(new double[][]{t1, t2},
        new double[]{0, 1, 4, 1, 0, 0, 0});
    Assertions.assertEquals(0, b[1], 1e-9);
  }

  @Test
  void sharedIonIsSplitBetweenCompounds() {
    // compound A at scan 30, compound B at scan 38 in the same modulations, 6 ions each, and one
    // shared ion that is the sum of both
    final List<IonImageFeature> features = new ArrayList<>();
    for (int c = 0; c < 6; c++) {
      features.add(blob(c, 1e5 * (1 - 0.1 * c), 30));
      features.add(blob(10 + c, 6e4 * (1 - 0.1 * c), 38));
    }
    features.add(sum(SHARED_CHANNEL, 2e4, 30, 1e4, 38));

    final List<List<IonImageFeature>> compounds = deconvolution(5).deconvolute(features,
        new AtomicLong());
    Assertions.assertEquals(2, compounds.size());
    for (final List<IonImageFeature> compound : compounds) {
      final IonImageFeature shared = compound.stream()
          .filter(f -> f.channel() == SHARED_CHANNEL).findFirst().orElseThrow();
      // each compound gets its share of the shared ion at its own apex
      final boolean isA = compound.getFirst().apexScan() == 30;
      Assertions.assertEquals(isA ? 30 : 38, shared.apexScan());
      Assertions.assertEquals(isA ? 2e4 : 1e4, shared.height(), 0.1 * (isA ? 2e4 : 1e4));
      Assertions.assertEquals(7, compound.size());
    }
  }

  @Test
  void noiseIsNotAssigned() {
    final List<IonImageFeature> features = new ArrayList<>();
    for (int c = 0; c < 6; c++) {
      features.add(blob(c, 1e5 * (1 - 0.1 * c), 30));
    }
    features.add(noise(50, 5e3));
    final List<List<IonImageFeature>> compounds = deconvolution(5).deconvolute(features,
        new AtomicLong());
    Assertions.assertEquals(1, compounds.size());
    Assertions.assertTrue(compounds.getFirst().stream().noneMatch(f -> f.channel() == 50));
  }

  @Test
  void compoundAcrossSegmentBorder() {
    // segments are 20 tolerances (2 modulations + 1) wide: the compound elutes at the border, the
    // apex of the model peak ends the first segment, the apexes of its ions start the second one
    // and the ions still join the compound
    final int border = IonImageTemplateDeconvolution.SEGMENT_TOLERANCES * 3;
    final List<IonImageFeature> features = new ArrayList<>();
    features.add(elutingAt(0, border - 0.6, 1e5));
    for (int c = 1; c < 6; c++) {
      features.add(elutingAt(c, border - 0.4, 8e4 * (1 - 0.1 * c)));
    }
    Assertions.assertEquals(border - 1, features.getFirst().apexModulation());
    Assertions.assertEquals(border, features.getLast().apexModulation());
    final List<List<IonImageFeature>> compounds = deconvolution(5).deconvolute(features,
        new AtomicLong());
    Assertions.assertEquals(1, compounds.size());
    Assertions.assertEquals(6, compounds.getFirst().size());
  }

  @Test
  void compoundsNeedMinimumSignals() {
    final List<IonImageFeature> features = new ArrayList<>();
    for (int c = 0; c < 3; c++) {
      features.add(blob(c, 1e5 * (1 - 0.1 * c), 30));
    }
    Assertions.assertTrue(deconvolution(5).deconvolute(features, new AtomicLong()).isEmpty());
    Assertions.assertEquals(1,
        deconvolution(3).deconvolute(features, new AtomicLong()).size());
  }

  private static @NotNull IonImageTemplateDeconvolution deconvolution(final int minSignals) {
    // tolerance: 2 modulations, 10 scans
    return new IonImageTemplateDeconvolution(SCANS, 2, 10, 0.8, minSignals, 3,
        GcxGcJobs.sequential());
  }

  private static double shape(final int m, final int s, final int apexModulation,
      final int apexScan) {
    return Math.exp(
        -0.5 * (Math.pow((m - apexModulation) / 1.0, 2) + Math.pow((s - apexScan) / 2.0, 2)));
  }

  private static @NotNull IonImageFeature blob(final int channel, final double height,
      final int apexScan) {
    return sum(channel, height, apexScan, 0, apexScan);
  }

  private static @NotNull IonImageFeature sum(final int channel, final double h1, final int s1,
      final double h2, final int s2) {
    return sumAt(channel, 4, h1, s1, h2, s2);
  }

  private static @NotNull IonImageFeature sumAt(final int channel, final int apexModulation,
      final double h1, final int s1, final double h2, final int s2) {
    final List<int[]> pixels = new ArrayList<>();
    final List<Float> values = new ArrayList<>();
    for (int m = apexModulation - 3; m <= apexModulation + 3; m++) {
      for (int s = 20; s < 50; s++) {
        final double v =
            h1 * shape(m, s, apexModulation, s1) + h2 * shape(m, s, apexModulation, s2);
        if (v > 50) {
          pixels.add(new int[]{m, s});
          values.add((float) v);
        }
      }
    }
    return feature(channel, pixels, values);
  }

  /**
   * @param center retention time between modulations
   */
  private static @NotNull IonImageFeature elutingAt(final int channel, final double center,
      final double height) {
    final List<int[]> pixels = new ArrayList<>();
    final List<Float> values = new ArrayList<>();
    final int first = (int) Math.floor(center) - 3;
    for (int m = first; m <= first + 7; m++) {
      for (int s = 20; s < 50; s++) {
        final double v = height * Math.exp(
            -0.5 * (Math.pow(m - center, 2) + Math.pow((s - 30) / 2.0, 2)));
        if (v > 50) {
          pixels.add(new int[]{m, s});
          values.add((float) v);
        }
      }
    }
    return feature(channel, pixels, values);
  }

  private static @NotNull IonImageFeature noise(final int channel, final double height) {
    final Random random = new Random(5);
    final List<int[]> pixels = new ArrayList<>();
    final List<Float> values = new ArrayList<>();
    for (int m = 2; m <= 6; m++) {
      for (int s = 25; s < 40; s++) {
        if (random.nextBoolean()) {
          pixels.add(new int[]{m, s});
          values.add((float) (height * random.nextDouble()));
        }
      }
    }
    return feature(channel, pixels, values);
  }

  private static @NotNull IonImageFeature feature(final int channel,
      @NotNull final List<int[]> pixels, @NotNull final List<Float> values) {
    final int n = pixels.size();
    final int[] m = new int[n];
    final int[] s = new int[n];
    final double[] mz = new double[n];
    final float[] v = new float[n];
    for (int i = 0; i < n; i++) {
      m[i] = pixels.get(i)[0];
      s[i] = pixels.get(i)[1];
      mz[i] = 100 + channel;
      v[i] = values.get(i);
    }
    return new IonImageFeature(channel, m, s, mz, v, v.clone());
  }
}
