/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
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

import io.github.mzmine.datamodel.PolarityType;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Conservative, feature-specific method scope derived from the raw files selected for a project.
 */
public record ProjectMethodCoverage(int projectIndex, @NotNull List<Window> windows) {

  public ProjectMethodCoverage {
    windows = List.copyOf(windows);
  }

  static @NotNull ProjectMethodCoverage unknown(final int projectIndex) {
    return new ProjectMethodCoverage(projectIndex, List.of());
  }

  static @NotNull ProjectMethodCoverage unrestricted(final int projectIndex) {
    return new ProjectMethodCoverage(projectIndex,
        List.of(new Window(0d, Double.MAX_VALUE, Set.of())));
  }

  @NotNull EvidenceState state(@NotNull final SpectralComponent component,
      final boolean hasUsableMs2) {
    if (component.isPresent(projectIndex)) {
      return EvidenceState.MATCHED;
    }
    if (windows.isEmpty()) {
      return EvidenceState.UNKNOWN;
    }
    final boolean inScope = windows.stream()
        .anyMatch(window -> window.covers(component.precursorMz(), component.polarity()));
    if (!inScope) {
      return EvidenceState.OUTSIDE_METHOD_SCOPE;
    }
    return hasUsableMs2 ? EvidenceState.COMPATIBLE_NO_MATCH : EvidenceState.UNKNOWN;
  }

  public record Window(double lowerMz, double upperMz, @NotNull Set<PolarityType> polarities) {

    public Window {
      if (!Double.isFinite(lowerMz) || !Double.isFinite(upperMz) || lowerMz > upperMz) {
        throw new IllegalArgumentException("Invalid project mass range");
      }
      polarities = Set.copyOf(polarities);
    }

    boolean covers(final double precursorMz, @NotNull final PolarityType polarity) {
      final boolean mzCovered = precursorMz >= lowerMz && precursorMz <= upperMz;
      final boolean polarityCovered =
          !PolarityType.isDefined(polarity) || polarities.isEmpty() || polarities.stream()
              .anyMatch(projectPolarity -> !PolarityType.isDefined(projectPolarity))
              || polarities.contains(polarity);
      return mzCovered && polarityCovered;
    }
  }

  enum EvidenceState {
    MATCHED, COMPATIBLE_NO_MATCH, OUTSIDE_METHOD_SCOPE, UNKNOWN
  }
}
