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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.CompoundAnnotationEvidence.Source;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.SpectralComponent.Member;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class CompoundAnnotationConsensusTest {

  @Test
  void analogMatchesRemainContextNotExactIdentityConsensus() {
    final CompoundAnnotationEvidence analog = new CompoundAnnotationEvidence("inchikey:ANALOG",
        "Related compound", Source.SPECTRAL_LIBRARY, "Analog search", null, null, null, 0.99f, true,
        true);
    final SpectralComponent component = component(member(0, 0, "A", analog));

    assertEquals(0, component.annotationConsensus().annotatedProjects());
    assertEquals(null, component.annotationConsensus().annotation());
    assertEquals(1, component.members().getFirst().feature().annotations().size());
  }

  @Test
  void countsAgreementByDistinctProjectAndCombinesEvidenceSources() {
    final CompoundAnnotationEvidence library = annotation("inchikey:SAME", "Library name",
        Source.SPECTRAL_LIBRARY, 0.96f);
    final CompoundAnnotationEvidence lipid = annotation("inchikey:SAME", "Lipid name", Source.LIPID,
        0.88f);
    final SpectralComponent component = component(member(0, 0, "A", library),
        member(1, 0, "A", library), member(2, 1, "B", lipid));

    final CompoundAnnotationConsensus consensus = component.annotationConsensus();

    assertEquals("Library name", consensus.displayName());
    assertEquals(2, consensus.supportingProjects());
    assertEquals(2, consensus.annotatedProjects());
    assertEquals("Mixed annotation evidence", consensus.sourceLabel());
    assertFalse(consensus.conflicting());
  }

  @Test
  void exposesConflictingPreferredIdentitiesAcrossProjects() {
    final SpectralComponent component = component(
        member(0, 0, "A", annotation("inchikey:A", "Compound A", Source.SPECTRAL_LIBRARY, 0.9f)),
        member(1, 1, "B", annotation("inchikey:B", "Compound B", Source.LIPID, 0.95f)));

    final CompoundAnnotationConsensus consensus = component.annotationConsensus();

    assertEquals("Compound A", consensus.displayName());
    assertEquals(1, consensus.supportingProjects());
    assertEquals(2, consensus.annotatedProjects());
    assertEquals(1, consensus.alternativeIdentities());
    assertTrue(consensus.conflicting());
    assertTrue(consensus.agreementLabel().contains("conflict"));
  }

  @Test
  void recalculatesConsensusForCurrentProjectSelection() {
    final SpectralComponent component = component(
        member(0, 0, "A", annotation("name:a", "Compound A", Source.SPECTRAL_LIBRARY, 0.9f)),
        member(1, 1, "B", annotation("name:b", "Compound B", Source.LIPID, 0.9f)));

    final CompoundAnnotationConsensus selected = component.annotationConsensus(List.of(1));

    assertEquals("Compound B", selected.displayName());
    assertEquals(1, selected.annotatedProjects());
    assertFalse(selected.conflicting());
  }

  private static @NotNull CompoundAnnotationEvidence annotation(@NotNull final String identity,
      @NotNull final String name, @NotNull final Source source, final float score) {
    return new CompoundAnnotationEvidence(identity, name, source, source.label(), null, "C10H20O",
        null, score, false, true);
  }

  private static @NotNull Member member(final int globalIndex, final int projectIndex,
      @NotNull final String projectName, @NotNull final CompoundAnnotationEvidence annotation) {
    final SpectralFeature feature = new SpectralFeature(globalIndex, projectIndex, projectName,
        PolarityType.POSITIVE, 1, globalIndex + 1, 500d,
        new io.github.mzmine.datamodel.DataPoint[0], new double[0], List.of(annotation));
    return new Member(feature, 1d);
  }

  private static @NotNull SpectralComponent component(@NotNull final Member... members) {
    final int maximumProject = java.util.Arrays.stream(members)
        .mapToInt(member -> member.feature().projectIndex()).max().orElse(0);
    final int[] counts = new int[maximumProject + 1];
    for (final Member member : members) {
      counts[member.feature().projectIndex()]++;
    }
    return new SpectralComponent(0, members[0].feature(), List.of(members), counts);
  }
}
