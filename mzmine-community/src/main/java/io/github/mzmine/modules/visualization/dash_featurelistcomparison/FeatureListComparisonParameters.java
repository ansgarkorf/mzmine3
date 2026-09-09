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

import io.github.mzmine.modules.dataprocessing.group_spectral_networking.SignalFiltersParameters;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.metadata.SampleTypeFilterParameter;
import io.github.mzmine.modules.visualization.projectmetadata.SampleType;
import io.github.mzmine.modules.visualization.projectmetadata.SampleTypeFilter;
import io.github.mzmine.parameters.parametertypes.PercentParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.submodules.ParameterSetParameter;
import io.github.mzmine.parameters.parametertypes.submodules.OptionalModuleParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import org.jetbrains.annotations.NotNull;

/**
 * Selects independent feature lists and the MS2 identity criteria used for comparison.
 */
public class FeatureListComparisonParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureLists = new FeatureListsParameter(
      "Feature lists", "Processed feature lists to compare as independent projects.", 2,
      Integer.MAX_VALUE);

  public static final MZToleranceParameter precursorMzTolerance = new MZToleranceParameter(
      "Precursor m/z tolerance",
      "Maximum precursor difference for spectra to be considered identity-match candidates.", 0.005,
      10);

  public static final MZToleranceParameter fragmentMzTolerance = new MZToleranceParameter(
      "Fragment m/z tolerance", "Maximum m/z difference between matching MS2 signals.", 0.003, 10);

  public static final IntegerParameter minimumMatchedSignals = new IntegerParameter(
      "Minimum matched signals", "Minimum number of aligned fragments required for an MS2 match.",
      4, 1, null);

  public static final PercentParameter minimumCosineSimilarity = new PercentParameter(
      "Minimum cosine similarity",
      "Minimum weighted cosine score for two spectra to represent the same component.", 0.7);

  public static final ParameterSetParameter<SignalFiltersParameters> signalFilters = new ParameterSetParameter<>(
      "Signal filters", "Preprocessing applied independently to each representative MS2 spectrum.",
      new SignalFiltersParameters());

  public FeatureListComparisonParameters() {
    super(featureLists, precursorMzTolerance, fragmentMzTolerance, minimumMatchedSignals,
        minimumCosineSimilarity, signalFilters, quantification, comparableAbundances, sampleTypes,
        independentUnitColumn, independentRawFiles, responseContrast);
  }

  public static final ComboParameter<ComparisonQuantification> quantification = new ComboParameter<>(
      "Abundance measure", "Use the same measurement throughout; missing values stay unavailable.",
      ComparisonQuantification.values(), ComparisonQuantification.AREA);

  public static final BooleanParameter comparableAbundances = new BooleanParameter(
      "Compare relative abundances",
      "Enable only when signal responses are comparable across input projects. Uses relative "
          + "abundance normalized to retained MS2-row signal, not absolute concentration.", false);

  public static final SampleTypeFilterParameter sampleTypes = new SampleTypeFilterParameter(
      "Comparison sample types", "Include these sample metadata types in detection and abundance "
      + "summaries. The default excludes blanks, QC, calibration and system suitability files.",
      SampleTypeFilter.of(SampleType.SAMPLE), true);

  public static final StringParameter independentUnitColumn = new StringParameter(
      "Independent sample ID column",
      "Optional metadata column identifying independent experimental units (e.g. subject ID). "
          + "Repeated injections are aggregated and the same unit stays in one validation fold.",
      "", false);

  public static final BooleanParameter independentRawFiles = new BooleanParameter(
      "Raw files are independent samples",
      "Use each raw file as an independent unit if no ID column is selected. Leave disabled for "
          + "technical replicates. RF requires a defined unit and at least three units per group.",
      false);

  public static final OptionalModuleParameter<ComparisonContrastParameters> responseContrast = new OptionalModuleParameter<>(
      "Historic response contrast",
      "Compare one selected query project with project-level effects from historical projects. "
          + "Requires comparable relative abundances and independent sample IDs.",
      new ComparisonContrastParameters(), false, false);

  @Override
  public @NotNull IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }
}
