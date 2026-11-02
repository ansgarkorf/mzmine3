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

package io.github.mzmine.modules.batchmode.order;

import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.batchmode.BatchQueue;
import io.github.mzmine.modules.dataprocessing.align_gc.GCAlignerModule;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.SpectralDeconvolutionGCModule;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Modules that need GC-EI compounds run after the GC-EI spectral deconvolution or another
 * {@link io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.GcEiDeconvolutionModule},
 * e.g., the GCxGC 2D feature finder of mzmine.
 */
class GcEiDeconvolutionConditionTest {

  @Test
  void gcAlignerNeedsTheDeconvolution() {
    final BatchModuleOrderValidationResult result = BatchModuleOrderValidator.validate(
        queue(new GCAlignerModule()));
    Assertions.assertTrue(result.hasIssues());
    Assertions.assertTrue(result.formatMessage().contains(SpectralDeconvolutionGCModule.NAME),
        result.formatMessage());
  }

  @Test
  void gcAlignerAfterTheSpectralDeconvolution() {
    Assertions.assertFalse(BatchModuleOrderValidator.validate(
        queue(new SpectralDeconvolutionGCModule(), new GCAlignerModule())).hasIssues());
  }

  @Test
  void gcAlignerAfterAnotherDeconvolutionModule() {
    Assertions.assertFalse(BatchModuleOrderValidator.validate(
        queue(new TestGcEiDeconvolutionModule(), new GCAlignerModule())).hasIssues());
  }

  private static BatchQueue queue(final MZmineProcessingModule... modules) {
    final BatchQueue queue = new BatchQueue();
    for (final MZmineProcessingModule module : modules) {
      queue.add(new MZmineProcessingStepImpl<>(module, new SimpleParameterSet()));
    }
    return queue;
  }
}
