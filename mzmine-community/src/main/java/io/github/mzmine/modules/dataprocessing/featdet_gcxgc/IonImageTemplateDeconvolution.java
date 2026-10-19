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

package io.github.mzmine.modules.dataprocessing.featdet_gcxgc;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;

/**
 * Deconvolutes GCxGC compounds directly from the 2D features of all m/z channels, like model peak
 * based GC-EI deconvolution (AMDIS) in two dimensions:
 * <ol>
 *   <li>Model peaks: the 2D features are visited from the most intense, in retention time
 *   segments in parallel. A feature that correlates
 *   with the template of a compound nearby (1D and 2D RT tolerances) is one of its ions. Otherwise,
 *   a clean single peak starts a new compound and its shape is the template.</li>
 *   <li>Overlaps: every other feature is fitted as a non-negative sum of the templates of the
 *   compounds that overlap it (non-negative least squares). Its intensity is shared between them
 *   by their fitted contributions, so a shared ion (e.g., m/z 73) or two merged peaks are split by
 *   the shapes of the other ions instead of a valley.</li>
 *   <li>Compounds keep the most intense part per m/z channel and need a minimum number of
 *   ions.</li>
 * </ol>
 */
final class IonImageTemplateDeconvolution {

  /**
   * decision: only clean single 2D peaks define the shape of a compound
   */
  static final double MODEL_SHAPE_QUALITY = 0.9;
  /**
   * decision: a fit of the templates must explain at least half of the variance of a feature,
   * otherwise its shape is unrelated to the compounds nearby
   */
  static final double MIN_FIT_QUALITY = 0.5;
  /**
   * decision: a compound gets a part of a feature only if the part holds at least this fraction of
   * the feature intensity, tiny fitted shares of neighboring compounds are fit noise
   */
  static final double MIN_PART_FRACTION = 0.1;
  private static final int NNLS_ITERATIONS = 200;
  /**
   * decision: retention time segments of this many tolerances are searched for model peaks in
   * parallel; the width does not depend on the number of threads, so the results do not either
   */
  static final int SEGMENT_TOLERANCES = 20;

  /**
   * decision: small fit jobs taken one by one from the queue. Features next to intense compounds
   * overlap hundreds of templates and take up to a second each; they are clustered in RT, so large
   * contiguous jobs left most threads idle.
   */
  private static final int FIT_JOB_SIZE = 32;

  private final int scansPerModulation;
  private final int rtToleranceModulations;
  private final int secondDimensionToleranceScans;
  private final double minCorrelation;
  private final int minSignals;
  private final int minPartSize;
  private final @NotNull GcxGcJobs jobs;

  /**
   * @param rtToleranceModulations        1D RT tolerance between ions of a compound in
   *                                      modulations
   * @param secondDimensionToleranceScans 2D RT tolerance between ions of a compound in scans
   * @param minCorrelation                minimum shape correlation of an ion with the template
   * @param minSignals                    minimum ions of a compound
   * @param minPartSize                   minimum signals of a feature part assigned by the fit
   * @param jobs                          runs the segments and fits in parallel
   */
  IonImageTemplateDeconvolution(final int scansPerModulation, final int rtToleranceModulations,
      final int secondDimensionToleranceScans, final double minCorrelation, final int minSignals,
      final int minPartSize, @NotNull final GcxGcJobs jobs) {
    this.scansPerModulation = scansPerModulation;
    this.rtToleranceModulations = Math.max(1, rtToleranceModulations);
    this.secondDimensionToleranceScans = Math.max(1, secondDimensionToleranceScans);
    this.minCorrelation = minCorrelation;
    this.minSignals = minSignals;
    this.minPartSize = minPartSize;
    this.jobs = jobs;
  }

  /**
   * @param progress counts each feature once for the model peaks and once for the fit
   * @return the ions of each compound, the model peak first
   */
  @NotNull List<List<IonImageFeature>> deconvolute(@NotNull final List<IonImageFeature> features,
      @NotNull final AtomicLong progress) {
    final List<IonImageFeature> sorted = new ArrayList<>(features);
    sorted.sort(Comparator.comparingDouble(IonImageFeature::height).reversed());

    // 1. model peaks and correlating ions in retention time segments, in the order of height
    // within each segment
    final int width = SEGMENT_TOLERANCES * (rtToleranceModulations + 1);
    final Int2ObjectRBTreeMap<List<IonImageFeature>> segments = new Int2ObjectRBTreeMap<>();
    for (final IonImageFeature feature : sorted) {
      segments.computeIfAbsent(feature.apexModulation() / width, _ -> new ArrayList<>())
          .add(feature);
    }
    // decision: even segments first, then odd segments, which also see the compounds of their
    // even neighbors. Only features within the tolerance of a segment border can differ from one
    // pass in the order of height over the whole run.
    final Long2ObjectOpenHashMap<List<IonImageComponent>> evenGrid = new Long2ObjectOpenHashMap<>();
    final Map<Integer, IonImageSegment> results = new TreeMap<>();
    for (final int parity : new int[]{0, 1}) {
      final List<Integer> keys = segments.keySet().intStream().filter(k -> k % 2 == parity)
          .boxed().toList();
      final Long2ObjectOpenHashMap<List<IonImageComponent>> fixed =
          parity == 0 ? new Long2ObjectOpenHashMap<>() : evenGrid;
      final List<Callable<IonImageSegment>> segmentJobs = new ArrayList<>(keys.size());
      for (final int key : keys) {
        segmentJobs.add(() -> modelPeaks(segments.get(key), fixed, progress));
      }
      final List<IonImageSegment> found = jobs.run("Finding GCxGC model peaks", segmentJobs);
      for (int i = 0; i < keys.size(); i++) {
        results.put(keys.get(i), found.get(i));
        if (parity == 0) {
          for (final IonImageComponent component : found.get(i).components()) {
            addToGrid(evenGrid, component);
          }
        }
      }
    }
    final List<IonImageComponent> components = new ArrayList<>();
    final List<IonImageFeature> others = new ArrayList<>();
    for (final IonImageSegment segment : results.values()) {
      components.addAll(segment.components());
      others.addAll(segment.others());
    }
    final Long2ObjectOpenHashMap<List<IonImageComponent>> grid = new Long2ObjectOpenHashMap<>();
    components.forEach(component -> addToGrid(grid, component));

    // 2. share every other feature between the compounds that overlap it
    final List<Callable<List<Map<IonImageComponent, IonImageFeature>>>> fitJobs = new ArrayList<>();
    for (final List<IonImageFeature> part : Lists.partition(others, FIT_JOB_SIZE)) {
      fitJobs.add(() -> {
        final List<Map<IonImageComponent, IonImageFeature>> fitted = new ArrayList<>(part.size());
        for (final IonImageFeature feature : part) {
          progress.incrementAndGet();
          fitted.add(fit(grid, feature));
        }
        return fitted;
      });
    }
    // models were counted for the fit stage as well
    progress.addAndGet(components.size());
    for (final List<Map<IonImageComponent, IonImageFeature>> fitted : jobs.run(
        "Fitting GCxGC compound shapes", fitJobs)) {
      for (final Map<IonImageComponent, IonImageFeature> featureParts : fitted) {
        featureParts.forEach(IonImageComponent::addMember);
      }
    }

    // 3. one part per m/z channel, minimum number of ions
    final List<List<IonImageFeature>> compounds = new ArrayList<>();
    for (final IonImageComponent component : components) {
      final Int2ObjectOpenHashMap<IonImageFeature> perChannel = new Int2ObjectOpenHashMap<>();
      for (final IonImageFeature member : component.members()) {
        perChannel.merge(member.channel(), member,
            (a, b) -> a.height() >= b.height() ? a : b);
      }
      if (perChannel.size() < minSignals) {
        continue;
      }
      final List<IonImageFeature> ions = new ArrayList<>(perChannel.size());
      final IonImageFeature model = component.model();
      ions.add(model);
      perChannel.values().stream().filter(f -> f != model)
          .sorted(Comparator.comparingDouble(IonImageFeature::height).reversed())
          .forEach(ions::add);
      compounds.add(ions);
    }
    return compounds;
  }

  /**
   * Model peak search of one segment: a feature that correlates with a compound nearby is an ion
   * of it, otherwise a clean single peak starts a new compound.
   *
   * @param features features of the segment sorted by descending height
   * @param fixed    compounds of other segments, only read
   */
  private @NotNull IonImageSegment modelPeaks(@NotNull final List<IonImageFeature> features,
      @NotNull final Long2ObjectOpenHashMap<List<IonImageComponent>> fixed,
      @NotNull final AtomicLong progress) {
    final List<IonImageComponent> components = new ArrayList<>();
    final Long2ObjectOpenHashMap<List<IonImageComponent>> local = new Long2ObjectOpenHashMap<>();
    final List<IonImageFeature> others = new ArrayList<>();
    for (final IonImageFeature feature : features) {
      progress.incrementAndGet();
      IonImageComponent best = null;
      double bestCorrelation = Double.NEGATIVE_INFINITY;
      for (final var grid : List.of(fixed, local)) {
        for (final IonImageComponent component : nearby(grid, feature)) {
          final double r = component.correlation(feature, scansPerModulation);
          if (r > bestCorrelation) {
            bestCorrelation = r;
            best = component;
          }
        }
      }
      if (best != null && bestCorrelation >= minCorrelation) {
        others.add(feature);
      } else if (feature.shapeQuality() >= MODEL_SHAPE_QUALITY) {
        final IonImageComponent component = new IonImageComponent(feature, scansPerModulation);
        components.add(component);
        addToGrid(local, component);
      } else {
        others.add(feature);
      }
    }
    return new IonImageSegment(components, others);
  }

  private void addToGrid(@NotNull final Long2ObjectOpenHashMap<List<IonImageComponent>> grid,
      @NotNull final IonImageComponent component) {
    grid.computeIfAbsent(cell(component.apexModulation(), component.apexScan()),
        _ -> new ArrayList<>()).add(component);
  }

  /**
   * Fits the feature as a non-negative sum of the overlapping templates and splits it by the
   * fitted contributions. Where the templates explain less than the measured intensity, the
   * contributions are kept and the rest is not assigned; where they explain more, the measured
   * intensity is shared in proportion.
   *
   * @return the part of the feature for each compound
   */
  private @NotNull Map<IonImageComponent, IonImageFeature> fit(
      @NotNull final Long2ObjectOpenHashMap<List<IonImageComponent>> grid,
      @NotNull final IonImageFeature feature) {
    final List<IonImageComponent> overlapping = nearby(grid, feature).stream()
        .filter(c -> c.overlaps(feature, scansPerModulation)).toList();
    final Map<IonImageComponent, IonImageFeature> result = new IdentityHashMap<>();
    if (overlapping.isEmpty()) {
      return result;
    }
    if (overlapping.size() == 1
        && overlapping.getFirst().correlation(feature, scansPerModulation) >= minCorrelation) {
      // an ion of a single compound keeps its measured shape
      result.put(overlapping.getFirst(), feature);
      return result;
    }
    final int n = feature.size();
    final int k = overlapping.size();
    final double[][] templates = new double[k][n];
    final double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      y[i] = feature.corrected(i);
      final long pixel = feature.pixel(i, scansPerModulation);
      for (int c = 0; c < k; c++) {
        templates[c][i] = overlapping.get(c).templateAt(pixel);
      }
    }
    final double[] a = nnls(templates, y);
    if (fitQuality(templates, a, y) < MIN_FIT_QUALITY) {
      return result;
    }
    double total = 0;
    for (final double v : y) {
      total += v;
    }

    for (int c = 0; c < k; c++) {
      if (a[c] <= 0) {
        continue;
      }
      final float[] part = new float[n];
      for (int i = 0; i < n; i++) {
        double model = 0;
        for (int j = 0; j < k; j++) {
          model += a[j] * templates[j][i];
        }
        final double contribution = a[c] * templates[c][i];
        // share the measured intensity, never more than measured
        part[i] = model <= 0 ? 0f : (float) (contribution * Math.min(1d, y[i] / model));
      }
      double partTotal = 0;
      for (final float v : part) {
        partTotal += v;
      }
      if (partTotal < MIN_PART_FRACTION * total) {
        continue;
      }
      final IonImageFeature split = feature.withCorrected(part);
      if (split.size() >= minPartSize && split.height() > 0) {
        result.put(overlapping.get(c), split);
      }
    }
    return result;
  }

  /**
   * Non-negative least squares by projected coordinate descent, few templates per feature
   *
   * @param columns the templates at the signals of the feature
   * @return the non-negative factor of each template
   */
  static double @NotNull [] nnls(final double @NotNull [][] columns, final double @NotNull [] y) {
    final int k = columns.length;
    final double[][] gram = new double[k][k];
    final double[] projection = new double[k];
    for (int a = 0; a < k; a++) {
      for (int b = a; b < k; b++) {
        double sum = 0;
        for (int i = 0; i < y.length; i++) {
          sum += columns[a][i] * columns[b][i];
        }
        gram[a][b] = sum;
        gram[b][a] = sum;
      }
      double sum = 0;
      for (int i = 0; i < y.length; i++) {
        sum += columns[a][i] * y[i];
      }
      projection[a] = sum;
    }
    final double[] x = new double[k];
    for (int iteration = 0; iteration < NNLS_ITERATIONS; iteration++) {
      double change = 0;
      for (int a = 0; a < k; a++) {
        if (gram[a][a] <= 0) {
          continue;
        }
        double residual = projection[a];
        for (int b = 0; b < k; b++) {
          if (b != a) {
            residual -= gram[a][b] * x[b];
          }
        }
        final double next = Math.max(0, residual / gram[a][a]);
        change = Math.max(change, Math.abs(next - x[a]));
        x[a] = next;
      }
      if (change <= 1e-9 * Math.max(1, x.length)) {
        break;
      }
    }
    return x;
  }

  /**
   * @return coefficient of determination of the template fit
   */
  private static double fitQuality(final double @NotNull [][] columns, final double @NotNull [] a,
      final double @NotNull [] y) {
    double mean = 0;
    for (final double v : y) {
      mean += v;
    }
    mean /= y.length;
    double residual = 0;
    double total = 0;
    for (int i = 0; i < y.length; i++) {
      double model = 0;
      for (int c = 0; c < a.length; c++) {
        model += a[c] * columns[c][i];
      }
      residual += (y[i] - model) * (y[i] - model);
      total += (y[i] - mean) * (y[i] - mean);
    }
    return total <= 0 ? 1 : 1 - residual / total;
  }

  /**
   * @return compounds whose apex is within the 1D and 2D RT tolerances of the feature apex
   */
  private @NotNull List<IonImageComponent> nearby(
      @NotNull final Long2ObjectOpenHashMap<List<IonImageComponent>> grid,
      @NotNull final IonImageFeature feature) {
    final List<IonImageComponent> result = new ArrayList<>();
    final int modulationCell = feature.apexModulation() / (rtToleranceModulations + 1);
    final int scanCell = feature.apexScan() / (secondDimensionToleranceScans + 1);
    for (int dm = -1; dm <= 1; dm++) {
      for (int ds = -1; ds <= 1; ds++) {
        final List<IonImageComponent> cell = grid.get(key(modulationCell + dm, scanCell + ds));
        if (cell == null) {
          continue;
        }
        for (final IonImageComponent component : cell) {
          if (Math.abs(component.apexModulation() - feature.apexModulation())
              <= rtToleranceModulations
              && Math.abs(component.apexScan() - feature.apexScan())
              <= secondDimensionToleranceScans) {
            result.add(component);
          }
        }
      }
    }
    return result;
  }

  private long cell(final int modulation, final int scan) {
    return key(modulation / (rtToleranceModulations + 1),
        scan / (secondDimensionToleranceScans + 1));
  }

  private static long key(final int modulationCell, final int scanCell) {
    return ((long) modulationCell << 32) | (scanCell & 0xffffffffL);
  }
}
