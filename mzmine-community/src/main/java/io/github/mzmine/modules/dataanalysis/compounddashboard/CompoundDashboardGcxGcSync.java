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

package io.github.mzmine.modules.dataanalysis.compounddashboard;

import com.google.common.collect.Range;
import io.github.mzmine.javafx.concurrent.threading.FxThread;
import io.github.mzmine.modules.visualization.intensitymap.EmbeddedIntensityMap;
import io.github.mzmine.modules.visualization.intensitymap.data.IntensityMapRegion;
import io.github.mzmine.modules.visualization.intensitymap.plot.IntensityMapViewport;
import io.github.mzmine.modules.visualization.otherdetectors.chromatogramplot.ChromatogramPlotController;
import javafx.geometry.Bounds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.AxisSpace;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.ChartProgressEvent;
import org.jfree.chart.fx.ChartCanvas;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleInsets;

/**
 * GCxGC layout of the compound dashboard: the 2D plot (retention time x 2D RT) in the center, the
 * chromatograms on top and the 2D RT shapes on its left, rotated. The 2D plot leads: both charts
 * show its axis ranges, and zooming a chart zooms the 2D plot (both of its axes, they share one
 * scale). Both charts align their data areas with the plot area of the 2D plot.
 */
final class CompoundDashboardGcxGcSync {

  // decision: alignment differences up to a pixel are not corrected, each correction redraws and
  // the data area is rounded to pixels
  private static final double ALIGN_TOLERANCE = 1.0;
  // decision: relative difference of axis ranges that counts as a zoom of the user
  private static final double RANGE_TOLERANCE = 1e-6;

  private final @NotNull ChromatogramPlotController chromatograms;
  private final @NotNull ChromatogramPlotController secondDimension;
  private @Nullable EmbeddedIntensityMap map;
  private @Nullable IntensityMapViewport viewport;
  private boolean active;
  // true while the axes are set to the 2D plot, so the change is not sent back
  private boolean syncing;

  /**
   * @param chromatograms   the chromatograms along the retention time, above the 2D plot
   * @param secondDimension the 2D RT shapes, left of the 2D plot
   */
  CompoundDashboardGcxGcSync(@NotNull final ChromatogramPlotController chromatograms,
      @NotNull final ChromatogramPlotController secondDimension) {
    this.chromatograms = chromatograms;
    this.secondDimension = secondDimension;
    chromatograms.getXYPlot().getDomainAxis()
        .addChangeListener(_ -> onAxisChanged(chromatograms, true));
    secondDimension.getXYPlot().getDomainAxis()
        .addChangeListener(_ -> onAxisChanged(secondDimension, false));
    chromatograms.getChart().getChart().addProgressListener(event -> {
      if (event.getType() == ChartProgressEvent.DRAWING_FINISHED) {
        align(chromatograms, true);
      }
    });
    secondDimension.getChart().getChart().addProgressListener(event -> {
      if (event.getType() == ChartProgressEvent.DRAWING_FINISHED) {
        align(secondDimension, false);
      }
    });
  }

  /**
   * Switches the charts between the GCxGC layout and the default layout of chromatograms and
   * mobilograms.
   *
   * @param gcxgc true for the GCxGC layout
   */
  void setActive(final boolean gcxgc) {
    active = gcxgc;
    final XYPlot plot = secondDimension.getXYPlot();
    // decision (user): the 2D RT runs vertically next to the 2D plot, intensities grow to the left.
    // The axes are shared like in the IMS trace visualizer: the 2D RT shapes show the 2D RT axis on
    // their outer side, the 2D plot the retention time axis below, the chromatograms none.
    plot.setOrientation(gcxgc ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL);
    plot.getRangeAxis().setInverted(gcxgc);
    plot.setDomainAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
    if (gcxgc) {
      // horizontal orientation: the domain axis on the left
      plot.setDomainAxisLocation(AxisLocation.TOP_OR_LEFT);
    }
    chromatograms.getXYPlot().getDomainAxis().setVisible(!gcxgc);
    if (!gcxgc) {
      setMap(null);
      chromatograms.getXYPlot().setFixedRangeAxisSpace(null);
      plot.setFixedRangeAxisSpace(null);
      chromatograms.applyAutoRangeToDomainAxis();
      secondDimension.applyAutoRangeToDomainAxis();
    }
  }

  /**
   * @param map the 2D plot of the GCxGC layout, null if there is none
   */
  void setMap(@Nullable final EmbeddedIntensityMap map) {
    final EmbeddedIntensityMap previous = this.map;
    if (previous != null) {
      previous.setOnViewportChanged(null);
    }
    this.map = map;
    viewport = null;
    if (map != null && active) {
      map.setOnViewportChanged(this::onViewportChanged);
    }
  }

  /**
   * Applies the axis ranges of the 2D plot again, e.g. after new datasets reset the axes
   */
  void applyViewport() {
    final IntensityMapViewport current = viewport;
    if (!active || current == null) {
      return;
    }
    syncing = true;
    try {
      setRange(chromatograms.getXYPlot().getDomainAxis(), current.region().x());
      setRange(secondDimension.getXYPlot().getDomainAxis(), current.region().y());
    } finally {
      syncing = false;
    }
    align(chromatograms, true);
    align(secondDimension, false);
  }

  private void onViewportChanged(@NotNull final IntensityMapViewport viewport) {
    this.viewport = viewport;
    applyViewport();
  }

  /**
   * A zoom of the user in a chart zooms the 2D plot, which then sets both charts. Automatic ranges,
   * e.g. after new datasets, are replaced by the ranges of the 2D plot.
   *
   * @param retentionTime true for the chromatograms, false for the 2D RT shapes
   */
  private void onAxisChanged(@NotNull final ChromatogramPlotController chart,
      final boolean retentionTime) {
    final IntensityMapViewport current = viewport;
    final EmbeddedIntensityMap target = map;
    if (syncing || !active || current == null || target == null) {
      return;
    }
    final ValueAxis axis = chart.getXYPlot().getDomainAxis();
    if (axis.isAutoRange()) {
      // the 2D plot leads, after the axis changes are done
      FxThread.runLater(this::applyViewport);
      return;
    }
    final Range<Double> shown = retentionTime ? current.region().x() : current.region().y();
    final Range<Double> other = retentionTime ? current.region().y() : current.region().x();
    final Range<Double> zoomed = Range.closed(axis.getLowerBound(), axis.getUpperBound());
    if (shown == null || other == null || sameRange(shown, zoomed)) {
      return;
    }
    // decision: both axes of the 2D plot have the same scale, so the other axis zooms by the same
    // factor around its center
    final double factor = length(zoomed) / Math.max(length(shown), Double.MIN_VALUE);
    final double center = (other.lowerEndpoint() + other.upperEndpoint()) / 2;
    final double half = length(other) * factor / 2;
    final Range<Double> scaled = Range.closed(center - half, center + half);
    target.showRegion(retentionTime ? new IntensityMapRegion(zoomed, scaled)
        : new IntensityMapRegion(scaled, zoomed));
  }

  private static double length(@NotNull final Range<Double> range) {
    return range.upperEndpoint() - range.lowerEndpoint();
  }

  /**
   * Sets fixed axis spaces, so that the data area of the chart lines up with the plot area of the
   * 2D plot: horizontally for the chromatograms on top, vertically for the 2D RT shapes on the
   * left.
   */
  private void align(@NotNull final ChromatogramPlotController chart, final boolean horizontal) {
    final IntensityMapViewport current = viewport;
    if (!active || current == null) {
      return;
    }
    final ChartCanvas canvas = chart.getChart().getCanvas();
    final ChartRenderingInfo info = canvas.getRenderingInfo();
    if (info == null || canvas.getScene() == null) {
      return;
    }
    final var plotArea = info.getPlotInfo().getPlotArea();
    final var dataArea = info.getPlotInfo().getDataArea();
    final Bounds target = canvas.sceneToLocal(current.plotArea());
    if (plotArea == null || dataArea == null || target == null || dataArea.isEmpty()) {
      return;
    }
    final XYPlot plot = chart.getXYPlot();
    final AxisSpace previous = plot.getFixedRangeAxisSpace();
    // decision: the drawn data area moves with the fixed axis space one to one, the difference to
    // the target corrects it; the first estimate uses the axis offset only, the plot area of the
    // rendering info is already inside the plot insets
    final RectangleInsets offset = plot.getAxisOffset();
    final AxisSpace space = new AxisSpace();
    if (horizontal) {
      space.setLeft(Math.max(0, previous != null ? previous.getLeft() + target.getMinX()
          - dataArea.getMinX() : target.getMinX() - plotArea.getMinX() - offset.getLeft()));
      space.setRight(Math.max(0, previous != null ? previous.getRight() + dataArea.getMaxX()
          - target.getMaxX() : plotArea.getMaxX() - offset.getRight() - target.getMaxX()));
    } else {
      space.setTop(Math.max(0, previous != null ? previous.getTop() + target.getMinY()
          - dataArea.getMinY() : target.getMinY() - plotArea.getMinY() - offset.getTop()));
      space.setBottom(Math.max(0, previous != null ? previous.getBottom() + dataArea.getMaxY()
          - target.getMaxY() : plotArea.getMaxY() - offset.getBottom() - target.getMaxY()));
    }
    if (previous != null && Math.abs(previous.getLeft() - space.getLeft()) < ALIGN_TOLERANCE
        && Math.abs(previous.getRight() - space.getRight()) < ALIGN_TOLERANCE
        && Math.abs(previous.getTop() - space.getTop()) < ALIGN_TOLERANCE
        && Math.abs(previous.getBottom() - space.getBottom()) < ALIGN_TOLERANCE) {
      return;
    }
    // redraws the chart, the next alignment finds no difference
    plot.setFixedRangeAxisSpace(space);
  }

  private static void setRange(@NotNull final ValueAxis axis, @Nullable final Range<Double> range) {
    if (range == null || !(range.upperEndpoint() > range.lowerEndpoint())) {
      return;
    }
    axis.setRange(new org.jfree.data.Range(range.lowerEndpoint(), range.upperEndpoint()), true,
        true);
  }

  private static boolean sameRange(@NotNull final Range<Double> a, @NotNull final Range<Double> b) {
    final double tolerance =
        RANGE_TOLERANCE * Math.max(a.upperEndpoint() - a.lowerEndpoint(), Double.MIN_VALUE);
    return Math.abs(a.lowerEndpoint() - b.lowerEndpoint()) <= tolerance
        && Math.abs(a.upperEndpoint() - b.upperEndpoint()) <= tolerance;
  }
}
