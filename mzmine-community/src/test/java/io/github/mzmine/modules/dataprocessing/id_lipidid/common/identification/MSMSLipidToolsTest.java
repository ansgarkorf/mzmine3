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

package io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidFragment;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class MSMSLipidToolsTest {

  @Test
  void calculateMsMsScoreCountsObservedSignalOnce() {
    final DataPoint matchedSignal = new SimpleDataPoint(100d, 40d);
    final DataPoint[] massList = {matchedSignal, new SimpleDataPoint(150d, 60d)};
    final LipidFragment firstAnnotation = createFragment(matchedSignal,
        LipidFragmentationRuleType.HEADGROUP_FRAGMENT, "C");
    final LipidFragment secondAnnotation = createFragment(matchedSignal,
        LipidFragmentationRuleType.HEADGROUP_FRAGMENT_NL, "H2O");

    final double score = new MSMSLipidTools().calculateMsMsScore(massList,
        Set.of(firstAnnotation, secondAnnotation), 200d, new MZTolerance(0.01, 5));

    assertEquals(0.4d, score, 1e-12,
        "Two annotations of one observed signal must not count its intensity twice.");
  }

  private static @NotNull LipidFragment createFragment(final @NotNull DataPoint dataPoint,
      final @NotNull LipidFragmentationRuleType ruleType, final @NotNull String ruleFormula) {
    return new LipidFragment(ruleType, LipidAnnotationLevel.SPECIES_LEVEL,
        LipidFragmentationRuleRating.MAJOR, dataPoint.getMZ(), "C", dataPoint,
        LipidClasses.FREEFATTYACIDS, null, null, null, null, null, 0, ruleFormula);
  }
}
