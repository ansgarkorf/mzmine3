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

package io.github.mzmine.modules.dataprocessing.id_lipididentification;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PeakList;
import io.github.mzmine.datamodel.PeakListRow;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.SimplePeakIdentity;
import io.github.mzmine.datamodel.impl.SimplePeakList;
import io.github.mzmine.datamodel.impl.SimplePeakListAppliedMethod;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.LipidFragmentationRule;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidFragment;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.lipidmodifications.LipidModification;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidIdentity;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;

/**
 * Task to search and annotate lipids in feature list
 *
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class LipidSearchTask extends AbstractTask {

  private Logger logger = Logger.getLogger(this.getClass().getName());
  private double finishedSteps, totalSteps;
  private PeakList peakList;
  private Object[] selectedObjects;
  private LipidClasses[] selectedLipids;
  private int minChainLength, maxChainLength, maxDoubleBonds, minDoubleBonds;
  private MZTolerance mzTolerance, mzToleranceMS2;
  private IonizationType ionizationType;
  private Boolean searchForMSMSFragments;
  private Boolean ionizationAutoSearch;
  private Boolean keepUnconfirmedAnnotations;
  private Boolean searchForModifications;
  private String massListName;
  private double[] lipidModificationMasses;
  private LipidModification[] lipidModification;

  private ParameterSet parameters;

  /**
   * @param parameters
   * @param peakList
   */
  public LipidSearchTask(ParameterSet parameters, PeakList peakList) {

    this.peakList = peakList;
    this.parameters = parameters;

    this.minChainLength =
        parameters.getParameter(LipidSearchParameters.chainLength).getValue().lowerEndpoint();
    this.maxChainLength =
        parameters.getParameter(LipidSearchParameters.chainLength).getValue().upperEndpoint();
    this.minDoubleBonds =
        parameters.getParameter(LipidSearchParameters.doubleBonds).getValue().lowerEndpoint();
    this.maxDoubleBonds =
        parameters.getParameter(LipidSearchParameters.doubleBonds).getValue().upperEndpoint();
    this.mzTolerance = parameters.getParameter(LipidSearchParameters.mzTolerance).getValue();
    this.selectedObjects = parameters.getParameter(LipidSearchParameters.lipidClasses).getValue();
    this.ionizationType =
        parameters.getParameter(LipidSearchParameters.ionizationMethod).getValue();
    this.searchForMSMSFragments =
        parameters.getParameter(LipidSearchParameters.searchForMSMSFragments).getValue();
    this.searchForModifications =
        parameters.getParameter(LipidSearchParameters.searchForModifications).getValue();
    if (searchForModifications) {
      this.lipidModification =
          LipidSearchParameters.searchForModifications.getEmbeddedParameter().getValue();
    }
    if (searchForMSMSFragments) {
      this.mzToleranceMS2 = parameters.getParameter(
          LipidSearchParameters.searchForMSMSFragments)
          .getEmbeddedParameters().getParameter(LipidSearchMSMSParameters.mzToleranceMS2)
          .getValue();
      this.massListName = parameters.getParameter(
          LipidSearchParameters.searchForMSMSFragments)
          .getEmbeddedParameters().getParameter(LipidSearchMSMSParameters.massList).getValue();
      this.ionizationAutoSearch = parameters.getParameter(
          LipidSearchParameters.searchForMSMSFragments)
          .getEmbeddedParameters().getParameter(LipidSearchMSMSParameters.ionizationAutoSearch)
          .getValue();
      this.keepUnconfirmedAnnotations =
          parameters
          .getParameter(LipidSearchParameters.searchForMSMSFragments).getEmbeddedParameters()
          .getParameter(LipidSearchMSMSParameters.keepUnconfirmedAnnotations).getValue();
    } else {
      this.ionizationAutoSearch = false;
      this.keepUnconfirmedAnnotations = true;
    }

    // Convert Objects to LipidClasses
    selectedLipids = Arrays.stream(selectedObjects).filter(o -> o instanceof LipidClasses)
        .map(o -> (LipidClasses) o).toArray(LipidClasses[]::new);
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#getFinishedPercentage()
   */
  @Override
  public double getFinishedPercentage() {
    if (totalSteps == 0)
      return 0;
    return (finishedSteps) / totalSteps;
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#getTaskDescription()
   */
  @Override
  public String getTaskDescription() {
    return "Prediction of lipids in " + peakList;
  }

  /**
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    logger.info("Starting lipid search in " + peakList);

    List<PeakListRow> rows = peakList.getRows();

    // Check if lipids should be modified
    if (searchForModifications) {
      lipidModificationMasses = getLipidModificationMasses(lipidModification);
    }
    // Calculate how many possible lipids we will try
    totalSteps = ((maxChainLength - minChainLength + 1) * (maxDoubleBonds - minDoubleBonds + 1))
        * selectedLipids.length;

    // Try all combinations of fatty acid lengths and double bonds
    for (int i = 0; i < selectedLipids.length; i++) {
      LipidChainType[] chainTypes = selectedLipids[i].getChainTypes();
      for (int chainLength = minChainLength; chainLength <= maxChainLength; chainLength++) {
        for (int chainDoubleBonds =
            minDoubleBonds; chainDoubleBonds <= maxDoubleBonds; chainDoubleBonds++) {
          // Task canceled?
          if (isCanceled())
            return;

          // If we have non-zero fatty acid, which is shorter
          // than minimal length, skip this lipid
          if (((chainLength > 0) && (chainLength < minChainLength))) {
            finishedSteps++;
            continue;
          }

          // If we have more double bonds than carbons, it
          // doesn't make sense, so let's skip such lipids
          if (((chainDoubleBonds > 0) && (chainDoubleBonds > chainLength - 1))) {
            finishedSteps++;
            continue;
          }
          // Prepare a lipid instance
          LipidIdentity lipidChain = new LipidIdentity(selectedLipids[i], chainLength,
              chainDoubleBonds, chainTypes);

          // Find all rows that match this lipid
          findPossibleLipid(lipidChain, rows);
          finishedSteps++;
        }
      }
    }
    // Add task description to peakList
    ((SimplePeakList) peakList)
        .addDescriptionOfAppliedTask(new SimplePeakListAppliedMethod("Lipid search", parameters));

    setStatus(TaskStatus.FINISHED);

    logger.info("Finished lipid search task in " + peakList);
  }

  /**
   * Check if candidate peak may be a possible adduct of a given main peak
   *
   * @param mainPeak
   * @param possibleFragment
   */
  private void findPossibleLipid(LipidIdentity lipid, List<PeakListRow> rows) {
    if (isCanceled())
      return;
    Set<IonizationType> ionizationTypeList = new HashSet<>();
    if (ionizationAutoSearch) {
      LipidFragmentationRule[] fragmentationRules = lipid.getLipidClass().getFragmentationRules();
      for (int i = 0; i < fragmentationRules.length; i++) {
        ionizationTypeList.add(fragmentationRules[i].getIonizationType());
      }
    } else {
      ionizationTypeList.add(ionizationType);
    }
    rows.parallelStream().forEach(row -> {
        for (IonizationType ionization : ionizationTypeList) {
          if (!row.getBestPeak().getRepresentativeScan().getPolarity()
              .equals(ionization.getPolarity())) {
            return;
          }
          double lipidIonMass = lipid.getMass() + ionization.getAddedMass();
          Range<Double> mzTolRange12C = mzTolerance.getToleranceRange(row.getAverageMZ());
          if (mzTolRange12C.contains(lipidIonMass)) {

            // Calc rel mass deviation;
            double relMassDev = ((lipidIonMass - row.getAverageMZ()) / lipidIonMass) * 1000000;
            row.addPeakIdentity(lipid, true);
            row.setPreferredPeakIdentity(lipid);
            row.setComment("Ionization: " + ionization.getAdduct()
                + ", Δ "
                + NumberFormat.getInstance().format(relMassDev) + " ppm"); // Format relativ mass
            // deviation
            // If search for MSMS fragments is selected search for fragments
            if (searchForMSMSFragments) {
              searchMsmsFragments(row, ionization, lipid);
            } else {
              row.setComment(row.getComment()
                  + " Warning: Lipid Annotation was not confirmed by MS/MS and needs to be checked manually!");
            }
            logger.info("Found lipid: " + lipid.getName() + ", Δ "
                + NumberFormat.getInstance().format(relMassDev) + " ppm");
          }
          // If search for modifications is selected search for modifications
          // in MS1
          if (searchForModifications) {
            searchModifications(row, lipidIonMass, lipid, lipidModificationMasses, mzTolRange12C);
          }
        }
    });
  }

  /**
   * This method searches for MS/MS fragments. A mass list for MS2 scans will be used if present. If
   * no mass list is present for MS2 scans it will create one using centroid or exact mass detection
   * algorithm
   */
  private void searchMsmsFragments(PeakListRow row, IonizationType ionization,
      LipidIdentity lipid) {

    // Check if selected feature has MSMS spectra and LipidIdentity
    if (row
        .getAllMS2Fragmentations().length > 0
        && row.getPreferredPeakIdentity() instanceof LipidIdentity) {
      Scan[] msmsScans = row.getAllMS2Fragmentations();
      boolean confirmFormula = false;
      List<LipidIdentity> listOfChainCompositions = new ArrayList<>();
      for (int i = 0; i < msmsScans.length; i++) {
        if (msmsScans[i].getMassLists().length == 0) {
          setErrorMessage("Mass List cannot be found.\nCheck if MS2 Scans have a Mass List");
          setStatus(TaskStatus.ERROR);
          return;
        }
        DataPoint[] massList = null;
        massList = msmsScans[i].getMassList(massListName).getDataPoints();
        MSMSLipidTools msmsLipidTools = new MSMSLipidTools();

        LipidFragmentationRule[] rules = lipid.getLipidClass().getFragmentationRules();
        List<LipidFragment> listOfAnnotatedFragments = new ArrayList<>();
        if (rules.length > 0) {
          for (int j = 0; j < massList.length; j++) {
            Range<Double> mzTolRangeMSMS = mzToleranceMS2.getToleranceRange(massList[j].getMZ());
              LipidFragment annotatedFragment =
                  msmsLipidTools.checkForClassSpecificFragment(
                      mzTolRangeMSMS,
                      (LipidIdentity) row.getPreferredPeakIdentity(), ionization,
                      rules,
                      massList[j].getMZ());
              if (annotatedFragment != null) {
                listOfAnnotatedFragments.add(annotatedFragment);
              }
            }
          }
          if (!listOfAnnotatedFragments.isEmpty()) {

            // check for headgroup fragment
            confirmFormula =
                msmsLipidTools.confirmHeadgroupFragmentPresent(listOfAnnotatedFragments);

            // predict lipid fatty acid composition if possible
            listOfChainCompositions =
                msmsLipidTools.predictChainComposition(listOfAnnotatedFragments,
                    row.getPreferredPeakIdentity(), lipid.getLipidClass().getChainTypes());

          } else {
            if (keepUnconfirmedAnnotations) {
              row.setComment(
                  row.getComment()
                      + " Warning: Lipid Annotation was not confirmed by MS/MS and needs to be checked manually!");
            } else {
              row.removePeakIdentity(row.getPreferredPeakIdentity());
              row.setComment("");
            }
          }
        }
        // add annotations to feature list
        if (listOfChainCompositions.isEmpty()
            && confirmFormula == false
            && keepUnconfirmedAnnotations == false) {
          row.removePeakIdentity(row.getPreferredPeakIdentity());
        }
        for (int j = 0; j < listOfChainCompositions.size(); j++) {
          String name = lipid.getLipidClass().getName() + " " + lipid.getLipidClass().getAbbr()
              + "(" + listOfChainCompositions.get(j).getName() + ")";
          row.addPeakIdentity(new LipidIdentity(name, listOfChainCompositions.get(j).getFormula()),
              true);
          row.setPreferredPeakIdentity(
              new LipidIdentity(name, listOfChainCompositions.get(j).getFormula()));
        }
      }
  }

  private void searchModifications(PeakListRow rows, double lipidIonMass, LipidIdentity lipid,
      double[] lipidModificationMasses, Range<Double> mzTolModification) {
    for (int j = 0; j < lipidModificationMasses.length; j++) {
      if (mzTolModification.contains(lipidIonMass + (lipidModificationMasses[j]))) {
        // Calc relativ mass deviation
        double relMassDev = ((lipidIonMass + (lipidModificationMasses[j]) - rows.getAverageMZ())
            / (lipidIonMass + lipidModificationMasses[j])) * 1000000;
        // Add row identity
        rows.addPeakIdentity(new SimplePeakIdentity(lipid + " " + lipidModification[j]), false);
        rows.setComment(rows.getComment()
            + " Ionization: " + ionizationType.getAdduct()
            + " Warning: Lipid Annotation was not confirmed by MS/MS and needs to be checked manually!"
            + lipidModification[j]
                    + ", Δ " + NumberFormat.getInstance().format(relMassDev) + " ppm");
            logger.info(
                " Warning: Lipid Annotation was not confirmed by MS/MS and needs to be checked manually! Found modified lipid: "
                    + lipid.getName() + " " + lipidModification[j]
                    + ", Δ "
            + NumberFormat.getInstance().format(relMassDev) + " ppm");
      }
    }
  }

  private double[] getLipidModificationMasses(LipidModification[] lipidModification) {
    double[] lipidModificationMasses = new double[lipidModification.length];
    for (int i = 0; i < lipidModification.length; i++) {
      lipidModificationMasses[i] = lipidModification[i].getModificationMass();
    }
    return lipidModificationMasses;
  }
}
