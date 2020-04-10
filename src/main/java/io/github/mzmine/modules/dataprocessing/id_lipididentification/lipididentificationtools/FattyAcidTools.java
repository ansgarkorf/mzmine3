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
import org.openscience.cdk.interfaces.IMolecularFormula;
import io.github.mzmine.datamodel.PeakIdentity;
import io.github.mzmine.util.FormulaUtils;

/**
 * This class contains methods to build fatty acids for MS/MS identification of lipids
 * 
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class FattyAcidTools {

  /**
   * This method calculates all possible fatty acids formulas for a selected annotated lipid
   */
  public List<String> calculateFattyAcidFormulas(PeakIdentity peakIdentity) {
    List<String> fattyAcidFormulas = new ArrayList<>();
    LipidTools lipidTools = new LipidTools();
    int minFattyAcidLength = 1;
    int maxFattyAcidLength = lipidTools.getNumberOfCAtoms(peakIdentity.getName());
    int minNumberOfDoubleBonds = 0;
    int maxNumberOfDoubleBonds = lipidTools.getNumberOfDB(peakIdentity.getName());

    for (int fattyAcidLength =
        minFattyAcidLength; fattyAcidLength <= maxFattyAcidLength; fattyAcidLength++) {
      for (int fattyAcidDoubleBonds =
          minNumberOfDoubleBonds; fattyAcidDoubleBonds <= maxNumberOfDoubleBonds; fattyAcidDoubleBonds++) {
        if (((fattyAcidDoubleBonds >= 0)
            && (fattyAcidDoubleBonds <= fattyAcidLength - 1))) {
          fattyAcidFormulas.add(calculateFattyAcidFormula(fattyAcidLength, fattyAcidDoubleBonds));
        }
      }
    }

    return fattyAcidFormulas;
  }

  /**
   * This method creates all possible fatty acid names for a selected annotated lipid
   */
  public List<String> getFattyAcidNames(PeakIdentity peakIdentity) {

    List<String> fattyAcidNames = new ArrayList<>();
    LipidTools lipidTools = new LipidTools();
    int minFattyAcidLength = 1;
    int maxFattyAcidLength = lipidTools.getNumberOfCAtoms(peakIdentity.getName());
    int minNumberOfDoubleBonds = 0;
    int maxNumberOfDoubleBonds = lipidTools.getNumberOfDB(peakIdentity.getName());

    for (int fattyAcidLength =
        minFattyAcidLength; fattyAcidLength <= maxFattyAcidLength; fattyAcidLength++) {
      for (int fattyAcidDoubleBonds =
          minNumberOfDoubleBonds; fattyAcidDoubleBonds <= maxNumberOfDoubleBonds; fattyAcidDoubleBonds++) {
        if (((fattyAcidDoubleBonds >= 0)
            && (fattyAcidDoubleBonds <= fattyAcidLength - 1))) {
          fattyAcidNames.add(getFattyAcidName(fattyAcidLength, fattyAcidDoubleBonds));
        }
      }
    }

    return fattyAcidNames;
  }

  /**
   * This method creates a String molecularFormula formula for a fatty acid
   */
  public String calculateFattyAcidFormula(int fattyAcidLength, int fattyAcidDoubleBonds) {
    int numberOfHydrogens = fattyAcidLength * 2 - fattyAcidDoubleBonds * 2;
    return "C" + fattyAcidLength + 'H' + numberOfHydrogens + 'O' + 2;
  }

  /**
   * This method creates the systematic name of a fatty acid
   */
  public String getFattyAcidName(int fattyAcidLength, int fattyAcidDoubleBonds) {
    return "(" + fattyAcidLength + ":" + fattyAcidDoubleBonds + ")";
  }

  public int getChainLengthFromFormula(String fattyAcidFormula) {
    IMolecularFormula formula = FormulaUtils.createMajorIsotopeMolFormula(fattyAcidFormula);
    return FormulaUtils.countElement(formula, "C");
  }

  public int getNumberOfDoubleBondsFromFormula(String fattyAcidFormula) {
    IMolecularFormula formula = FormulaUtils.createMajorIsotopeMolFormula(fattyAcidFormula);
    int chainLength = FormulaUtils.countElement(formula, "C");
    int numberOfHydrogen = FormulaUtils.countElement(formula, "H");
    int delta = (chainLength * 2 - numberOfHydrogen);
    if (delta > 0) {
      return delta / 2;
    } else {
      return 0;
    }
  }

}
