/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.io.gcxgctoims;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.impl.TaskPerRawDataFileModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GCxGCToImsModule extends TaskPerRawDataFileModule {

  public GCxGCToImsModule() {
    super("GCxGC to pseudo-IMS file", GCxGCToImsParameters.class,
        MZmineModuleCategory.RAWDATAFILTERING, true,
        "Convert a raw GCxGC raw data file to an IMS file to process 2D GC data using the IMS workflow.");
  }

  @Override
  public @NotNull Task createTask(@NotNull MZmineProject project, @NotNull ParameterSet parameters,
      @NotNull Instant moduleCallDate, @Nullable MemoryMapStorage storage,
      @NotNull RawDataFile raw) {
    return new GCxGCToImsTask(storage, moduleCallDate, parameters, raw, project);
  }

  @Override
  public MemoryMapStorage get() {
    return MemoryMapStorage.forRawDataFile();
  }
}
