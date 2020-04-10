package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids;

import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.LipidFragmentationRuleType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainType;

public class LipidFragment {

  private LipidFragmentationRuleType ruleType;
  private LipidFragmentInformationLevelType lipidFragmentInformationLevelType;
  private Double mzExact;
  private Double mzAccurate;
  private LipidClasses lipidClass;
  private Integer chainLength;
  private Integer numberOfDoubleBonds;
  private LipidChainType lipidChainType;

  public LipidFragment(LipidFragmentationRuleType ruleType,
      LipidFragmentInformationLevelType lipidFragmentInformationLevelType, Double mzExact,
      Double mzAccurate, LipidClasses lipidClass, Integer chainLength,
      Integer numberOfDoubleBonds, LipidChainType lipidChainType) {
    this.ruleType = ruleType;
    this.lipidFragmentInformationLevelType = lipidFragmentInformationLevelType;
    this.mzExact = mzExact;
    this.mzAccurate = mzAccurate;
    this.lipidClass = lipidClass;
    this.chainLength = chainLength;
    this.numberOfDoubleBonds = numberOfDoubleBonds;
    this.lipidChainType = lipidChainType;
  }

  public LipidFragmentationRuleType getRuleType() {
    return ruleType;
  }

  public LipidFragmentInformationLevelType getLipidFragmentInformationLevelType() {
    return lipidFragmentInformationLevelType;
  }

  public Double getMzExact() {
    return mzExact;
  }

  public Double getMzAccurate() {
    return mzAccurate;
  }

  public LipidClasses getLipidClass() {
    return lipidClass;
  }

  public Integer getChainLength() {
    return chainLength;
  }

  public Integer getNumberOfDoubleBonds() {
    return numberOfDoubleBonds;
  }

  public LipidChainType getLipidChainType() {
    return lipidChainType;
  }
}
