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

package io.github.mzmine.modules.visualization.intensitymap;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapPerspective;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapProjection;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapRegion;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapTopView;
import io.github.mzmine.modules.visualization.intensitymap.plot.IntensityMapViewport;
import io.github.mzmine.modules.visualization.intensitymap.sampling.IntensityMapSampler;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.combowithinput.MsLevelFilter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.GcxGcUtils;
import io.github.mzmine.util.RangeUtils;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The plot of the 2D or 3D visualizer of one raw data file embedded in another view, e.g., the
 * compound dashboard, zoomed to a group of features with their stored data drawn over the raw
 * data. Only the plot is shown, without the toolbar, overlay panel, spectrum and footer. Close it
 * when it is replaced.
 */
public final class EmbeddedIntensityMap {

  // decision: the view shows the features with a margin of at least this share of their span on
  // each side, so that neighboring compounds stay in view
  private static final double MARGIN_SHARE = 1.0;
  private static final double MIN_RT_MARGIN = 0.25;
  // share of the mobility or 2D RT range of the file
  private static final double MIN_SECOND_DIMENSION_MARGIN_SHARE = 0.05;
  // margin of the m/z range of an ion
  private static final double ION_MZ_PPM = 10;
  private static final double MIN_ION_MZ_MARGIN = 0.001;

  private final @NotNull IntensityMapTab map;

  private EmbeddedIntensityMap(@NotNull final IntensityMapTab map) {
    this.map = map;
  }

  /**
   * @return true if the platform can show the visualizer
   */
  public static boolean isSupported() {
    return Platform.isSupported(ConditionalFeature.SCENE3D);
  }

  /**
   * Creates the visualizer of one raw data file around features of this file. Mobility data and
   * GCxGC data show retention time against mobility or 2D RT, all other data retention time against
   * m/z.
   *
   * @param features   features of the file, e.g., the ions of a compound
   * @param projection the 2D or 3D view
   * @param labelList  feature list of the peak labels, null for none
   * @param labelRows  only these rows of the label list get labels, null for all rows
   * @param ionMzRange m/z range of the shown ion for retention time against mobility or 2D RT,
   *                   see {@link #ionMzRange(Feature)}; null sums all m/z. Ignored for retention
   *                   time against m/z.
   * @return the view or null if 3D is not supported or no feature has a retention time range
   */
  public static @Nullable EmbeddedIntensityMap forFeatures(@NotNull final RawDataFile file,
      @NotNull final List<? extends Feature> features,
      @NotNull final IntensityMapProjection projection, @Nullable final FeatureList labelList,
      @Nullable final Set<FeatureListRow> labelRows, @Nullable final Range<Double> ionMzRange) {
    if (!isSupported()) {
      return null;
    }
    final List<? extends Feature> shown = shownFeatures(features);
    final ParameterSet parameters = parameters(file, shown, projection, ionMzRange);
    if (parameters == null) {
      return null;
    }
    final List<Range<Double>> mzRanges = ionMzRanges(file, ionMzRange);
    final IntensityMapTab map = new IntensityMapTab(new RawDataFile[]{file}, parameters,
        mzRanges, projection, labelList, shown);
    // decision (user): the dashboard side pane shows only the plot, without the controls
    map.setCompact(true);
    map.setLabelRows(labelRows);
    return new EmbeddedIntensityMap(map);
  }

  private static @NotNull List<? extends Feature> shownFeatures(
      @NotNull final List<? extends Feature> features) {
    return features.stream()
        .filter(f -> f.getFeatureStatus() != FeatureStatus.UNKNOWN)
        .filter(f -> f.getRawDataPointsRTRange() != null && f.getRawDataPointsMZRange() != null)
        .toList();
  }

  /**
   * @return the visualizer settings: the file, retention time against mobility or 2D RT for
   * mobility and GCxGC data, otherwise against m/z, the window around the features. Null if no
   * feature can be shown.
   */
  static @Nullable ParameterSet parameters(@NotNull final RawDataFile file,
      @NotNull final List<? extends Feature> features,
      @NotNull final IntensityMapProjection projection, @Nullable final Range<Double> ionMzRange) {
    final List<? extends Feature> shown = shownFeatures(features);
    if (shown.isEmpty()) {
      return null;
    }
    final Range<Float> featureRt = shown.stream().map(Feature::getRawDataPointsRTRange)
        .reduce(Range::span).orElseThrow();
    final Range<Double> rtRange = expand(RangeUtils.toDoubleRange(featureRt), MIN_RT_MARGIN,
        RangeUtils.toDoubleRange(file.getDataRTRange(1)));

    final boolean mobilityData = file instanceof IMSRawDataFile;
    Range<Double> mobilityRange = null;
    if (mobilityData) {
      final Range<Double> dataMobility = ((IMSRawDataFile) file).getDataMobilityRange();
      mobilityRange = shown.stream().map(GcxGcUtils::getSecondDimensionRange)
          .filter(Objects::nonNull).map(RangeUtils::toDoubleRange).reduce(Range::span)
          .map(range -> expand(range,
              RangeUtils.rangeLength(dataMobility) * MIN_SECOND_DIMENSION_MARGIN_SHARE,
              dataMobility)).orElse(null);
    }

    final Class<? extends MZmineRunnableModule> module = switch (projection) {
      case IntensityMapTopView _ -> IntensityMap2DModule.class;
      case IntensityMapPerspective _ -> IntensityMap3DModule.class;
    };
    // decision: a copy of the module settings, so the feature window does not become the default
    final ParameterSet parameters = ConfigService.getConfiguration().getModuleParameters(module)
        .cloneParameterSet();
    parameters.getParameter(IntensityMapParameters.dataFile)
        .setValue(RawDataFilesSelectionType.SPECIFIC_FILES, new RawDataFile[]{file});
    parameters.getParameter(IntensityMapParameters.mode).setValue(
        mobilityRange != null ? IntensityMapDimensions.RT_MOBILITY
            : IntensityMapDimensions.AUTOMATIC);
    final PolarityType polarity = shown.getFirst().getRepresentativePolarity();
    parameters.getParameter(IntensityMapParameters.scanSelection).setValue(
        new ScanSelection(null, null, rtRange, mobilityRange,
            Objects.requireNonNullElse(polarity, PolarityType.ANY), MassSpectrumType.ANY,
            MsLevelFilter.of(1), null));
    // decision (user): retention time against mobility or 2D RT shows the m/z range of the
    // selected ion, otherwise all m/z like a TIC; retention time against m/z shows the m/z range
    // of the features
    final Range<Double> mzRange = mobilityRange != null ? Objects.requireNonNullElse(ionMzRange,
        IntensityMapSampler.dataMzRange(file))
        : expand(shown.stream().map(Feature::getRawDataPointsMZRange).reduce(Range::span)
            .orElseThrow(), 5d, null);
    parameters.getParameter(IntensityMapParameters.mzRange).setValue(mzRange);
    return parameters;
  }

  /**
   * @return the range with a margin of the larger of its span and the minimum margin on each side,
   * limited to the bounds
   */
  private static @NotNull Range<Double> expand(@NotNull final Range<Double> range,
      final double minMargin, @Nullable final Range<Double> bounds) {
    final double margin = Math.max(minMargin, RangeUtils.rangeLength(range) * MARGIN_SHARE);
    final Range<Double> expanded = Range.closed(range.lowerEndpoint() - margin,
        range.upperEndpoint() + margin);
    return bounds == null || !bounds.isConnected(expanded) ? expanded
        : expanded.intersection(bounds);
  }

  /**
   * @param rows only these rows of the label list get labels, null for all rows
   */
  public void setLabelRows(@Nullable final Set<FeatureListRow> rows) {
    map.setLabelRows(rows);
  }

  /**
   * Shows another ion for retention time against mobility or 2D RT
   *
   * @param ionMzRange m/z range of the ion, null for all m/z
   */
  public void setIonMzRange(@Nullable final Range<Double> ionMzRange) {
    map.showMzRanges(ionMzRanges(map.getRawDataFiles().iterator().next(), ionMzRange));
  }

  /**
   * @return the m/z range of the ion of a feature, the m/z range of its data points with a margin
   * of 10 ppm, at least 0.001
   */
  public static @NotNull Range<Double> ionMzRange(@NotNull final Feature feature) {
    final Range<Double> range = Objects.requireNonNullElse(feature.getRawDataPointsMZRange(),
        Range.singleton(feature.getMZ()));
    final double margin = Math.max(MIN_ION_MZ_MARGIN,
        range.upperEndpoint() * ION_MZ_PPM * 1e-6);
    return Range.closed(Math.max(0, range.lowerEndpoint() - margin),
        range.upperEndpoint() + margin);
  }

  /**
   * @return the overlay of the ion for retention time against mobility or 2D RT, empty for the
   * all-m/z overlay and for retention time against m/z
   */
  private static @NotNull List<Range<Double>> ionMzRanges(@NotNull final RawDataFile file,
      @Nullable final Range<Double> ionMzRange) {
    return ionMzRange != null && file instanceof IMSRawDataFile ? List.of(ionMzRange) : List.of();
  }

  /**
   * @param regions data windows to mark with a solid outline, e.g. the selected compound
   */
  public void setMarkedRegions(@NotNull final List<IntensityMapRegion> regions) {
    map.setMarkedRegions(regions);
  }

  /**
   * @param listener gets the axis ranges and plot area of the 2D view after every change, e.g. to
   *                 synchronize the axes of neighboring charts; null removes it
   */
  public void setOnViewportChanged(@Nullable final Consumer<IntensityMapViewport> listener) {
    map.setOnViewportChanged(listener);
  }

  /**
   * For a 2D view next to a chart that shows the same y axis: hides the y axis and lets the plot
   * area fill the view, with independent scales of both axes
   *
   * @param shared true if a neighboring chart shows the y axis
   */
  public void setYAxisShared(final boolean shared) {
    map.setYAxisShared(shared);
  }

  /**
   * Zooms the 2D view to the data window. Both axes keep the same scale, so the view may show more
   * of one axis; the viewport listener gets what is shown.
   *
   * @param region data window, a null range keeps the shown range of its axis
   */
  public void showRegion(@NotNull final IntensityMapRegion region) {
    map.showRegion(region);
  }

  /**
   * @return the view to place in a layout
   */
  public @NotNull Node view() {
    return map.getContent();
  }

  /**
   * Stops loading and releases the view
   */
  public void close() {
    map.dispose();
  }
}
