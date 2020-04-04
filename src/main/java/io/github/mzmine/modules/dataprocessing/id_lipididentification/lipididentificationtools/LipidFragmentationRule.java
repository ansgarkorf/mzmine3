package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PolarityType;

public class LipidFragmentationRule {

  private PolarityType polarityType;
  private IonizationType ionizationType;
  private LipidFragmentationRuleType lipidFragmentationRuleType;
  private String molecularFormula;

  public LipidFragmentationRule(PolarityType polarityType, IonizationType ionizationType,
      LipidFragmentationRuleType lipidFragmentationRuleType) {
    super();
    this.polarityType = polarityType;
    this.ionizationType = ionizationType;
    this.lipidFragmentationRuleType = lipidFragmentationRuleType;
  }

  public LipidFragmentationRule(PolarityType polarityType, IonizationType ionizationType,
      LipidFragmentationRuleType lipidFragmentationRuleType, String molecularFormula) {
    this.polarityType = polarityType;
    this.ionizationType = ionizationType;
    this.lipidFragmentationRuleType = lipidFragmentationRuleType;
    this.molecularFormula = molecularFormula;
  }

  public PolarityType getPolarityType() {
    return polarityType;
  }

  public IonizationType getIonizationType() {
    return ionizationType;
  }

  public LipidFragmentationRuleType getLipidFragmentationRuleType() {
    return lipidFragmentationRuleType;
  }

  public String getMolecularFormula() {
    return molecularFormula;
  }
  
}
