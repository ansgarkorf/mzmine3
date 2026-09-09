/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.github.mzmine.modules.visualization.dash_featurelistcomparison;

import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYZDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYSmallBlockRenderer;
import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.ComparisonEvidence.Pattern;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.ComparisonEvidence.RecurrencePoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.Layer;
import org.jfree.data.xy.XYDataset;

/**
 * Linked historical, intersection, and chemistry views.
 */
final class ComparisonEvidenceViewBuilder extends
    FxViewBuilder<FeatureListComparisonDashboardModel> {

  private static final int MAX_UPSET_PATTERNS = 12;
  private static final int MAX_CLASS_ROWS = 10;
  private Subscription recurrenceSelection = Subscription.EMPTY;
  private Subscription patternSelection = Subscription.EMPTY;

  ComparisonEvidenceViewBuilder(@NotNull final FeatureListComparisonDashboardModel model) {
    super(model);
  }

  @Override
  public @NotNull Region build() {
    final BorderPane pane = new BorderPane();
    model.analysisProperty().subscribe(analysis -> {
      recurrenceSelection.unsubscribe();
      if (analysis == null) {
        pane.setCenter(FxLabels.newLabel("Preparing feature priorities…"));
      } else {
        final Region prevalence = createPrevalenceNovelty(analysis);
        pane.setCenter(analysis.effectConcordance().available() ? FxSplitPanes.newSplitPane(0.56,
            Orientation.VERTICAL, prevalence, createEffectConcordance(analysis.effectConcordance()))
            : prevalence);
      }
    });
    return pane;
  }

  @NotNull Region buildPatterns() {
    final BorderPane pane = new BorderPane();
    model.analysisProperty().subscribe(analysis -> {
      patternSelection.unsubscribe();
      pane.setCenter(analysis == null ? FxLabels.newLabel("Preparing intersections…")
          : createAdaptiveIntersections(analysis.evidence(), analysis.selectedProjects()));
    });
    return pane;
  }

  @NotNull Region buildClassComparison() {
    final BorderPane pane = new BorderPane();
    model.analysisProperty().subscribe(analysis -> pane.setCenter(
        analysis == null ? FxLabels.newLabel("Preparing chemical classes…")
            : createClassComparison(analysis)));
    return pane;
  }

  private @NotNull Region createPrevalenceNovelty(
      @NotNull final ProjectSelectionAnalysis.Result analysis) {
    final ComparisonEvidence evidence = analysis.evidence();
    if (evidence.reference().isEmpty() || evidence.query().isEmpty()) {
      return FxLabels.newLabel(evidence.query().isEmpty()
          ? "Reference A is pinned. Select query projects outside A to compare."
          : "Pin reference projects as A and select query projects as B. References need usable MS2 spectra.");
    }
    if (evidence.points().isEmpty()) {
      return FxLabels.newLabel("No query components have sample-detection evidence. "
          + "Missing MS2 evidence is not plotted as zero.");
    }
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>(
        "Feature recurrence & query detection", "Eligible history with MS2 match",
        "Query sample detection");
    style(chart);
    chart.setStickyZeroRangeAxis(false);
    chart.setLegendItemsVisible(true);
    final Map<XYDataset, List<RecurrencePoint>> pointsByDataset = new IdentityHashMap<>();
    final Map<AnnotationTier, List<RecurrencePoint>> byTier = new EnumMap<>(AnnotationTier.class);
    for (final RecurrencePoint point : evidence.points()) {
      final SpectralComponent component = component(point.componentId());
      byTier.computeIfAbsent(AnnotationTier.of(component.annotationConsensus()),
          _ -> new ArrayList<>()).add(point);
    }
    for (final AnnotationTier tier : AnnotationTier.values()) {
      final List<RecurrencePoint> points = byTier.getOrDefault(tier, List.of());
      if (points.isEmpty()) {
        continue;
      }
      final StaticXYProvider provider = new StaticXYProvider(tier.label,
          points.stream().mapToDouble(RecurrencePoint::prevalence).toArray(),
          points.stream().mapToDouble(RecurrencePoint::detection).toArray(), null,
          points.stream().map(point -> pointTooltip(point, evidence)).toArray(String[]::new),
          tier.color(), 1d);
      final ColoredXYDataset dataset = new ColoredXYDataset(provider, RunOption.THIS_THREAD);
      chart.addDataset(dataset, new ColoredXYShapeRenderer());
      pointsByDataset.put(dataset, points);
    }
    percentageAxis((NumberAxis) chart.getXYPlot().getDomainAxis());
    percentageAxis((NumberAxis) chart.getXYPlot().getRangeAxis());
    chart.setOnMouseClicked(event -> {
      final var position = chart.getCursorPosition();
      if (position == null || position.getValueIndex() < 0) {
        return;
      }
      final List<RecurrencePoint> points = pointsByDataset.get(position.getDataset());
      if (points == null || position.getValueIndex() >= points.size()) {
        return;
      }
      final Set<Integer> selected = new LinkedHashSet<>();
      if (event != null && (event.isShiftDown() || event.isShortcutDown())
          && model.componentScopeProperty().get() != null) {
        selected.addAll(model.componentScopeProperty().get().componentIds());
      }
      selected.add(points.get(position.getValueIndex()).componentId());
      model.componentScopeProperty().set(new ComponentScope(selected,
          "%d selected component%s from feature priority map".formatted(selected.size(),
              selected.size() == 1 ? "" : "s")));
    });
    recurrenceSelection = model.selectedComponentProperty().subscribe(component -> {
      chart.getXYPlot().clearDomainMarkers();
      chart.getXYPlot().clearRangeMarkers();
      if (component == null) {
        return;
      }
      evidence.points().stream().filter(point -> point.componentId() == component.id()).findFirst()
          .ifPresent(point -> {
            final Color color = ConfigService.getDefaultColorPalette().getNeutralColorAWT();
            chart.getXYPlot()
                .addDomainMarker(new ValueMarker(point.prevalence(), color, new BasicStroke(1f)));
            chart.getXYPlot()
                .addRangeMarker(new ValueMarker(point.detection(), color, new BasicStroke(1f)));
          });
    });
    final Button filterVisible = FxButtons.createButton("Filter visible region",
        "Drag in the chart to zoom, then keep components inside the visible rectangle.",
        () -> filterVisiblePoints(chart, evidence));
    final Button resetAxes = FxButtons.createButton("Reset axes", () -> {
      chart.getXYPlot().getDomainAxis().setAutoRange(true);
      chart.getXYPlot().getRangeAxis().setAutoRange(true);
    });
    final var caption = FxLabels.newSmallLabel(
        "%d components · %d reference projects with usable MS2 · %d excluded without usable MS2. ".formatted(
            evidence.points().size(), evidence.reference().size(), evidence.excludedReferences())
            + "Left edge means unseen in eligible references, not proven chemical novelty. "
            + "Click; Shift/Cmd-click accumulates. Drag to zoom and filter the visible region.");
    caption.setWrapText(true);
    final BorderPane pane = new BorderPane(chart);
    pane.setBottom(FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true,
        FxLayout.newFlowPane(Insets.EMPTY, filterVisible, resetAxes), caption));
    return pane;
  }

  private void filterVisiblePoints(@NotNull final SimpleXYChart<StaticXYProvider> chart,
      @NotNull final ComparisonEvidence evidence) {
    final double minX = chart.getXYPlot().getDomainAxis().getLowerBound();
    final double maxX = chart.getXYPlot().getDomainAxis().getUpperBound();
    final double minY = chart.getXYPlot().getRangeAxis().getLowerBound();
    final double maxY = chart.getXYPlot().getRangeAxis().getUpperBound();
    final Set<Integer> selected = evidence.points().stream().filter(point -> {
      final double x = point.prevalence();
      return x >= minX && x <= maxX && point.detection() >= minY && point.detection() <= maxY;
    }).map(RecurrencePoint::componentId).collect(java.util.stream.Collectors.toSet());
    if (!selected.isEmpty()) {
      model.componentScopeProperty().set(new ComponentScope(selected,
          "%d components in the visible prevalence–detection region".formatted(selected.size())));
    }
  }

  private @NotNull String pointTooltip(@NotNull final RecurrencePoint point,
      @NotNull final ComparisonEvidence evidence) {
    final SpectralComponent component = component(point.componentId());
    final CompoundAnnotationConsensus annotation = component.annotationConsensus(
        model.comparisonProjects());
    return ("C%d · m/z %.4f\n%s · %s\nHistorical MS2 matches: %d/%d eligible projects\n"
        + "Query MS2 coverage: %d/%d projects\nOutside scope / unknown history: %d / %d\n"
        + "Mean query detection: %.0f%% among measured matches\n"
        + "No match is not proof of chemical absence.").formatted(component.id() + 1,
        component.precursorMz(), annotation.displayName(), annotation.sourceLabel(),
        point.referenceMatches(), point.eligibleReferences(), point.queryMatches(),
        evidence.query().size(), point.outsideScopeReferences(), point.unknownReferences(),
        point.detection() * 100d);
  }

  private @NotNull Region createEffectConcordance(@NotNull final EffectConcordance concordance) {
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>("Response concordance",
        "New-project effect · Hedges g", "Pooled historical effect · Hedges g");
    style(chart);
    chart.setStickyZeroRangeAxis(false);
    chart.setLegendItemsVisible(true);
    final Map<XYDataset, List<EffectConcordance.ComponentEffect>> effectsByDataset = new IdentityHashMap<>();
    for (final EffectTier tier : EffectTier.values()) {
      final List<EffectConcordance.ComponentEffect> effects = concordance.components().stream()
          .filter(tier::matches).toList();
      if (effects.isEmpty()) {
        continue;
      }
      final StaticXYProvider provider = new StaticXYProvider(tier.label,
          effects.stream().mapToDouble(effect -> effect.query().effect()).toArray(),
          effects.stream().mapToDouble(EffectConcordance.ComponentEffect::pooledHistorical)
              .toArray(), null,
          effects.stream().map(effect -> effectTooltip(effect, concordance)).toArray(String[]::new),
          tier.color(), 1d);
      final ColoredXYDataset dataset = new ColoredXYDataset(provider, RunOption.THIS_THREAD);
      chart.addDataset(dataset, new ColoredXYShapeRenderer());
      effectsByDataset.put(dataset, effects);
    }
    final double limit = concordance.components().stream().flatMapToDouble(
        effect -> java.util.stream.DoubleStream.of(Math.abs(effect.query().effect()),
            Math.abs(effect.pooledHistorical()))).max().orElse(1d) * 1.12d;
    chart.getXYPlot().getDomainAxis().setRange(-limit, limit);
    chart.getXYPlot().getRangeAxis().setRange(-limit, limit);
    final Color neutral = ConfigService.getDefaultColorPalette().getNeutralColorAWT();
    chart.getXYPlot().addDomainMarker(new ValueMarker(0d, neutral, new BasicStroke(1f)));
    chart.getXYPlot().addRangeMarker(new ValueMarker(0d, neutral, new BasicStroke(1f)));
    final StaticXYProvider diagonal = new StaticXYProvider("Agreement", new double[]{-limit, limit},
        new double[]{-limit, limit}, null, null, neutral, 1d);
    chart.addDataset(new ColoredXYDataset(diagonal, RunOption.THIS_THREAD),
        new ColoredXYLineRenderer());
    chart.setOnMouseClicked(_ -> {
      final var position = chart.getCursorPosition();
      if (position == null || position.getValueIndex() < 0) {
        return;
      }
      final List<EffectConcordance.ComponentEffect> effects = effectsByDataset.get(
          position.getDataset());
      if (effects != null && position.getValueIndex() < effects.size()) {
        final SpectralComponent component = effects.get(position.getValueIndex()).component();
        model.componentScopeProperty().set(new ComponentScope(Set.of(component.id()),
            "Component C%d selected from response concordance".formatted(component.id() + 1)));
      }
    });
    final BorderPane pane = new BorderPane(chart);
    final var caption = FxLabels.newSmallLabel(concordance.status()
        + ". Effects use independent-unit Hedges g; history uses a random-effects summary. "
        + "Opposite signs are contradictory; points near historical zero are query-specific.");
    caption.setWrapText(true);
    pane.setBottom(caption);
    return pane;
  }

  private @NotNull String effectTooltip(@NotNull final EffectConcordance.ComponentEffect effect,
      @NotNull final EffectConcordance concordance) {
    final var annotation = effect.component().annotationConsensus(model.comparisonProjects());
    return ("C%d · %s\n%s\nNew project: %+.2f [%.2f, %.2f]\n"
        + "Historical random effects: %+.2f [%.2f, %.2f]\n%d historical projects · I² %.0f%%").formatted(
        effect.component().id() + 1, annotation.displayName(), concordance.contrast().label(),
        effect.query().effect(), effect.query().lower95(), effect.query().upper95(),
        effect.pooledHistorical(), effect.pooledLower95(), effect.pooledUpper95(),
        effect.historical().size(), effect.iSquared() * 100d);
  }

  private @NotNull Region createAdaptiveIntersections(@NotNull final ComparisonEvidence evidence,
      @NotNull final List<Integer> projects) {
    if (evidence.patterns().isEmpty()) {
      return FxLabels.newLabel("No MS2 support intersections in this selection.");
    }
    if (projects.size() < 3) {
      return createOverlapBars(evidence);
    }
    if (projects.size() > 30) {
      return createCohortPrevalence(evidence, projects);
    }
    return createUpSet(evidence, projects);
  }

  private @NotNull Region createUpSet(@NotNull final ComparisonEvidence evidence,
      @NotNull final List<Integer> projects) {
    final int columns = Math.min(MAX_UPSET_PATTERNS, evidence.patterns().size());
    final List<Pattern> patterns = evidence.patterns().subList(0, columns);
    final double[] xValues = java.util.stream.IntStream.range(0, columns).mapToDouble(i -> i)
        .toArray();
    final String[] tips = java.util.stream.IntStream.range(0, columns).mapToObj(
        i -> "Intersection %d\n%d components\n%d/%d projects\nClick to filter linked views.".formatted(
            i + 1, patterns.get(i).components().size(), patterns.get(i).projects().size(),
            projects.size())).toArray(String[]::new);
    final SimpleXYChart<StaticXYProvider> bars = new SimpleXYChart<>("Selected-cohort UpSet",
        "Intersection", "Components");
    style(bars);
    bars.setLegendItemsVisible(false);
    final StaticXYProvider barProvider = new StaticXYProvider("Intersection size", xValues,
        patterns.stream().mapToDouble(pattern -> pattern.components().size()).toArray(), null, tips,
        ConfigService.getDefaultColorPalette().getPositiveColorAWT(), 0.78d);
    final ColoredXYDataset barDataset = new ColoredXYDataset(barProvider, RunOption.THIS_THREAD);
    bars.addDataset(barDataset, new ColoredXYBarRenderer(false));
    final String[] intersectionLabels = java.util.stream.IntStream.range(0, columns)
        .mapToObj(i -> "#" + (i + 1)).toArray(String[]::new);
    configureIntersectionAxis(bars, intersectionLabels);
    bars.setOnMouseClicked(_ -> selectPattern(bars, barDataset, patterns));

    final int rows = projects.size();
    final double[][] values = new double[rows][columns];
    final String[][] matrixTips = new String[rows][columns];
    for (int row = 0; row < rows; row++) {
      final int project = projects.get(row);
      for (int col = 0; col < columns; col++) {
        values[row][col] = patterns.get(col).projects().contains(project) ? 1d : 0d;
        matrixTips[row][col] = "%s · intersection #%d\n%s\n%d components".formatted(
            model.getResult().summaries().get(project).name(), col + 1,
            values[row][col] == 1d ? "MS2 match" : "Not in intersection",
            patterns.get(col).components().size());
      }
    }
    final ComparisonMatrixProvider provider = new ComparisonMatrixProvider("UpSet membership",
        values, matrixTips, columns, false);
    final SimpleXYChart<ComparisonMatrixProvider> membership = new SimpleXYChart<>("",
        "Intersection", "Selected projects");
    style(membership);
    membership.setLegendItemsVisible(false);
    final ColoredXYZDataset membershipDataset = new ColoredXYZDataset(provider, false,
        RunOption.THIS_THREAD);
    final ColoredXYSmallBlockRenderer membershipRenderer = new ColoredXYSmallBlockRenderer();
    membershipRenderer.setBlockWidth(0.75d);
    membershipRenderer.setBlockHeight(0.75d);
    membership.addDataset(membershipDataset, membershipRenderer);
    final SymbolAxis x = new SymbolAxis("Intersection", intersectionLabels);
    final SymbolAxis y = new SymbolAxis("Selected projects",
        projects.stream().map(project -> model.getResult().summaries().get(project).name())
            .toArray(String[]::new));
    x.setGridBandsVisible(false);
    y.setGridBandsVisible(false);
    y.setInverted(true);
    x.setRange(-0.5d, columns - 0.5d);
    y.setRange(-0.5d, rows - 0.5d);
    membership.getXYPlot().setDomainAxis(x);
    membership.getXYPlot().setRangeAxis(y);
    membership.getXYPlot().setDomainGridlinesVisible(false);
    membership.getXYPlot().setRangeGridlinesVisible(false);
    membership.setOnMouseClicked(_ -> selectPattern(membership, membershipDataset, patterns));
    patternSelection = model.componentScopeProperty().subscribe(scope -> {
      bars.getXYPlot().clearDomainMarkers();
      membership.getXYPlot().clearDomainMarkers();
      if (scope == null) {
        return;
      }
      for (int col = 0; col < columns; col++) {
        if (patterns.get(col).components().equals(scope.componentIds())) {
          bars.getXYPlot().addDomainMarker(selectionMarker(col), Layer.FOREGROUND);
          membership.getXYPlot().addDomainMarker(selectionMarker(col), Layer.FOREGROUND);
        }
      }
    });
    bars.setMinHeight(180d);
    membership.setMinHeight(190d);
    final VBox charts = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true, bars, membership);
    VBox.setVgrow(bars, javafx.scene.layout.Priority.ALWAYS);
    VBox.setVgrow(membership, javafx.scene.layout.Priority.ALWAYS);
    final BorderPane pane = new BorderPane(charts);
    final var caption = FxLabels.newSmallLabel(
        "Top %d/%d exact intersections. Bars show component counts; the matrix shows project membership. ".formatted(
            columns, evidence.patterns().size())
            + "These are MS2 co-observation patterns, not chemical families.");
    caption.setWrapText(true);
    pane.setBottom(caption);
    return pane;
  }

  private @NotNull Region createOverlapBars(@NotNull final ComparisonEvidence evidence) {
    final List<Pattern> patterns = evidence.patterns();
    final String[] labels = patterns.stream().map(
        pattern -> pattern.projects().isEmpty() ? "Neither" : pattern.projects().stream()
            .map(project -> model.getResult().summaries().get(project).name())
            .collect(java.util.stream.Collectors.joining(" + "))).toArray(String[]::new);
    return patternBarChart("MS2 overlap", labels, patterns,
        "Direct overlap summary for one or two selected projects.");
  }

  private @NotNull Region createCohortPrevalence(@NotNull final ComparisonEvidence evidence,
      @NotNull final List<Integer> projects) {
    final Map<Integer, Set<Integer>> componentsByPrevalence = new TreeMap<>();
    for (final Pattern pattern : evidence.patterns()) {
      componentsByPrevalence.computeIfAbsent(pattern.projects().size(), _ -> new LinkedHashSet<>())
          .addAll(pattern.components());
    }
    final List<Pattern> bins = componentsByPrevalence.entrySet().stream().map(
            entry -> new Pattern(java.util.Collections.nCopies(entry.getKey(), -1), entry.getValue()))
        .toList();
    final String[] labels = componentsByPrevalence.keySet().stream()
        .map(count -> "%d/%d".formatted(count, projects.size())).toArray(String[]::new);
    return patternBarChart("Selected-cohort prevalence", labels, bins,
        "More than 30 projects selected: prevalence replaces unreadable exact intersections.");
  }

  private @NotNull Region patternBarChart(@NotNull final String title,
      final String @NotNull [] labels, @NotNull final List<Pattern> patterns,
      @NotNull final String captionText) {
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>(title, "Project support",
        "Components");
    style(chart);
    chart.setLegendItemsVisible(false);
    final double[] x = java.util.stream.IntStream.range(0, patterns.size()).mapToDouble(i -> i)
        .toArray();
    final StaticXYProvider provider = new StaticXYProvider("Components", x,
        patterns.stream().mapToDouble(pattern -> pattern.components().size()).toArray(), null,
        java.util.stream.IntStream.range(0, patterns.size()).mapToObj(
            i -> "%s\n%d components\nClick to filter linked views.".formatted(labels[i],
                patterns.get(i).components().size())).toArray(String[]::new),
        ConfigService.getDefaultColorPalette().getPositiveColorAWT(), 0.8d);
    final ColoredXYDataset dataset = new ColoredXYDataset(provider, RunOption.THIS_THREAD);
    chart.addDataset(dataset, new ColoredXYBarRenderer(false));
    configureIntersectionAxis(chart, labels);
    chart.setOnMouseClicked(_ -> selectPattern(chart, dataset, patterns));
    final BorderPane pane = new BorderPane(chart);
    pane.setBottom(FxLabels.newSmallLabel(captionText));
    return pane;
  }

  private void selectPattern(@NotNull final SimpleXYChart<?> chart,
      @NotNull final XYDataset dataset, @NotNull final List<Pattern> patterns) {
    final var position = chart.getCursorPosition();
    if (position == null || position.getDataset() != dataset) {
      return;
    }
    final int column = (int) Math.round(position.getDomainValue());
    if (column >= 0 && column < patterns.size()) {
      final Pattern pattern = patterns.get(column);
      model.componentScopeProperty().set(new ComponentScope(pattern.components(),
          "%d components in an exact %d-project MS2 intersection".formatted(
              pattern.components().size(), pattern.projects().size())));
    }
  }

  private @NotNull Region createClassComparison(
      @NotNull final ProjectSelectionAnalysis.Result analysis) {
    final ComparisonEvidence evidence = analysis.evidence();
    if (evidence.reference().isEmpty() || evidence.query().isEmpty()) {
      return FxLabels.newLabel(
          "Select query and historical reference projects to compare classes.");
    }
    final Map<String, ClassCounts> counts = new TreeMap<>();
    int queryAnnotated = 0;
    int referenceAnnotated = 0;
    int queryTotal = 0;
    int referenceTotal = 0;
    for (final var difference : analysis.differences()) {
      final SpectralComponent component = difference.component();
      final boolean inQuery = evidence.query().stream().anyMatch(component::isPresent);
      final boolean inReference = evidence.reference().stream().anyMatch(component::isPresent);
      queryTotal += inQuery ? 1 : 0;
      referenceTotal += inReference ? 1 : 0;
      final var consensus = component.annotationConsensus(model.comparisonProjects());
      final String chemicalClass = consensus.annotation() == null || consensus.conflicting() ? null
          : consensus.annotation().chemicalClass();
      if (chemicalClass == null) {
        continue;
      }
      queryAnnotated += inQuery ? 1 : 0;
      referenceAnnotated += inReference ? 1 : 0;
      counts.computeIfAbsent(chemicalClass, _ -> new ClassCounts())
          .add(component.id(), inQuery, inReference);
    }
    final int queryDenominator = queryAnnotated;
    final int referenceDenominator = referenceAnnotated;
    final List<ClassEffect> effects = counts.entrySet().stream().map(
        entry -> ClassEffect.of(entry.getKey(), entry.getValue(), queryDenominator,
            referenceDenominator)).sorted(
        Comparator.comparingDouble((ClassEffect effect) -> Math.abs(effect.log2Odds())).reversed()
            .thenComparing(ClassEffect::name)).limit(MAX_CLASS_ROWS).toList();
    if (effects.isEmpty()) {
      return FxLabels.newLabel(
          "No consistent lipid/ClassyFire classes are available for this comparison.");
    }
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>("Chemical class enrichment",
        "Chemical class", "log2 odds · query / history");
    style(chart);
    chart.setLegendItemsVisible(false);
    final StaticXYProvider provider = new StaticXYProvider("Class enrichment",
        java.util.stream.IntStream.range(0, effects.size()).mapToDouble(i -> i).toArray(),
        effects.stream().mapToDouble(ClassEffect::log2Odds).toArray(), null,
        effects.stream().map(ClassEffect::tooltip).toArray(String[]::new),
        ConfigService.getDefaultColorPalette().getPositiveColorAWT(), 0.72d);
    final ColoredXYDataset dataset = new ColoredXYDataset(provider, RunOption.THIS_THREAD);
    chart.addDataset(dataset, new ColoredXYBarRenderer(false));
    final SymbolAxis x = new SymbolAxis("Chemical class",
        effects.stream().map(ClassEffect::shortName).toArray(String[]::new));
    x.setGridBandsVisible(false);
    x.setVerticalTickLabels(true);
    x.setRange(-0.5d, effects.size() - 0.5d);
    chart.getXYPlot().setDomainAxis(x);
    chart.getXYPlot().addRangeMarker(
        new ValueMarker(0d, ConfigService.getDefaultColorPalette().getNeutralColorAWT(),
            new BasicStroke(1f)));
    chart.setOnMouseClicked(_ -> {
      final var position = chart.getCursorPosition();
      if (position == null || position.getDataset() != dataset) {
        return;
      }
      final int index = (int) Math.round(position.getDomainValue());
      if (index >= 0 && index < effects.size()) {
        final ClassEffect effect = effects.get(index);
        model.componentScopeProperty().set(new ComponentScope(effect.components(),
            "%d components annotated as %s".formatted(effect.components().size(), effect.name())));
      }
    });
    final var caption = FxLabels.newSmallLabel(
        "%d/%d query and %d/%d historical components classified. Positive values favor query. ".formatted(
            queryAnnotated, queryTotal, referenceAnnotated, referenceTotal)
            + "Component-level descriptive odds with 0.5 correction; unclassified/conflicting annotations stay visible in coverage totals.");
    caption.setWrapText(true);
    final BorderPane pane = new BorderPane(chart);
    pane.setBottom(caption);
    return pane;
  }

  private @NotNull SpectralComponent component(final int componentId) {
    return model.getResult().components().get(componentId);
  }

  private static void configureIntersectionAxis(@NotNull final SimpleXYChart<?> chart,
      final String @NotNull [] labels) {
    final SymbolAxis axis = new SymbolAxis("Project support", labels);
    axis.setGridBandsVisible(false);
    axis.setVerticalTickLabels(labels.length > 6);
    axis.setRange(-0.5d, Math.max(0.5d, labels.length - 0.5d));
    chart.getXYPlot().setDomainAxis(axis);
  }

  private static @NotNull IntervalMarker selectionMarker(final int column) {
    final IntervalMarker marker = new IntervalMarker(column - 0.45d, column + 0.45d,
        new Color(0, 0, 0, 0));
    marker.setOutlinePaint(ConfigService.getDefaultColorPalette().getNeutralColorAWT());
    marker.setOutlineStroke(new BasicStroke(2f));
    return marker;
  }

  private static void percentageAxis(@NotNull final NumberAxis axis) {
    axis.setRange(-0.05d, 1.05d);
    axis.setNumberFormatOverride(ConfigService.getConfiguration().getPercentFormat());
  }

  private static void style(@NotNull final SimpleXYChart<?> chart) {
    chart.getXYPlot().setDomainCrosshairVisible(false);
    chart.getXYPlot().setRangeCrosshairVisible(false);
    chart.getChart().getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
    chart.setItemLabelsVisible(false);
  }

  private enum EffectTier {
    CONSISTENT("Consistent response", 1), QUERY_SPECIFIC("Query-specific response",
        2), CONTRADICTORY("Contradictory response", 0), WEAK("Weak / heterogeneous", 4);

    private final String label;
    private final int paletteIndex;

    EffectTier(@NotNull final String label, final int paletteIndex) {
      this.label = label;
      this.paletteIndex = paletteIndex;
    }

    private boolean matches(@NotNull final EffectConcordance.ComponentEffect effect) {
      final double query = effect.query().effect();
      final double historical = effect.pooledHistorical();
      return switch (this) {
        case CONSISTENT -> Math.abs(query) >= 0.5d && Math.abs(historical) >= 0.2d
            && Math.signum(query) == Math.signum(historical);
        case QUERY_SPECIFIC -> Math.abs(query) >= 0.5d && Math.abs(historical) < 0.2d;
        case CONTRADICTORY -> Math.abs(query) >= 0.2d && Math.abs(historical) >= 0.2d
            && Math.signum(query) != Math.signum(historical);
        case WEAK ->
            !(CONSISTENT.matches(effect) || QUERY_SPECIFIC.matches(effect) || CONTRADICTORY.matches(
                effect));
      };
    }

    private @NotNull Color color() {
      return this == WEAK ? ConfigService.getDefaultColorPalette().getNeutralColorAWT()
          : ConfigService.getDefaultColorPalette().getAWT(paletteIndex);
    }
  }

  private enum AnnotationTier {
    CONFLICT("Conflicting annotation", 0), SPECTRAL_LIBRARY("Spectral library", 1), LIPID(
        "Lipid annotation", 2), OTHER("Other annotation", 3), UNANNOTATED("Unannotated", 4);

    private final String label;
    private final int paletteIndex;

    AnnotationTier(@NotNull final String label, final int paletteIndex) {
      this.label = label;
      this.paletteIndex = paletteIndex;
    }

    private static @NotNull AnnotationTier of(
        @NotNull final CompoundAnnotationConsensus consensus) {
      if (consensus.conflicting()) {
        return CONFLICT;
      }
      if (consensus.annotation() == null) {
        return UNANNOTATED;
      }
      return switch (consensus.annotation().source()) {
        case SPECTRAL_LIBRARY -> SPECTRAL_LIBRARY;
        case LIPID -> LIPID;
        case COMPOUND_DATABASE, OTHER -> OTHER;
      };
    }

    private @NotNull Color color() {
      return this == UNANNOTATED ? ConfigService.getDefaultColorPalette().getNeutralColorAWT()
          : ConfigService.getDefaultColorPalette().getAWT(paletteIndex);
    }
  }

  private static final class ClassCounts {

    private final Set<Integer> components = new LinkedHashSet<>();
    private int query;
    private int reference;

    private void add(final int component, final boolean inQuery, final boolean inReference) {
      components.add(component);
      query += inQuery ? 1 : 0;
      reference += inReference ? 1 : 0;
    }
  }

  private record ClassEffect(@NotNull String name, int query, int queryTotal, int reference,
                             int referenceTotal, double log2Odds,
                             @NotNull Set<Integer> components) {

    private static @NotNull ClassEffect of(@NotNull final String name,
        @NotNull final ClassCounts counts, final int queryTotal, final int referenceTotal) {
      final double queryOther = Math.max(0, queryTotal - counts.query);
      final double referenceOther = Math.max(0, referenceTotal - counts.reference);
      final double oddsRatio =
          ((counts.query + 0.5d) * (referenceOther + 0.5d)) / ((queryOther + 0.5d) * (
              counts.reference + 0.5d));
      return new ClassEffect(name, counts.query, queryTotal, counts.reference, referenceTotal,
          Math.log(oddsRatio) / Math.log(2d), Set.copyOf(counts.components));
    }

    private @NotNull String shortName() {
      return name.length() <= 24 ? name : name.substring(0, 21) + "…";
    }

    private @NotNull String tooltip() {
      return "%s\nQuery: %d/%d classified components\nHistory: %d/%d classified components\nlog2 odds: %+.2f\nClick to filter linked views.".formatted(
          name, query, queryTotal, reference, referenceTotal, log2Odds);
    }
  }
}
