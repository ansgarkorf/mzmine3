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

package io.github.mzmine.modules.dataprocessing.featdet_gcxgc;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.batchmode.order.MassDetectionCondition;
import io.github.mzmine.modules.batchmode.order.ModuleOrderRecommendation;
import io.github.mzmine.modules.batchmode.order.ModuleOrderRule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates one spectrum per second dimension scan index of a GCxGC file by summing the detected
 * signals of this scan index over all modulations. Chromatograms are then built along these 2D RT
 * spectra in the second dimension, which has many more data points than the first dimension. Mass
 * detection needs to run before.
 */
public class GcxGcSecondDimensionSpectraModule implements MZmineProcessingModule {

  public static final String NAME = "GCxGC 2D RT spectra";

  @Override
  public @NotNull String getName() {
    return NAME;
  }

  @Override
  public @NotNull String getDescription() {
    return """
        Sums the detected signals (mass lists) of each 2D RT scan over all GCxGC modulations into \
        one spectrum per 2D RT. The chromatogram builder then builds chromatograms in the second \
        dimension along these spectra. Run mass detection on all scan types before.""";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return GcxGcSecondDimensionSpectraParameters.class;
  }

  @Override
  public @NotNull ExitCode runModule(@NotNull final MZmineProject project,
      @NotNull final ParameterSet parameters, @NotNull final Collection<Task> tasks,
      @NotNull final Instant moduleCallDate) {
    for (final RawDataFile file : parameters.getValue(
        GcxGcSecondDimensionSpectraParameters.RAW_DATA_FILES).getMatchingRawDataFiles()) {
      tasks.add(new GcxGcSecondDimensionSpectraTask(file, parameters, moduleCallDate));
    }
    return ExitCode.OK;
  }

  @Override
  public @NotNull List<@NotNull ModuleOrderRecommendation> getModuleOrderRecommendations() {
    return List.of(ModuleOrderRecommendation.of(
        "GCxGC 2D RT spectra sum the mass lists of the single scans",
        ModuleOrderRule.mustRunAfter(MassDetectionCondition.MSany)));
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.RAWDATA;
  }
}
