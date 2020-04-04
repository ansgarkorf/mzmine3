package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils;

import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClasses;
import io.github.mzmine.util.FormulaUtils;

public class LipidFactory {

  private static final LipidChainBuilder LIPID_CHAIN_BUILDER = new LipidChainBuilder();

  public LipidIdentity buildLipid(LipidClasses lipidClass, int chainLength,
   int chainDoubleBonds,  LipidChainType[] chainTypes) {
   String name = lipidClass.getName() + " " + lipidClass.getAbbr() + '(' + chainLength + ':'
       + chainDoubleBonds + ')';
   String molecularFormula = MolecularFormulaManipulator.getString(synthesisLipidMolecularFormula(
       lipidClass.getBackBoneFormula(), chainLength, chainDoubleBonds, chainTypes));
   return new LipidIdentity(name,molecularFormula);
   }


   // lipid synthesis
   public IMolecularFormula synthesisLipidMolecularFormula(
       String lipidBackbone,
       int chainLength,
       int chainDoubleBonds, LipidChainType[] chainTypes) {

     IMolecularFormula lipidBackboneFormula =
         FormulaUtils.createMajorIsotopeMolFormula(lipidBackbone);

     int numberOfCarbonsPerChain = chainLength / chainTypes.length;
     int restCarbons = chainLength % chainTypes.length;
     int numberOfDoubleBondsPerChain = chainDoubleBonds / chainTypes.length;
     int restDoubleBonds = chainDoubleBonds % chainTypes.length;

     // build chains
     for (int i = 0; i < chainTypes.length; i++) {

       // add rests to last chain
       if (i == chainTypes.length - 1) {
         numberOfCarbonsPerChain = numberOfCarbonsPerChain + restCarbons;
         numberOfDoubleBondsPerChain = numberOfDoubleBondsPerChain + restDoubleBonds;
       }
       IMolecularFormula chainFormula = LIPID_CHAIN_BUILDER.buildLipidChainFormula(chainTypes[i],
           numberOfCarbonsPerChain, numberOfDoubleBondsPerChain);
       lipidBackboneFormula =
           doChainTypeSpecificSynthesis(chainTypes[i], lipidBackboneFormula, chainFormula);
     }
     return lipidBackboneFormula;
   }

   // Chemical reactions
   private IMolecularFormula doChainTypeSpecificSynthesis(LipidChainType type,
       IMolecularFormula lipidBackbone, IMolecularFormula chainFormula) {
     switch (type) {
       case ACYL_CHAIN:
         return doEsterBonding(lipidBackbone, chainFormula);
       case ALKYL_CHAIN:
         return doEtherBonding(lipidBackbone, chainFormula);
       default:
         return null;
     }
   }

   // create ester bonding
   private IMolecularFormula doEsterBonding(IMolecularFormula backboneFormula,
       IMolecularFormula chainFormula) {
     IMolecularFormula secondaryProduct = FormulaUtils.createMajorIsotopeMolFormula("H2O");
     IMolecularFormula product = FormulaUtils.addFormula(backboneFormula, chainFormula);
     product = FormulaUtils.subtractFormula(product, secondaryProduct);
     return product;
   }

   // create ester bonding
   private IMolecularFormula doEtherBonding(IMolecularFormula backboneFormula,
       IMolecularFormula chainFormula) {
     IMolecularFormula secondaryProduct = FormulaUtils.createMajorIsotopeMolFormula("H2");
     IMolecularFormula product = FormulaUtils.addFormula(backboneFormula, chainFormula);
     product = FormulaUtils.subtractFormula(product, secondaryProduct);
     return product;
   }



}
