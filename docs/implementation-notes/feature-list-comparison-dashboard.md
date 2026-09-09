# Feature list comparison dashboard

## Intention

Compare independently processed feature lists at portfolio scale without assuming that retention
times are comparable between chromatographic methods. Each selected MZmine feature list represents
one analysis project, and the dashboard uses its representative MS2 spectra as the shared evidence.

## Decisions

- Retention time is not used for cross-feature-list identity or similarity. It may be added later as
  contextual evidence for feature lists produced with the same method.
- Candidate spectrum pairs are restricted by precursor m/z and compatible known polarity/charge,
  then scored from filtered fragment spectra with weighted cosine similarity and a minimum
  matched-signal count.
- A single deterministic component assignment is created across all selected feature lists. A
  spectrum must match a fixed component representative, which avoids transitive single-link
  chaining. Project similarity is the Jaccard overlap of these global component-presence sets.
- MZmine compound annotations are copied as immutable contextual evidence for each component
  member. Preferred spectral-library, lipid, compound-database, and other annotations are shown
  first; additional ranked candidates, including analog-library matches, retain their source,
  method/database, formula, adduct, and score. Annotation data never merges or splits an MS2
  component.
- Component annotation consensus is recalculated for the current project selection. Structural
  identifiers are preferred for identity reconciliation, support is counted once per project, and
  cross-project disagreements are surfaced as conflicts instead of being silently collapsed.
  Analog matches remain member context, not exact-identity consensus. Retention-time values on
  annotations do not contribute to this consensus. Consistent lipid, ClassyFire, and NPClassifier
  classes support a component-level log2-odds enrichment view. Conflicting and unclassified
  components are disclosed but excluded from the annotated denominator.
- Missing MS2 acquisition, unusable spectra, and missing mass lists remain separate from chemical
  absence. The dashboard exposes usable-MS2 coverage next to similarity values.
- Projects are average-linkage clustered from Jaccard distance. The clustered heatmap and
  dendrogram share the same project order. The project UMAP uses the same distance matrix and a
  deterministic fuzzy-neighbor optimization. Optional abundance landscapes use Bray–Curtis on
  shared, quantified component medians; the landscape is unavailable when any project pair has
  no usable shared signal. Missing pairs are never imputed as maximal distance.
- The dashboard follows one overview-to-detail flow instead of nesting plots in tabs. The clustered
  project matrix, dendrogram, UMAP, feature-level recurrence map, adaptive cohort intersections,
  method-aware evidence matrix, ranked table, and spectrum inspector remain visible in coordinated
  split panes. The global prevalence histogram, threshold-sensitivity curve, generic importance bars,
  and class treemap are removed. Chemical-class enrichment replaces the treemap because it exposes
  relative over-representation without using area to encode both abundance and hierarchy.
- Five styled cards establish the workflow hierarchy: overview, project selection, historical
  context, pattern/feature prioritization, and evidence verification. Chart headings are left aligned, secondary guidance
  is visually subdued, and compound-annotation consensus/conflicts receive explicit emphasis.
- Clicking a matrix cell selects its project pair. Shift/Cmd-clicking matrix cells or UMAP points
  extends the selection to multiple projects; clicking a diagonal cell selects one project. The
  shared selection drives summary counts, feature ranking, and component-member ordering.
  Historical projects can be pinned as reference A, with subsequent selections defining query B;
  both groups can contain multiple projects. Pinning is session-local, not a persistent archive.
  A shortcut pins all projects outside the current selection. Without a pinned reference, a selected
  pair uses A/B; a single or larger subset uses unselected projects as recurrence context.
- The recurrence map has one point per component in the query. Its x coordinate is the fraction of
  eligible historical projects with an MS2 match; its y coordinate is query-sample detection, and
  color identifies annotation evidence or conflict. Reference eligibility is derived conservatively
  from each raw file's acquired MS1 m/z range and polarity. Projects outside that scope or with
  unknown coverage are counted separately and excluded from the denominator. A left-edge point means
  unseen in eligible references, not proven chemical novelty.
- Drag-to-zoom followed by **Filter visible region** provides a rectangular feature brush. Direct
  clicks select one component, while Shift/Cmd-click accumulates a component scope.
- Cohort intersections adapt to scale: one or two projects use direct overlap bars, three to thirty
  use an UpSet-style intersection-size chart and membership matrix, and larger cohorts use a
  prevalence histogram. The twelve largest exact intersections are shown for UpSet. These are MS2
  co-observation patterns, not inferred biological modules or chemical families.
- Clicking a recurrence point or cohort pattern creates one shared component scope. The evidence matrix,
  table and selected-component inspector update from model state. The question filter intersects
  that scope; it can be cleared explicitly and resets when project/reference context changes.
- Heatmap selection markers use outlines without a translucent fill, so interaction never changes
  the apparent Jaccard colors or competes with the quantitative legend.
- Mixed studies and analytical methods are the primary use case. Cross-method default views show
  MS2 recurrence and within-project feature detection, not comparable abundance or chemical absence.
  Components unobserved throughout the selected projects are excluded from their ranking. Focus
  filters distinguish directional/detection differences, no reference MS2 match, and recurrence.
- Abundance comparison requires explicit opt-in. One selected metric (area or height) is used
  throughout without fallback. Component sums are normalized per raw file to retained MS2-row signal
  (ppm), not total metabolome signal or concentration. Missing measurements and zero-total samples
  remain unavailable; missing spectral components are never zero-imputed. Project medians carry
  equal weight in group effects. Log2 median ratios use no arbitrary pseudocount; zero-reference
  ratios are presented as detection-only. The heatmap's row-centered log2(1 + ppm) display is
  separate from this effect-size calculation.
- Sample-type metadata excludes blanks, QC, calibration and suitability samples by default; rows
  detected only in excluded samples do not enter the comparison. Existing MZmine sample-type
  inference applies when explicit metadata is unavailable. Included/excluded counts are shown.
- RF is off by default and is explicitly enabled in the dashboard. Optional abundance-mode RF
  requires declared independent raw files or a sample-ID metadata
  column. Repeated injections are aggregated by unit and comparison label. Every unit stays in
  one fold, including units spanning labels. Three repeated grouped three-fold validations train
  64 class-balanced CART trees per fit, requiring at least three independent units per group.
  Only shared components with finite, variable measurements are eligible; MS2 coverage masks
  cannot themselves become predictors.
- RF reports held-out balanced accuracy, held-out permutation importance, repeat variability and
  top-ten selection frequency. Rankings fall back to descriptive effects without sufficient
  predictive separation. The separation guard is a heuristic, not a significance test, and these
  grouped splits do not control study/method confounding or establish cross-study generalization.
  Interactive RF is bounded to two million sample-by-predictor entries.
- A linked top-component × clustered-project evidence matrix, ranked table, and sample-level dot
  distributions expose the evidence behind prioritization. Matrix cells are categorical: MS2 match,
  compatible/no match, outside method scope, or unknown coverage. This prevents missing acquisition
  from becoming chemical absence. Selection markers never change quantitative colors.
- Selecting a component exposes its detected/eligible prevalence over any project metadata column.
  Hierarchies encoded with `>`, `/`, or `::` become a tree; flat values remain a single level.
- An optional metadata contrast enables response analysis only for comparable abundance data with
  independent-unit identifiers. Repeated injections are median-aggregated per unit and condition;
  project effects use Hedges g, historical effects use a DerSimonian–Laird random-effects summary,
  and the dashboard links a query-versus-history quadrant to a per-component confidence-interval
  forest. Insufficient units, historical studies, or metadata leave these views unavailable instead
  of substituting detection or pseudo-replicates.
- Selecting a ranked component updates the persistent inspector. For the current projects, the
  inspector preselects up to two available component members for a mirror-spectrum comparison.
  One selected member shows its single spectrum rather than a self-comparison;
  the feature table reports missing project detections rather than silently substituting a member
  from another project.
- Expensive spectral comparisons use precursor blocking and a bounded score cache. The module task
  checks cancellation between input lists and throughout component assignment. Selection-analysis
  jobs run away from the JavaFX thread, discard stale results, and cache completed project sets.
