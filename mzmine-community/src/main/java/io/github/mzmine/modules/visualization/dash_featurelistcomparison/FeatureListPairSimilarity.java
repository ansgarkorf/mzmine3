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

/**
 * One-to-one MS2 overlap between two feature lists.
 */
public record FeatureListPairSimilarity(double jaccardSimilarity, int matchedSpectra, int spectraA,
                                        int spectraB, double meanMatchedCosineSimilarity) {

  public FeatureListPairSimilarity {
    if (!Double.isFinite(jaccardSimilarity) || !Double.isFinite(meanMatchedCosineSimilarity)
        || jaccardSimilarity < 0d || jaccardSimilarity > 1d || matchedSpectra < 0 || spectraA < 0
        || spectraB < 0 || matchedSpectra > Math.min(spectraA, spectraB)
        || meanMatchedCosineSimilarity < 0d || meanMatchedCosineSimilarity > 1d) {
      throw new IllegalArgumentException("Invalid pair similarity values");
    }
  }

  public int unmatchedSpectraA() {
    return spectraA - matchedSpectra;
  }

  public int unmatchedSpectraB() {
    return spectraB - matchedSpectra;
  }
}
