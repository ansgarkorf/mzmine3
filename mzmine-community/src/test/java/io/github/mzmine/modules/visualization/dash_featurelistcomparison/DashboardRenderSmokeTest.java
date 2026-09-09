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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.gui.chartbasics.simplechart.PlotCursorPosition;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.main.ConfigService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in desktop render and linked-interaction smoke test; requires a graphical environment.
 */
@EnabledIfSystemProperty(named = "mzmine.test.dashboard.render", matches = "true")
class DashboardRenderSmokeTest {

  @Test
  void rendersAndLinksRecurrencePatternsAndFeatureInspection() throws Exception {
    final CompletableFuture<Void> startup = new CompletableFuture<>();
    try {
      Platform.startup(() -> startup.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      Platform.runLater(() -> startup.complete(null));
    }
    startup.get(15, TimeUnit.SECONDS);
    final State state = onFx(() -> {
      final List<Throwable> callbackErrors = new ArrayList<>();
      final var handler = Thread.currentThread().getUncaughtExceptionHandler();
      Thread.currentThread()
          .setUncaughtExceptionHandler((thread, error) -> callbackErrors.add(error));
      final double[][][] values = new double[3][12][4];
      for (int p = 0; p < 3; p++) {
        for (int c = 0; c < 12; c++) {
          values[p][c] = c >= 4 && c < 8 && p == 2 || c >= 8 && p < 2 ? null
              : new double[]{1d, 1d, 1d, c % 2 == 0 ? 0d : 1d};
        }
      }
      final var result = ProjectComparisonBenchmarkTest.fixture(values, true, true,
          new ComparisonContrast("condition", "response", "baseline"));
      final var model = new FeatureListComparisonDashboardModel(result);
      model.setSelectedProjects(List.of(2));
      model.referenceProjectsProperty().set(List.of(0, 1));
      final var analysis = ProjectSelectionAnalysis.calculate(result, model.comparisonProjects(),
          model.referenceProjectsProperty().get(), () -> false);
      assertTrue(analysis.effectConcordance().available());
      model.analysisStatusProperty().set(analysis.status());
      model.analysisProperty().set(analysis);
      final Region root = new FeatureListComparisonDashboardViewBuilder(model).build();
      final Scene scene = new Scene(root, 1700d, 1100d);
      ConfigService.getPreferences().getThemeConfig().apply(scene.getStylesheets());
      root.resize(1700d, 1100d);
      root.applyCss();
      root.layout();
      return new State(model, root, callbackErrors, handler);
    });
    try {
      awaitPulse();
      onFx(() -> {
        final ScrollPane scroll = assertInstanceOf(ScrollPane.class, state.root());
        assertTrue(scroll.isFitToWidth());
        assertFalse(scroll.isFitToHeight());
        final Region content = (Region) scroll.getContent();
        assertTrue(content.getHeight() > scroll.getViewportBounds().getHeight(),
            "The whole dashboard must scroll instead of squeezing the plot rows");
        final var umap = chart(state.root(), "Project UMAP · MS2 composition · Jaccard");
        chart(state.root(), "Response concordance");
        assertTrue(umap.getHeight() >= 300d, "The overview chart needs a readable height");
        for (int p = 0; p < state.model().getResult().size(); p++) {
          final double[] coordinate = state.model().getResult().projectUmapCoordinate(p);
          assertTrue(umap.getXYPlot().getRangeAxis().getRange().contains(coordinate[1]),
              "Negative UMAP coordinates must stay visible");
        }
        snapshot(state.root(), "dashboard-overview.png");
        return null;
      });
      onFx(() -> {
        final var model = state.model();
        final var recurrence = chart(state.root(), "Feature recurrence & query detection");
        final var dataset = recurrence.getXYPlot().getDataset(0);
        recurrence.cursorPositionProperty().set(
            new PlotCursorPosition(dataset.getXValue(0, 0), dataset.getYValue(0, 0), 0, dataset));
        recurrence.getOnMouseClicked().handle(null);
        assertNotNull(model.componentScopeProperty().get());
        assertTrue(model.visibleDifferences().size() < model.analysisProperty().get().differences()
            .size());
        assertTrue(model.componentScopeProperty().get().componentIds()
            .contains(model.getSelectedComponent().id()));
        state.root().applyCss();
        state.root().layout();
        return null;
      });
      onFx(() -> {
        snapshot(state.root(), "dashboard-recurrence-selection.png");
        final ScrollPane scroll = (ScrollPane) state.root();
        final double contentHeight = scroll.getContent().getLayoutBounds().getHeight();
        final double chartHeight = chart(scroll,
            "Project UMAP · MS2 composition · Jaccard").getHeight();
        scroll.resize(1700d, 700d);
        scroll.layout();
        assertEquals(contentHeight, scroll.getContent().getLayoutBounds().getHeight(), 1d,
            "Shorter windows must not compress the dashboard content");
        assertEquals(chartHeight,
            chart(scroll, "Project UMAP · MS2 composition · Jaccard").getHeight(), 1d);
        scroll.setVvalue(scroll.getVmax());
        return null;
      });
      awaitPulse();
      onFx(() -> {
        final ScrollPane scroll = (ScrollPane) state.root();
        final Region content = (Region) scroll.getContent();
        final var contentBottom = content.localToScene(0d, content.getHeight());
        assertTrue(contentBottom.getY() <= scroll.getHeight(),
            "The footer and evidence views must be reachable by scrolling to the bottom");
        snapshot(scroll, "dashboard-scrolled-evidence.png");
        return null;
      });
      onFx(() -> {
        final var model = state.model();
        final var analysis = model.analysisProperty().get();
        final var patterns = chart(state.root(), "Selected-cohort UpSet");
        final var patternDataset = patterns.getXYPlot().getDataset(0);
        patterns.cursorPositionProperty().set(new PlotCursorPosition(0d, 0d, 0, patternDataset));
        patterns.getOnMouseClicked().handle(null);
        assertEquals(analysis.evidence().patterns().getFirst().components(),
            model.componentScopeProperty().get().componentIds());
        model.componentScopeProperty().set(null);
        assertEquals(analysis.differences().size(), model.visibleDifferences().size());
        final var featureTable = descendants(state.root()).stream()
            .filter(node -> node instanceof TableView<?>).map(node -> (TableView<?>) node).filter(
                table -> !table.getItems().isEmpty() && table.getItems()
                    .getFirst() instanceof ProjectSelectionAnalysis.Difference).findFirst()
            .orElseThrow();
        featureTable.getSelectionModel().select(1);
        final var selected = (ProjectSelectionAnalysis.Difference) featureTable.getItems().get(1);
        assertEquals(selected.component(), model.getSelectedComponent());
        final var effectComponent = analysis.effectConcordance().components().getFirst()
            .component();
        model.setSelectedComponent(effectComponent);
        state.root().applyCss();
        state.root().layout();
        chart(state.root(), "Effect forest · response − baseline");
        return null;
      });
      awaitPulse();
      onFx(() -> {
        final var forest = chart(state.root(), "Effect forest · response − baseline");
        assertTrue(forest.getWidth() >= 180d && forest.getHeight() >= 100d,
            "The effect forest must retain an inspectable size");
        snapshot(forest, "dashboard-effect-forest.png");
        final var model = state.model();
        model.analysisProperty().set(null);
        assertNull(model.getSelectedComponent());
        assertTrue(state.errors().isEmpty(), state.errors().toString());
        return null;
      });
    } finally {
      onFx(() -> {
        Thread.currentThread().setUncaughtExceptionHandler(state.previousHandler());
        return null;
      });
    }
  }

  private static <T> @Nullable T onFx(@NotNull final java.util.concurrent.Callable<T> action)
      throws Exception {
    final CompletableFuture<T> completed = new CompletableFuture<>();
    Platform.runLater(() -> {
      try {
        completed.complete(action.call());
      } catch (Throwable error) {
        completed.completeExceptionally(error);
      }
    });
    return completed.get(40, TimeUnit.SECONDS);
  }

  private static void awaitPulse() throws Exception {
    final CompletableFuture<Void> pulsed = new CompletableFuture<>();
    Platform.runLater(() -> new javafx.animation.AnimationTimer() {
      private int frames;

      @Override
      public void handle(final long now) {
        if (++frames >= 3) {
          stop();
          pulsed.complete(null);
        }
      }
    }.start());
    pulsed.get(10, TimeUnit.SECONDS);
  }

  private record State(@NotNull FeatureListComparisonDashboardModel model, @NotNull Region root,
                       @NotNull List<Throwable> errors,
                       @org.jetbrains.annotations.Nullable Thread.UncaughtExceptionHandler previousHandler) {

  }

  private static void snapshot(@NotNull final Region root, @NotNull final String name)
      throws java.io.IOException {
    root.applyCss();
    root.layout();
    for (final Node node : descendants(root)) {
      if (node instanceof io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer viewer) {
        viewer.getCanvas().draw();
      }
    }
    final File output = new File("build", name);
    ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", output);
  }

  private static @NotNull SimpleXYChart<?> chart(@NotNull final Parent root,
      @NotNull final String title) {
    return descendants(root).stream().filter(node -> node instanceof SimpleXYChart<?>)
        .map(node -> (SimpleXYChart<?>) node)
        .filter(chart -> chart.getChart().getTitle().getText().equals(title)).findFirst()
        .orElseThrow();
  }

  private static @NotNull List<Node> descendants(@NotNull final Parent root) {
    final List<Node> nodes = new ArrayList<>();
    for (final Node child : root.getChildrenUnmodifiable()) {
      nodes.add(child);
      if (child instanceof Parent parent) {
        nodes.addAll(descendants(parent));
      }
    }
    return nodes;
  }
}
