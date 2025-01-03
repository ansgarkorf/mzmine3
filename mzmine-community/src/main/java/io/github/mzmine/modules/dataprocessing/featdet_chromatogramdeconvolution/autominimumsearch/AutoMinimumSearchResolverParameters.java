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

package io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.autominimumsearch;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.TimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.SummedIntensityMobilitySeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolverSetupDialog;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.Resolver;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.ResolvingDimension;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.util.ExitCode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoMinimumSearchResolverParameters extends GeneralResolverParameters {

  public static final DoubleParameter SEARCH_RT_RANGE = new DoubleParameter(
      "Minimum search range RT/Mobility (absolute)",
      "If a local minimum is minimal in this range of retention time or mobility, it will be considered a border between two peaks.\n"
          + "Start optimising with a value close to the FWHM of a peak.",
      new DecimalFormat("0.000"), 0.05);

  public static final DoubleParameter SIGNAL_TO_NOISE = new DoubleParameter("Signal-to-Noise (S/N)",
      "Minimum S/N ratio required for a peak to be considered valid.", new DecimalFormat("0.0"),
      3.0);


  public AutoMinimumSearchResolverParameters() {
    super(new Parameter[]{PEAK_LISTS, SUFFIX, handleOriginal, groupMS2Parameters, dimension,
            SEARCH_RT_RANGE, SIGNAL_TO_NOISE, MIN_NUMBER_OF_DATAPOINTS},
        "https://mzmine.github.io/mzmine_documentation/module_docs/featdet_resolver_local_minimum/local-minimum-resolver.html");
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    final FeatureResolverSetupDialog dialog = new FeatureResolverSetupDialog(valueCheckRequired,
        this, null);
    dialog.showAndWait();
    return dialog.getExitCode();
  }

  //@Nullable
  //@Override
  //public Resolver getResolver(ParameterSet parameters, ModularFeatureList flist) {
  //  return new AutoMinimumSearchResolver(parameters, flist);
  //}

  @Nullable
  @Override
  public Resolver getResolver(ParameterSet parameters, ModularFeatureList flist) {
    // The dimension from your general parameters
    ResolvingDimension dimension = parameters.getParameter(GeneralResolverParameters.dimension)
        .getValue();

    List<double[]> allX = new ArrayList<>();
    List<double[]> allY = new ArrayList<>();

    // Gather data from the entire FeatureList
    // One robust approach is FeatureFullDataAccess if dimension == RT
    if (dimension == ResolvingDimension.RETENTION_TIME) {
      List<ModularFeature> features = flist.getFeatures(flist.getRawDataFile(0));
      for (ModularFeature feature : features) {

        IonTimeSeries<? extends Scan> series = feature.getFeatureData();
        int numValues = series.getNumberOfValues();

        // Skip very short or empty features
        if (numValues < MIN_NUMBER_OF_DATAPOINTS.getValue()) {
          continue;
        }

        double[] rtArray = new double[numValues];
        double[] intensityArray = new double[numValues];

        // Fill with RT + intensities
        for (int i = 0; i < numValues; i++) {
          rtArray[i] = series.getRetentionTime(i);
          intensityArray[i] = series.getIntensity(i);
        }

        allX.add(rtArray);
        allY.add(intensityArray);
      }
    }

    // Alternatively, if dimension == MOBILITY, gather mobility + intensities
    else if (dimension == ResolvingDimension.MOBILITY) {
      List<ModularFeature> features = flist.getFeatures(flist.getRawDataFile(0));
      for (ModularFeature feature : features) {
        IonTimeSeries<? extends Scan> series = feature.getFeatureData();
        if (!(series instanceof IonMobilogramTimeSeries mobSeries)) {
          // skip or handle differently
          continue;
        }

        // SummedMobilogram or each IonMobilogram, etc.
        // For example, use the "summed mobilogram"
        SummedIntensityMobilitySeries summedMob = mobSeries.getSummedMobilogram();
        int numValues = summedMob.getNumberOfValues();
        if (numValues < MIN_NUMBER_OF_DATAPOINTS.getValue()) {
          continue;
        }

        double[] mobilityArray = new double[numValues];
        double[] intensityArray = new double[numValues];
        for (int i = 0; i < numValues; i++) {
          mobilityArray[i] = summedMob.getMobility(i);
          intensityArray[i] = summedMob.getIntensity(i);
        }

        allX.add(mobilityArray);
        allY.add(intensityArray);
      }
    }

// 2) Create the resolver
    AutoGlobalMinimumSearchResolver resolver = new AutoGlobalMinimumSearchResolver(parameters,
        flist);

// 3) Calibrate globally
    resolver.calibrateGlobalParameters(allX, allY);

    return resolver;
  }


  @NotNull
  @Override
  public IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }


  /**
   * Extracts the rt values of the scans into a buffer. If the size of the buffer is too small, a
   * new buffer will be allocated and returned.
   *
   * @param timeSeries The time series.
   * @param rtBuffer   The proposed buffer.
   * @return The buffer the rt values were written into.
   */
  protected double[] extractRtValues(@NotNull final TimeSeries timeSeries, double[] rtBuffer) {
    final int numValues = timeSeries.getNumberOfValues();
    if (rtBuffer == null || rtBuffer.length < numValues) {
      rtBuffer = new double[numValues];
    }
    Arrays.fill(rtBuffer, numValues, rtBuffer.length, 0d);
    for (int i = 0; i < numValues; i++) {
      rtBuffer[i] = timeSeries.getRetentionTime(i);
    }
    return rtBuffer;
  }

}