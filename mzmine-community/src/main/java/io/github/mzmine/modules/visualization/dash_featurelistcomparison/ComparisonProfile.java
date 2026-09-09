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


package io.github.mzmine.modules.visualization.dash_featurelistcomparison;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Relative abundance (ppm of retained MS2-row signal), separately from spectral support.
 */
final class ComparisonProfile {

  private final double[][] normalized;
  private final double[] detection;
  private final boolean[] spectralSupport;
  private final double[] medians;

  ComparisonProfile(@NotNull final List<SpectralComponent> components, final int project,
      final int samples) {
    normalized = new double[components.size()][samples];
    detection = new double[components.size()];
    spectralSupport = new boolean[components.size()];
    medians = new double[components.size()];
    for (int c = 0; c < components.size(); c++) {
      final SpectralComponent component = components.get(c);
      spectralSupport[c] = component.isPresent(project);
      final boolean[] detected = new boolean[samples];
      for (final var member : component.members()) {
        if (member.feature().projectIndex() != project) {
          continue;
        }
        final double[] values = member.feature().sampleAbundances();
        for (int sample = 0; sample < samples; sample++) {
          final double value = sample < values.length ? values[sample] : Double.NaN;
          normalized[c][sample] += value;
          detected[sample] |=
              sample < member.feature().sampleDetections().length && member.feature()
                  .sampleDetections()[sample];
        }
      }
      int observed = 0;
      for (final boolean present : detected) {
        if (present) {
          observed++;
        }
      }
      detection[c] = !spectralSupport[c] || samples == 0 ? Double.NaN : (double) observed / samples;
    }
    for (int sample = 0; sample < samples; sample++) {
      double total = 0d;
      for (int c = 0; c < components.size(); c++) {
        if (Double.isFinite(normalized[c][sample])) {
          total += normalized[c][sample];
        }
      }
      for (int c = 0; c < components.size(); c++) {
        normalized[c][sample] =
            total > 0d && spectralSupport[c] ? normalized[c][sample] / total * 1_000_000d
                : Double.NaN;
      }
    }
    for (int c = 0; c < components.size(); c++) {
      medians[c] = median(normalized[c]);
    }
  }

  double @NotNull [] values(final int component) {
    return normalized[component].clone();
  }

  double median(final int component) {
    return medians[component];
  }

  double detection(final int component) {
    return detection[component];
  }

  boolean supported(final int component) {
    return spectralSupport[component];
  }

  static double median(final double @NotNull [] values) {
    final double[] sorted = Arrays.stream(values).filter(Double::isFinite).sorted().toArray();
    if (sorted.length == 0) {
      return Double.NaN;
    }
    final int middle = sorted.length / 2;
    return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2d : sorted[middle];
  }
}
