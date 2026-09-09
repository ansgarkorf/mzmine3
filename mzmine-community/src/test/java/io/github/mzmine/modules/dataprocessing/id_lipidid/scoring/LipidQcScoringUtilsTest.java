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

package io.github.mzmine.modules.dataprocessing.id_lipidid.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LipidQcScoringUtilsTest {

  @Test
  @SuppressWarnings("unchecked")
  void filtersUsingFrozenOverallQualityScores() {
    final MatchedLipid belowThreshold = matchWithOverallScore(0.59f);
    final MatchedLipid atThreshold = matchWithOverallScore(0.6f);
    final MatchedLipid aboveThreshold = matchWithOverallScore(0.8f);
    final MatchedLipid missingScore = matchWithOverallScore(null);
    final FeatureListRow row = mock(FeatureListRow.class);
    when(row.getLipidMatches()).thenReturn(
        List.of(belowThreshold, atThreshold, aboveThreshold, missingScore));

    LipidQcScoringUtils.filterLipidAnnotationsByOverallQualityScore(row, 0.6d);

    final ArgumentCaptor<List<MatchedLipid>> matchesCaptor = ArgumentCaptor.forClass(List.class);
    verify(row).setLipidAnnotations(matchesCaptor.capture());
    final List<MatchedLipid> retained = matchesCaptor.getValue();
    assertEquals(2, retained.size());
    assertSame(atThreshold, retained.get(0));
    assertSame(aboveThreshold, retained.get(1));
  }

  @Test
  void zeroThresholdDoesNotRewriteAnnotations() {
    final FeatureListRow row = mock(FeatureListRow.class);

    LipidQcScoringUtils.filterLipidAnnotationsByOverallQualityScore(row, 0d);

    verify(row, never()).setLipidAnnotations(anyList());
  }

  private static @NotNull MatchedLipid matchWithOverallScore(final @Nullable Float score) {
    final MatchedLipid match = mock(MatchedLipid.class);
    when(match.getOverallQualityScore()).thenReturn(score);
    return match;
  }
}
