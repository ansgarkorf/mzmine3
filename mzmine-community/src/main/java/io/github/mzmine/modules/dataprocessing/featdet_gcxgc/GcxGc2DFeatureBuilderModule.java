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
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Expands the chromatograms built and resolved along the GCxGC 2D RT spectra into the first
 * dimension: extracts each chromatogram from the single scans of all modulations within its 2D RT
 * range, so that the 2D features can be resolved in the retention time (first) dimension.
 */
public class GcxGc2DFeatureBuilderModule implements MZmineProcessingModule {

  public static final String NAME = "GCxGC 2D feature builder";

  @Override
  public @NotNull String getName() {
    return NAME;
  }

  @Override
  public @NotNull String getDescription() {
    return """
        Expands chromatograms built and resolved along the 2D RT into the first dimension: \
        extracts each chromatogram within its 2D RT range from the single scans of all \
        modulations. Resolve the 2D features in the retention time dimension afterwards.""";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return GcxGc2DFeatureBuilderParameters.class;
  }

  @Override
  public @NotNull ExitCode runModule(@NotNull final MZmineProject project,
      @NotNull final ParameterSet parameters, @NotNull final Collection<Task> tasks,
      @NotNull final Instant moduleCallDate) {
    final MemoryMapStorage storage = MemoryMapStorage.forFeatureList();
    for (final ModularFeatureList flist : parameters.getValue(
        GcxGc2DFeatureBuilderParameters.FEATURE_LISTS).getMatchingFeatureLists()) {
      tasks.add(new GcxGc2DFeatureBuilderTask(storage, parameters, flist, project,
          moduleCallDate));
    }
    return ExitCode.OK;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.EIC_DETECTION;
  }
}
