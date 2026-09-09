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

import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import io.github.mzmine.gui.chartbasics.simplechart.SimpleXYChart;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYZDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYBarRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYSmallBlockRenderer;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxButtons;
import io.github.mzmine.javafx.components.factories.FxSplitPanes;
import io.github.mzmine.javafx.components.factories.TableColumns;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.ProjectSelectionAnalysis.Difference;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.SpectralComponent.Member;
import io.github.mzmine.util.MirrorChartFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.HorizontalAlignment;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleEdge;

/**
 * Builds linked portfolio, component, embedding, and two-project comparison views.
 */
public class FeatureListComparisonDashboardViewBuilder extends
    FxViewBuilder<FeatureListComparisonDashboardModel> {

  private static final int MAX_VISIBLE_AXIS_LABELS = 30;
  private static final double OVERVIEW_HEIGHT = 720;
  private static final double DRILL_DOWN_HEIGHT = 1000;
  private Subscription matrixSelectionSubscription = Subscription.EMPTY;
  private Subscription tableSelectionSubscription = Subscription.EMPTY;
  private final ComparisonEvidenceViewBuilder evidenceViews;

  protected FeatureListComparisonDashboardViewBuilder(
      @NotNull final FeatureListComparisonDashboardModel model) {
    super(model);
    evidenceViews = new ComparisonEvidenceViewBuilder(model);
  }

  @Override
  public @NotNull Region build() {
    final FeatureListComparisonResult result = model.getResult();
    final BorderPane root = new BorderPane();
    root.getStyleClass().add("feature-comparison-dashboard");
    final Label detailLabel = FxLabels.newLabel("");
    detailLabel.setWrapText(true);
    detailLabel.getStyleClass().add("feature-comparison-footer");

    final int totalRows = result.summaries().stream().mapToInt(FeatureListSummary::totalRows).sum();
    final int usableSpectra = result.summaries().stream()
        .mapToInt(FeatureListSummary::usableSpectra).sum();
    final Label dashboardTitle = FxLabels.newLabel("Feature list comparison");
    dashboardTitle.getStyleClass().add("feature-comparison-dashboard-title");
    final Label overview = FxLabels.newLabel(
        "%d feature lists · %,d rows · %,d usable representative MS2 spectra · %,d global spectral components".formatted(
            result.size(), totalRows, usableSpectra, result.components().size()));
    overview.getStyleClass().add("feature-comparison-dashboard-metrics");
    final FeatureListComparisonSettings settings = result.settings();
    final Label method = FxLabels.newLabel(
        "Components use precursor %s; fragments use %s, ≥%d matches and cosine ≥%.2f. Retention time is not used.".formatted(
            settings.precursorMzTolerance(), settings.fragmentMzTolerance(),
            settings.minimumMatchedSignals(), settings.minimumCosineSimilarity()));
    method.setWrapText(true);
    method.getStyleClass().add("feature-comparison-secondary-text");
    final VBox header = FxLayout.newVBox(Pos.CENTER_LEFT, new Insets(8, 10, 10, 10), dashboardTitle,
        overview, method);
    final ComboBox<ProjectLandscape> perspective = new ComboBox<>();
    perspective.getItems().add(result.compositionLandscape());
    final ProjectLandscape abundance = result.abundanceLandscape();
    if (abundance != null && abundance.available()) {
      perspective.getItems().add(abundance);
    }
    perspective.valueProperty().bindBidirectional(model.landscapeProperty());
    final Label perspectiveHint = FxLabels.newSmallLabel(result.comparableAbundances()
        ? "Abundance view compares quantified shared components; coverage remains a separate measure."
        : "Mixed methods: composition view. Enable comparable abundances in input parameters when justified.");
    perspectiveHint.setWrapText(true);
    header.getChildren().add(FxLayout.newHBox(Insets.EMPTY, perspective, perspectiveHint));
    header.getStyleClass().add("feature-comparison-header");
    root.setTop(header);

    final Label selectionSummary = FxLabels.newLabel("");
    selectionSummary.setWrapText(true);
    selectionSummary.setAlignment(Pos.TOP_LEFT);
    selectionSummary.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
    selectionSummary.setMaxWidth(Double.MAX_VALUE);
    selectionSummary.getStyleClass().add("feature-comparison-selection-summary");
    final Label selectionHint = FxLabels.newLabel(
        "Click a heatmap cell for a pair. Shift/Cmd-click cells or UMAP points to extend the selection.");
    selectionHint.setWrapText(true);
    selectionHint.getStyleClass().add("feature-comparison-secondary-text");
    final BorderPane projectMapPane = new BorderPane();
    projectMapPane.setTop(
        FxLayout.newVBox(Pos.TOP_LEFT, new Insets(0, 0, 6, 0), true, selectionSummary,
            selectionHint));
    final BorderPane embedding = new BorderPane();
    final Map<ProjectLandscape, Region> embeddingViews = new java.util.HashMap<>();
    model.landscapeProperty().subscribe(landscape -> embedding.setCenter(
        embeddingViews.computeIfAbsent(landscape, _ -> createUmapChart())));
    projectMapPane.setCenter(embedding);
    final BorderPane landscapePane = new BorderPane();
    final Map<ProjectLandscape, Region> landscapeViews = new java.util.HashMap<>();
    model.landscapeProperty().subscribe(landscape -> landscapePane.setCenter(
        landscapeViews.computeIfAbsent(landscape,
            _ -> createSimilarityAndClusteringView(detailLabel))));
    final Region overviewPane = FxSplitPanes.newSplitPane(0.40, Orientation.HORIZONTAL,
        dashboardCard("01  OVERVIEW", "Similarity & clustering",
            "Start with the portfolio structure; select a cell to define the comparison.",
            landscapePane), FxSplitPanes.newSplitPane(0.42, Orientation.HORIZONTAL,
            dashboardCard("02  SELECT", "Project landscape",
                "Navigate projects; use the heatmaps to explain their differences.",
                projectMapPane), dashboardCard("03  PRIORITIZE", "Feature priorities",
                "Find query components unseen in comparable history, then filter the evidence.",
                evidenceViews.build())));
    final Region drillDownPane = FxSplitPanes.newSplitPane(0.68, Orientation.HORIZONTAL,
        dashboardCard("04  EXPLAIN", "Cohort patterns & evidence",
            "Select an intersection or chemical class, inspect method-aware evidence, then rank features.",
            createSelectionAnalysisView()),
        dashboardCard("05  VERIFY", "Compound & spectrum evidence",
            "Reconcile MZmine annotations and compare the underlying MS2 spectra.",
            createComponentExplorer()));
    // Keep enough room for the charts and evidence tables, even in a short window.
    // Horizontal and nested split panes still allow adjustment within each section.
    overviewPane.setPrefHeight(OVERVIEW_HEIGHT);
    overviewPane.setMinHeight(Region.USE_PREF_SIZE);
    drillDownPane.setPrefHeight(DRILL_DOWN_HEIGHT);
    drillDownPane.setMinHeight(Region.USE_PREF_SIZE);
    root.setCenter(FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true, overviewPane, drillDownPane));
    root.setBottom(FxLayout.newVBox(detailLabel));
    root.setMinHeight(Region.USE_PREF_SIZE);
    final ScrollPane scrollPane = FxLayout.newScrollPane(root, ScrollBarPolicy.AS_NEEDED,
        ScrollBarPolicy.AS_NEEDED);
    scrollPane.setFitToHeight(false);

    model.selectionProperty().subscribe(_ -> updateDetailLabel(detailLabel));
    model.landscapeProperty().subscribe(_ -> updateDetailLabel(detailLabel));
    model.selectedProjectsProperty().subscribe(_ -> updateSelectionSummary(selectionSummary));
    model.referenceProjectsProperty().subscribe(_ -> updateSelectionSummary(selectionSummary));
    updateDetailLabel(detailLabel);
    updateSelectionSummary(selectionSummary);
    return scrollPane;
  }

  private @NotNull Region dashboardCard(@NotNull final String step, @NotNull final String title,
      @NotNull final String subtitle, @NotNull final Region content) {
    final Label stepLabel = FxLabels.newLabel(step);
    stepLabel.getStyleClass().add("feature-comparison-card-step");
    final Label titleLabel = FxLabels.newLabel(title);
    titleLabel.getStyleClass().add("feature-comparison-card-title");
    final Label subtitleLabel = FxLabels.newLabel(subtitle);
    subtitleLabel.setWrapText(true);
    subtitleLabel.getStyleClass().add("feature-comparison-secondary-text");
    final VBox cardHeader = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, stepLabel, titleLabel,
        subtitleLabel);
    cardHeader.getStyleClass().add("feature-comparison-card-header");
    final BorderPane card = new BorderPane(content);
    card.setTop(cardHeader);
    card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    card.getStyleClass().addAll("quality-card", "feature-comparison-card");
    return card;
  }

  private @NotNull Region createSimilarityAndClusteringView(@NotNull final Label hoverDetail) {
    return FxSplitPanes.newSplitPane(0.75, Orientation.HORIZONTAL,
        createSimilarityChart(hoverDetail), createDendrogramChart());
  }

  private @NotNull SimpleXYChart<ProjectSimilarityMatrixProvider> createSimilarityChart(
      @NotNull final Label hoverDetail) {
    final FeatureListComparisonResult result = model.getResult();
    final ProjectLandscape landscape = model.landscapeProperty().get();
    final int[] projectOrder = landscape.order();
    final SimpleXYChart<ProjectSimilarityMatrixProvider> chart = new SimpleXYChart<>(
        landscape.label(), "Feature list", "Feature list");
    styleChart(chart);
    chart.setItemLabelsVisible(false);
    chart.setLegendItemsVisible(false);
    chart.setStickyZeroRangeAxis(false);

    final ProjectSimilarityMatrixProvider provider = new ProjectSimilarityMatrixProvider(result,
        landscape);
    final ColoredXYZDataset dataset = new ColoredXYZDataset(provider, false, RunOption.THIS_THREAD);
    final ColoredXYSmallBlockRenderer renderer = new ColoredXYSmallBlockRenderer();
    renderer.setBlockWidth(1d);
    renderer.setBlockHeight(1d);
    chart.addDataset(dataset, renderer);

    final String[] names = orderedProjectNames(result, projectOrder);
    final SymbolAxis domainAxis = new SymbolAxis("Feature list", names);
    final SymbolAxis rangeAxis = new SymbolAxis("Feature list", names);
    configureMatrixAxes(result.size(), domainAxis, rangeAxis);
    chart.getXYPlot().setDomainAxis(domainAxis);
    chart.getXYPlot().setRangeAxis(rangeAxis);
    chart.getXYPlot().setDomainGridlinesVisible(false);
    chart.getXYPlot().setRangeGridlinesVisible(false);
    addPaintScaleLegend(chart, provider.getPaintScale(), "Similarity", false);

    chart.cursorPositionProperty().subscribe(position -> {
      if (position == null) {
        return;
      }
      final int displayA = (int) Math.round(position.getRangeValue());
      final int displayB = (int) Math.round(position.getDomainValue());
      if (displayA >= 0 && displayA < result.size() && displayB >= 0 && displayB < result.size()) {
        updatePairDetailLabel(hoverDetail, projectOrder[displayA], projectOrder[displayB]);
      }
    });
    chart.setOnMouseClicked(event -> {
      final var position = chart.getCursorPosition();
      if (position == null) {
        return;
      }
      final int displayA = (int) Math.round(position.getRangeValue());
      final int displayB = (int) Math.round(position.getDomainValue());
      if (displayA < 0 || displayA >= result.size() || displayB < 0 || displayB >= result.size()) {
        return;
      }
      final int projectA = projectOrder[displayA];
      final int projectB = projectOrder[displayB];
      if (event.isShortcutDown() || event.isShiftDown()) {
        if (projectA == projectB) {
          model.toggleSelectedProject(projectA);
        } else {
          model.addSelectedProjects(List.of(projectA, projectB));
        }
      } else {
        model.setSelectedProjects(
            projectA == projectB ? List.of(projectA) : List.of(projectA, projectB));
      }
    });
    model.selectedProjectsProperty()
        .subscribe(_ -> updateMatrixSelectionMarkers(chart, projectOrder));
    model.selectionProperty().subscribe(_ -> updateMatrixSelectionMarkers(chart, projectOrder));
    model.referenceProjectsProperty()
        .subscribe(_ -> updateMatrixSelectionMarkers(chart, projectOrder));
    updateMatrixSelectionMarkers(chart, projectOrder);
    return chart;
  }

  private @NotNull SimpleXYChart<StaticXYProvider> createDendrogramChart() {
    final FeatureListComparisonResult result = model.getResult();
    final ProjectLandscape landscape = model.landscapeProperty().get();
    final List<HierarchicalProjectClustering.Segment> segments = landscape.dendrogram();
    final double[] x = new double[segments.size() * 3];
    final double[] y = new double[segments.size() * 3];
    for (int i = 0; i < segments.size(); i++) {
      final HierarchicalProjectClustering.Segment segment = segments.get(i);
      x[i * 3] = segment.x1();
      y[i * 3] = segment.y1();
      x[i * 3 + 1] = segment.x2();
      y[i * 3 + 1] = segment.y2();
      x[i * 3 + 2] = Double.NaN;
      y[i * 3 + 2] = Double.NaN;
    }
    final StaticXYProvider provider = new StaticXYProvider("Average linkage", x, y, null, null,
        ConfigService.getDefaultColorPalette().getNeutralColorAWT(), 1d);
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>("Project dendrogram",
        "Distance", "Feature list");
    styleChart(chart);
    chart.setItemLabelsVisible(false);
    chart.setLegendItemsVisible(false);
    chart.addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD),
        new ColoredXYLineRenderer());
    final SymbolAxis rangeAxis = new SymbolAxis("Feature list",
        orderedProjectNames(result, landscape.order()));
    rangeAxis.setGridBandsVisible(false);
    rangeAxis.setRange(-0.5d, result.size() - 0.5d);
    if (result.size() > MAX_VISIBLE_AXIS_LABELS) {
      rangeAxis.setTickLabelsVisible(false);
    }
    chart.getXYPlot().setRangeAxis(rangeAxis);
    final NumberAxis domainAxis = new NumberAxis("Distance");
    domainAxis.setRange(0d, 1d);
    chart.getXYPlot().setDomainAxis(domainAxis);
    return chart;
  }

  private @NotNull SimpleXYChart<StaticXYProvider> createUmapChart() {
    final FeatureListComparisonResult result = model.getResult();
    final ProjectLandscape landscape = model.landscapeProperty().get();
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>(
        "Project UMAP · " + landscape.label(), "UMAP 1", "UMAP 2");
    styleChart(chart);
    chart.setStickyZeroRangeAxis(false);
    chart.setLegendItemsVisible(false);
    chart.setItemLabelsVisible(result.size() <= MAX_VISIBLE_AXIS_LABELS);
    final List<ColoredXYShapeRenderer> renderers = new java.util.ArrayList<>();
    for (int project = 0; project < result.size(); project++) {
      final double[] coordinate = landscape.umap()[project];
      final String name = result.summaries().get(project).name();
      final StaticXYProvider provider = new StaticXYProvider(name, new double[]{coordinate[0]},
          new double[]{coordinate[1]}, new String[]{name}, new String[]{name},
          ConfigService.getDefaultColorPalette().getAWT(project), 1d);
      final ColoredXYShapeRenderer renderer = new ColoredXYShapeRenderer(false,
          ColoredXYShapeRenderer.defaultShape, true);
      renderers.add(renderer);
      chart.addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD), renderer);
    }
    chart.setOnMouseClicked(event -> {
      final var position = chart.getCursorPosition();
      if (position == null || result.size() == 0) {
        return;
      }
      int closest = 0;
      double closestDistance = Double.POSITIVE_INFINITY;
      for (int project = 0; project < result.size(); project++) {
        final double[] coordinate = landscape.umap()[project];
        final double dx = coordinate[0] - position.getDomainValue();
        final double dy = coordinate[1] - position.getRangeValue();
        final double distance = dx * dx + dy * dy;
        if (distance < closestDistance) {
          closestDistance = distance;
          closest = project;
        }
      }
      if (event.isShortcutDown() || event.isShiftDown()) {
        model.toggleSelectedProject(closest);
      } else {
        model.setSelectedProjects(List.of(closest));
      }
    });
    model.selectedProjectsProperty().subscribe(_ -> updateUmapSelection(renderers));
    model.referenceProjectsProperty().subscribe(_ -> updateUmapSelection(renderers));
    updateUmapSelection(renderers);
    return chart;
  }

  private void updateUmapSelection(@NotNull final List<ColoredXYShapeRenderer> renderers) {
    final List<Integer> selected = model.comparisonProjects();
    for (int project = 0; project < renderers.size(); project++) {
      final double radius = selected.contains(project) ? 6d : 3.5d;
      renderers.get(project)
          .setSeriesShape(0, new Ellipse2D.Double(-radius, -radius, radius * 2d, radius * 2d));
    }
  }


  private @NotNull Region createSelectionAnalysisView() {
    final BorderPane pane = new BorderPane();
    final Label status = FxLabels.newSmallLabel("");
    status.textProperty().bind(model.analysisStatusProperty());
    final Tooltip statusTip = new Tooltip();
    statusTip.textProperty().bind(model.analysisStatusProperty());
    status.setTooltip(statusTip);
    final ComboBox<ComparisonFocus> focus = new ComboBox<>(
        FXCollections.observableArrayList(ComparisonFocus.values()));
    focus.valueProperty().bindBidirectional(model.focusProperty());
    final Button reference = FxButtons.createButton("Pin selection as A",
        () -> model.referenceProjectsProperty().set(List.copyOf(model.getSelectedProjects())));
    final Button others = FxButtons.createButton("Other projects as A",
        () -> model.referenceProjectsProperty().set(
            java.util.stream.IntStream.range(0, model.getResult().size())
                .filter(p -> !model.getSelectedProjects().contains(p)).boxed().toList()));
    final Button clear = FxButtons.createButton("Unpin A",
        () -> model.referenceProjectsProperty().set(List.of()));
    final CheckBox rf = new CheckBox("Explore with RF");
    rf.selectedProperty().bindBidirectional(model.randomForestProperty());
    rf.setDisable(!model.getResult().comparableAbundances());
    rf.setTooltip(new Tooltip("Optional grouped prediction. Requires compatible abundances and "
        + "independent samples; does not adjust for analytical-method confounding."));
    final Button clearScope = FxButtons.createButton("Clear feature filter",
        () -> model.componentScopeProperty().set(null));
    clearScope.disableProperty().bind(model.componentScopeProperty().isNull());
    final Label scopeLabel = FxLabels.newBoldLabel("");
    scopeLabel.setWrapText(true);
    model.componentScopeProperty().subscribe(scope -> scopeLabel.setText(
        scope == null ? "All components for the current question" : scope.label()));
    pane.setTop(FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true,
        FxLayout.newFlowPane(Insets.EMPTY, focus, reference, others, clear, rf),
        FxLayout.newFlowPane(Insets.EMPTY, scopeLabel, clearScope), status));
    final BorderPane featurePane = new BorderPane();
    final BorderPane tablePane = new BorderPane();
    final Region classComparison = evidenceViews.buildClassComparison();
    final SplitPane tableAndClasses = FxSplitPanes.newSplitPane(0.72, Orientation.HORIZONTAL,
        tablePane, classComparison);
    pane.setCenter(FxSplitPanes.newSplitPane(0.60, Orientation.VERTICAL,
        FxSplitPanes.newSplitPane(0.34, Orientation.HORIZONTAL, evidenceViews.buildPatterns(),
            featurePane), tableAndClasses));
    final Runnable refresh = () -> {
      matrixSelectionSubscription.unsubscribe();
      tableSelectionSubscription.unsubscribe();
      final ProjectSelectionAnalysis.Result analysis = model.analysisProperty().get();
      if (analysis == null) {
        model.setSelectedComponent(null);
        featurePane.setCenter(FxLabels.newLabel("Preparing comparison…"));
        tablePane.setCenter(null);
        return;
      }
      final List<Difference> filtered = model.visibleDifferences();
      final ProjectSelectionAnalysis.Result visible = analysis.withDifferences(filtered);
      final boolean hasClasses = analysis.differences().stream().anyMatch(
          difference -> difference.annotationConsensus().annotation() != null
              && difference.annotationConsensus().annotation().chemicalClass() != null
              && !difference.annotationConsensus().conflicting());
      tableAndClasses.getItems()
          .setAll(hasClasses ? List.of(tablePane, classComparison) : List.of(tablePane));
      if (hasClasses) {
        tableAndClasses.setDividerPositions(0.72d);
      }
      if (filtered.isEmpty()) {
        model.setSelectedComponent(null);
        featurePane.setCenter(
            FxLabels.newLabel("No components match this question and feature filter."));
        tablePane.setCenter(null);
        return;
      }
      featurePane.setCenter(createFeatureMatrix(visible));
      tablePane.setCenter(createDifferenceTable(visible));
      if (filtered.stream().noneMatch(d -> d.component() == model.getSelectedComponent())) {
        model.setSelectedComponent(filtered.getFirst().component());
      }
    };
    model.analysisProperty().subscribe(_ -> refresh.run());
    model.focusProperty().subscribe(_ -> refresh.run());
    model.componentScopeProperty().subscribe(_ -> refresh.run());
    return pane;
  }

  private @NotNull Region createPrevalenceTree(@NotNull final SpectralComponent component) {
    final FeatureListComparisonResult result = model.getResult();
    final List<Integer> projects = model.comparisonProjects();
    final List<String> attributes = projects.stream()
        .flatMap(project -> result.sampleMetadata(project).attributeNames().stream()).distinct()
        .sorted().toList();
    if (attributes.isEmpty()) {
      final Label unavailable = FxLabels.newSmallLabel(
          "Prevalence tree unavailable: add sample-type, organism, tissue, or other project metadata.");
      unavailable.setWrapText(true);
      return unavailable;
    }
    final ComboBox<String> attribute = new ComboBox<>(
        FXCollections.observableArrayList(attributes));
    attribute.setMaxWidth(Double.MAX_VALUE);
    attribute.setValue(attributes.stream().filter(
        name -> name.equalsIgnoreCase("mzmine_sample_type") || name.toLowerCase()
            .contains("sample type")).findFirst().orElse(attributes.getFirst()));
    final BorderPane treePane = new BorderPane();
    final Label caption = FxLabels.newSmallLabel("");
    caption.setWrapText(true);
    final Runnable refresh = () -> {
      final OntologyNode rootNode = new OntologyNode("All eligible samples");
      int outside = 0;
      int unknown = 0;
      final String selectedAttribute = attribute.getValue();
      for (final int project : projects) {
        final ProjectSampleMetadata metadata = result.sampleMetadata(project);
        final var state = result.methodCoverage(project)
            .state(component, result.summaries().get(project).usableSpectra() > 0);
        if (state == ProjectMethodCoverage.EvidenceState.OUTSIDE_METHOD_SCOPE) {
          outside += metadata.sampleNames().size();
          continue;
        }
        if (state == ProjectMethodCoverage.EvidenceState.UNKNOWN) {
          unknown += metadata.sampleNames().size();
          continue;
        }
        for (int sample = 0; sample < metadata.sampleNames().size(); sample++) {
          final int sampleIndex = sample;
          final boolean detected = component.members().stream()
              .filter(member -> member.feature().projectIndex() == project).anyMatch(
                  member -> sampleIndex < member.feature().sampleDetections().length
                      && member.feature().sampleDetections()[sampleIndex]);
          final String value = metadata.attributes().get(sample)
              .getOrDefault(selectedAttribute, "Unspecified");
          final List<String> path = java.util.Arrays.stream(value.split("\\s*(?:>|/|::)\\s*"))
              .filter(part -> !part.isBlank()).toList();
          rootNode.include(path.isEmpty() ? List.of("Unspecified") : path, detected);
        }
      }
      final TreeItem<String> rootItem = rootNode.toTreeItem();
      rootItem.setExpanded(true);
      final TreeView<String> tree = new TreeView<>(rootItem);
      tree.setShowRoot(true);
      treePane.setCenter(tree);
      caption.setText(
          "MS2 evidence by %s · %d samples outside known method scope · %d with unknown coverage. ".formatted(
              selectedAttribute, outside, unknown)
              + "Nodes report detected / eligible samples; no match is not chemical absence.");
    };
    attribute.valueProperty().addListener((_, _, _) -> refresh.run());
    refresh.run();
    final BorderPane pane = new BorderPane(treePane);
    pane.setTop(FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true,
        FxLabels.newBoldLabel("Historical prevalence tree"), attribute));
    pane.setBottom(caption);
    return pane;
  }

  private @NotNull Region createEffectForest(
      @NotNull final EffectConcordance.ComponentEffect effect,
      @NotNull final EffectConcordance concordance) {
    final List<EffectConcordance.ProjectEffect> history = effect.historical();
    final int pooledRow = history.size();
    final int queryRow = history.size() + 1;
    final String[] labels = new String[history.size() + 2];
    for (int row = 0; row < history.size(); row++) {
      labels[row] = shortName(history.get(row).project());
    }
    labels[pooledRow] = "Historical pooled";
    labels[queryRow] = shortName(effect.query().project()) + " [query]";
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>(
        "Effect forest · " + concordance.contrast().label(), "Hedges g", "Project");
    styleChart(chart);
    chart.setStickyZeroRangeAxis(false);
    chart.setItemLabelsVisible(false);
    chart.setLegendItemsVisible(true);
    final Color historicalColor = ConfigService.getDefaultColorPalette().getNeutralColorAWT();
    final Color pooledColor = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
    final Color queryColor = ConfigService.getDefaultColorPalette().getAWT(2);
    final StaticXYProvider historyProvider = new StaticXYProvider("Historical projects",
        history.stream().mapToDouble(EffectConcordance.ProjectEffect::effect).toArray(),
        java.util.stream.IntStream.range(0, history.size()).mapToDouble(i -> i).toArray(), null,
        history.stream().map(projectEffect -> effectProjectTooltip(projectEffect, concordance))
            .toArray(String[]::new), historicalColor, 1d);
    chart.addDataset(new ColoredXYDataset(historyProvider, RunOption.THIS_THREAD),
        new ColoredXYShapeRenderer());
    final StaticXYProvider pooledProvider = new StaticXYProvider("Historical random effects",
        new double[]{effect.pooledHistorical()}, new double[]{pooledRow}, null, new String[]{
        "Pooled history\n%+.2f [%.2f, %.2f]\nI² %.0f%%".formatted(effect.pooledHistorical(),
            effect.pooledLower95(), effect.pooledUpper95(), effect.iSquared() * 100d)}, pooledColor,
        1d);
    chart.addDataset(new ColoredXYDataset(pooledProvider, RunOption.THIS_THREAD),
        new ColoredXYShapeRenderer());
    final StaticXYProvider queryProvider = new StaticXYProvider("New project",
        new double[]{effect.query().effect()}, new double[]{queryRow}, null,
        new String[]{effectProjectTooltip(effect.query(), concordance)}, queryColor, 1d);
    chart.addDataset(new ColoredXYDataset(queryProvider, RunOption.THIS_THREAD),
        new ColoredXYShapeRenderer());
    for (int row = 0; row < history.size(); row++) {
      final var estimate = history.get(row);
      chart.getXYPlot().addAnnotation(
          new XYLineAnnotation(estimate.lower95(), row, estimate.upper95(), row,
              new BasicStroke(1f), historicalColor));
    }
    chart.getXYPlot().addAnnotation(
        new XYLineAnnotation(effect.pooledLower95(), pooledRow, effect.pooledUpper95(), pooledRow,
            new BasicStroke(2f), pooledColor));
    chart.getXYPlot().addAnnotation(
        new XYLineAnnotation(effect.query().lower95(), queryRow, effect.query().upper95(), queryRow,
            new BasicStroke(2f), queryColor));
    chart.getXYPlot().addDomainMarker(new ValueMarker(0d, historicalColor, new BasicStroke(1f)));
    ((NumberAxis) chart.getXYPlot().getDomainAxis()).setNumberFormatOverride(numberFormat(2));
    final SymbolAxis y = new SymbolAxis("Project", labels);
    y.setGridBandsVisible(false);
    y.setInverted(true);
    y.setRange(-0.5d, queryRow + 0.5d);
    chart.getXYPlot().setRangeAxis(y);
    final BorderPane pane = new BorderPane(chart);
    pane.setBottom(FxLabels.newSmallLabel(
        "Project effects use independent-unit Hedges g; lines are approximate 95% intervals."));
    return pane;
  }

  private @NotNull String effectProjectTooltip(
      @NotNull final EffectConcordance.ProjectEffect effect,
      @NotNull final EffectConcordance concordance) {
    return "%s\n%s: %+.2f [%.2f, %.2f]\n%d vs %d independent units".formatted(
        model.getResult().summaries().get(effect.project()).name(), concordance.contrast().label(),
        effect.effect(), effect.lower95(), effect.upper95(), effect.numeratorUnits(),
        effect.denominatorUnits());
  }

  private @NotNull Region createFeatureMatrix(
      @NotNull final ProjectSelectionAnalysis.Result analysis) {
    final FeatureListComparisonResult result = model.getResult();
    final List<Integer> projectOrder = java.util.Arrays.stream(result.clusteredProjectOrder())
        .filter(analysis.selectedProjects()::contains).boxed().toList();
    final List<Difference> clustered = analysis.differences().stream().sorted(
            Comparator.comparingInt((Difference difference) -> (int) projectOrder.stream()
                .filter(difference.component()::isPresent).count()).reversed().thenComparing(
                difference -> projectOrder.stream()
                    .map(project -> difference.component().isPresent(project) ? "1" : "0")
                    .collect(java.util.stream.Collectors.joining())).thenComparing(
                Comparator.comparingDouble(Difference::directDifferenceScore).reversed())).limit(30)
        .toList();
    final int rows = clustered.size();
    final int columns = projectOrder.size();
    final double[][] values = new double[rows][columns];
    final String[][] tips = new String[rows][columns];
    final String[] rowNames = new String[rows];
    final String[] columnNames = projectOrder.stream().map(
        project -> shortName(project) + (analysis.groupA().contains(project) ? " [A]"
            : analysis.groupB().contains(project) ? " [B]" : "")).toArray(String[]::new);
    for (int row = 0; row < rows; row++) {
      final Difference d = clustered.get(row);
      rowNames[row] = "C" + (d.component().id() + 1) + " " + d.annotationConsensus().displayName();
      if (rowNames[row].length() > 34) {
        rowNames[row] = rowNames[row].substring(0, 31) + "…";
      }
      for (int col = 0; col < columns; col++) {
        final int project = projectOrder.get(col);
        final var state = result.methodCoverage(project)
            .state(d.component(), result.summaries().get(project).usableSpectra() > 0);
        values[row][col] = switch (state) {
          case UNKNOWN -> 0d;
          case OUTSIDE_METHOD_SCOPE -> 1d;
          case COMPATIBLE_NO_MATCH -> 2d;
          case MATCHED -> 3d;
        };
        final double detection = result.profile(project)
            .detection(result.components().indexOf(d.component()));
        tips[row][col] = "%s · C%d · %s\n%s\n%s".formatted(columnNames[col], d.component().id() + 1,
            d.annotationConsensus().displayName(), stateLabel(state),
            Double.isFinite(detection) ? "Detection in included samples: %.0f%%".formatted(
                100d * detection) : "No sample-level detection estimate");
      }
    }
    final Color match = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
    final org.jfree.chart.renderer.PaintScale stateScale = new org.jfree.chart.renderer.PaintScale() {
      @Override
      public double getLowerBound() {
        return 0d;
      }

      @Override
      public double getUpperBound() {
        return 3d;
      }

      @Override
      public @NotNull java.awt.Paint getPaint(final double value) {
        return switch ((int) Math.round(value)) {
          case 0 -> new Color(205, 205, 205);
          case 1 -> new Color(115, 115, 115);
          case 2 -> new Color(244, 244, 244);
          case 3 -> match;
          default -> new Color(205, 205, 205);
        };
      }
    };
    final ComparisonMatrixProvider provider = new ComparisonMatrixProvider("Selected components",
        values, tips, columns, stateScale);
    final SimpleXYChart<ComparisonMatrixProvider> chart = new SimpleXYChart<>("MS2 evidence matrix",
        "Clustered selected projects", "Top components · grouped by evidence pattern");
    styleChart(chart);
    chart.setLegendItemsVisible(true);
    chart.setItemLabelsVisible(false);
    final ColoredXYSmallBlockRenderer renderer = new ColoredXYSmallBlockRenderer();
    renderer.setBlockWidth(1d);
    renderer.setBlockHeight(1d);
    chart.addDataset(new ColoredXYZDataset(provider, false, RunOption.THIS_THREAD), renderer);
    final SymbolAxis x = new SymbolAxis("Selected projects", columnNames);
    final SymbolAxis y = new SymbolAxis("", rowNames);
    x.setGridBandsVisible(false);
    y.setGridBandsVisible(false);
    x.setVerticalTickLabels(true);
    x.setTickLabelsVisible(columns <= MAX_VISIBLE_AXIS_LABELS);
    x.setRange(-0.5d, Math.max(0.5d, columns - 0.5d));
    y.setRange(-0.5d, Math.max(0.5d, rows - 0.5d));
    y.setInverted(true);
    y.setTickLabelFont(y.getTickLabelFont().deriveFont(9f));
    chart.getXYPlot().setDomainAxis(x);
    chart.getXYPlot().setRangeAxis(y);
    chart.getXYPlot().setDomainGridlinesVisible(false);
    chart.getXYPlot().setRangeGridlinesVisible(false);
    final org.jfree.chart.LegendItemCollection legend = new org.jfree.chart.LegendItemCollection();
    legend.add(new org.jfree.chart.LegendItem("MS2 match", match));
    legend.add(new org.jfree.chart.LegendItem("Compatible · no match", new Color(244, 244, 244)));
    legend.add(new org.jfree.chart.LegendItem("Outside method scope", new Color(115, 115, 115)));
    legend.add(new org.jfree.chart.LegendItem("Unknown coverage", new Color(205, 205, 205)));
    chart.getXYPlot().setFixedLegendItems(legend);
    chart.setOnMouseClicked(_ -> {
      final var position = chart.getCursorPosition();
      if (position != null) {
        final int row = (int) Math.round(position.getRangeValue());
        if (row >= 0 && row < rows) {
          model.setSelectedComponent(clustered.get(row).component());
        }
      }
    });
    matrixSelectionSubscription.unsubscribe();
    matrixSelectionSubscription = model.selectedComponentProperty().subscribe(component -> {
      chart.getXYPlot().clearRangeMarkers();
      for (int row = 0; row < rows; row++) {
        if (clustered.get(row).component() == component) {
          final IntervalMarker outline = new IntervalMarker(row - 0.5d, row + 0.5d,
              new Color(0, 0, 0, 0));
          outline.setOutlinePaint(ConfigService.getDefaultColorPalette().getNeutralColorAWT());
          outline.setOutlineStroke(new java.awt.BasicStroke(2f));
          chart.getXYPlot().addRangeMarker(outline, Layer.FOREGROUND);
        }
      }
    });
    final BorderPane pane = new BorderPane(chart);
    pane.setBottom(FxLabels.newSmallLabel(
        "Four states prevent missing acquisition from becoming chemical absence. Projects follow the global clustering; rows group similar evidence patterns. Top 30 shown."));
    return pane;
  }

  private static @NotNull String stateLabel(
      @NotNull final ProjectMethodCoverage.EvidenceState state) {
    return switch (state) {
      case MATCHED -> "Representative MS2 match observed";
      case COMPATIBLE_NO_MATCH -> "Method-compatible; no representative MS2 match";
      case OUTSIDE_METHOD_SCOPE -> "Precursor m/z or polarity outside known raw-file scope";
      case UNKNOWN -> "Method/acquisition coverage unknown";
    };
  }

  private @NotNull Region createSampleDistribution(@NotNull final SpectralComponent component) {
    final FeatureListComparisonResult result = model.getResult();
    final int index = result.components().indexOf(component);
    final List<Integer> projects = model.comparisonProjects();
    final boolean abundances = result.comparableAbundances();
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>(
        abundances ? "Sample evidence · relative " + result.quantification()
            : "Sample evidence · detection", "Project",
        abundances ? "log2(1 + relative signal ppm)" : "Feature detection");
    styleChart(chart);
    chart.setItemLabelsVisible(false);
    chart.setLegendItemsVisible(false);
    int unavailable = 0;
    for (int col = 0; col < projects.size(); col++) {
      final int project = projects.get(col);
      final double[] measurements = result.profile(project).values(index);
      final var metadata = result.sampleMetadata(project);
      final List<Double> x = new java.util.ArrayList<>();
      final List<Double> y = new java.util.ArrayList<>();
      final List<String> tooltips = new java.util.ArrayList<>();
      for (int sample = 0; sample < metadata.sampleNames().size(); sample++) {
        if (!component.isPresent(project) || abundances && !Double.isFinite(measurements[sample])) {
          unavailable++;
          continue;
        }
        final int sampleIndex = sample;
        final boolean detected = component.members().stream()
            .filter(member -> member.feature().projectIndex() == project).anyMatch(
                member -> sampleIndex < member.feature().sampleDetections().length
                    && member.feature().sampleDetections()[sampleIndex]);
        final double value =
            abundances ? Math.log1p(measurements[sample]) / Math.log(2d) : detected ? 1d : 0d;
        x.add(col + ((sample * 0.61803398875d) % 1d - 0.5d) * 0.5d);
        y.add(value);
        tooltips.add(
            "%s · %s\n%s\nIndependent unit: %s".formatted(result.summaries().get(project).name(),
                metadata.sampleNames().get(sample),
                abundances ? "%.3f ppm".formatted(measurements[sample])
                    : detected ? "Feature detected" : "Feature not observed",
                metadata.independentUnits().get(sample).isBlank() ? "undefined"
                    : metadata.independentUnits().get(sample)));
      }
      final var provider = new StaticXYProvider(result.summaries().get(project).name(),
          x.stream().mapToDouble(Double::doubleValue).toArray(),
          y.stream().mapToDouble(Double::doubleValue).toArray(), null,
          tooltips.toArray(String[]::new), ConfigService.getDefaultColorPalette().getAWT(project),
          1d);
      chart.addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD),
          new ColoredXYShapeRenderer());
      if (!y.isEmpty()) {
        final double median = ComparisonProfile.median(
            y.stream().mapToDouble(Double::doubleValue).toArray());
        final var medianProvider = new StaticXYProvider("Median",
            new double[]{col - 0.25d, col + 0.25d}, new double[]{median, median}, null, null,
            ConfigService.getDefaultColorPalette().getAWT(project), 1d);
        chart.addDataset(new ColoredXYDataset(medianProvider, RunOption.THIS_THREAD),
            new ColoredXYLineRenderer());
      }
    }
    final SymbolAxis axis = new SymbolAxis("Project",
        projects.stream().map(this::shortName).toArray(String[]::new));
    axis.setGridBandsVisible(false);
    axis.setRange(-0.6d, Math.max(0.6d, projects.size() - 0.4d));
    chart.getXYPlot().setDomainAxis(axis);
    if (!abundances) {
      chart.getXYPlot().getRangeAxis().setRange(-0.15d, 1.15d);
    }
    final BorderPane pane = new BorderPane(chart);
    pane.setBottom(FxLabels.newSmallLabel(
        "%d unavailable sample measurements; dots are raw files, lines are medians.".formatted(
            unavailable)));
    return pane;
  }

  private @NotNull TableView<Difference> createDifferenceTable(
      @NotNull final ProjectSelectionAnalysis.Result analysis) {
    final TableView<Difference> table = new TableView<>(
        FXCollections.observableArrayList(analysis.differences()));
    final NumberFormat mzFormat = ConfigService.getConfiguration().getMZFormat();
    final NumberFormat scoreFormat = numberFormat(4);
    final TableColumn<Difference, Integer> componentColumn = new TableColumn<>("Component");
    componentColumn.setPrefWidth(85d);
    TableColumns.setMappedValueFactory(componentColumn, item -> item.component().id() + 1);
    final TableColumn<Difference, Double> mzColumn = new TableColumn<>("Precursor m/z");
    mzColumn.setPrefWidth(110d);
    TableColumns.setMappedFormattedFactories(mzColumn, mzFormat,
        item -> item.component().precursorMz());
    final TableColumn<Difference, String> annotationColumn = new TableColumn<>("Annotation");
    annotationColumn.setPrefWidth(180d);
    TableColumns.setMappedValueFactory(annotationColumn,
        item -> item.annotationConsensus().displayName());
    final TableColumn<Difference, String> sourceColumn = new TableColumn<>("Evidence");
    sourceColumn.setPrefWidth(130d);
    TableColumns.setMappedValueFactory(sourceColumn,
        item -> item.annotationConsensus().sourceLabel());
    final TableColumn<Difference, String> formulaColumn = new TableColumn<>("Formula");
    formulaColumn.setPrefWidth(90d);
    TableColumns.setMappedValueFactory(formulaColumn,
        item -> item.annotationConsensus().formula() == null ? ""
            : item.annotationConsensus().formula());
    final TableColumn<Difference, String> agreementColumn = new TableColumn<>("Agreement");
    agreementColumn.setPrefWidth(190d);
    TableColumns.setMappedValueFactory(agreementColumn,
        item -> item.annotationConsensus().agreementLabel());
    final TableColumn<Difference, String> sharingColumn = new TableColumn<>("Sharing");
    sharingColumn.setPrefWidth(115d);
    TableColumns.setMappedValueFactory(sharingColumn,
        item -> sharingLabel(item, analysis.selectedProjects().size()));
    final TableColumn<Difference, String> prevalenceColumn = new TableColumn<>("Detection");
    prevalenceColumn.setPrefWidth(220d);
    TableColumns.setMappedValueFactory(prevalenceColumn,
        item -> prevalenceLabel(item, analysis.selectedProjects()));
    final TableColumn<Difference, String> foldColumn = new TableColumn<>(
        analysis.groupB().isEmpty() ? "Log2 median range" : "Log2 median A / B");
    foldColumn.setPrefWidth(105d);
    TableColumns.setMappedValueFactory(foldColumn, d -> effectLabel(
        analysis.groupB().isEmpty() ? d.log2AbundanceRange() : d.signedLog2Effect()));
    final TableColumn<Difference, String> directionColumn = new TableColumn<>("Main difference");
    directionColumn.setPrefWidth(180d);
    TableColumns.setMappedValueFactory(directionColumn, Difference::direction);
    final TableColumn<Difference, String> deltaColumn = new TableColumn<>("Detection A − B");
    deltaColumn.setPrefWidth(125d);
    TableColumns.setMappedValueFactory(deltaColumn,
        d -> Double.isFinite(d.detectionDelta()) ? "%+.0f pp".formatted(d.detectionDelta() * 100d)
            : "Unavailable");
    final TableColumn<Difference, String> recurrenceColumn = new TableColumn<>(
        "Reference MS2 matches");
    recurrenceColumn.setPrefWidth(150d);
    TableColumns.setMappedValueFactory(recurrenceColumn,
        difference -> analysis.evidence().points().stream()
            .filter(point -> point.componentId() == difference.component().id()).findFirst().map(
                point -> "%d / %d eligible".formatted(point.referenceMatches(),
                    point.eligibleReferences())).orElse("—"));
    final TableColumn<Difference, String> stabilityColumn = new TableColumn<>(
        "RF uncertainty / stability");
    stabilityColumn.setPrefWidth(170d);
    TableColumns.setMappedValueFactory(stabilityColumn,
        d -> "SD %.3f · top 10 in %.0f%%".formatted(d.importanceDeviation(),
            d.rankStability() * 100d));
    final TableColumn<Difference, Double> rfColumn = new TableColumn<>("Held-out accuracy loss");
    TableColumns.setMappedFormattedFactories(rfColumn, scoreFormat,
        Difference::randomForestImportance);
    table.getColumns().add(componentColumn);
    table.getColumns().add(mzColumn);
    table.getColumns().add(annotationColumn);
    table.getColumns().add(directionColumn);
    if (analysis.abundanceEnabled()) {
      table.getColumns().add(foldColumn);
    }
    if (!analysis.groupB().isEmpty()) {
      table.getColumns().add(deltaColumn);
    }
    if (!analysis.evidence().reference().isEmpty()) {
      table.getColumns().add(recurrenceColumn);
    }
    table.getColumns().add(sourceColumn);
    table.getColumns().add(formulaColumn);
    table.getColumns().add(agreementColumn);
    table.getColumns().add(sharingColumn);
    table.getColumns().add(prevalenceColumn);
    if (analysis.validation().evaluated()) {
      table.getColumns().add(rfColumn);
    }
    if (analysis.validation().evaluated()) {
      table.getColumns().add(stabilityColumn);
    }
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.setRowFactory(_ -> new TableRow<>() {
      @Override
      protected void updateItem(@Nullable final Difference item, final boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().remove("feature-comparison-conflict-row");
        if (!empty && item != null && item.annotationConsensus().conflicting()) {
          getStyleClass().add("feature-comparison-conflict-row");
        }
      }
    });
    table.getSelectionModel().selectedItemProperty().addListener((_, _, item) -> {
      if (item != null) {
        model.setSelectedComponent(item.component());
      }
    });
    tableSelectionSubscription.unsubscribe();
    tableSelectionSubscription = model.selectedComponentProperty().subscribe(component -> {
      final Difference selected = table.getItems().stream()
          .filter(item -> item.component() == component).findFirst().orElse(null);
      if (table.getSelectionModel().getSelectedItem() != selected) {
        table.getSelectionModel().select(selected);
        if (selected != null) {
          table.scrollTo(selected);
        }
      }
    });
    return table;
  }

  private @NotNull String sharingLabel(@NotNull final Difference difference,
      final int selectedProjectCount) {
    if (difference.sharedByAll()) {
      return "MS2 matched in all";
    }
    if (difference.exclusiveToOne()) {
      return "MS2 observed in one";
    }
    return "%d of %d projects".formatted(difference.observedProjects(), selectedProjectCount);
  }

  private @NotNull String prevalenceLabel(@NotNull final Difference difference,
      @NotNull final List<Integer> projects) {
    if (projects.size() <= 3) {
      final NumberFormat percent = ConfigService.getConfiguration().getPercentFormat();
      final StringBuilder label = new StringBuilder();
      for (int i = 0; i < projects.size(); i++) {
        if (i > 0) {
          label.append(" · ");
        }
        final double detection = difference.prevalences().get(i);
        label.append(shortName(projects.get(i))).append(' ')
            .append(Double.isFinite(detection) ? percent.format(detection) : "MS2 unavailable");
      }
      return label.toString();
    }
    final double minimum = difference.prevalences().stream().mapToDouble(Double::doubleValue)
        .filter(Double::isFinite).min().orElse(0d);
    final double maximum = difference.prevalences().stream().mapToDouble(Double::doubleValue)
        .filter(Double::isFinite).max().orElse(0d);
    final NumberFormat percent = ConfigService.getConfiguration().getPercentFormat();
    return "%s–%s · see project heatmap".formatted(percent.format(minimum),
        percent.format(maximum));
  }

  private @NotNull Region createComponentExplorer() {
    final FeatureListComparisonResult result = model.getResult();
    final TableView<Member> memberTable = createMemberTable();
    final BorderPane detailPane = new BorderPane();
    final Label componentSummary = FxLabels.newBoldLabel("");
    componentSummary.setWrapText(true);
    final Label annotationSummary = FxLabels.newLabel("");
    annotationSummary.setWrapText(true);
    final Label missingSummary = FxLabels.newSmallLabel("");
    missingSummary.setWrapText(true);
    detailPane.setTop(
        FxLayout.newVBox(Pos.TOP_LEFT, new Insets(0, 0, 6, 0), componentSummary, annotationSummary,
            missingSummary));
    final BorderPane mirrorPane = new BorderPane();
    final BorderPane samplePane = new BorderPane();
    final BorderPane ontologyPane = new BorderPane();
    detailPane.setCenter(FxSplitPanes.newSplitPane(0.23, Orientation.VERTICAL, memberTable,
        FxSplitPanes.newSplitPane(0.52, Orientation.VERTICAL,
            FxSplitPanes.newSplitPane(0.58, Orientation.HORIZONTAL, samplePane, ontologyPane),
            mirrorPane)));

    final Runnable update = () -> {
      final SpectralComponent component = model.getSelectedComponent();
      if (component == null) {
        componentSummary.setText("Select a component");
        annotationSummary.setText("");
        memberTable.getItems().clear();
        samplePane.setCenter(null);
        ontologyPane.setCenter(null);
        mirrorPane.setCenter(null);
        return;
      }
      final List<Integer> selectedProjects = model.comparisonProjects();
      samplePane.setCenter(createSampleDistribution(component));
      final Region prevalenceTree = createPrevalenceTree(component);
      final ProjectSelectionAnalysis.Result currentAnalysis = model.analysisProperty().get();
      final EffectConcordance.ComponentEffect effect = currentAnalysis == null ? null
          : currentAnalysis.effectConcordance().component(component.id());
      ontologyPane.setCenter(effect == null ? prevalenceTree
          : FxSplitPanes.newSplitPane(0.44, Orientation.VERTICAL, prevalenceTree,
              createEffectForest(effect, currentAnalysis.effectConcordance())));
      final CompoundAnnotationConsensus annotation = component.annotationConsensus(
          selectedProjects);
      componentSummary.setText(
          "Component %d · precursor m/z %.4f · %d spectra in %d/%d projects".formatted(
              component.id() + 1, component.precursorMz(), component.totalMemberCount(),
              component.prevalence(), result.size()));
      annotationSummary.getStyleClass().removeAll("contrast-label", "warning-label", "bold-label");
      annotationSummary.getStyleClass().add("bold-label");
      if (annotation.annotated()) {
        final String formula = annotation.formula() == null ? "" : " · " + annotation.formula();
        final String alternatives = annotation.alternativeIdentities() == 0 ? ""
            : " · %d alternative identit%s".formatted(annotation.alternativeIdentities(),
                annotation.alternativeIdentities() == 1 ? "y" : "ies");
        annotationSummary.setText("Annotation  %s · %s%s · %s%s".formatted(annotation.displayName(),
            annotation.sourceLabel(), formula, annotation.agreementLabel(), alternatives));
        annotationSummary.getStyleClass()
            .add(annotation.conflicting() ? "warning-label" : "contrast-label");
      } else {
        annotationSummary.setText("Unannotated in the selected projects");
      }
      final String missingProjects = selectedProjects.stream()
          .filter(project -> component.firstMember(project) == null)
          .map(project -> result.summaries().get(project).name())
          .collect(java.util.stream.Collectors.joining(", "));
      final List<Member> orderedMembers = component.members().stream()
          .sorted(Comparator.comparingInt(member -> {
            final int selectedIndex = selectedProjects.indexOf(member.feature().projectIndex());
            return selectedIndex < 0 ? Integer.MAX_VALUE : selectedIndex;
          })).toList();
      memberTable.setItems(FXCollections.observableArrayList(orderedMembers));
      memberTable.getSelectionModel().clearSelection();
      for (final int project : selectedProjects) {
        final Member member = component.firstMember(project);
        if (member != null && memberTable.getSelectionModel().getSelectedItems().size() < 2) {
          memberTable.getSelectionModel().select(member);
        }
      }
      if (memberTable.getSelectionModel().getSelectedItems().isEmpty()
          && !orderedMembers.isEmpty()) {
        memberTable.getSelectionModel().select(orderedMembers.getFirst());
      }
      missingSummary.setText(
          missingProjects.isEmpty() ? "Select up to two members below for a mirror comparison."
              : "No matched MS2 evidence in: %s. This does not establish chemical absence.".formatted(
                  missingProjects));
      missingSummary.setVisible(true);
      updateMirror(mirrorPane, component,
          List.copyOf(memberTable.getSelectionModel().getSelectedItems()));
    };
    memberTable.getSelectionModel().getSelectedItems().addListener(
        (ListChangeListener<Member>) _ -> updateMirror(mirrorPane, model.getSelectedComponent(),
            List.copyOf(memberTable.getSelectionModel().getSelectedItems())));
    model.selectedComponentProperty().subscribe(_ -> update.run());
    model.selectedProjectsProperty().subscribe(_ -> update.run());
    model.referenceProjectsProperty().subscribe(_ -> update.run());
    if (model.getSelectedComponent() != null) {
      update.run();
    }
    return detailPane;
  }

  private @NotNull TableView<Member> createMemberTable() {
    final TableView<Member> table = new TableView<>();
    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    final TableColumn<Member, String> projectColumn = new TableColumn<>("Project");
    TableColumns.setMappedValueFactory(projectColumn, member -> member.feature().projectName());
    final TableColumn<Member, Integer> rowColumn = new TableColumn<>("Row ID");
    TableColumns.setMappedValueFactory(rowColumn, member -> member.feature().rowId());
    final TableColumn<Member, Double> mzColumn = new TableColumn<>("Precursor m/z");
    TableColumns.setMappedFormattedFactories(mzColumn,
        ConfigService.getConfiguration().getMZFormat(), member -> member.feature().precursorMz());
    final TableColumn<Member, String> annotationColumn = new TableColumn<>("Annotation");
    TableColumns.setMappedValueFactory(annotationColumn,
        FeatureListComparisonDashboardViewBuilder::memberAnnotationName);
    final TableColumn<Member, String> evidenceColumn = new TableColumn<>("Annotation evidence");
    TableColumns.setMappedValueFactory(evidenceColumn,
        FeatureListComparisonDashboardViewBuilder::memberAnnotationEvidence);
    final TableColumn<Member, Double> cosineColumn = new TableColumn<>("Cosine to representative");
    TableColumns.setMappedFormattedFactories(cosineColumn, numberFormat(3),
        Member::representativeCosine);
    table.getColumns().add(projectColumn);
    table.getColumns().add(rowColumn);
    table.getColumns().add(mzColumn);
    table.getColumns().add(annotationColumn);
    table.getColumns().add(evidenceColumn);
    table.getColumns().add(cosineColumn);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    return table;
  }

  private void updateMirror(@NotNull final BorderPane pane,
      @Nullable final SpectralComponent component, @NotNull final List<Member> selectedMembers) {
    if (component == null || selectedMembers.isEmpty()) {
      pane.setCenter(FxLabels.newLabel("Select a component member to inspect its spectrum."));
      return;
    }
    final Member representative = selectedMembers.getFirst();
    if (selectedMembers.size() == 1) {
      pane.setCenter(createSingleSpectrum(representative));
      return;
    }
    final Member selected =
        selectedMembers.size() > 1 ? selectedMembers.get(1) : component.members().getFirst();
    final double pairCosine = FeatureListComparisonCalculator.cosine(representative.feature(),
        selected.feature(), model.getResult().settings());
    final String representativeSuffix =
        representative.feature().globalIndex() == component.representative().globalIndex()
            ? " (component representative)" : "";
    final EChartViewer mirror = MirrorChartFactory.createMirrorChartViewer(
        "%s row %d · %s%s".formatted(representative.feature().projectName(),
            representative.feature().rowId(), memberAnnotationName(representative),
            representativeSuffix), representative.feature().precursorMz(), -1d,
        representative.feature().dataPoints(),
        "%s row %d · %s · cosine %.3f".formatted(selected.feature().projectName(),
            selected.feature().rowId(), memberAnnotationName(selected), pairCosine),
        selected.feature().precursorMz(), -1d, selected.feature().dataPoints(), false, false);
    if (mirror.getChart().getPlot() instanceof org.jfree.chart.plot.CombinedDomainXYPlot plot) {
      for (final var subplot : plot.getSubplots()) {
        if (subplot instanceof org.jfree.chart.plot.XYPlot xyPlot) {
          xyPlot.getRangeAxis().setLabel("Intensity");
        }
      }
    }
    mirror.getChart().removeLegend();
    pane.setCenter(mirror);
  }

  private @NotNull Region createSingleSpectrum(@NotNull final Member member) {
    final var points = member.feature().dataPoints();
    final double maximum = java.util.Arrays.stream(points)
        .mapToDouble(io.github.mzmine.datamodel.DataPoint::getIntensity).max().orElse(0d);
    final var provider = new StaticXYProvider("Selected MS2",
        java.util.Arrays.stream(points).mapToDouble(io.github.mzmine.datamodel.DataPoint::getMZ)
            .toArray(), java.util.Arrays.stream(points)
        .mapToDouble(p -> maximum > 0d ? p.getIntensity() / maximum * 100d : 0d).toArray(), null,
        null, ConfigService.getDefaultColorPalette().getPositiveColorAWT(), 0.1d);
    final SimpleXYChart<StaticXYProvider> chart = new SimpleXYChart<>("Selected MS2 spectrum",
        "m/z", "Relative intensity [%]");
    styleChart(chart);
    chart.setLegendItemsVisible(false);
    chart.setItemLabelsVisible(false);
    chart.addDataset(new ColoredXYDataset(provider, RunOption.THIS_THREAD),
        new ColoredXYBarRenderer(false));
    return chart;
  }

  private static @NotNull String memberAnnotationName(@NotNull final Member member) {
    final CompoundAnnotationEvidence annotation = bestMemberAnnotation(member);
    if (annotation == null) {
      return "Unannotated";
    }
    final String formula = annotation.formula() == null ? "" : " · " + annotation.formula();
    return annotation.displayName() + formula;
  }

  private static @NotNull String memberAnnotationEvidence(@NotNull final Member member) {
    final CompoundAnnotationEvidence annotation = bestMemberAnnotation(member);
    if (annotation == null) {
      return "";
    }
    final String provenance =
        annotation.database() == null ? annotation.method() : annotation.database();
    final String score =
        annotation.score() == null ? "" : " · score %.3f".formatted(annotation.score());
    final int alternatives = member.feature().annotations().size() - 1;
    final String alternativesLabel = alternatives == 0 ? ""
        : " · +%d alternative%s".formatted(alternatives, alternatives == 1 ? "" : "s");
    return "%s · %s%s%s".formatted(annotation.sourceLabel(), provenance, score, alternativesLabel);
  }

  private static @Nullable CompoundAnnotationEvidence bestMemberAnnotation(
      @NotNull final Member member) {
    return member.feature().annotations().stream().filter(CompoundAnnotationEvidence::preferred)
        .findFirst()
        .orElseGet(() -> member.feature().annotations().stream().findFirst().orElse(null));
  }

  private void updateSelectionSummary(@NotNull final Label label) {
    final FeatureListComparisonResult result = model.getResult();
    final List<Integer> selected = model.comparisonProjects();
    int union = 0;
    int sharedByAll = 0;
    int exclusive = 0;
    for (final SpectralComponent component : result.components()) {
      int observed = 0;
      for (final int project : selected) {
        if (component.isPresent(project)) {
          observed++;
        }
      }
      if (observed > 0) {
        union++;
      }
      if (observed == selected.size()) {
        sharedByAll++;
      }
      if (observed == 1) {
        exclusive++;
      }
    }
    final String names = selected.stream().limit(3)
        .map(index -> result.summaries().get(index).name())
        .collect(java.util.stream.Collectors.joining(", "));
    final String remaining = selected.size() > 3 ? " +%d more".formatted(selected.size() - 3) : "";
    final double averageCoverage = selected.stream().map(result.summaries()::get)
        .mapToDouble(FeatureListSummary::usableMs2Coverage).average().orElse(0d);
    final int samples = selected.stream().map(result::sampleMetadata)
        .mapToInt(metadata -> metadata.sampleNames().size()).sum();
    final int excluded = selected.stream().map(result::sampleMetadata)
        .mapToInt(ProjectSampleMetadata::excludedSamples).sum();
    label.setText(
        ("%d compared: %s%s\n%,d MS2 components · %,d matched in all · %,d observed in one · "
            + "%.1f%% mean usable-MS2 coverage · %d samples included / %d excluded by sample type").formatted(
            selected.size(), names, remaining, union, sharedByAll, exclusive,
            averageCoverage * 100d, samples, excluded));
  }

  private void updateMatrixSelectionMarkers(
      @NotNull final SimpleXYChart<ProjectSimilarityMatrixProvider> chart,
      final int @NotNull [] projectOrder) {
    chart.getXYPlot().clearDomainMarkers();
    chart.getXYPlot().clearRangeMarkers();
    chart.getXYPlot().clearAnnotations();
    final Color base = ConfigService.getDefaultColorPalette().getPositiveColorAWT();
    final Color transparent = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);
    final java.awt.BasicStroke outline = new java.awt.BasicStroke(1.8f);
    for (final int selectedProject : model.comparisonProjects()) {
      int position = -1;
      for (int i = 0; i < projectOrder.length; i++) {
        if (projectOrder[i] == selectedProject) {
          position = i;
          break;
        }
      }
      if (position < 0) {
        continue;
      }
      final IntervalMarker domain = new IntervalMarker(position - 0.5d, position + 0.5d,
          transparent);
      final IntervalMarker range = new IntervalMarker(position - 0.5d, position + 0.5d,
          transparent);
      domain.setOutlinePaint(base);
      range.setOutlinePaint(base);
      domain.setOutlineStroke(outline);
      range.setOutlineStroke(outline);
      chart.getXYPlot().addDomainMarker(domain, Layer.FOREGROUND);
      chart.getXYPlot().addRangeMarker(range, Layer.FOREGROUND);
    }
    final FeatureListComparisonSelection selection = model.getSelection();
    final int positionA = displayPosition(projectOrder, selection.indexA());
    final int positionB = displayPosition(projectOrder, selection.indexB());
    if (positionA >= 0 && positionB >= 0) {
      final java.awt.BasicStroke activeOutline = new java.awt.BasicStroke(2.8f);
      chart.getXYPlot().addAnnotation(
          new XYBoxAnnotation(positionB - 0.47d, positionA - 0.47d, positionB + 0.47d,
              positionA + 0.47d, activeOutline, base, null));
      if (positionA != positionB) {
        chart.getXYPlot().addAnnotation(
            new XYBoxAnnotation(positionA - 0.47d, positionB - 0.47d, positionA + 0.47d,
                positionB + 0.47d, activeOutline, base, null));
      }
    }
  }

  private static int displayPosition(final int @NotNull [] projectOrder, final int projectIndex) {
    for (int i = 0; i < projectOrder.length; i++) {
      if (projectOrder[i] == projectIndex) {
        return i;
      }
    }
    return -1;
  }

  private void updatePairDetailLabel(@NotNull final Label detailLabel, final int projectA,
      final int projectB) {
    final FeatureListComparisonResult result = model.getResult();
    final FeatureListSummary summaryA = result.summaries().get(projectA);
    final FeatureListSummary summaryB = result.summaries().get(projectB);
    final FeatureListPairSimilarity similarity = result.similarity(projectA, projectB);
    final ProjectLandscape landscape = model.landscapeProperty().get();
    if (landscape != result.compositionLandscape()) {
      final var quantitative = landscape.matrix()[projectA][projectB];
      detailLabel.setText(
          "%s ↔ %s: %.1f%% Bray–Curtis similarity on %d shared, quantified components".formatted(
              summaryA.name(), summaryB.name(), quantitative.jaccardSimilarity() * 100d,
              quantitative.matchedSpectra()));
      return;
    }
    if (projectA == projectB) {
      detailLabel.setText(
          "%s: %,d rows · %,d usable spectra (%.1f%%) · %,d missing mass lists".formatted(
              summaryA.name(), summaryA.totalRows(), summaryA.usableSpectra(),
              summaryA.usableMs2Coverage() * 100d, summaryA.missingMassLists()));
      return;
    }
    detailLabel.setText(("%s ↔ %s: %.1f%% Jaccard overlap · %d shared global components · "
        + "%.3f mean best matched cosine · %d/%d project-specific components").formatted(
        summaryA.name(), summaryB.name(), similarity.jaccardSimilarity() * 100d,
        similarity.matchedSpectra(), similarity.meanMatchedCosineSimilarity(),
        similarity.unmatchedSpectraA(), similarity.unmatchedSpectraB()));
  }

  private void updateDetailLabel(@NotNull final Label detailLabel) {
    final FeatureListComparisonSelection selection = model.getSelection();
    updatePairDetailLabel(detailLabel, selection.indexA(), selection.indexB());
  }

  private void configureMatrixAxes(final int size, @NotNull final SymbolAxis domainAxis,
      @NotNull final SymbolAxis rangeAxis) {
    domainAxis.setGridBandsVisible(false);
    rangeAxis.setGridBandsVisible(false);
    domainAxis.setVerticalTickLabels(true);
    rangeAxis.setInverted(true);
    if (size > MAX_VISIBLE_AXIS_LABELS) {
      domainAxis.setTickLabelsVisible(false);
      rangeAxis.setTickLabelsVisible(false);
    }
    domainAxis.setRange(-0.5d, size - 0.5d);
    rangeAxis.setRange(-0.5d, size - 0.5d);
  }

  private void addPaintScaleLegend(@NotNull final SimpleXYChart<?> chart,
      @NotNull final PaintScale paintScale, @NotNull final String label, final boolean binary) {
    final NumberAxis scaleAxis = new NumberAxis(label);
    scaleAxis.setRange(0d, 1d);
    scaleAxis.setNumberFormatOverride(ConfigService.getConfiguration().getPercentFormat());
    final var chartAxis = chart.getXYPlot().getDomainAxis();
    scaleAxis.setAxisLinePaint(chartAxis.getAxisLinePaint());
    scaleAxis.setTickMarkPaint(chartAxis.getTickMarkPaint());
    scaleAxis.setLabelFont(chartAxis.getLabelFont());
    scaleAxis.setLabelPaint(chartAxis.getLabelPaint());
    scaleAxis.setTickLabelFont(chartAxis.getTickLabelFont());
    scaleAxis.setTickLabelPaint(chartAxis.getTickLabelPaint());
    final PaintScaleLegend legend = new PaintScaleLegend(paintScale, scaleAxis);
    legend.setAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
    legend.setAxisOffset(5d);
    legend.setSubdivisionCount(binary ? 2 : 100);
    legend.setStripOutlineVisible(false);
    legend.setPadding(5d, 0d, 5d, 0d);
    legend.setPosition(RectangleEdge.RIGHT);
    legend.setBackgroundPaint(new Color(0, 0, 0, 0));
    chart.getChart().addSubtitle(legend);
  }

  private @NotNull String[] orderedProjectNames(@NotNull final FeatureListComparisonResult result,
      final int @NotNull [] order) {
    final String[] names = new String[order.length];
    for (int i = 0; i < order.length; i++) {
      names[i] = result.summaries().get(order[i]).name();
    }
    return names;
  }

  private @NotNull String shortName(final int projectIndex) {
    final String name = model.getResult().summaries().get(projectIndex).name();
    return name.length() <= 16 ? name : name.substring(0, 13) + "…";
  }

  private static void styleChart(@NotNull final SimpleXYChart<?> chart) {
    chart.getXYPlot().setDomainCrosshairVisible(false);
    chart.getXYPlot().setRangeCrosshairVisible(false);
    chart.getChart().getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
    chart.getChart().getTitle().setMargin(0d, 0d, 5d, 0d);
  }

  private static @NotNull String effectLabel(final double value) {
    if (Double.isNaN(value)) {
      return "Unavailable";
    }
    if (Double.isInfinite(value)) {
      return value > 0d ? "B/min median zero" : "A median zero";
    }
    return "%+.2f".formatted(value);
  }

  private static @NotNull NumberFormat numberFormat(final int decimals) {
    final NumberFormat format = NumberFormat.getNumberInstance();
    format.setMinimumFractionDigits(decimals);
    format.setMaximumFractionDigits(decimals);
    return format;
  }

  private static final class OntologyNode {

    private final String name;
    private final Map<String, OntologyNode> children = new TreeMap<>();
    private int detected;
    private int eligible;

    private OntologyNode(@NotNull final String name) {
      this.name = name;
    }

    private void include(@NotNull final List<String> path, final boolean isDetected) {
      eligible++;
      detected += isDetected ? 1 : 0;
      OntologyNode node = this;
      for (final String part : path) {
        node = node.children.computeIfAbsent(part, OntologyNode::new);
        node.eligible++;
        node.detected += isDetected ? 1 : 0;
      }
    }

    private @NotNull TreeItem<String> toTreeItem() {
      final String prevalence =
          eligible == 0 ? "—" : "%.0f%%".formatted((double) detected / eligible * 100d);
      final TreeItem<String> item = new TreeItem<>(
          "%s — %d/%d (%s)".formatted(name, detected, eligible, prevalence));
      item.getChildren().setAll(children.values().stream().map(OntologyNode::toTreeItem).toList());
      return item;
    }
  }
}
