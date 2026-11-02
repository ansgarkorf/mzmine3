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

package io.github.mzmine.modules.visualization.pseudospectrumvisualizer;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.featuredata.impl.SummedIntensityMobilitySeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.series.IonTimeSeriesToXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.providers.impl.series.SummedMobilogramXYProvider;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredAreaShapeRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYLineRenderer;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.util.GcxGcUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

/**
 * Creates the second dimension retention time (2D RT) traces of a GCxGC pseudo spectrum, next to
 * the retention time chromatograms. Each trace is the sum of the 2D RT traces of all modulations of
 * the feature.
 */
final class SecondDimensionDatasetsBuilder {

  private SecondDimensionDatasetsBuilder() {
  }

  /**
   * The main feature is drawn as area, all other features as lines.
   *
   * @param main    the representative feature
   * @param members other features of the compound
   * @return the 2D RT datasets, empty if the main feature is no GCxGC feature
   */
  static @NotNull List<DatasetAndRenderer> fromFeatures(@NotNull final Feature main,
      @NotNull final List<? extends Feature> members, @NotNull final Color color) {
    if (!GcxGcUtils.isGcxGcFeature(main)) {
      return List.of();
    }
    final List<DatasetAndRenderer> datasets = new ArrayList<>();
    datasets.add(new DatasetAndRenderer(createDataset(summed(main), main.getMZ(), color),
        new ColoredAreaShapeRenderer()));
    members.stream().filter(GcxGcUtils::isGcxGcFeature)
        .sorted(Comparator.comparingDouble(Feature::getMZ)).forEach(
            member -> datasets.add(
                new DatasetAndRenderer(createDataset(summed(member), member.getMZ(), color),
                    new ColoredXYLineRenderer())));
    return datasets;
  }

  /**
   * Extracts the retention time and 2D RT traces of all signals of the pseudo spectrum from the
   * single scans of the modulations of the main feature within a 2D RT window around the feature.
   * The modulations have no spectra of their own.
   *
   * @param main     the feature with the pseudo spectrum
   * @param mzs      the signals of the pseudo spectrum
   * @param mzTol    tolerance to extract the signals
   * @param canceled stops the extraction
   * @return the datasets, empty if the main feature is no GCxGC feature
   */
  static @NotNull GcxGcPseudoSpectrumDatasets extract(@NotNull final Feature main,
      final double @NotNull [] mzs, @NotNull final MZTolerance mzTol, @NotNull final Color color,
      @NotNull final BooleanSupplier canceled) {
    final Range<Float> secondDimRange = main.getSecondDimensionRTRange();
    final FeatureList flist = main.getFeatureList();
    if (!GcxGcUtils.isGcxGcFeature(main) || secondDimRange == null || flist == null
        || !(main.getRawDataFile() instanceof IMSRawDataFile gcxgc)) {
      return GcxGcPseudoSpectrumDatasets.EMPTY;
    }
    // decision: show the 2D RT window around the feature with the same width on both sides
    final float width = secondDimRange.upperEndpoint() - secondDimRange.lowerEndpoint();
    final Range<Float> window = Range.closed(secondDimRange.lowerEndpoint() - width,
        secondDimRange.upperEndpoint() + width);
    final Range<Float> rtRange = main.getRawDataPointsRTRange();
    final List<Frame> modulations = flist.getSeletedScans(gcxgc).stream()
        .filter(scan -> rtRange.contains(scan.getRetentionTime())).map(Frame.class::cast)
        .toList();
    if (modulations.isEmpty()) {
      return GcxGcPseudoSpectrumDatasets.EMPTY;
    }
    final BinningMobilogramDataAccess binning = BinningMobilogramDataAccess.createWithPreviousParameters(
        gcxgc, flist);

    final List<DatasetAndRenderer> rtDatasets = new ArrayList<>();
    rtDatasets.add(new DatasetAndRenderer(
        createRtDataset(main.getFeatureData(), main.getMZ(), color),
        new ColoredAreaShapeRenderer()));
    final List<DatasetAndRenderer> datasets = new ArrayList<>();
    datasets.add(new DatasetAndRenderer(createDataset(summed(main), main.getMZ(), color),
        new ColoredAreaShapeRenderer()));
    for (final double mz : Arrays.stream(mzs).sorted().toArray()) {
      if (canceled.getAsBoolean()) {
        return GcxGcPseudoSpectrumDatasets.EMPTY;
      }
      final IonMobilogramTimeSeries trace = IonTimeSeriesUtils.extractIonMobilogramTimeSeries(
          new MobilityScanDataAccess(gcxgc, MobilityScanDataType.MASS_LIST, modulations),
          mzTol.getToleranceRange(mz), rtRange, window, null, binning);
      // signals without data points in the 2D RT window of this feature are not shown
      final SummedIntensityMobilitySeries summed = trace.getSummedMobilogram();
      if (hasIntensity(summed)) {
        datasets.add(new DatasetAndRenderer(createDataset(summed, mz, color),
            new ColoredXYLineRenderer()));
        // the modulation sums of the same signal within the 2D RT window
        rtDatasets.add(new DatasetAndRenderer(createRtDataset(trace, mz, color),
            new ColoredXYLineRenderer()));
      }
    }
    return new GcxGcPseudoSpectrumDatasets(rtDatasets, datasets);
  }

  private static @NotNull ColoredXYDataset createRtDataset(
      @NotNull final IonTimeSeries<? extends Scan> trace, final double mz,
      @NotNull final Color color) {
    return new ColoredXYDataset(
        new IonTimeSeriesToXYProvider(trace, ConfigService.getGuiFormats().mz(mz), color),
        RunOption.THIS_THREAD);
  }

  private static boolean hasIntensity(@NotNull final SummedIntensityMobilitySeries trace) {
    for (int i = 0; i < trace.getNumberOfValues(); i++) {
      if (trace.getIntensity(i) > 0) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull SummedIntensityMobilitySeries summed(@NotNull final Feature feature) {
    return ((IonMobilogramTimeSeries) feature.getFeatureData()).getSummedMobilogram();
  }

  private static @NotNull ColoredXYDataset createDataset(
      @NotNull final SummedIntensityMobilitySeries trace, final double mz,
      @NotNull final Color color) {
    final String label = ConfigService.getGuiFormats().mz(mz);
    return new ColoredXYDataset(
        new SummedMobilogramXYProvider(trace, new SimpleObjectProperty<>(color), label),
        RunOption.THIS_THREAD);
  }
}
