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

package io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc;

/**
 * Running sums for a Pearson correlation without storing the values.
 */
final class PearsonCorrelationSums {

  private int n;
  private double sum1;
  private double sum2;
  private double sum11;
  private double sum22;
  private double sum12;

  void add(final double v1, final double v2) {
    n++;
    sum1 += v1;
    sum2 += v2;
    sum11 += v1 * v1;
    sum22 += v2 * v2;
    sum12 += v1 * v2;
  }

  /**
   * @return the Pearson correlation or 0 for fewer than 3 values or a flat profile
   */
  double correlation() {
    if (n < 3) {
      return 0d;
    }
    final double covariance = sum12 - sum1 * sum2 / n;
    final double variance1 = sum11 - sum1 * sum1 / n;
    final double variance2 = sum22 - sum2 * sum2 / n;
    if (variance1 <= 0 || variance2 <= 0) {
      return 0d;
    }
    return covariance / Math.sqrt(variance1 * variance2);
  }
}
