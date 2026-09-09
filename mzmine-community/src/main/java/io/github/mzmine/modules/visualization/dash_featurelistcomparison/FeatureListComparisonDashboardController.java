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

import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

/**
 * Controller entry point for the feature-list comparison dashboard.
 */
public class FeatureListComparisonDashboardController extends
    FxController<FeatureListComparisonDashboardModel> {

  private final AtomicLong revision = new AtomicLong();
  private final Map<SelectionKey, ProjectSelectionAnalysis.Result> cache = new LinkedHashMap<>();

  public FeatureListComparisonDashboardController(@NotNull FeatureListComparisonResult result) {
    super(new FeatureListComparisonDashboardModel(result));
    model.selectedProjectsProperty().subscribe(_ -> scheduleComparison());
    model.referenceProjectsProperty().subscribe(_ -> scheduleComparison());
    model.randomForestProperty().subscribe(_ -> scheduleComparison());
    scheduleComparison();
  }

  private void scheduleComparison() {
    final long request = revision.incrementAndGet();
    final SelectionKey key = new SelectionKey(model.comparisonProjects(),
        List.copyOf(model.referenceProjectsProperty().get()), model.randomForestProperty().get());
    model.analysisProperty().set(null);
    model.analysisStatusProperty().set(
        key.randomForest() ? "Computing recurrence, effects and grouped RF validation…"
            : "Computing recurrence and shared MS2 patterns…");
    final ProjectSelectionAnalysis.Result previous = cache.get(key);
    if (previous != null) {
      model.analysisProperty().set(previous);
      model.analysisStatusProperty().set(previous.status());
      return;
    }
    onTaskThreadDelayed(() -> {
      try {
        final var comparison = ProjectSelectionAnalysis.calculate(model.getResult(), key.projects(),
            key.reference(), key.randomForest(),
            () -> revision.get() != request || Thread.currentThread().isInterrupted());
        onGuiThread(() -> {
          if (revision.get() != request) {
            return;
          }
          if (cache.size() >= 8) {
            cache.remove(cache.keySet().iterator().next());
          }
          cache.put(key, comparison);
          model.analysisStatusProperty().set(comparison.status());
          model.analysisProperty().set(comparison);
        });
      } catch (Exception error) {
        onGuiThread(() -> {
          if (revision.get() == request) {
            model.analysisStatusProperty().set("Comparison failed: " + error.getMessage());
          }
        });
      }
    }, "Feature-list comparison ranking");
  }

  @Override
  public void close() {
    revision.incrementAndGet();
    super.close();
  }

  private record SelectionKey(@NotNull List<Integer> projects, @NotNull List<Integer> reference,
                              boolean randomForest) {

  }

  @Override
  protected @NotNull FxViewBuilder<FeatureListComparisonDashboardModel> getViewBuilder() {
    return new FeatureListComparisonDashboardViewBuilder(model);
  }
}
