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

package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.customlipidclass;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.controlsfx.control.CheckListView;

import com.Ostermiller.util.CSVParser;
import com.Ostermiller.util.CSVPrinter;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.LipidFragmentationRule;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainType;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.util.ExitCode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class CustomLipidClassChoiceComponent extends BorderPane {

	// Logger.
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final CheckListView<CustomLipidClass> checkList = new CheckListView<CustomLipidClass>();
	private final FlowPane buttonsPane = new FlowPane(Orientation.VERTICAL);
	private final Button addButton = new Button("Add...");
	private final Button importButton = new Button("Import...");
	private final Button exportButton = new Button("Export...");
	private final Button removeButton = new Button("Remove");

	// Filename extension.
	private static final String FILENAME_EXTENSION = "*.csv";

	public CustomLipidClassChoiceComponent(CustomLipidClass[] choices) {

		ObservableList<CustomLipidClass> choicesList = FXCollections.observableArrayList(Arrays.asList(choices));

		checkList.setItems(choicesList);
		setCenter(checkList);
		setMaxHeight(100);
		addButton.setOnAction(e -> {
			final ParameterSet parameters = new AddCustomLipidClassParameters();
			if (parameters.showSetupDialog(true) != ExitCode.OK)
				return;

			// Create new custom lipid class
			CustomLipidClass customLipidClass = new CustomLipidClass(
					parameters.getParameter(AddCustomLipidClassParameters.name).getValue(),
					parameters.getParameter(AddCustomLipidClassParameters.abbr).getValue(),
					parameters.getParameter(AddCustomLipidClassParameters.backBoneFormula).getValue(),
					parameters.getParameter(AddCustomLipidClassParameters.lipidChainTypes)
							.getChoices(),
					parameters.getParameter(AddCustomLipidClassParameters.customLipidClassFragmentationRules)
							.getEmbeddedParameter().getChoices());

			// Add to list of choices (if not already present).
			if (!checkList.getItems().contains(customLipidClass)) {
				checkList.getItems().add(customLipidClass);
			}
		});

		importButton.setTooltip(new Tooltip("Import custom lipid class from a CSV file"));
		importButton.setOnAction(e -> {

			// Create the chooser if necessary.
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Select custom lipid class file");
			chooser.getExtensionFilters().add(new ExtensionFilter("Comma-separated values files", FILENAME_EXTENSION));

			// Select a file.
			final File file = chooser.showOpenDialog(this.getScene().getWindow());
			if (file == null)
				return;

			// Read the CSV file into a string array.
			String[][] csvLines = null;
			try {

				csvLines = CSVParser.parse(new FileReader(file));
			} catch (IOException ex) {
				final String msg = "There was a problem reading the custom lipid class file.";
				MZmineCore.getDesktop().displayErrorMessage(msg + "\n(" + ex.getMessage() + ')');
				logger.log(Level.SEVERE, msg, ex);
				return;
			}

			// Load adducts from CSV data into parent choices.
			for (final String line[] : csvLines) {
				try {

//					// Create new custom lipid cöass and add it to the choices if it's
//					// new.
//					final CustomLipidClass customLipidClass = new CustomLipidClass(line[0], line[1], line[2], line[3],
//							line[4]);
//					if (!checkList.getItems().contains(customLipidClass)) {
//						checkList.getItems();
//					}
				} catch (final NumberFormatException ignored) {
					logger.warning("Couldn't find custom lipid class information in line " + line[0]);
				}
			}

		});

		exportButton.setTooltip(new Tooltip("Export custom modifications to a CSV file"));
		exportButton.setOnAction(e -> {
			// Create the chooser if necessary.

			FileChooser chooser = new FileChooser();
			chooser.setTitle("Select lipid modification file");
			chooser.getExtensionFilters().add(new ExtensionFilter("Comma-separated values files", FILENAME_EXTENSION));

			// Choose the file.
			final File file = chooser.showSaveDialog(this.getScene().getWindow());
			if (file == null)
				return;

			// Export the modifications.
			try {

				final CSVPrinter writer = new CSVPrinter(new FileWriter(file));
				for (final CustomLipidClass customLipidClass : checkList.getItems()) {

					writer.writeln(new String[] { //
							customLipidClass.getName(), //
							customLipidClass.getAbbr(), //
							customLipidClass.getBackBoneFormula(), //
							customLipidClass.getChainTypes().toString(), //
							customLipidClass.getFragmentationRules().toString() });
				}

			} catch (IOException ex) {
				final String msg = "There was a problem writing the lipid modifications file.";
				MZmineCore.getDesktop().displayErrorMessage(msg + "\n(" + ex.getMessage() + ')');
				logger.log(Level.SEVERE, msg, ex);
			}

		});

		removeButton.setTooltip(new Tooltip("Remove all lipid modification"));
		removeButton.setOnAction(e -> {
			checkList.getItems().clear();
		});

		buttonsPane.getChildren().addAll(addButton, importButton, exportButton, removeButton);
		setRight(buttonsPane);

	}

	void setValue(List<CustomLipidClass> checkedItems) {
		checkList.getSelectionModel().clearSelection();
		for (CustomLipidClass mod : checkedItems)
			checkList.getSelectionModel().select(mod);
	}

	public List<CustomLipidClass> getChoices() {
		return checkList.getCheckModel().getCheckedItems();
	}

	public List<CustomLipidClass> getValue() {
		return checkList.getSelectionModel().getSelectedItems();
	}

	/**
	 * Represents a custom lipid class.
	 */
	public static class AddCustomLipidClassParameters extends SimpleParameterSet {

		// lipid modification
		private static final StringParameter name = new StringParameter("Custom lipid class name",
				"Enter the name of the custom lipid class");

		private static final StringParameter abbr = new StringParameter("Custom lipid class abbreviation",
				"Enter a abbreviation for the custom lipid class");

		private static final StringParameter backBoneFormula = new StringParameter("Lipid Backbone Molecular Formula",
				"Enter the backbone molecular formula of the custom lipid class. Include all elements of the original molecular, e.g. in case of glycerol based  lipid classes add C3H8O3");

		private static final CustomLipidChainChoiceParameter lipidChainTypes = new CustomLipidChainChoiceParameter(
				"Add Lipid Chains", "Add Lipid Chains", new LipidChainType[0]);

		public static final OptionalParameter<CustomLipidClassFragmentationRulesChoiceParameters> customLipidClassFragmentationRules = new OptionalParameter<CustomLipidClassFragmentationRulesChoiceParameters>(
				new CustomLipidClassFragmentationRulesChoiceParameters(
						"Add fragmentation rules",
						"Add custom lipid class fragmentation rules", new LipidFragmentationRule[0]));

		public AddCustomLipidClassParameters() {
			super(new Parameter[] { name, abbr, backBoneFormula, lipidChainTypes, customLipidClassFragmentationRules });
	    }
	}

}
