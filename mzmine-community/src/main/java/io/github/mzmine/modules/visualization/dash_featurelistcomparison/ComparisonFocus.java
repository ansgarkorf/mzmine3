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

import org.jetbrains.annotations.NotNull;

/**
 * User questions that filter the same ranked component selection and linked charts.
 */
enum ComparisonFocus {
  ALL("All observed components"), HIGHER_A("Higher / more detected in A"), HIGHER_B(
      "Higher / more detected in B"), DETECTION("Detection differences"), NEW_TO_REFERENCE(
      "No historical MS2 match"), RECURRENT("Recurrent across compared projects"), MISSING_MS2(
      "Missing MS2 evidence"), SHARED_PATTERNS("Shared, frequent detection");

  private final String label;

  ComparisonFocus(@NotNull final String label) {
    this.label = label;
  }

  boolean accepts(@NotNull final ProjectSelectionAnalysis.Difference difference) {
    return switch (this) {
      case ALL -> true;
      case HIGHER_A -> difference.sharedByAll() && (difference.signedLog2Effect() > 0d
          || difference.detectionDelta() > 0d);
      case HIGHER_B -> difference.sharedByAll() && (difference.signedLog2Effect() < 0d
          || difference.detectionDelta() < 0d);
      case DETECTION ->
          difference.sharedByAll() && difference.prevalences().stream().allMatch(Double::isFinite)
              && difference.prevalences().stream().distinct().count() > 1;
      case MISSING_MS2 -> difference.missingEvidence();
      case NEW_TO_REFERENCE -> difference.direction().equals("No reference MS2 match");
      case RECURRENT -> difference.observedProjects() >= 2;
      case SHARED_PATTERNS -> difference.sharedByAll() && difference.prevalences().stream()
          .allMatch(value -> Double.isFinite(value) && value >= 0.8d);
    };
  }

  @Override
  public @NotNull String toString() {
    return label;
  }
}
