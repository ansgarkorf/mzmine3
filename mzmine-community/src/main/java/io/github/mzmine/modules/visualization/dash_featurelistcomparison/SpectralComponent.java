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

import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A globally assigned MS2 consensus component shared by every dashboard view.
 */
public final class SpectralComponent {

  private final int id;
  private final SpectralFeature representative;
  private final List<Member> members;
  private final int[] projectMemberCounts;
  private final CompoundAnnotationConsensus annotationConsensus;

  SpectralComponent(final int id, @NotNull final SpectralFeature representative,
      @NotNull final List<Member> members, @NotNull final int[] projectMemberCounts) {
    this.id = id;
    this.representative = representative;
    this.members = List.copyOf(members);
    this.projectMemberCounts = projectMemberCounts.clone();
    annotationConsensus = CompoundAnnotationConsensus.calculate(this.members, null);
  }

  public int id() {
    return id;
  }

  @NotNull SpectralFeature representative() {
    return representative;
  }

  public double precursorMz() {
    return representative.precursorMz();
  }

  public @NotNull PolarityType polarity() {
    return representative.polarity();
  }

  public @NotNull List<Member> members() {
    return members;
  }

  public int prevalence() {
    int prevalence = 0;
    for (final int count : projectMemberCounts) {
      if (count > 0) {
        prevalence++;
      }
    }
    return prevalence;
  }

  public boolean isPresent(final int projectIndex) {
    return projectMemberCounts[projectIndex] > 0;
  }

  public int memberCount(final int projectIndex) {
    return projectMemberCounts[projectIndex];
  }

  public int totalMemberCount() {
    return members.size();
  }

  public @NotNull CompoundAnnotationConsensus annotationConsensus() {
    return annotationConsensus;
  }

  public @NotNull CompoundAnnotationConsensus annotationConsensus(
      @NotNull final List<Integer> includedProjects) {
    return CompoundAnnotationConsensus.calculate(members, includedProjects);
  }

  public @Nullable Member firstMember(final int projectIndex) {
    return members.stream().filter(member -> member.feature().projectIndex() == projectIndex)
        .findFirst().orElse(null);
  }

  /**
   * A spectrum assigned to this component and its score to the fixed representative.
   */
  public record Member(@NotNull SpectralFeature feature, double representativeCosine) {

  }
}
