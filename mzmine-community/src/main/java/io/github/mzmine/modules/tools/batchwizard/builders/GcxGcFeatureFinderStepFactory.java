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

package io.github.mzmine.modules.tools.batchwizard.builders;

import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.MZmineProcessingStep;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the GCxGC 2D feature finder module and creates its batch step. The finder is part of mzmine (closed
 * source) and registers its factory in {@link GcxGcFeatureFinderRegistry}.
 */
public interface GcxGcFeatureFinderStepFactory {

  /**
   * @return the module of the GCxGC 2D feature finder, e.g., for menus
   */
  @NotNull Class<? extends MZmineProcessingModule> getModuleClass();

  /**
   * @param settings the settings of the wizard
   * @return the batch step of the GCxGC 2D feature finder
   */
  @NotNull MZmineProcessingStep<MZmineProcessingModule> createStep(
      @NotNull GcxGcFeatureFinderSettings settings);
}
