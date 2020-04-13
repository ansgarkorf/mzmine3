/*
 * Copyright 2006-2020 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Range;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PeakIdentity;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidFragment;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidFragmentInformationLevelType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainBuilder;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidIdentity;
import io.github.mzmine.util.FormulaUtils;

/**
 * This class contains methods for MS/MS lipid identifications
 * 
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class MSMSLipidTools {

	private static final ChainTools CHAIN_TOOLS = new ChainTools();
	private static final LipidChainBuilder LIPID_CHAIN_BUILDER = new LipidChainBuilder();

	public LipidFragment checkForClassSpecificFragment(Range<Double> mzTolRangeMSMS, LipidIdentity peakIdentity,
			IonizationType ionizationType, LipidFragmentationRule[] rules, Double mzAccurate) {
		for (int i = 0; i < rules.length; i++) {
			if (!ionizationType.equals(rules[i].getIonizationType())) {
				continue;
			}
			LipidFragment detectedFragment = checkForSpecificRuleTpye(rules[i], mzTolRangeMSMS, peakIdentity,
					mzAccurate);
			if (detectedFragment != null) {
				return detectedFragment;
			}
		}
		return null;
	}

	private LipidFragment checkForSpecificRuleTpye(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity peakIdentity, Double mzAccurate) {

		LipidFragmentationRuleType ruleType = rule.getLipidFragmentationRuleType();
		switch (ruleType) {
		case HEADGROUP_FRAGMENT:
			return checkForHeadgroupFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case HEADGROUP_FRAGMENT_NL:
			return checkForHeadgroupFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ACYLCHAIN_FRAGMENT:
			return checkForAcylChainFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ACYLCHAIN_FRAGMENT_NL:
			return checkForAcylChainFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ACYLCHAIN_MINUS_FORMULA_FRAGMENT:
			return checkForAcylChainMinusFormulaFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ACYLCHAIN_MINUS_FORMULA_FRAGMENT_NL:
			return checkForAcylChainMinusFormulaFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ACYLCHAIN_PLUS_FORMULA_FRAGMENT:
			return checkForAcylChainPlusFormulaFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ACYLCHAIN_PLUS_FORMULA_FRAGMENT_NL:
			return checkForAcylChainPlusFormulaFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ALKYLCHAIN_FRAGMENT:
			return checkForAlkylChainFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ALKYLCHAIN_FRAGMENT_NL:
			return checkForAlkylChainFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ALKYLCHAIN_MINUS_FORMULA_FRAGMENT:
			return checkForAlkylChainMinusFormulaFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ALKYLCHAIN_MINUS_FORMULA_FRAGMENT_NL:
			return checkForAlkylChainMinusFormulaFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ALKYLCHAIN_PLUS_FORMULA_FRAGMENT:
			return checkForAlkylChainPlusFormulaFragment(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		case ALKYLCHAIN_PLUS_FORMULA_FRAGMENT_NL:
			return checkForAlkylChainPlusFormulaFragmentNL(rule, mzTolRangeMSMS, peakIdentity, mzAccurate);
		default:
			return null;
		}
	}

	private LipidFragment checkForHeadgroupFragment(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity identity, Double mzAccurate) {
		String fragmentFormula = rule.getMolecularFormula();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		if (mzTolRangeMSMS.contains(mzFragmentExact)) {
			return new LipidFragment(rule.getLipidFragmentationRuleType(), rule.getLipidFragmentInformationLevelType(),
					mzFragmentExact, mzAccurate, identity.getLipidClass(), null, null, null);
		} else {
			return null;
		}
	}

	private LipidFragment checkForHeadgroupFragmentNL(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity identity, Double mzAccurate) {
		String fragmentFormula = rule.getMolecularFormula();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		Double mzExact = mzPrecursorExact - mzFragmentExact;
		if (mzTolRangeMSMS.contains(mzExact)) {
			return new LipidFragment(rule.getLipidFragmentationRuleType(), rule.getLipidFragmentInformationLevelType(),
					mzExact, mzAccurate, identity.getLipidClass(), null, null, LipidChainType.ACYL_CHAIN);
		} else {
			return null;
		}
	}

	// Acly Chains
	private LipidFragment checkForAcylChainFragment(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity identity, Double mzAccurate) {

		List<String> fattyAcidFormulas = CHAIN_TOOLS.calculateFattyAcidFormulas(identity);
		for (String fattyAcidFormula : fattyAcidFormulas) {
			FormulaUtils.ionizeFormula(fattyAcidFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = FormulaUtils.calculateExactMass(fattyAcidFormula);
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(fattyAcidFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(fattyAcidFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAcylChainFragmentNL(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity identity, Double mzAccurate) {

		List<String> fattyAcidFormulas = CHAIN_TOOLS.calculateFattyAcidFormulas(identity);
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		for (String fattyAcidFormula : fattyAcidFormulas) {
			Double mzFattyAcid = FormulaUtils.calculateExactMass(fattyAcidFormula);
			Double mzExact = mzPrecursorExact - mzFattyAcid;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(fattyAcidFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(fattyAcidFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAcylChainMinusFormulaFragment(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> fattyAcidFormulas = CHAIN_TOOLS.calculateFattyAcidFormulas(identity);
		for (String fattyAcidFormula : fattyAcidFormulas) {
			FormulaUtils.ionizeFormula(fattyAcidFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = FormulaUtils.calculateExactMass(fattyAcidFormula) - mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(fattyAcidFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(fattyAcidFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAcylChainMinusFormulaFragmentNL(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> fattyAcidFormulas = CHAIN_TOOLS.calculateFattyAcidFormulas(identity);
		for (String fattyAcidFormula : fattyAcidFormulas) {
			FormulaUtils.ionizeFormula(fattyAcidFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = mzPrecursorExact - FormulaUtils.calculateExactMass(fattyAcidFormula) - mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(fattyAcidFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(fattyAcidFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAcylChainPlusFormulaFragment(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> fattyAcidFormulas = CHAIN_TOOLS.calculateFattyAcidFormulas(identity);
		for (String fattyAcidFormula : fattyAcidFormulas) {
			FormulaUtils.ionizeFormula(fattyAcidFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = FormulaUtils.calculateExactMass(fattyAcidFormula) + mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(fattyAcidFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(fattyAcidFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAcylChainPlusFormulaFragmentNL(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> fattyAcidFormulas = CHAIN_TOOLS.calculateFattyAcidFormulas(identity);
		for (String fattyAcidFormula : fattyAcidFormulas) {
			FormulaUtils.ionizeFormula(fattyAcidFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = mzPrecursorExact - FormulaUtils.calculateExactMass(fattyAcidFormula) + mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(fattyAcidFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(fattyAcidFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	// Alkyl Chains
	private LipidFragment checkForAlkylChainFragment(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity identity, Double mzAccurate) {

		List<String> chainFormulas = CHAIN_TOOLS.calculateHydroCarbonFormulas(identity);
		for (String chainFormula : chainFormulas) {
			FormulaUtils.ionizeFormula(chainFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = FormulaUtils.calculateExactMass(chainFormula);
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(chainFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(chainFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAlkylChainFragmentNL(LipidFragmentationRule rule, Range<Double> mzTolRangeMSMS,
			LipidIdentity identity, Double mzAccurate) {

		List<String> chainFormulas = CHAIN_TOOLS.calculateHydroCarbonFormulas(identity);
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		for (String chainFormula : chainFormulas) {
			Double mzFattyAcid = FormulaUtils.calculateExactMass(chainFormula);
			Double mzExact = mzPrecursorExact - mzFattyAcid;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(chainFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(chainFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAlkylChainMinusFormulaFragment(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> chainFormulas = CHAIN_TOOLS.calculateHydroCarbonFormulas(identity);
		for (String chainFormula : chainFormulas) {
			FormulaUtils.ionizeFormula(chainFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = FormulaUtils.calculateExactMass(chainFormula) - mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(chainFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(chainFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAlkylChainMinusFormulaFragmentNL(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> chainFormulas = CHAIN_TOOLS.calculateHydroCarbonFormulas(identity);
		for (String chainFormula : chainFormulas) {
			FormulaUtils.ionizeFormula(chainFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = mzPrecursorExact - FormulaUtils.calculateExactMass(chainFormula) - mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(chainFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(chainFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAlkylChainPlusFormulaFragment(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> chainFormulas = CHAIN_TOOLS.calculateHydroCarbonFormulas(identity);
		for (String chainFormula : chainFormulas) {
			FormulaUtils.ionizeFormula(chainFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = FormulaUtils.calculateExactMass(chainFormula) + mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(chainFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(chainFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	private LipidFragment checkForAlkylChainPlusFormulaFragmentNL(LipidFragmentationRule rule,
			Range<Double> mzTolRangeMSMS, LipidIdentity identity, Double mzAccurate) {

		String fragmentFormula = rule.getMolecularFormula();
		Double mzPrecursorExact = identity.getMass() + rule.getIonizationType().getAddedMass();
		Double mzFragmentExact = FormulaUtils.calculateExactMass(fragmentFormula);
		List<String> chainFormulas = CHAIN_TOOLS.calculateHydroCarbonFormulas(identity);
		for (String chainFormula : chainFormulas) {
			FormulaUtils.ionizeFormula(chainFormula, IonizationType.NEGATIVE_HYDROGEN, 1);
			Double mzExact = mzPrecursorExact - FormulaUtils.calculateExactMass(chainFormula) + mzFragmentExact;
			if (mzTolRangeMSMS.contains(mzExact)) {
				int chainLength = CHAIN_TOOLS.getChainLengthFromFormula(chainFormula);
				int numberOfDoubleBonds = CHAIN_TOOLS.getNumberOfDoubleBondsFromFormula(chainFormula);
				return new LipidFragment(rule.getLipidFragmentationRuleType(),
						rule.getLipidFragmentInformationLevelType(), mzExact, mzAccurate, identity.getLipidClass(),
						chainLength, numberOfDoubleBonds, LipidChainType.ACYL_CHAIN);
			}
		}
		return null;
	}

	public boolean confirmHeadgroupFragmentPresent(List<LipidFragment> listOfAnnotatedFragments) {
		for (LipidFragment lipidFragment : listOfAnnotatedFragments) {
			if (lipidFragment.getLipidFragmentInformationLevelType()
					.equals(LipidFragmentInformationLevelType.MOLECULAR_FORMULA)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This methods tries to reconstruct a possible chain composition of the
	 * annotated lipid using the annotated MS/MS fragments
	 */
	public List<LipidIdentity> predictChainComposition(List<LipidFragment> listOfDetectedFragments,
			PeakIdentity peakIdentity, LipidChainType[] types) {
		List<LipidIdentity> fattyAcidComposition = new ArrayList<>();
		// get number of total C atoms, double bonds and number of chains
		LipidTools lipidTools = new LipidTools();
		int totalNumberOfCAtoms = lipidTools.getNumberOfCAtoms(peakIdentity.getName());
		int totalNumberOfDB = lipidTools.getNumberOfDB(peakIdentity.getName());

		int testNumberOfCAtoms = 0;
		int testNumberOfDoubleBonds = 0;

		// combine all fragments with each other to check for a matching
		// composition
		for (int i = 0; i < listOfDetectedFragments.size(); i++) {
			if (!listOfDetectedFragments.get(i).getLipidFragmentInformationLevelType()
					.equals(LipidFragmentInformationLevelType.CHAIN_COMPOSITION)) {
				continue;
			}
			int numberOfCAtomsInFragment = listOfDetectedFragments.get(i).getChainLength();
			int numberOfDBInFragment = listOfDetectedFragments.get(i).getNumberOfDoubleBonds();

			// one chain
			if (types.length >= 1) {
				// check if number of C atoms is equal
				testNumberOfCAtoms = numberOfCAtomsInFragment;
				if (testNumberOfCAtoms == totalNumberOfCAtoms) {
					// check number of double bonds
					testNumberOfDoubleBonds = numberOfDBInFragment;
					if (testNumberOfDoubleBonds == totalNumberOfDB) {
						String chainOne = LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
								listOfDetectedFragments.get(i).getLipidChainType(), testNumberOfCAtoms,
								testNumberOfDoubleBonds);
						fattyAcidComposition
								.add(new LipidIdentity(chainOne, peakIdentity.getPropertyValue("Molecular formula")));
					}
				}

				// two chains
				if (types.length >= 2) {
					for (int j = 0; j < listOfDetectedFragments.size(); j++) {
						if (!listOfDetectedFragments.get(j).getLipidFragmentInformationLevelType()
								.equals(LipidFragmentInformationLevelType.CHAIN_COMPOSITION)) {
							continue;
						}
						// check if number of C atoms is equal
						testNumberOfCAtoms = numberOfCAtomsInFragment + listOfDetectedFragments.get(j).getChainLength();
						if (testNumberOfCAtoms == totalNumberOfCAtoms) {
							// check number of double bonds
							testNumberOfDoubleBonds = numberOfDBInFragment
									+ listOfDetectedFragments.get(j).getNumberOfDoubleBonds();
							if (testNumberOfDoubleBonds == totalNumberOfDB) {
								List<String> chains = new ArrayList<>();
								chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
										listOfDetectedFragments.get(i).getLipidChainType(), numberOfCAtomsInFragment,
										numberOfDBInFragment));
								chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
										listOfDetectedFragments.get(j).getLipidChainType(),
										listOfDetectedFragments.get(j).getChainLength(),
										listOfDetectedFragments.get(j).getNumberOfDoubleBonds()));
								String name = LIPID_CHAIN_BUILDER.connectLipidChainAnnotations(chains);
								fattyAcidComposition.add(
										new LipidIdentity(name, peakIdentity.getPropertyValue("Molecular formula")));
							}
						}
						// three chains
						if (types.length >= 3) {
							for (int k = 0; k < listOfDetectedFragments.size(); k++) {
								if (!listOfDetectedFragments.get(k).getLipidFragmentInformationLevelType()
										.equals(LipidFragmentInformationLevelType.CHAIN_COMPOSITION)) {
									continue;
								}
								// check if number of C atoms is equal
								testNumberOfCAtoms = numberOfCAtomsInFragment
										+ listOfDetectedFragments.get(j).getChainLength()
										+ listOfDetectedFragments.get(k).getChainLength();
								if (testNumberOfCAtoms == totalNumberOfCAtoms) {
									// check number of double bonds
									testNumberOfDoubleBonds = numberOfDBInFragment
											+ listOfDetectedFragments.get(j).getNumberOfDoubleBonds()
											+ listOfDetectedFragments.get(k).getNumberOfDoubleBonds();
									if (testNumberOfDoubleBonds == totalNumberOfDB) {
										List<String> chains = new ArrayList<>();
										chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
												listOfDetectedFragments.get(i).getLipidChainType(),
												numberOfCAtomsInFragment, numberOfDBInFragment));
										chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
												listOfDetectedFragments.get(j).getLipidChainType(),
												listOfDetectedFragments.get(j).getChainLength(),
												listOfDetectedFragments.get(j).getNumberOfDoubleBonds()));
										chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
												listOfDetectedFragments.get(k).getLipidChainType(),
												listOfDetectedFragments.get(k).getChainLength(),
												listOfDetectedFragments.get(k).getNumberOfDoubleBonds()));
										String name = LIPID_CHAIN_BUILDER.connectLipidChainAnnotations(chains);
										fattyAcidComposition.add(new LipidIdentity(name,
												peakIdentity.getPropertyValue("Molecular formula")));
									}
								}
								// four chains
								if (types.length >= 4) {
									for (int l = 0; l < listOfDetectedFragments.size(); l++) {
										if (!listOfDetectedFragments.get(l).getLipidFragmentInformationLevelType()
												.equals(LipidFragmentInformationLevelType.CHAIN_COMPOSITION)) {
											continue;
										}
										// check if number of C atoms is
										// equal
										testNumberOfCAtoms = numberOfCAtomsInFragment
												+ listOfDetectedFragments.get(j).getChainLength()
												+ listOfDetectedFragments.get(k).getChainLength()
												+ listOfDetectedFragments.get(l).getChainLength();
										if (testNumberOfCAtoms == totalNumberOfCAtoms) {
											// check number of double
											// bonds
											testNumberOfDoubleBonds = numberOfDBInFragment
													+ listOfDetectedFragments.get(j).getNumberOfDoubleBonds()
													+ listOfDetectedFragments.get(k).getNumberOfDoubleBonds()
													+ listOfDetectedFragments.get(l).getNumberOfDoubleBonds();
											if (testNumberOfDoubleBonds == totalNumberOfDB) {
												List<String> chains = new ArrayList<>();
												chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
														listOfDetectedFragments.get(i).getLipidChainType(),
														numberOfCAtomsInFragment, numberOfDBInFragment));
												chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
														listOfDetectedFragments.get(j).getLipidChainType(),
														listOfDetectedFragments.get(j).getChainLength(),
														listOfDetectedFragments.get(j).getNumberOfDoubleBonds()));
												chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
														listOfDetectedFragments.get(k).getLipidChainType(),
														listOfDetectedFragments.get(k).getChainLength(),
														listOfDetectedFragments.get(k).getNumberOfDoubleBonds()));
												chains.add(LIPID_CHAIN_BUILDER.builLipidChainAnnotation(
														listOfDetectedFragments.get(l).getLipidChainType(),
														listOfDetectedFragments.get(l).getChainLength(),
														listOfDetectedFragments.get(l).getNumberOfDoubleBonds()));
												String name = LIPID_CHAIN_BUILDER.connectLipidChainAnnotations(chains);
												fattyAcidComposition.add(new LipidIdentity(name,
														peakIdentity.getPropertyValue("Molecular formula")));
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		// remove double entries for lipids with more than one chain
		if (types.length > 1)
			fattyAcidComposition = removeDoubleEntries(fattyAcidComposition);
		return fattyAcidComposition;
	}

	private List<LipidIdentity> removeDoubleEntries(List<LipidIdentity> validatedLipidIdentities) {
		return validatedLipidIdentities.parallelStream().distinct().collect(Collectors.toList());
	}
}
