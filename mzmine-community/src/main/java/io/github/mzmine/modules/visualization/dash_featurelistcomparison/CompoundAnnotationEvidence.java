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

package io.github.mzmine.modules.visualization.dash_featurelistcomparison;

import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.matched_levels.MatchedLipid;
import io.github.mzmine.util.spectraldb.entry.SpectralDBAnnotation;
import io.github.mzmine.util.annotations.CompoundAnnotationUtils;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.ClassyFireClassType;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable, lightweight annotation evidence copied from a feature-list row for the dashboard.
 * Annotation evidence labels MS2 components but never participates in component assignment.
 */
public record CompoundAnnotationEvidence(@NotNull String identityKey, @NotNull String displayName,
                                         @NotNull Source source, @NotNull String method,
                                         @Nullable String database, @Nullable String formula,
                                         @Nullable String adduct, @Nullable Float score,
                                         boolean analog, boolean preferred,
                                         @Nullable String chemicalClass) {

  public CompoundAnnotationEvidence {
    identityKey = identityKey.strip();
    displayName = displayName.strip();
    method = method.strip();
    database = clean(database);
    formula = clean(formula);
    adduct = clean(adduct);
    score = score != null && Float.isFinite(score) ? score : null;
    chemicalClass = clean(chemicalClass);
  }

  public CompoundAnnotationEvidence(@NotNull final String identityKey,
      @NotNull final String displayName, @NotNull final Source source, @NotNull final String method,
      @Nullable final String database, @Nullable final String formula,
      @Nullable final String adduct, @Nullable final Float score, final boolean analog,
      final boolean preferred) {
    this(identityKey, displayName, source, method, database, formula, adduct, score, analog,
        preferred, null);
  }

  static @NotNull CompoundAnnotationEvidence from(@NotNull final FeatureAnnotation annotation,
      final boolean preferred) {
    final Source source = Source.from(annotation);
    final String name = firstNonBlank(annotation.getCompoundName(),
        annotation.getBestNameIdentifier(), "Unnamed annotation");
    final String method = firstNonBlank(annotation.getAnnotationMethodName(), null, source.label());
    final String formula = clean(annotation.getFormula());
    final String adduct =
        annotation.getAdductType() == null ? null : annotation.getAdductType().toString();
    return new CompoundAnnotationEvidence(identityKey(annotation, name, formula), name, source,
        method, annotation.getDatabase(), formula, adduct, annotation.getScore(),
        annotation.isAnalogMatch(), preferred, chemicalClass(annotation));
  }

  private static @Nullable String chemicalClass(@NotNull final FeatureAnnotation annotation) {
    if (annotation instanceof MatchedLipid lipid) {
      return "Lipid: " + lipid.getLipidAnnotation().getLipidClass().getName();
    }
    final String classyFire = CompoundAnnotationUtils.getTypeValue(annotation,
        ClassyFireClassType.class);
    return classyFire == null || classyFire.isBlank() ? null : "ClassyFire: " + classyFire;
  }

  public @NotNull String sourceLabel() {
    return analog ? "Analog spectral library" : source.label();
  }

  int confidencePriority() {
    return analog ? 0 : source.priority();
  }

  private static @NotNull String identityKey(@NotNull final FeatureAnnotation annotation,
      @NotNull final String name, @Nullable final String formula) {
    final String inchiKey = clean(annotation.getInChIKey());
    if (inchiKey != null) {
      return "inchikey:" + inchiKey.toUpperCase(Locale.ROOT);
    }
    final String smiles = clean(annotation.getSmiles());
    if (smiles != null) {
      return "smiles:" + smiles;
    }
    final String inchi = clean(annotation.getInChI());
    if (inchi != null) {
      return "inchi:" + inchi;
    }
    if (!name.equals("Unnamed annotation")) {
      return "name:" + normalize(name) + (formula == null ? "" : "|formula:" + normalize(formula));
    }
    if (formula != null) {
      return "formula:" + normalize(formula);
    }
    final Double precursorMz = annotation.getPrecursorMZ();
    return "source:" + normalize(annotation.getAnnotationMethodUniqueId()) + "|mz:" + (
        precursorMz == null ? "unknown" : String.format(Locale.ROOT, "%.6f", precursorMz));
  }

  private static @NotNull String normalize(@NotNull final String value) {
    return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static @Nullable String clean(@Nullable final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }

  private static @NotNull String firstNonBlank(@Nullable final String first,
      @Nullable final String second, @NotNull final String fallback) {
    final String cleanFirst = clean(first);
    if (cleanFirst != null) {
      return cleanFirst;
    }
    final String cleanSecond = clean(second);
    return cleanSecond == null ? fallback : cleanSecond;
  }

  public enum Source {
    SPECTRAL_LIBRARY("Spectral library", 4), LIPID("Lipid annotation", 3), COMPOUND_DATABASE(
        "Compound database", 2), OTHER("Other annotation", 1);

    private final String label;
    private final int priority;

    Source(@NotNull final String label, final int priority) {
      this.label = label;
      this.priority = priority;
    }

    public @NotNull String label() {
      return label;
    }

    int priority() {
      return priority;
    }

    static @NotNull Source from(@NotNull final FeatureAnnotation annotation) {
      return switch (annotation) {
        case SpectralDBAnnotation _ -> SPECTRAL_LIBRARY;
        case MatchedLipid _ -> LIPID;
        case CompoundDBAnnotation _ -> COMPOUND_DATABASE;
        default -> OTHER;
      };
    }
  }
}
