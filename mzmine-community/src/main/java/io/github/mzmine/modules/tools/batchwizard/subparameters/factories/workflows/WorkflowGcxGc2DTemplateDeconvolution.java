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

package io.github.mzmine.modules.tools.batchwizard.subparameters.factories.workflows;

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardPartFilter;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.builders.WizardBatchBuilder;
import io.github.mzmine.modules.tools.batchwizard.builders.WizardBatchBuilderGcxGcEi2DFeatureFinder;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WizardStepParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.WorkflowGcElectronImpactWizardParameters;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.WorkflowWizardParameterFactory;
import io.mzio.general.Result;
import io.mzio.users.service.UserActiveService;
import io.mzio.users.user.MZmineUser;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GCxGC-EI workflow with the GCxGC 2D feature finder in template deconvolution mode: compounds are
 * deconvoluted from the 2D features by the shapes of model peaks, no separate GC-EI spectral
 * deconvolution. Shares the GC-EI deconvolution workflow parameters, so all GCxGC workflows can be
 * compared.
 */
public class WorkflowGcxGc2DTemplateDeconvolution extends WorkflowWizardParameterFactory {

  @Override
  public @NotNull String getUniqueID() {
    return "GCXGC_2D_TEMPLATE_DECONVOLUTION";
  }

  @Override
  public WizardStepParameters create() {
    return new WorkflowGcElectronImpactWizardParameters(this, 8, true, true, null, true, true,
        false);
  }

  @Override
  public String toString() {
    return "Template deconvolution (GCxGC 2D feature finder)";
  }

  @Override
  public Map<WizardPart, WizardPartFilter> getStepFilters() {
    return Map.of(WizardPart.ION_INTERFACE,
        WizardPartFilter.allow(List.of(IonInterfaceWizardParameterFactory.GCXGC_EI)));
  }

  @Override
  public @NotNull WizardBatchBuilder getBatchBuilder(final @NotNull WizardSequence steps) {
    return new WizardBatchBuilderGcxGcEi2DFeatureFinder(steps, true);
  }

  @Override
  public @NotNull Set<@NotNull UserActiveService> getUnlockingServices() {
    return EnumSet.allOf(UserActiveService.class);
  }

  @Override
  public Result checkUserForServices(@Nullable final MZmineUser user) {
    // like the deconvolution workflow: always displayed, tasks check the user
    return Result.OK;
  }
}
