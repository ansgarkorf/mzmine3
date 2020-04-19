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

package io.github.mzmine.modules.visualization.spectra.simplespectra.spectraidentification.lipidsearch;

import java.awt.Color;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.ui.TextAnchor;

import com.google.common.collect.Range;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.LipidSearchParameters;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.LipidFragmentationRule;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.MSMSLipidTools;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClassType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidFragment;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.customlipidclass.CustomLipidClass;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.lipidmodifications.LipidModification;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidIdentity;
import io.github.mzmine.modules.visualization.spectra.simplespectra.SpectraPlot;
import io.github.mzmine.modules.visualization.spectra.simplespectra.datasets.DataPointsDataSet;
import io.github.mzmine.modules.visualization.spectra.simplespectra.spectraidentification.SpectraDatabaseSearchLabelGenerator;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;

	/**
	 * Task to search and annotate lipids in spectra
	 * 
	 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
	 */
	public class SpectraIdentificationLipidSearchTask extends AbstractTask {

		private Logger logger = Logger.getLogger(this.getClass().getName());
		private String massListName;
		private DataPoint[] massList;
		private Object[] selectedObjects;
		private LipidClasses[] selectedLipids;
		private int minChainLength;
		private int maxChainLength;
		private int maxDoubleBonds;
		private int minDoubleBonds;
		private MZTolerance mzTolerance;
		private IonizationType ionizationType;
		private Boolean searchForCustomLipidClasses;
		private CustomLipidClass[] customLipidClasses;
		private Boolean searchForMSMSFragments;
		private Boolean ionizationAutoSearch;
		private Boolean searchForModifications;
		private double[] lipidModificationMasses;
		private LipidModification[] lipidModification;
		private Scan currentScan;
		private SpectraPlot spectraPlot;
		private Map<DataPoint, String> annotatedMassList = new HashMap<>();

		private int finishedSteps = 0;
		private int totalSteps;

		public static final NumberFormat massFormater = MZmineCore.getConfiguration().getMZFormat();

		/**
		 * Create the task.
		 * 
		 * @param parameters task parameters.
		 */
		public SpectraIdentificationLipidSearchTask(ParameterSet parameters, Scan currentScan,
				SpectraPlot spectraPlot) {

			this.currentScan = currentScan;
			this.spectraPlot = spectraPlot;

			this.massListName = parameters.getParameter(SpectraIdentificationLipidSearchParameters.massList).getValue();
			this.minChainLength = parameters.getParameter(SpectraIdentificationLipidSearchParameters.chainLength)
					.getValue().lowerEndpoint();
			this.maxChainLength = parameters.getParameter(SpectraIdentificationLipidSearchParameters.chainLength)
					.getValue().upperEndpoint();
			this.minDoubleBonds = parameters.getParameter(SpectraIdentificationLipidSearchParameters.doubleBonds)
					.getValue().lowerEndpoint();
			this.maxDoubleBonds = parameters.getParameter(SpectraIdentificationLipidSearchParameters.doubleBonds)
					.getValue().upperEndpoint();
			this.mzTolerance = parameters.getParameter(SpectraIdentificationLipidSearchParameters.mzTolerance)
					.getValue();
			this.selectedObjects = parameters.getParameter(
					SpectraIdentificationLipidSearchParameters.lipidClasses)
					.getValue();
			this.ionizationType = parameters.getParameter(
					SpectraIdentificationLipidSearchParameters.ionizationMethod)
					.getValue();
			this.searchForMSMSFragments = parameters
					.getParameter(SpectraIdentificationLipidSearchParameters.searchForMSMSFragments).getValue();
			this.searchForModifications = parameters.getParameter(LipidSearchParameters.searchForModifications)
					.getValue();
			if (searchForModifications) {
				this.lipidModification = LipidSearchParameters.searchForModifications.getEmbeddedParameter().getValue();
			}
			if (searchForMSMSFragments) {
				this.ionizationAutoSearch = parameters
						.getParameter(SpectraIdentificationLipidSearchParameters.searchForMSMSFragments)
						.getEmbeddedParameters().getParameter(LipidSpeactraSearchMSMSParameters.ionizationAutoSearch)
						.getValue();
			} else {
				this.ionizationAutoSearch = false;
			}
			this.searchForCustomLipidClasses = parameters
					.getParameter(SpectraIdentificationLipidSearchParameters.customLipidClasses).getValue();
			if (searchForCustomLipidClasses) {
				this.customLipidClasses = SpectraIdentificationLipidSearchParameters.customLipidClasses
						.getEmbeddedParameter().getChoices();
			}
			if (currentScan.getMassLists().length == 0) {
				setErrorMessage("Mass List cannot be found.\nCheck if MS2 Scans have a Mass List");
				setStatus(TaskStatus.ERROR);
				return;
			}
			massList = currentScan.getMassList(massListName).getDataPoints();
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
			return ((double) finishedSteps) / totalSteps;
		}

		/**
		 * @see io.github.mzmine.taskcontrol.Task#getTaskDescription()
		 */
		@Override
		public String getTaskDescription() {
			return "Signal identification " + " using the Lipid Search module";
		}

		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {

			setStatus(TaskStatus.PROCESSING);

			Set<DataPoint> massesSet = new HashSet<>(Arrays.asList(massList));

			totalSteps = massList.length;
			// loop through every peak in mass list
			if (getStatus() != TaskStatus.PROCESSING) {
				return;
			}

			// Check if lipids should be modified
			if (searchForModifications) {
				lipidModificationMasses = getLipidModificationMasses(lipidModification);
			}

			// build lipid species database
			Set<LipidIdentity> lipidDatabase = buildLipidDatabase();

			// start lipid search
			massesSet.parallelStream().forEach(dataPoint -> {
				for (LipidIdentity lipid : lipidDatabase) {
					findPossibleLipid(lipid, dataPoint);
				}
				finishedSteps++;
			});

			// new mass list
			DataPoint[] massListAnnotated = annotatedMassList.keySet().toArray(new DataPoint[0]);
			String[] annotations = annotatedMassList.values().toArray(new String[0]);
			DataPointsDataSet detectedCompoundsDataset = new DataPointsDataSet("Detected compounds", massListAnnotated);

			// Add label generator for the dataset
			SpectraDatabaseSearchLabelGenerator labelGenerator = new SpectraDatabaseSearchLabelGenerator(annotations,
					spectraPlot);
			spectraPlot.addDataSet(detectedCompoundsDataset, Color.orange, true, labelGenerator);
			spectraPlot.getXYPlot().getRenderer().setSeriesItemLabelGenerator(spectraPlot.getXYPlot().getSeriesCount(),
					labelGenerator);
			spectraPlot.getXYPlot().getRenderer().setDefaultPositiveItemLabelPosition(
					new ItemLabelPosition(ItemLabelAnchor.CENTER, TextAnchor.TOP_LEFT, TextAnchor.BOTTOM_CENTER, 0.0),
					true);
			setStatus(TaskStatus.FINISHED);

		}

		private Set<LipidIdentity> buildLipidDatabase() {

			Set<LipidIdentity> lipidDatabase = new HashSet<>();
			// Try all combinations of fatty acid lengths and double bonds
			for (int i = 0; i < selectedLipids.length; i++) {
				LipidChainType[] chainTypes = selectedLipids[i].getChainTypes();
				for (int chainLength = minChainLength; chainLength <= maxChainLength; chainLength++) {
					for (int chainDoubleBonds = minDoubleBonds; chainDoubleBonds <= maxDoubleBonds; chainDoubleBonds++) {

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
						lipidDatabase.add(new LipidIdentity(selectedLipids[i], chainLength, chainDoubleBonds,
								chainTypes, LipidClassType.LIPID_CLASS));
					}
				}
			}

			if (searchForCustomLipidClasses && customLipidClasses.length > 0) {
			// add custom lipid classes
			for (int i = 0; i < customLipidClasses.length; i++) {
				LipidChainType[] chainTypes = customLipidClasses[i].getChainTypes();
				for (int chainLength = minChainLength; chainLength <= maxChainLength; chainLength++) {
					for (int chainDoubleBonds = minDoubleBonds; chainDoubleBonds <= maxDoubleBonds; chainDoubleBonds++) {

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
						lipidDatabase.add(new LipidIdentity(customLipidClasses[i], chainLength, chainDoubleBonds,
								chainTypes, LipidClassType.CUSTOM_LIPID_CLASS));
					}
				}
			}
		}
			return lipidDatabase;
		}

		private void findPossibleLipid(LipidIdentity lipid, DataPoint dataPoint) {
			if (isCanceled())
				return;
			Set<IonizationType> ionizationTypeList = new HashSet<>();
			if (ionizationAutoSearch) {
				if (lipid.getLipidClassType().equals(LipidClassType.LIPID_CLASS)) {
					LipidFragmentationRule[] fragmentationRules = lipid.getLipidClass().getFragmentationRules();
					for (int i = 0; i < fragmentationRules.length; i++) {
						ionizationTypeList.add(fragmentationRules[i].getIonizationType());
					}
				} else if (lipid.getLipidClassType().equals(LipidClassType.CUSTOM_LIPID_CLASS)) {
					LipidFragmentationRule[] fragmentationRules = lipid.getCustomLipidClass().getFragmentationRules();
					for (int i = 0; i < fragmentationRules.length; i++) {
						ionizationTypeList.add(fragmentationRules[i].getIonizationType());
					}
				}
			} else {
				ionizationTypeList.add(ionizationType);
			}
			for (IonizationType ionization : ionizationTypeList) {
				if (!currentScan.getPolarity().equals(ionization.getPolarity())) {
					continue;
				}
				double lipidIonMass = lipid.getMass() + ionization.getAddedMass();
				Range<Double> mzTolRange12C = mzTolerance.getToleranceRange(dataPoint.getMZ());
				if (mzTolRange12C.contains(lipidIonMass)) {

					// Calc rel mass deviation;
					double relMassDev = ((lipidIonMass - dataPoint.getMZ()) / lipidIonMass) * 1000000;
					annotatedMassList.put(dataPoint, lipid.getName() + " " + ionization.getAdduct() + ", Δ "
							+ NumberFormat.getInstance().format(relMassDev) + " ppm");

					// If search for MSMS fragments is selected search for fragments
					if (searchForMSMSFragments) {
						searchMsmsFragments(dataPoint, ionization, lipid);
					}

					logger.info("Found lipid: " + lipid.getName() + ", Δ "
							+ NumberFormat.getInstance().format(relMassDev) + " ppm");
				}
				// If search for modifications is selected search for modifications
				// in MS1
				if (searchForModifications) {
					searchModifications(dataPoint, lipidIonMass, lipid, lipidModificationMasses, mzTolRange12C);
				}
			}

		}

		private void searchMsmsFragments(DataPoint dataPoint, IonizationType ionization, LipidIdentity lipid) {
			MSMSLipidTools msmsLipidTools = new MSMSLipidTools();
					LipidFragmentationRule[] rules = new LipidFragmentationRule[] {};
				if (lipid.getLipidClassType().equals(LipidClassType.LIPID_CLASS)) {
					rules = lipid.getLipidClass().getFragmentationRules();
				}
				else if (lipid.getLipidClassType().equals(LipidClassType.CUSTOM_LIPID_CLASS)) {
					rules = lipid.getCustomLipidClass().getFragmentationRules();
				}
				if (rules.length > 0) {
					for (int i = 0; i < massList.length; i++) {
						Range<Double> mzTolRange = mzTolerance.getToleranceRange(massList[i].getMZ());
						LipidFragment annotatedFragment = msmsLipidTools.checkForClassSpecificFragment(mzTolRange,
								lipid, ionization, rules, massList[i].getMZ());
						if (annotatedFragment != null) {
							double relMassDev = ((annotatedFragment.getMzExact() - massList[i]
									.getMZ()) / annotatedFragment.getMzExact()) * 1000000;
							annotatedMassList.put(
									massList[i],
									annotatedFragment.getLipidClass().getAbbr() + " " + annotatedFragment.getRuleType()
											+ " "
											+ ionization.getAdduct()
											+ ", Δ "
											+ NumberFormat.getInstance().format(relMassDev) + " ppm");
						}
					}
				}
			}

			private String findPossibleLipidModification(LipidIdentity lipid, DataPoint dataPoint) {
			String lipidAnnoation = "";
			double lipidIonMass = 0.0;
			double lipidMass = lipid.getMass();
			lipidIonMass = lipidMass + ionizationType.getAddedMass();
			logger.info("Searching for lipid " + lipid.getDescription() + ", " + lipidIonMass + " m/z");
			Range<Double> mzTolRange12C = mzTolerance.getToleranceRange(dataPoint.getMZ());
			// If search for modifications is selected search for modifications in
			// MS1
			if (searchForModifications == true) {
				lipidAnnoation = searchModifications(dataPoint, lipidIonMass,
						lipid,
						lipidModificationMasses,
						mzTolRange12C);
			}
			return lipidAnnoation;
		}

		private String searchModifications(DataPoint dataPoint, double lipidIonMass,
				LipidIdentity lipid,
				double[] lipidModificationMasses, Range<Double> mzTolModification) {
			String lipidAnnoation = "";
			for (int j = 0; j < lipidModificationMasses.length; j++) {
				if (mzTolModification.contains(lipidIonMass + (lipidModificationMasses[j]))) {
					// Calc relativ mass deviation
					double relMassDev = ((lipidIonMass + (lipidModificationMasses[j])
							- dataPoint.getMZ())
							/ (lipidIonMass + lipidModificationMasses[j])) * 1000000;
					// Add row identity
					lipidAnnoation = lipid + " " + ionizationType.getAdduct() + " " + lipidModification[j] + ", Δ "
							+ NumberFormat.getInstance().format(relMassDev) + " ppm";
					logger.info("Found modified lipid: " + lipid.getName() + " " + lipidModification[j] + ", Δ "
							+ NumberFormat.getInstance().format(relMassDev) + " ppm");
				}
			}
			return lipidAnnoation;
		}

		private double[] getLipidModificationMasses(LipidModification[] lipidModification) {
			double[] lipidModificationMasses = new double[lipidModification.length];
			for (int i = 0; i < lipidModification.length; i++) {
				lipidModificationMasses[i] = lipidModification[i].getModificationMass();
			}
			return lipidModificationMasses;
		}

	}
