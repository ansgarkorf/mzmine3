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

package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.classic;

import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolverSetupDialog;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.Resolver;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.util.ExitCode;
import java.text.DecimalFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassicResolverParameters extends GeneralResolverParameters {

  public static final DoubleParameter SEARCH_RT_RANGE = new DoubleParameter(
      "Minimum search range RT/Mobility (absolute)",
      "If a local minimum is minimal in this range of retention time or mobility, it will be considered a border between two peaks.\n"
          + "Start optimising with a value close to the FWHM of a peak.",
      new DecimalFormat("0.000"), 0.05);

  public static final DoubleParameter SIGNAL_TO_NOISE = new DoubleParameter("Signal-to-Noise (S/N)",
      "Minimum S/N ratio required for a peak to be considered valid.", new DecimalFormat("0.0"),
      3.0);

  public static final DoubleParameter MIN_RATIO = new DoubleParameter("Min ratio of peak top/edge",
      "Minimum ratio between peak's top intensity and side (lowest) data points."
          + "\nThis parameter helps to reduce detection of false peaks in case the chromatogram is not smooth.",
      new DecimalFormat("0.00"), 1.7d);

  public ClassicResolverParameters() {
    super(new Parameter[]{PEAK_LISTS, SUFFIX, handleOriginal, groupMS2Parameters, dimension,
            SEARCH_RT_RANGE, SIGNAL_TO_NOISE, MIN_RATIO,
            MIN_NUMBER_OF_DATAPOINTS},
        "https://mzmine.github.io/mzmine_documentation/module_docs/featdet_resolver_local_minimum/local-minimum-resolver.html");
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    final FeatureResolverSetupDialog dialog = new FeatureResolverSetupDialog(valueCheckRequired,
        this, null);
    dialog.showAndWait();
    return dialog.getExitCode();
  }

  @Nullable
  @Override
  public Resolver getResolver(ParameterSet parameters, ModularFeatureList flist) {
    return new ClassicResolver(parameters, flist);
  }

  @NotNull
  @Override
  public IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }

}