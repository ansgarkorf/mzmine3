# GCxGC-EI processing

## Intention

Process comprehensive two-dimensional GC (GCxGC) with EI: build and resolve chromatograms in the
second dimension (second column retention time, many data points), expand them into the first
dimension (modulations) and resolve them there, then group EI fragments into compounds with a 2D
aware spectral deconvolution. GCxGC raw data opens in the 2D intensity map as first against second
dimension retention time.

## Decisions

- Requirement set by the user: GCxGC is processed like LC-IMS conceptually and reuses the IMS
  code, but it is its own data type for the user. No module, parameter, column, chart, menu,
  message or export of the GCxGC workflow may use ion mobility terms (frame, mobility, mobilogram,
  IMS, CCS). An earlier experimental branch that converted imported files to IMS and added a 2D RT
  to every `Scan` was rejected by the user.
- Data model (user choice): `GCxGCRawDataFile`/`Modulation` (`GCxGCRawDataFileImpl`,
  `SimpleModulation`) extend the IMS interfaces (`IMSRawDataFile`/`Frame`), so the IMS algorithms
  work unchanged. A modulation is stored like a frame, a single scan like a mobility scan, the 2D
  RT like the mobility. `MobilityType.GCXGC` is only an internal marker
  (`isChromatographic()`, excluded from IMS type combos via `ionMobilityValues()`).
- All GCxGC checks are type based (`util/GcxGcUtils`: `isGcxGc`, `isModulation`, `isGcxGcScan`,
  `isGcxGcFeature`), never `instanceof GCxGCRawDataFile`: project load wraps raw files and frames in
  `CachedIMSRawDataFile`/`CachedIMSFrame`. `isGcxGcFeature` falls back to the feature data, as
  copied features may get their data before their raw file. `FeatureUtils.isImsFeature` is false
  for GCxGC.
- Unit (user choice): the 2D RT is in minutes everywhere, also internally (import divides the
  `MS:1002083` seconds by 60). 2D tolerances are `RTTolerance`s restricted to minutes/seconds
  (`GcxGcUtils.secondDimensionRTToleranceParameter`), defaults entered in seconds. Always use
  `getToleranceInMinutes`/`getToleranceRange`. 2D RT format `0.0000` (GUI) and `0.00000` (export).
  The mzML mobility index uses a 2E-6 min grouping delta for GCxGC (2E-5 would merge scans of fast
  acquisitions).
- mzML import: the scan RT is `MS:1002082` (first column elution time), because the parser groups
  scans with an equal RT into one modulation. ChromaTOF offsets the `scan start time` (starts at 0
  while the first modulation starts at the solvent delay), so it must not be used for GCxGC.
- Feature types: `SecondDimensionRTType` (`rt_2d`, "2D RT"), `SecondDimensionRTRangeType`
  (`rt_2d_range`), `SecondDimensionFwhmType` (`fwhm_2d`, "2D FWHM") and the graphical
  `FeatureShapeGcxGc2DType` ("2D feature", user wording: features, not peaks), and the row column
  `FeatureShapeSecondDimensionType` ("2D RT shapes", summed 2D RT trace per sample next to the RT
  "Shapes" column). They are set instead
  of the mobility, mobility range, mobility unit, mobility FWHM and IMS chart types
  (`FeatureDataUtils.recalculateIonSeriesDependingTypes`, `QualityParameters`,
  `DataTypeUtils.addDefaultIonMobilityTypeColumns`/`applyFeatureSpecificGraphicalTypes`,
  `FeatureList.fireFeatureChangedEvent`). Accessors: `Feature.getSecondDimensionRT()/Range()`,
  `FeatureListRow.getAverageSecondDimensionRT()/getSecondDimensionRTRange()`.
- Detection order (user decision, replaces the earlier first-dimension-first order): the second
  dimension has far more data points (hundreds of scans per modulation, 3-6 modulations per
  compound). 2D RT spectra → chromatogram builder along the 2D RT → resolver in 2D RT → GCxGC 2D
  feature builder (expansion into the modulations) → resolver in the retention time.
- Dedicated modules (user choice, `modules/dataprocessing/featdet_gcxgc`), recorded only themselves
  in the processing history:
  - GCxGC 2D RT spectra: sums the mass lists of the single scans with the same scan index over all
    modulations of an optional 1D RT range into one `SecondDimensionSpectrum` per scan index
    (retention time = mean 2D RT of the index, scan number = scan index). Requirement set by the
    user: no mass detection inside; mass detection runs before on all scan types. The spectra are
    stored on the `GCxGCRawDataFile` with the used 1D RT range, not in `getScans()`. One applied
    method on the raw file, so project loading replays it after the mass detection. Modulations get
    no spectra of their own, so raw data TIC/spectrum views of modulations are empty.
  - Chromatogram builder (ADAP): uses the 2D RT spectra of GCxGC files; the RT filter of its scan
    selection is ignored (it is a 1D range, cropping happens in the 2D RT spectra). Its features
    ("2D RT chromatograms", `GcxGcUtils.isSecondDimensionChromatogram`) have the 2D RT and 2D RT
    range types, 2D FWHM and the "2D RT shapes" column, but no RT, RT range, FWHM, RT shape or MS2.
    `ScanListDataAccess` sizes its buffers from the given scans, as these spectra have more signals
    than the file maximum.
  - GCxGC 2D feature builder: own task (no longer the IMS expander). One parallel pass over the
    single scans of the modulations of the 2D RT spectra range; each signal is offered to the 2D RT
    chromatograms whose scan index range contains the scan, the closest signal per scan is kept.
    Result: 2D features (`IonMobilogramTimeSeries`), selected scans = modulations, row IDs kept.
- GCxGC 2D feature finder (user request: image detection approach, compared against the 2D RT
  first pipeline): finds 2D features directly in the ion images (modulation x scan index per m/z
  channel), replacing chromatogram building and resolving in both dimensions.
  - m/z channels like the ADAP chromatogram builder: signals sorted by intensity, signals of at
    least the minimum height start non-overlapping channels of the m/z tolerance, the most intense
    signal per channel and pixel is kept. Signals are processed in m/z blocks (at most ~20 M
    signals, borders at the least populated 0.01 Da bin) to limit memory; one pass over the single
    scans per block.
  - Background (`IonImageBackground`): column bleed and long tails form ridges along the first
    dimension at a constant 2D scan index and were glued to compounds (one m/z 73 feature spanned
    298 modulations). Per 2D scan index, a signal present in all modulations of the background
    window (default 21) has the rolling minimum of these signals as background; missing signals
    count as zero, so sparse channels and features narrower than the window are unchanged. The
    window always spans its full width (shifted at the run edges); runs shorter than the window
    have no background. Detection uses the corrected intensities, the feature data keeps the
    measured intensities like gap filling.
  - Watershed by flooding from the top (`IonImageWatershed`): a region whose apex is not at least
    the minimum top/saddle ratio above the saddle, or below the minimum apex height, is merged into
    the highest touching region (topological persistence; 2D analog of the top/edge ratio, no
    search range in minutes). 8-neighborhood of direct neighbors only: bridging single missing
    scans chained scattered noise into features (user feedback). Signals below the edge level
    (fraction of the apex) are dropped.
  - Splitting (`IonImageRegionSplitter`): the watershed decides per signal, so a long but low area
    next to a compound is merged as a shoulder although its summed 2D RT trace is a second peak
    (user example: apexes at 0.016 and 0.019 min). Each region is split at the valley of its summed
    2D RT profile, then of its summed 1D profile, if a second apex (at least 5 % of the main apex,
    profile smoothed 1-2-1) is the top/saddle ratio above the valley; parts are split again.
  - Peak shape (user decision): a GCxGC compound is one chromatographic peak (Gaussian, tailing,
    fronting, broad, noisy) cut into few modulation slices with almost the same 2D apex. The 2D RT
    has many data points and must look like a peak; the first dimension has too few modulations
    for a shape requirement. Shape quality = R² of the closest unimodal profile (isotonic
    regression rising to and falling from the apex, `UnimodalShape`), no peak model. Features need
    a summed 2D RT profile with R² of at least the "Minimum 2D peak shape" (default 0.8). Splits use
    the local valley ratio, not R²: R² is dominated by the main peak and gating splits with it kept
    small compounds next to intense ones merged (Biogluc: two-apex features 2.4 k -> 10.8 k). Both
    parts of a split must still be peaks: at least the minimum 2D data points (2D split) or the
    minimum modulations (1D split), so tail bumps and fuzzy tops are not cut off and a compound is
    never cut into 1D fragments.
  - Noise (user feedback, noise features with apexes of 6-11 counts): the apex modulation needs the
    minimum 2D data points as consecutive scans around the apex, plus the minimum modulations.
    Requirement set by the user: no own noise estimate, mass detection decides which signals are
    noise; the feature apex only needs the minimum height (wizard: minimum feature height). An
    automatic signal-to-noise filter (median of all signals) was removed.
  - Main peak (user decision): a region that cannot be split into peaks (valley too shallow or a
    part too small) and fails the 2D shape check keeps its main peak: it is split at valleys
    without part size limits and the part with the apex is checked again. Before, the whole region
    was discarded.
  - Output: 2D features as `IonMobilogramTimeSeries` with the modulations as selected scans, so the
    2D deconvolution, aligner, gap filling and visualizers work unchanged. GC-EI gap filling takes
    the m/z tolerance and minimum modulations from the finder. Detection works on candidates in
    pixel form (`IonImageFeature`); mzmine features are created at the end.
  - Template deconvolution (optional mode, user request; model peak deconvolution like AMDIS in
    2D, `IonImageTemplateDeconvolution`): all ions of a compound share its 2D shape. Candidates are
    visited from the most intense; a candidate that correlates (Pearson over the union of pixels,
    default 0.8) with the template of a compound whose apex is within the 1D and 2D RT tolerances
    is an ion of it; otherwise a clean single peak (2D shape R² >= 0.9) becomes a new compound
    and its normalized shape is the template; otherwise the candidate is fitted later. Every
    non-model candidate is fitted as a non-negative sum of the overlapping templates (NNLS by
    projected coordinate descent): one overlapping compound and a correlating ion keep the ion
    whole; otherwise each compound gets its fitted share of the measured intensity (proportional
    where the templates explain more than measured, the fitted contribution where they explain
    less). Shares below 10 % of the feature intensity and fits explaining less than half of the
    variance are not assigned. Compounds keep the most intense part per m/z channel and need the
    minimum number of signals. The compounds are built with
    `SpectralDeconvolutionUtils.createDeconvolutedFeatureList`, so the output is the same kind of
    deconvoluted list as the GC-EI spectral deconvolution, which is not needed afterwards.
    Unassigned candidates are dropped, like ungrouped features of the GC-EI deconvolution. Not
    done: the unexplained rest of a feature after the fit could start a new compound.
  - Multithreading (user request: use mzmine's thread pool and respect the thread setting): all
    parallel stages (modulation chunks, m/z channels, feature creation, template segments and fits)
    run as jobs on the executor of the task controller (`TaskPoolGcxGcJobs`), at most the number of
    threads set by the user; not the common ForkJoinPool. Not as sub tasks of a `ThreadPoolTask`:
    each sub task is checked for a logged-in user, which killed headless runs and tests; the finder
    task itself already passed that check. The calling thread works on the jobs too, so they finish
    even when all pool threads are busy. The finder task has high priority like the IMS expander,
    so it runs on the high priority executor and does not take a worker slot. Other modules split
    by rows (formula prediction), raw files (gap filling) or RT (IMS expander), none by m/z; the
    finder splits by m/z channels, packed by number of signals into 4 bins per thread.
  - Results do not depend on the number of threads: candidates are ordered by channel and apex,
    and the template deconvolution runs in retention time segments of 20 RT tolerances (fixed, not
    per thread). Even segments find their model peaks in parallel first, then the odd segments,
    which also see the compounds of their even neighbors. Compared with one pass in the order of
    height over the whole run, only candidates within the RT tolerance of a segment border can
    end up differently: a more intense candidate of the odd neighbor did not exist yet when an
    even segment chose its model peaks. The fit of the other candidates runs in parallel on all
    compounds, in small jobs taken from a queue: next to intense compounds a candidate overlaps
    hundreds of templates (Biogluc: up to ~390, ~0.5 s per fit) and these candidates cluster in
    RT, so large contiguous jobs left most threads idle.
  - Wizard: second workflow "Spectral deconvolution (GCxGC 2D feature finder)"
    (`WorkflowGcxGc2DFeatureFinder`, `WizardBatchBuilderGcxGcEi2DFeatureFinder`), only for
    `GCXGC_EI`, same parameters and steps after the feature detection as the other GCxGC workflow
    so both can be compared. Minimum height = wizard minimum feature height, top/saddle ratio 1.5,
    minimum 2D peak shape 0.8, edge level 1 %, background window 21, minimum modulations =
    minimum RT data points. Third workflow "Template deconvolution (GCxGC 2D feature finder)"
    (`WorkflowGcxGc2DTemplateDeconvolution`): the same with template deconvolution (intra sample
    1D and 2D tolerances, shape similarity 0.8, minimum signals of the workflow) and without the
    GC-EI spectral deconvolution step.
- Requirement set by the user: no GCxGC specific resolver. `ResolvingDimension.SECOND_DIMENSION_RT`
  ("2D RT (GCxGC)") is a dimension of the base resolver. It resolves 2D RT chromatograms along their
  retention times (the 2D RT) and 2D features along their summed 2D trace (mobility resolving
  internally). The RT dimension rejects 2D RT chromatograms, the 2D RT dimension rejects non-GCxGC
  features and the mobility dimension rejects GCxGC features. The resolver parameters keep their
  names (saved batches); their values are in minutes for the 2D RT. The local minimum resolver
  needs at least 4 points, so the 3 modulation test file cannot be resolved in RT.
- Project save: scans are saved as indices into `GcxGcUtils.getScansForIndexing` = all scans of the
  file followed by the 2D RT spectra (feature data, selected scans, scan references), so existing
  indices are unchanged and lists of 2D RT chromatograms can be saved.
- The 2D deconvolution is an option of the GC-EI spectral deconvolution module
  (`TWO_D_GROUPING_AND_SHAPE_CORRELATION`). It groups by 1D RT tolerance, 2D RT tolerance and the
  correlation of the full 2D feature shape: the union of all data points of both features, missing
  points count as zero (the 1D profile has only 3-6 modulations). Single scans are matched by
  modulation id and scan number, as scan instances are created on access. A feature within the
  tolerances that does not correlate starts a new compound instead of being dropped.
- The GC aligner, GC-EI gap filling and GC-EI duplicate filter have optional 2D RT tolerances. The
  2D RT is a hard filter in the aligner, not part of the alignment score.
  `GCConsensusAlignerPostProcessor.extractNewFeature` re-extracts 2D features from the single scans
  within the 2D RT range, so compounds of a modulation that share the m/z are not summed. The
  generic alignment score "mobility difference" is now empty (null) for rows without mobility (LC
  and GCxGC), so exports drop the column.
- Wizard: ion interface `GCXGC_EI` with its own `IonIterfaceGroup.CHROMATOGRAPHY_2D_HARD`; only the
  spectral deconvolution workflow supports it (`WizardBatchBuilderGcxGcEiDeconvolution`, extends the
  GC-EI builder, shares its 1D parameters). Workflow: import, mass detection (all scan types, single
  scan noise level = 1/5 of the MS1 noise level like IMS mobility scans), GCxGC 2D RT spectra (RT
  crop), ADAP along the 2D RT (min 2D data points), local minimum resolver in 2D RT, GCxGC 2D
  feature builder, RT local minimum resolver (min modulations, 1D FWHM), 2D deconvolution, GC
  aligner, row filter, GC-EI gap filling, GC-EI duplicate filter, library search, exports. No feature shape filter (too few modulations) and no 2D smoothing.
- Requirement set by the user: no second 2D RT resolver after the RT resolver. Known consequence:
  the 2D RT spectra sum all modulations, so compounds at different retention times and close 2D
  RTs can form one 2D RT chromatogram without a minimum (Biogluc m/z 361.17: one 2D range of
  1.5 s). The RT resolver separates them, but each keeps the full 2D range and its 2D apex may be
  the one of the other compound.
- 2D thresholds are multiples of the single scan noise level, not the wizard minimum feature
  height: ADAP minimum height and 2D resolver minimum height 5x, ADAP group intensity 2x. The 2D RT
  spectra and summed 2D traces are sums of few single scans, orders of magnitude lower than the
  first dimension trace of a 2D feature. The RT resolver uses the wizard minimum feature height.
  Chromatographic threshold 0.5 (IMS uses 0.8).
- Preset defaults were tuned on Pegasus HRT data (3 s modulation, 200 Hz): 3 modulations minimum,
  1D FWHM 0.15 min, 1D intra-sample tolerance 0.06 min (apexes of one compound can be in
  neighboring modulations), 2D FWHM 0.1 s, 2D tolerances 0.1 s, 3 minimum 2D data points. A 2D
  tolerance of 0.05 s split tailing compounds into several pieces.
- Pseudo spectrum visualizer and deconvolution preview: for GCxGC, a second feature overlay plot
  shows the 2D RT traces next to the RT plot (`SecondDimensionDatasetsBuilder`). Compounds use the
  summed 2D traces of their member features. Pseudo spectra without a compound (deconvolution
  preview) extract each signal for both plots from the single scans of the main feature's
  modulations within a 2D RT window of three times the main feature's 2D width (modulations have
  no spectra). Signals without data in this window are not shown.
- GC-EI gap filling takes the minimum consecutive data points of GCxGC lists from the last RT
  resolver after the 2D feature builder (the chromatogram builder value counts 2D scans), and only
  smoothing after the 2D feature builder.
- Compound dashboard (user request, to validate compound detection visually): the toolbar button
  left of the raw file selector cycles the side plot: feature plot (4D bubble plot, unchanged),
  2D plot, 3D plot, hidden (`CompoundDashboardSidePlot`). The 2D and 3D plots are the 2D / 3D
  visualizers (intensity map) of the selected raw data file embedded via `EmbeddedIntensityMap`,
  only the plot (user decision: `IntensityMapPlot.setCompact`, no toolbar, overlay panel, spectrum
  pane or footer; mouse zoom, pan and rotation remain):
  mobility and GCxGC data show retention time against mobility / 2D RT of the m/z range of the
  selected ion (user decision; the representative ion if none is selected; data point m/z range
  of its feature with 10 ppm, at least 0.001 margin; only its trace outline is drawn, and selecting
  another ion replaces the overlay without changing the window), other data retention time
  against the m/z range of the compound; the window is the span
  of the compound's features in this file with a margin of their span on each side (at least
  0.25 min RT, 5 % of the mobility / 2D RT range of the file); the stored data of all member
  features are drawn over the raw data. Peak labels (user decision): only the selected compound
  (named at its representative ion) and the selected member ion, via the label row filter of
  `IntensityMapLabels`; changing the selected ion updates the labels without rebuilding the plot.
  The
  view is rebuilt (debounced) when the compound, the raw file or the side plot changes and closed
  when replaced. GCxGC lists show the second overlay below the RT traces like mobilograms for IMS
  (same task and plot), titled "2D RT shapes" with a 2D RT axis.
- GUI: double click and the raw data menu "Show GCxGC 2D retention time map" open the 2D intensity
  map (automatic mode resolves to RT x 2D RT, axis kind `SECOND_DIMENSION_RT`); the IMS overview
  menu entry is hidden for GCxGC files. Scans read "Modulation #n" and "Scan #n" with 2D RT. IMS
  feature table menus are hidden for GCxGC lists. GCxGC files count as non-IMS in the module
  compatibility check: no "untested with ion mobility" popups, IMS-only modules reject them.
- Not GCxGC specific and still visible to all users: the "CCS tolerance" in the advanced library
  search parameters, the mobility filter of the scan selection, and the "mobility difference"
  sub-column header of the alignment scores in the feature table.
