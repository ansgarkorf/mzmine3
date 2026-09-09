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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.ProjectSelectionAnalysis.Result;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Observable state shared by the comparison dashboard views.
 */
public class FeatureListComparisonDashboardModel {

  private final FeatureListComparisonResult result;
  private final ObjectProperty<@NotNull FeatureListComparisonSelection> selection;
  private final ObjectProperty<@NotNull List<Integer>> selectedProjects;
  private final ObjectProperty<@Nullable SpectralComponent> selectedComponent;
  private final ObjectProperty<@NotNull List<Integer>> referenceProjects = new SimpleObjectProperty<>(
      List.of());
  private final ObjectProperty<@Nullable Result> analysis = new SimpleObjectProperty<>();
  private final ObjectProperty<@NotNull String> analysisStatus = new SimpleObjectProperty<>(
      "Preparing comparison…");
  private final ObjectProperty<@NotNull ProjectLandscape> landscape;
  private final ObjectProperty<@NotNull ComparisonFocus> focus = new SimpleObjectProperty<>(
      ComparisonFocus.ALL);
  private final ObjectProperty<@Nullable ComponentScope> componentScope = new SimpleObjectProperty<>();
  private final BooleanProperty randomForest = new SimpleBooleanProperty(false);

  public FeatureListComparisonDashboardModel(@NotNull final FeatureListComparisonResult result) {
    this.result = result;
    landscape = new SimpleObjectProperty<>(result.compositionLandscape());
    selection = new SimpleObjectProperty<>(
        new FeatureListComparisonSelection(0, Math.min(1, result.size() - 1)));
    selectedProjects = new SimpleObjectProperty<>(result.size() > 1 ? List.of(0, 1) : List.of(0));
    selectedComponent = new SimpleObjectProperty<>();
    selectedProjects.subscribe(_ -> componentScope.set(null));
    referenceProjects.subscribe(_ -> componentScope.set(null));
  }

  public @NotNull FeatureListComparisonResult getResult() {
    return result;
  }

  public @NotNull FeatureListComparisonSelection getSelection() {
    return selection.get();
  }

  public void setSelection(@NotNull final FeatureListComparisonSelection selection) {
    this.selection.set(selection);
    setSelectedProjects(selection.indexA() == selection.indexB() ? List.of(selection.indexA())
        : List.of(selection.indexA(), selection.indexB()));
  }

  public @NotNull ObjectProperty<@NotNull FeatureListComparisonSelection> selectionProperty() {
    return selection;
  }

  public @NotNull List<Integer> getSelectedProjects() {
    return selectedProjects.get();
  }

  public void setSelectedProjects(@NotNull final List<Integer> projects) {
    final List<Integer> valid = projects.stream().distinct()
        .filter(index -> index >= 0 && index < result.size()).toList();
    if (valid.isEmpty()) {
      return;
    }
    selectedProjects.set(List.copyOf(valid));
    final int first = valid.getFirst();
    final int second = valid.size() > 1 ? valid.get(1) : first;
    selection.set(new FeatureListComparisonSelection(first, second));
  }

  public void addSelectedProjects(@NotNull final List<Integer> projects) {
    final LinkedHashSet<Integer> combined = new LinkedHashSet<>(getSelectedProjects());
    combined.addAll(projects);
    setSelectedProjects(new ArrayList<>(combined));
  }

  public void toggleSelectedProject(final int project) {
    final List<Integer> updated = new ArrayList<>(getSelectedProjects());
    if (updated.contains(project) && updated.size() > 1) {
      updated.remove(Integer.valueOf(project));
    } else if (!updated.contains(project)) {
      updated.add(project);
    }
    setSelectedProjects(updated);
  }

  public @NotNull ObjectProperty<@NotNull List<Integer>> selectedProjectsProperty() {
    return selectedProjects;
  }

  public @Nullable SpectralComponent getSelectedComponent() {
    return selectedComponent.get();
  }

  public void setSelectedComponent(@Nullable final SpectralComponent component) {
    selectedComponent.set(component);
  }

  public @NotNull ObjectProperty<@Nullable SpectralComponent> selectedComponentProperty() {
    return selectedComponent;
  }

  @NotNull List<Integer> comparisonProjects() {
    return java.util.stream.Stream.concat(referenceProjects.get().stream(),
        getSelectedProjects().stream()).distinct().sorted().toList();
  }

  @NotNull ObjectProperty<@NotNull List<Integer>> referenceProjectsProperty() {
    return referenceProjects;
  }

  @NotNull ObjectProperty<@Nullable Result> analysisProperty() {
    return analysis;
  }

  @NotNull ObjectProperty<@NotNull String> analysisStatusProperty() {
    return analysisStatus;
  }

  @NotNull ObjectProperty<@NotNull ProjectLandscape> landscapeProperty() {
    return landscape;
  }

  @NotNull ObjectProperty<@NotNull ComparisonFocus> focusProperty() {
    return focus;
  }

  @NotNull ObjectProperty<@Nullable ComponentScope> componentScopeProperty() {
    return componentScope;
  }

  @NotNull BooleanProperty randomForestProperty() {
    return randomForest;
  }

  @NotNull List<ProjectSelectionAnalysis.Difference> visibleDifferences() {
    final Result current = analysis.get();
    if (current == null) {
      return List.of();
    }
    final ComponentScope scope = componentScope.get();
    return current.differences().stream().filter(
            d -> focus.get() == ComparisonFocus.NEW_TO_REFERENCE ?
                !current.evidence().reference().isEmpty() && current.evidence().reference().stream()
                    .noneMatch(d.component()::isPresent) && current.evidence().query().stream()
                    .anyMatch(d.component()::isPresent) : focus.get().accepts(d))
        .filter(d -> scope == null || scope.componentIds().contains(d.component().id())).toList();
  }

}
