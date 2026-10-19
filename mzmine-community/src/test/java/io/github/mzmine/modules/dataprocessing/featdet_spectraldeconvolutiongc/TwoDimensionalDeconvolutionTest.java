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

package io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.twodgroupingandshapecorrelation.TwoDimensionalGroupingAndShapeCorrelationAlgorithm;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * GCxGC deconvolution with synthetic 2D peaks: frames are modulations, mobility scans are the
 * scans within a modulation (second dimension).
 */
class TwoDimensionalDeconvolutionTest {

  private static final int FRAMES = 6;
  private static final int SCANS_PER_FRAME = 40;
  // 3 s modulation, 5 ms per scan
  private static final float MODULATION_MIN = 0.05f;
  // 2D RT in minutes
  private static final float SCAN_MINUTES = 0.005f / 60f;

  private final List<Frame> frames = createFrames();

  @Test
  void sameShapeCorrelatesWithNewScanInstances() {
    // mobility scans are created on access, the same data point must still match
    final IonMobilogramTimeSeries main = peak(2.5, 20, 1000);
    final IonMobilogramTimeSeries fragment = peak(2.5, 20, 50);
    // the smaller fragment misses the lowest edge points (zero), so not exactly 1
    final double r = SpectralDeconvolutionUtils.calculateShapeCorrelation2D(main, fragment);
    Assertions.assertTrue(r > 0.99, "r = " + r);
  }

  @Test
  void shiftInSecondDimensionIsPenalized() {
    final IonMobilogramTimeSeries main = peak(2.5, 12, 1000);
    final IonMobilogramTimeSeries shifted = peak(2.5, 28, 1000);
    Assertions.assertTrue(
        SpectralDeconvolutionUtils.calculateShapeCorrelation2D(main, shifted) < 0.2);
  }

  @Test
  void coElutingCompoundsAreSeparatedInSecondDimension() {
    final var algorithm = new TwoDimensionalGroupingAndShapeCorrelationAlgorithm(
        new RTTolerance(0.06f, Unit.MINUTES), new RTTolerance(0.05f, Unit.SECONDS), 2, 0.8);
    // compound A at 2D scan 10, compound B at 2D scan 30 in the same modulation
    final List<ModularFeature> features = new ArrayList<>(List.of( //
        feature(2.5, 10, 1000), feature(2.5, 10, 300), feature(2.5, 10, 100), //
        feature(2.5, 30, 800), feature(2.5, 30, 200)));
    final List<List<ModularFeature>> groups = algorithm.groupFeatures(features);
    Assertions.assertEquals(2, groups.size());
    Assertions.assertEquals(List.of(3, 2), groups.stream().map(List::size).sorted(
        (a, b) -> b - a).toList());
  }

  @Test
  void uncorrelatedFeatureStartsNewCompound() {
    // within both tolerances, but a broad feature that does not correlate with the narrow peak
    final var algorithm = new TwoDimensionalGroupingAndShapeCorrelationAlgorithm(
        new RTTolerance(0.06f, Unit.MINUTES), new RTTolerance(0.1f, Unit.SECONDS), 1, 0.8);
    final ModularFeature narrow = feature(2.5, 20, 1000, 1.5);
    final ModularFeature broad = feature(2.5, 22, 500, 8);
    final List<List<ModularFeature>> groups = algorithm.groupFeatures(
        new ArrayList<>(List.of(narrow, broad)));
    Assertions.assertEquals(2, groups.size());
  }

  private ModularFeature feature(double apexFrame, double apexScan, double height) {
    return feature(apexFrame, apexScan, height, 3);
  }

  private ModularFeature feature(double apexFrame, double apexScan, double height,
      double scanSigma) {
    final ModularFeature feature = mock(ModularFeature.class);
    final IonMobilogramTimeSeries data = peak(apexFrame, apexScan, height, scanSigma);
    when(feature.getFeatureData()).thenAnswer(_ -> data);
    when(feature.getRT()).thenReturn((float) (apexFrame * MODULATION_MIN));
    when(feature.getSecondDimensionRT()).thenReturn((float) (apexScan * SCAN_MINUTES));
    when(feature.getHeight()).thenReturn((float) height);
    return feature;
  }

  private IonMobilogramTimeSeries peak(double apexFrame, double apexScan, double height) {
    return peak(apexFrame, apexScan, height, 3);
  }

  /**
   * Gaussian 2D peak with 1 modulation sigma in the first dimension
   */
  private IonMobilogramTimeSeries peak(double apexFrame, double apexScan, double height,
      double scanSigma) {
    final List<IonMobilitySeries> mobilograms = new ArrayList<>();
    for (int f = 0; f < FRAMES; f++) {
      final double frameFactor = Math.exp(-0.5 * Math.pow(f - apexFrame, 2));
      final List<MobilityScan> scans = new ArrayList<>();
      final List<Double> intensities = new ArrayList<>();
      for (int s = 0; s < SCANS_PER_FRAME; s++) {
        final double intensity =
            height * frameFactor * Math.exp(-0.5 * Math.pow((s - apexScan) / scanSigma, 2));
        // like a noise level after mass detection
        if (intensity > 1) {
          scans.add(mobilityScan(frames.get(f), s));
          intensities.add(intensity);
        }
      }
      mobilograms.add(mobilogram(scans, intensities));
    }
    final IonMobilogramTimeSeries series = mock(IonMobilogramTimeSeries.class);
    when(series.getMobilograms()).thenReturn(mobilograms);
    return series;
  }

  private static @NotNull IonMobilitySeries mobilogram(@NotNull List<MobilityScan> scans,
      @NotNull List<Double> intensities) {
    final IonMobilitySeries mobilogram = mock(IonMobilitySeries.class);
    when(mobilogram.getNumberOfValues()).thenReturn(scans.size());
    for (int i = 0; i < scans.size(); i++) {
      when(mobilogram.getSpectrum(i)).thenReturn(scans.get(i));
      when(mobilogram.getIntensity(i)).thenReturn(intensities.get(i));
    }
    return mobilogram;
  }

  /**
   * A new instance for every data point, like mobility scans created on access
   */
  private static @NotNull MobilityScan mobilityScan(@NotNull Frame frame, int scanNumber) {
    final MobilityScan scan = mock(MobilityScan.class);
    when(scan.getFrame()).thenReturn(frame);
    when(scan.getMobilityScanNumber()).thenReturn(scanNumber);
    return scan;
  }

  private static @NotNull List<Frame> createFrames() {
    final List<Frame> frames = new ArrayList<>();
    for (int f = 0; f < FRAMES; f++) {
      final Frame frame = mock(Frame.class);
      when(frame.getFrameId()).thenReturn(f);
      frames.add(frame);
    }
    return frames;
  }
}
