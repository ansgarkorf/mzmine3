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

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.modules.dataprocessing.group_spectral_networking.SpectralSignalFilter;
import io.github.mzmine.modules.visualization.dash_featurelistcomparison.FeatureListComparisonCalculator.SpectralFeature;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.exceptions.MissingMassListException;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.modules.visualization.projectmetadata.SampleTypeFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extracts representative MS2 spectra and computes every feature-list pair off the GUI thread.
 */
public class FeatureListComparisonTask extends AbstractTask {

  private static final int MAX_ANNOTATIONS_PER_FEATURE = 20;

  private final List<ModularFeatureList> featureLists;
  private final FeatureListComparisonSettings settings;
  private final SpectralSignalFilter signalFilter;
  private final Consumer<FeatureListComparisonResult> onFinished;
  private final long totalWorkUnits;
  private long finishedWorkUnits;
  private int nextSpectrumIndex;
  private final ComparisonQuantification quantification;
  private final boolean comparableAbundances;
  private final SampleTypeFilter sampleTypes;
  private final String independentUnitColumn;
  private final boolean independentRawFiles;
  private final boolean responseContrastEnabled;
  private final String contrastColumn;
  private final String contrastNumerator;
  private final String contrastDenominator;

  public FeatureListComparisonTask(@NotNull List<ModularFeatureList> featureLists,
      @NotNull ParameterSet parameters, @NotNull Consumer<FeatureListComparisonResult> onFinished,
      @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate, "Compare feature lists by MS2 similarity");
    this.featureLists = List.copyOf(featureLists);
    settings = new FeatureListComparisonSettings(
        parameters.getValue(FeatureListComparisonParameters.precursorMzTolerance),
        parameters.getValue(FeatureListComparisonParameters.fragmentMzTolerance),
        parameters.getValue(FeatureListComparisonParameters.minimumMatchedSignals),
        parameters.getValue(FeatureListComparisonParameters.minimumCosineSimilarity));
    signalFilter = parameters.getValue(FeatureListComparisonParameters.signalFilters)
        .createFilter();
    this.onFinished = onFinished;
    quantification = parameters.getValue(FeatureListComparisonParameters.quantification);
    comparableAbundances = parameters.getValue(
        FeatureListComparisonParameters.comparableAbundances);
    sampleTypes = parameters.getValue(FeatureListComparisonParameters.sampleTypes);
    independentUnitColumn = java.util.Objects.requireNonNullElse(
        parameters.getValue(FeatureListComparisonParameters.independentUnitColumn), "").strip();
    independentRawFiles = parameters.getValue(FeatureListComparisonParameters.independentRawFiles);
    final var contrastParameter = parameters.getParameter(
        FeatureListComparisonParameters.responseContrast);
    responseContrastEnabled = contrastParameter.getValue();
    final ParameterSet contrastParameters = contrastParameter.getEmbeddedParameters();
    contrastColumn = java.util.Objects.requireNonNullElse(
        contrastParameters.getValue(ComparisonContrastParameters.metadataColumn), "").strip();
    contrastNumerator = java.util.Objects.requireNonNullElse(
        contrastParameters.getValue(ComparisonContrastParameters.numerator), "").strip();
    contrastDenominator = java.util.Objects.requireNonNullElse(
        contrastParameters.getValue(ComparisonContrastParameters.denominator), "").strip();
    totalWorkUnits = featureLists.size() + 3L;
  }

  @Override
  public @NotNull String getTaskDescription() {
    return "Comparing %d feature lists by MS2 spectral similarity".formatted(featureLists.size());
  }

  @Override
  public double getFinishedPercentage() {
    return totalWorkUnits == 0 ? 0d : (double) finishedWorkUnits / totalWorkUnits;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    if (!independentUnitColumn.isEmpty()
        && ProjectService.getMetadata().getColumnByName(independentUnitColumn) == null) {
      setErrorMessage("Independent sample ID metadata column not found: " + independentUnitColumn);
      setStatus(TaskStatus.ERROR);
      return;
    }
    if (responseContrastEnabled && (contrastColumn.isEmpty() || contrastNumerator.isEmpty()
        || contrastDenominator.isEmpty() || contrastNumerator.equals(contrastDenominator))) {
      setErrorMessage("Historic response contrast needs a metadata column and two distinct values");
      setStatus(TaskStatus.ERROR);
      return;
    }
    if (responseContrastEnabled
        && ProjectService.getMetadata().getColumnByName(contrastColumn) == null) {
      setErrorMessage("Response contrast metadata column not found: " + contrastColumn);
      setStatus(TaskStatus.ERROR);
      return;
    }
    final List<PreparedFeatureList> preparedLists = new ArrayList<>(featureLists.size());
    for (int i = 0; i < featureLists.size(); i++) {
      if (isCanceled()) {
        return;
      }
      preparedLists.add(prepareFeatureList(i, featureLists.get(i)));
      finishedWorkUnits++;
    }

    final List<SpectralFeature> allSpectra = preparedLists.stream()
        .flatMap(prepared -> prepared.spectra().stream()).toList();
    final GlobalSpectralComponentCalculator.Result componentResult = GlobalSpectralComponentCalculator.calculate(
        allSpectra, preparedLists.size(), settings, this::isCanceled);
    if (isCanceled()) {
      return;
    }
    finishedWorkUnits++;

    final HierarchicalProjectClustering.Result clustering = HierarchicalProjectClustering.cluster(
        componentResult.similarities());
    finishedWorkUnits++;
    final double[][] umapCoordinates = UmapProjector.project(componentResult.similarities());
    finishedWorkUnits++;

    final List<FeatureListSummary> summaries = preparedLists.stream()
        .map(PreparedFeatureList::summary).toList();
    final List<ProjectSampleMetadata> sampleMetadata = preparedLists.stream()
        .map(PreparedFeatureList::sampleMetadata).toList();
    final List<ProjectMethodCoverage> methodCoverage = preparedLists.stream()
        .map(PreparedFeatureList::methodCoverage).toList();
    onFinished.accept(
        new FeatureListComparisonResult(summaries, componentResult.similarities(), settings,
            componentResult.components(), clustering.projectOrder(), clustering.segments(),
            umapCoordinates, sampleMetadata, quantification, comparableAbundances, methodCoverage,
            responseContrastEnabled ? new ComparisonContrast(contrastColumn, contrastNumerator,
                contrastDenominator) : null));
    setStatus(TaskStatus.FINISHED);
  }

  private @NotNull PreparedFeatureList prepareFeatureList(final int index,
      @NotNull ModularFeatureList featureList) {
    final List<SpectralFeature> spectra = new ArrayList<>();
    final List<RawDataFile> rawDataFiles = featureList.getRawDataFiles().stream()
        .filter(sampleTypes::matches).toList();
    int rowsWithMs2 = 0;
    int missingMassLists = 0;
    for (final FeatureListRow row : featureList.getRows()) {
      if (isCanceled()) {
        break;
      }
      final Scan scan = row.getMostIntenseFragmentScan();
      if (scan == null) {
        continue;
      }
      rowsWithMs2++;
      final Double scanPrecursorMz = scan.getPrecursorMz();
      final double precursorMz =
          scanPrecursorMz == null || scanPrecursorMz <= 0d ? row.getAverageMZ() : scanPrecursorMz;
      try {
        final var dataPoints = signalFilter.applyFilterAndSortByIntensity(scan, precursorMz,
            settings.minimumMatchedSignals());
        if (dataPoints != null) {
          final double[] abundances = new double[rawDataFiles.size()];
          final boolean[] detections = new boolean[rawDataFiles.size()];
          boolean anyDetected = false;
          for (int sampleIndex = 0; sampleIndex < rawDataFiles.size(); sampleIndex++) {
            final Feature feature = row.getFeature(rawDataFiles.get(sampleIndex));
            abundances[sampleIndex] = quantification.abundance(feature);
            detections[sampleIndex] =
                feature != null && feature.getFeatureStatus() != FeatureStatus.UNKNOWN;
            anyDetected |= detections[sampleIndex];
          }
          if (!anyDetected) {
            continue;
          }
          spectra.add(new SpectralFeature(nextSpectrumIndex++, index, featureList.getName(),
              scan.getPolarity(), scan.getPrecursorCharge(), row.getID(), precursorMz, dataPoints,
              abundances, extractAnnotationEvidence(row), detections));
        }
      } catch (MissingMassListException _) {
        missingMassLists++;
      }
    }
    final FeatureListSummary summary = new FeatureListSummary(index, featureList.getName(),
        featureList.getNumberOfRows(), rowsWithMs2, spectra.size(), missingMassLists);
    final ProjectSampleMetadata metadata = new ProjectSampleMetadata(index,
        rawDataFiles.stream().map(RawDataFile::getName).toList(),
        rawDataFiles.stream().map(this::independentUnit).toList(),
        featureList.getRawDataFiles().size() - rawDataFiles.size(),
        rawDataFiles.stream().map(this::metadataValues).toList());
    final List<ProjectMethodCoverage.Window> windows = rawDataFiles.stream().map(file -> {
      final var mzRange = file.getDataMZRange();
      return new ProjectMethodCoverage.Window(mzRange.lowerEndpoint(), mzRange.upperEndpoint(),
          Set.copyOf(file.getDataPolarity()));
    }).toList();
    return new PreparedFeatureList(summary, List.copyOf(spectra), metadata,
        new ProjectMethodCoverage(index, windows));
  }

  private @NotNull String independentUnit(@NotNull final RawDataFile file) {
    if (!independentUnitColumn.isEmpty()) {
      final var metadata = ProjectService.getMetadata();
      final Object value = metadata.getValue(metadata.getColumnByName(independentUnitColumn), file);
      return value == null ? "" : value.toString().strip();
    }
    return independentRawFiles ? file.getAbsoluteFilePath().toString() : "";
  }

  private @NotNull Map<String, String> metadataValues(@NotNull final RawDataFile file) {
    final var metadata = ProjectService.getMetadata();
    final Map<String, String> values = new LinkedHashMap<>();
    for (final var column : metadata.getColumns()) {
      final Object value = metadata.getValue(column, file);
      if (value != null && !value.toString().isBlank()) {
        values.put(column.getTitle(), value.toString().strip());
      }
    }
    return Map.copyOf(values);
  }

  private static @NotNull List<CompoundAnnotationEvidence> extractAnnotationEvidence(
      @NotNull final FeatureListRow row) {
    final FeatureAnnotation preferred = row.getPreferredAnnotation();
    final List<FeatureAnnotation> ordered = new ArrayList<>();
    if (preferred != null) {
      ordered.add(preferred);
    }
    ordered.addAll(row.getAllFeatureAnnotations(true));

    final Map<String, CompoundAnnotationEvidence> unique = new LinkedHashMap<>();
    for (final FeatureAnnotation annotation : ordered) {
      final boolean isPreferred = annotation == preferred || annotation.equals(preferred);
      final CompoundAnnotationEvidence evidence = CompoundAnnotationEvidence.from(annotation,
          isPreferred);
      final String key = String.join("\u0000", evidence.identityKey(), evidence.sourceLabel(),
          evidence.method(), evidence.adduct() == null ? "" : evidence.adduct());
      unique.putIfAbsent(key, evidence);
      if (unique.size() >= MAX_ANNOTATIONS_PER_FEATURE) {
        break;
      }
    }
    return List.copyOf(unique.values());
  }

  private record PreparedFeatureList(@NotNull FeatureListSummary summary,
                                     @NotNull List<SpectralFeature> spectra,
                                     @NotNull ProjectSampleMetadata sampleMetadata,
                                     @NotNull ProjectMethodCoverage methodCoverage) {

  }
}
