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

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Raw-file sample labels retained for sample-level two-project comparisons.
 */
public record ProjectSampleMetadata(int projectIndex, @NotNull List<String> sampleNames,
                                    @NotNull List<String> independentUnits, int excludedSamples,
                                    @NotNull List<Map<String, String>> attributes) {

  public ProjectSampleMetadata {
    sampleNames = List.copyOf(sampleNames);
    independentUnits = List.copyOf(independentUnits);
    attributes = attributes.stream().map(Map::copyOf).toList();
    if (sampleNames.size() != independentUnits.size() || sampleNames.size() != attributes.size()) {
      throw new IllegalArgumentException(
          "Sample names, unit identifiers, and metadata must have equal length");
    }
  }

  public ProjectSampleMetadata(final int projectIndex, @NotNull final List<String> sampleNames,
      @NotNull final List<String> independentUnits, final int excludedSamples) {
    this(projectIndex, sampleNames, independentUnits, excludedSamples,
        Collections.nCopies(sampleNames.size(), Map.of()));
  }

  public ProjectSampleMetadata(final int projectIndex, @NotNull final List<String> sampleNames) {
    this(projectIndex, sampleNames, Collections.nCopies(sampleNames.size(), ""), 0,
        Collections.nCopies(sampleNames.size(), Map.of()));
  }

  @NotNull Set<String> attributeNames() {
    return attributes.stream().flatMap(values -> values.keySet().stream())
        .collect(Collectors.toCollection(java.util.TreeSet::new));
  }

  boolean hasIndependentUnits() {
    return !independentUnits.isEmpty() && independentUnits.stream().noneMatch(String::isBlank);
  }
}
