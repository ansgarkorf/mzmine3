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

import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.SpectralComponent.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-balanced consensus of the preferred annotations attached to component members.
 */
public record CompoundAnnotationConsensus(@Nullable CompoundAnnotationEvidence annotation,
                                          int supportingProjects, int annotatedProjects,
                                          int alternativeIdentities, boolean conflicting,
                                          @NotNull String sourceLabel) {

  private static final Comparator<CompoundAnnotationEvidence> EVIDENCE_ORDER = Comparator.comparing(
          CompoundAnnotationEvidence::preferred).reversed().thenComparing(
          Comparator.comparingInt(CompoundAnnotationEvidence::confidencePriority).reversed())
      .thenComparing(CompoundAnnotationEvidence::score,
          Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(CompoundAnnotationEvidence::displayName);

  static @NotNull CompoundAnnotationConsensus calculate(@NotNull final List<Member> members,
      @Nullable final Collection<Integer> includedProjects) {
    final Map<String, IdentityGroup> groups = new HashMap<>();
    final Set<Integer> annotatedProjectIndexes = new HashSet<>();
    for (final Member member : members) {
      final SpectralFeature feature = member.feature();
      if (includedProjects != null && !includedProjects.contains(feature.projectIndex())) {
        continue;
      }
      final CompoundAnnotationEvidence best = bestEvidence(feature.annotations());
      if (best == null) {
        continue;
      }
      annotatedProjectIndexes.add(feature.projectIndex());
      groups.computeIfAbsent(best.identityKey(), _ -> new IdentityGroup())
          .add(feature.projectIndex(), best);
    }
    if (groups.isEmpty()) {
      return new CompoundAnnotationConsensus(null, 0, 0, 0, false, "Unannotated");
    }

    final IdentityGroup consensusGroup = groups.values().stream().sorted(
        Comparator.comparingInt(IdentityGroup::projectCount).reversed()
            .thenComparing(IdentityGroup::bestEvidence, EVIDENCE_ORDER)
            .thenComparing(group -> group.bestEvidence().identityKey())).findFirst().orElseThrow();
    final CompoundAnnotationEvidence best = consensusGroup.bestEvidence();
    final int annotatedProjects = annotatedProjectIndexes.size();
    final int supportingProjects = consensusGroup.projectCount();
    return new CompoundAnnotationConsensus(best, supportingProjects, annotatedProjects,
        groups.size() - 1, annotatedProjects > 1 && supportingProjects < annotatedProjects,
        consensusGroup.sourceLabel());
  }

  public boolean annotated() {
    return annotation != null;
  }

  public @NotNull String displayName() {
    return annotation == null ? "Unannotated" : annotation.displayName();
  }

  public @Nullable String formula() {
    return annotation == null ? null : annotation.formula();
  }

  public @NotNull String agreementLabel() {
    if (annotation == null) {
      return "No annotation";
    }
    if (annotatedProjects == 1) {
      return "1 annotated project";
    }
    return "%d/%d annotated projects agree%s".formatted(supportingProjects, annotatedProjects,
        conflicting ? " · conflict" : "");
  }

  private static @Nullable CompoundAnnotationEvidence bestEvidence(
      @NotNull final List<CompoundAnnotationEvidence> annotations) {
    return annotations.stream().filter(annotation -> !annotation.analog()).min(EVIDENCE_ORDER)
        .orElse(null);
  }

  private static final class IdentityGroup {

    private final Set<Integer> projects = new HashSet<>();
    private final List<CompoundAnnotationEvidence> evidence = new ArrayList<>();

    private void add(final int project, @NotNull final CompoundAnnotationEvidence annotation) {
      projects.add(project);
      evidence.add(annotation);
    }

    private int projectCount() {
      return projects.size();
    }

    private @NotNull CompoundAnnotationEvidence bestEvidence() {
      return evidence.stream().min(EVIDENCE_ORDER).orElseThrow();
    }

    private @NotNull String sourceLabel() {
      final List<String> sources = evidence.stream().map(CompoundAnnotationEvidence::sourceLabel)
          .distinct().toList();
      return sources.size() == 1 ? sources.getFirst() : "Mixed annotation evidence";
    }
  }
}
