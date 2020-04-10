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

package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils;

import java.util.Comparator;
import java.util.List;
import org.openscience.cdk.interfaces.IMolecularFormula;
import io.github.mzmine.util.FormulaUtils;

/**
 * This class contains a method to build radyl chains for lipids.
 * 
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class LipidChainBuilder {

  public IMolecularFormula buildLipidChainFormula(LipidChainType chainType, int chainLength,
      int numberOfDB) {
    switch (chainType) {
      case ACYL_CHAIN:
        return calculateMolecularFormulaAcylChain(chainLength, numberOfDB);
      case ALKYL_CHAIN:
        return calculateMolecularFormulaAlkylChain(chainLength, numberOfDB);
      default:
        return calculateMolecularFormulaAcylChain(chainLength, numberOfDB);
    }
  }

  private IMolecularFormula calculateMolecularFormulaAcylChain(int chainLength,
      int numberOfDoubleBonds) {
    int numberOfCAtoms = chainLength;
    int numberOfHAtoms = numberOfCAtoms * 2 - numberOfDoubleBonds * 2;
    int numberOfOAtoms = 2;
    return FormulaUtils.createMajorIsotopeMolFormula(
        "C" + numberOfCAtoms + "H" + numberOfHAtoms + "O" + numberOfOAtoms);
  }

  private IMolecularFormula calculateMolecularFormulaAlkylChain(int chainLength,
      int numberOfDoubleBonds) {
    int numberOfCAtoms = chainLength;
    int numberOfHAtoms = numberOfCAtoms * 2 - numberOfDoubleBonds * 2 + 2;
    return FormulaUtils.createMajorIsotopeMolFormula("C" + numberOfCAtoms + "H" + numberOfHAtoms);
  }

  public String builLipidChainAnnotation(LipidChainType chainType, int chainLength,
      int numberOfDB) {
    switch (chainType) {
      case ACYL_CHAIN:
        return chainLength + ":" + numberOfDB;
      case ALKYL_CHAIN:
        return "O-" + chainLength + ":" + numberOfDB;
      default:
        return chainLength + ":" + numberOfDB;
    }
  }

  public String connectLipidChainAnnotations(List<String> chains) {
    StringBuilder sb = new StringBuilder();
    chains.sort(Comparator.comparing(String::toString));
    boolean allChainsAreSame = allChainsAreSame(chains);
    for (int i = 0; i < chains.size(); i++) {
      if (i == 1) {
          sb.append(chains.get(i));
      } else {
        if (allChainsAreSame) {
          sb.append(chains.get(i) + "/");
        } else {
          sb.append(chains.get(i) + "_");
        }
      }
    }
    return sb.toString();
  }

  private boolean allChainsAreSame(List<String> chains) {
    String firstChain = chains.get(0);
    for (int i = 1; i < chains.size(); i++)
      if (!chains.get(i).equals(firstChain)) {
        return false;
      }
    return true;
  }


}
