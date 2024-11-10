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

package io.github.mzmine.modules.dataanalysis.plsda;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.features.FeatureAnnotationPriority;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYZDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ImputationFunction;
import io.github.mzmine.modules.dataanalysis.utils.imputation.ImputationFunctions;
import io.github.mzmine.modules.dataanalysis.utils.scaling.ScalingFunction;
import io.github.mzmine.modules.dataanalysis.utils.scaling.ScalingFunctions;
import io.github.mzmine.modules.visualization.projectmetadata.SampleTypeFilter;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import io.github.mzmine.taskcontrol.progress.TotalFinishedItemsProgress;
import io.github.mzmine.util.annotations.CompoundAnnotationUtils;
import io.github.mzmine.util.collections.SortOrder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class PLSDAUpdateTask extends FxUpdateTask<PLSDAModel> {

  private final TotalFinishedItemsProgress progressProvider = new TotalFinishedItemsProgress(3);
  private final Integer rangeComponentIndex;
  private final Integer domainComponentIndex;
  private final MetadataColumn<?> metadataColumn;
  private final List<FeatureListRow> selectedRows;
  private final AbundanceMeasure abundance;
  private final List<FeatureList> featureLists;
  private final List<DatasetAndRenderer> scoresDatasets = new ArrayList<>();
  private final List<DatasetAndRenderer> loadingsDatasets = new ArrayList<>();
  private final List<Integer> components = new ArrayList<>();
  private final ImputationFunction imputer;

  private final ScalingFunction scaling;
  private final SampleTypeFilter sampleTypeFilter;
  private PLSDARowsResult plsdaRowsResult;

  protected PLSDAUpdateTask(@NotNull String taskName, PLSDAModel model) {
    super(taskName, model);

    domainComponentIndex = Objects.requireNonNullElse(model.getDomainComponent(), 0) - 1;
    rangeComponentIndex = Objects.requireNonNullElse(model.getRangeComponent(), 0) - 1;
    metadataColumn = model.getMetadataColumn();
    selectedRows = model.getSelectedRows();
    featureLists = model.getFeatureLists().stream()
        .filter(flist -> flist.getNumberOfRawDataFiles() > 1).toList();
    abundance = model.getAbundance();

    final ScalingFunctions scalingFunction = model.getScalingFunction();
    scaling = scalingFunction.getScalingFunction();

    final ImputationFunctions imputationFunction = model.getImputationFunction();
    imputer = imputationFunction.getImputer();
    sampleTypeFilter = model.getSampleTypeFilter();
  }

  @Override
  public void onFailedPreCondition() {
    clearGui();
  }

  @Override
  public boolean checkPreConditions() {
    if (rangeComponentIndex < 0 || domainComponentIndex < 0) {
      return false;
    }
    if (featureLists == null || featureLists.isEmpty() || featureLists.get(0) == null) {
      return false;
    }
    if (abundance == null) {
      return false;
    }
    if (sampleTypeFilter.isEmpty()) {
      return false;
    }
    // Ensure metadata column is specified for supervised analysis
    if (metadataColumn == null) {
      clearGui();
      return false;
    }
    return true;
  }

  @Override
  protected void process() {
    final List<FeatureListRow> rows = sampleTypeFilter.filter(featureLists.getFirst().getRows());
    MetadataColumn<?> metadataColumn = model.getMetadataColumn();
    final Comparator<? super DataType<?>> annotationPrioSorter = FeatureAnnotationPriority.createSorter(
        SortOrder.ASCENDING);
    final Map<FeatureListRow, DataType<?>> rowsMappedToBestAnnotation = CompoundAnnotationUtils.mapBestAnnotationTypesByPriority(
        rows, true);
    final List<FeatureListRow> rowsSortedByAnnotationPrio = rows.stream().sorted(
        ((r1, r2) -> annotationPrioSorter.compare(rowsMappedToBestAnnotation.get(r1),
            rowsMappedToBestAnnotation.get(r2)))).toList();

    if (metadataColumn != null) {
      plsdaRowsResult = PLSDAUtils.performPLSDAOnRows(rowsSortedByAnnotationPrio, abundance,
          scaling, imputer, sampleTypeFilter, metadataColumn);
      if (plsdaRowsResult == null) {
        return;
      }
      progressProvider.getAndIncrement();

      final PLSDAScoresProvider scores = new PLSDAScoresProvider(plsdaRowsResult, "Scores",
          Color.RED, domainComponentIndex, rangeComponentIndex, metadataColumn);
      final ColoredXYZDataset scoresDS = new ColoredXYZDataset(scores, RunOption.THIS_THREAD);
      progressProvider.getAndIncrement();

      final PLSDALoadingsProvider loadings = new PLSDALoadingsProvider(plsdaRowsResult, "Loadings",
          Color.RED, domainComponentIndex, rangeComponentIndex);
      final ColoredXYZDataset loadingsDS = new ColoredXYZDataset(loadings, RunOption.THIS_THREAD);
      progressProvider.getAndIncrement();

      loadingsDatasets.add(new DatasetAndRenderer(loadingsDS, new ColoredXYShapeRenderer()));
      scoresDatasets.add(new DatasetAndRenderer(scoresDS, new ColoredXYShapeRenderer()));

      for (int i = 1; i <= plsdaRowsResult.plsdaResult().getLoadingsMatrix().getRowDimension();
          i++) {
        components.add(i);
      }
    }
  }

  @Override
  protected void updateGuiModel() {
    model.setScoresDatasets(scoresDatasets);
    model.setLoadingsDatasets(loadingsDatasets);
    model.setPlsdaResult(plsdaRowsResult);

    if (model.getAvailableComponents().size() != components.size()) {
      model.getAvailableComponents().setAll(components);
    }

    if (rangeComponentIndex < components.size()) {
      model.setRangeComponent(rangeComponentIndex + 1);
    } else if (!components.isEmpty()) {
      model.setRangeComponent(components.get(components.size() - 1));
    }
    if (domainComponentIndex < components.size()) {
      model.setDomainComponent(domainComponentIndex + 1);
    } else if (!components.isEmpty()) {
      model.setDomainComponent(components.get(0));
    }
  }

  private void clearGui() {
    model.setScoresDatasets(List.of());
    model.setLoadingsDatasets(List.of());
    model.setPlsdaResult(null);
    model.getAvailableComponents().clear();
    model.setDomainComponent(1);
    model.setRangeComponent(2);
  }

  @Override
  public String getTaskDescription() {
    return String.format("Computing PLS-DA dataset for %s", featureLists.get(0).getName());
  }

  @Override
  public double getFinishedPercentage() {
    return progressProvider.progress();
  }

}
