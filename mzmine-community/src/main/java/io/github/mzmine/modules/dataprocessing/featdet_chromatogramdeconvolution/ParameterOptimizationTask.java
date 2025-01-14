/*
 * Copyright (c) 2004-2024 The mzmine Development Team
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

package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution;/*
 * Copyright (c) 2004-2024 The mzmine Development Team
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

import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Task;
import org.jetbrains.annotations.NotNull;

public class ParameterOptimizationTask extends Task<ParameterSet> {

  private final ParameterSet baseParams;
  private final ModularFeatureList flist;

  public ParameterOptimizationTask(@NotNull ParameterSet baseParams,
      @NotNull ModularFeatureList flist) {
    this.baseParams = baseParams;
    this.flist = flist;
  }

  @Override
  protected ParameterSet call() throws Exception {
    // 1) Generate candidate ParameterSets (some grid or random search, etc.)
    List<ParameterSet> candidates = generateCandidateParams(baseParams);

    double bestScore = Double.NEGATIVE_INFINITY;
    ParameterSet bestParams = baseParams.cloneParameterSet();

    // 2) Evaluate each candidate
    for (int i = 0; i < candidates.size(); i++) {
      if (isCancelled()) {
        break;
      }
      ParameterSet candidate = candidates.get(i);

      // Evaluate (dummy example)
      double score = evaluateCandidate(candidate, flist);

      if (score > bestScore) {
        bestScore = score;
        bestParams = candidate.cloneParameterSet();
      }

      // Update progress
      updateProgress(i + 1, candidates.size());
    }

    // Return best result
    return bestParams;
  }

  /**
   * Example: Evaluate a single ParameterSet by resolving features and measuring quality from the 5
   * metrics you mentioned.
   */
  private double evaluateCandidate(ParameterSet candidate, ModularFeatureList flist) {
    // 1) apply the candidate
    // 2) run partial or full feature resolution
    // 3) compute asymmetry, zigzag penalty, R², kurtosis, tailing factor => combine into score
    // ...
    return Math.random(); // placeholder
  }

  private List<ParameterSet> generateCandidateParams(ParameterSet baseParams) {
    // create param combos, e.g. grid search or random
    return new ArrayList<>();
  }
}