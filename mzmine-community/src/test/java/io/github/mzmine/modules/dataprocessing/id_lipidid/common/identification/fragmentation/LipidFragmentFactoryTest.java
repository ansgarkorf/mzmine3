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

package io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.fragmentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.dataprocessing.id_lipidid.annotation_modules.LipidAnnotationChainParameters;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.ILipidAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.LipidFragmentationRule;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.LipidFragmentationRuleType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidAnnotationLevel;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidFragment;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.lipidchain.LipidChainFactory;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.lipidchain.LipidChainType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.utils.LipidFactory;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.FormulaUtils;
import java.util.List;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IMolecularFormula;

class LipidFragmentFactoryTest {

  private static final LipidChainFactory LIPID_CHAIN_FACTORY = new LipidChainFactory();
  private static final LipidFactory LIPID_FACTORY = new LipidFactory();
  private static final MZTolerance MZ_TOLERANCE = new MZTolerance(0.01, 5);

  @Test
  void chainMinusFormulaNeutralLossRejectsOversizedLoss() {
    final ILipidAnnotation lipid = LIPID_FACTORY.buildSpeciesLevelLipid(
        LipidClasses.SULFONOLIPIDTRIHYDROXY, 20, 0, 0);
    final IMolecularFormula lipidIonFormula = FormulaUtils.cloneFormula(
        lipid.getMolecularFormula());
    IonizationType.POSITIVE_HYDROGEN.ionizeFormula(lipidIonFormula);

    final IMolecularFormula chainFormula = LIPID_CHAIN_FACTORY.buildLipidChainFormula(
        LipidChainType.AMID_CHAIN, 22, 0);
    final IMolecularFormula modificationFormula =
        FormulaUtils.createMajorIsotopeMolFormulaWithCharge("NH3");
    assertTrue(FormulaUtils.subtractFormulaIfPossible(chainFormula, modificationFormula)
        .isPresent());
    final IMolecularFormula lossFormula = FormulaUtils.subtractFormula(chainFormula,
        modificationFormula);
    assertTrue(FormulaUtils.subtractFormulaIfPossible(lipidIonFormula, lossFormula).isEmpty());

    final IMolecularFormula clippedFormula = FormulaUtils.subtractFormula(
        FormulaUtils.cloneFormula(lipidIonFormula), lossFormula);
    final Scan scan = createScan(FormulaUtils.calculateMzRatio(clippedFormula));
    final LipidFragmentationRule rule = new LipidFragmentationRule(PolarityType.POSITIVE,
        IonizationType.POSITIVE_HYDROGEN,
        LipidFragmentationRuleType.AMID_CHAIN_MINUS_FORMULA_FRAGMENT_NL,
        LipidAnnotationLevel.SPECIES_LEVEL, "NH3");
    final LipidAnnotationChainParameters chainParameters = LipidAnnotationChainParameters.create(22,
        26, 0, 6, false);

    final List<LipidFragment> fragments = new LipidFragmentFactory(MZ_TOLERANCE, lipid,
        IonizationType.POSITIVE_HYDROGEN, new LipidFragmentationRule[]{rule}, scan,
        chainParameters).findLipidFragments();

    assertTrue(fragments.isEmpty(),
        "Oversized neutral losses must not collapse to and repeatedly match a clipped formula.");
  }

  @Test
  void chainMinusFormulaFragmentRejectsOversizedModification() {
    final ILipidAnnotation lipid = LIPID_FACTORY.buildSpeciesLevelLipid(
        LipidClasses.SULFONOLIPIDTRIHYDROXY, 24, 0, 0);
    final IMolecularFormula chainFormula = LIPID_CHAIN_FACTORY.buildLipidChainFormula(
        LipidChainType.AMID_CHAIN, 12, 0);
    final IMolecularFormula modificationFormula =
        FormulaUtils.createMajorIsotopeMolFormulaWithCharge("O2");
    assertTrue(FormulaUtils.subtractFormulaIfPossible(chainFormula, modificationFormula).isEmpty());

    final IMolecularFormula clippedFormula = FormulaUtils.subtractFormula(chainFormula,
        modificationFormula);
    IonizationType.POSITIVE.ionizeFormula(clippedFormula);
    final Scan scan = createScan(FormulaUtils.calculateMzRatio(clippedFormula));
    final LipidFragmentationRule rule = new LipidFragmentationRule(PolarityType.POSITIVE,
        IonizationType.POSITIVE_HYDROGEN,
        LipidFragmentationRuleType.AMID_CHAIN_MINUS_FORMULA_FRAGMENT,
        LipidAnnotationLevel.MOLECULAR_SPECIES_LEVEL, "O2");
    final LipidAnnotationChainParameters chainParameters = LipidAnnotationChainParameters.create(12,
        12, 0, 0, false);

    final List<LipidFragment> fragments = new LipidFragmentFactory(MZ_TOLERANCE, lipid,
        IonizationType.POSITIVE_HYDROGEN, new LipidFragmentationRule[]{rule}, scan,
        chainParameters).findLipidFragments();

    assertTrue(fragments.isEmpty(),
        "An impossible formula modification must not produce a clipped chain fragment.");
  }

  @Test
  void chainPlusFormulaNeutralLossRejectsCollidingInvalidCandidates() {
    final ILipidAnnotation lipid = LIPID_FACTORY.buildSpeciesLevelLipid(LipidClasses.NACYLGLYCINE,
        24, 0, 0);
    final LipidFragmentationRule rule = new LipidFragmentationRule(PolarityType.POSITIVE,
        IonizationType.AMMONIUM,
        LipidFragmentationRuleType.AMID_CHAIN_PLUS_FORMULA_FRAGMENT_NL,
        LipidAnnotationLevel.MOLECULAR_SPECIES_LEVEL, "C2H9N2O2");
    assertFalse(canSubtractPlusFormulaNeutralLoss(lipid, rule, 24, 0));
    assertFalse(canSubtractPlusFormulaNeutralLoss(lipid, rule, 24, 1));
    final IMolecularFormula clippedFormula = calculateClippedPlusFormulaNeutralLoss(lipid, rule,
        24, 0);
    final Scan scan = createScan(FormulaUtils.calculateMzRatio(clippedFormula));
    final LipidAnnotationChainParameters chainParameters = LipidAnnotationChainParameters.create(24,
        24, 0, 1, false);

    final List<LipidFragment> fragments = new LipidFragmentFactory(MZ_TOLERANCE, lipid,
        IonizationType.AMMONIUM, new LipidFragmentationRule[]{rule}, scan,
        chainParameters).findLipidFragments();

    assertTrue(fragments.isEmpty(),
        "Invalid neutral losses must not collapse to and match the same clipped formula.");
  }

  @Test
  void chainPlusFormulaNeutralLossKeepsValidCandidateOnly() {
    final ILipidAnnotation lipid = LIPID_FACTORY.buildSpeciesLevelLipid(LipidClasses.NACYLGLYCINE,
        24, 0, 0);
    final LipidFragmentationRule rule = new LipidFragmentationRule(PolarityType.POSITIVE,
        IonizationType.AMMONIUM,
        LipidFragmentationRuleType.AMID_CHAIN_PLUS_FORMULA_FRAGMENT_NL,
        LipidAnnotationLevel.MOLECULAR_SPECIES_LEVEL, "NH4");
    assertFalse(canSubtractPlusFormulaNeutralLoss(lipid, rule, 26, 0));
    assertTrue(canSubtractPlusFormulaNeutralLoss(lipid, rule, 26, 1));
    final IMolecularFormula fragmentFormula = calculateClippedPlusFormulaNeutralLoss(lipid, rule,
        26, 1);
    final Scan scan = createScan(FormulaUtils.calculateMzRatio(fragmentFormula));
    final LipidAnnotationChainParameters chainParameters = LipidAnnotationChainParameters.create(26,
        26, 0, 1, false);

    final List<LipidFragment> fragments = new LipidFragmentFactory(MZ_TOLERANCE, lipid,
        IonizationType.AMMONIUM, new LipidFragmentationRule[]{rule}, scan,
        chainParameters).findLipidFragments();

    assertEquals(1, fragments.size(),
        "The invalid 26:0 loss must not duplicate the valid 26:1 fragment.");
    assertEquals(26, fragments.getFirst().getChainLength());
    assertEquals(1, fragments.getFirst().getNumberOfDBEs());
  }

  private static @NotNull IMolecularFormula calculateClippedPlusFormulaNeutralLoss(
      final @NotNull ILipidAnnotation lipid, final @NotNull LipidFragmentationRule rule,
      final int chainLength, final int numberOfDBEs) {
    final IMolecularFormula lipidIonFormula = getIonizedLipidFormula(lipid, rule);
    final IMolecularFormula lossFormula = calculatePlusFormulaNeutralLoss(rule, chainLength,
        numberOfDBEs);
    return FormulaUtils.subtractFormula(lipidIonFormula, lossFormula);
  }

  private static boolean canSubtractPlusFormulaNeutralLoss(final @NotNull ILipidAnnotation lipid,
      final @NotNull LipidFragmentationRule rule, final int chainLength,
      final int numberOfDBEs) {
    final IMolecularFormula lipidIonFormula = getIonizedLipidFormula(lipid, rule);
    final IMolecularFormula lossFormula = calculatePlusFormulaNeutralLoss(rule, chainLength,
        numberOfDBEs);
    return FormulaUtils.subtractFormulaIfPossible(lipidIonFormula, lossFormula).isPresent();
  }

  private static @NotNull IMolecularFormula getIonizedLipidFormula(
      final @NotNull ILipidAnnotation lipid, final @NotNull LipidFragmentationRule rule) {
    final IMolecularFormula lipidIonFormula = FormulaUtils.cloneFormula(
        lipid.getMolecularFormula());
    rule.getIonizationType().ionizeFormula(lipidIonFormula);
    return lipidIonFormula;
  }

  private static @NotNull IMolecularFormula calculatePlusFormulaNeutralLoss(
      final @NotNull LipidFragmentationRule rule, final int chainLength,
      final int numberOfDBEs) {
    final IMolecularFormula chainFormula = LIPID_CHAIN_FACTORY.buildLipidChainFormula(
        LipidChainType.AMID_CHAIN, chainLength, numberOfDBEs);
    final IMolecularFormula modificationFormula =
        FormulaUtils.createMajorIsotopeMolFormulaWithCharge(rule.getMolecularFormula());
    return FormulaUtils.addFormula(chainFormula, modificationFormula);
  }

  private static @NotNull Scan createScan(final double mz) {
    final RawDataFile file = new RawDataFileImpl("lipid-fragment-factory-test", null, null,
        Color.BLACK);
    final Scan scan = new SimpleScan(file, -1, 2, 0.1F, null, new double[]{mz},
        new double[]{100d}, MassSpectrumType.CENTROIDED, PolarityType.POSITIVE, "test", null);
    final DataPoint[] dataPoints = {new SimpleDataPoint(mz, 100d)};
    scan.addMassList(SimpleMassList.create(null, dataPoints));
    return scan;
  }
}
