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

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.LipidFragmentationRule;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipididentificationtools.LipidFragmentationRuleType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidFragmentInformationLevelType;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
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

public class CustomLipidClassFragmentationRulesChoiceComponent extends BorderPane {

	// Logger.
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final CheckListView<LipidFragmentationRule> checkList = new CheckListView<LipidFragmentationRule>();
	private final FlowPane buttonsPane = new FlowPane(Orientation.VERTICAL);
	private final Button addButton = new Button("Add...");
	private final Button importButton = new Button("Import...");
	private final Button exportButton = new Button("Export...");
	private final Button removeButton = new Button("Remove");

	// Filename extension.
	private static final String FILENAME_EXTENSION = "*.csv";

	public CustomLipidClassFragmentationRulesChoiceComponent(LipidFragmentationRule[] choices) {
	
			ObservableList<LipidFragmentationRule> choicesList = FXCollections.observableArrayList(Arrays.asList(choices));
	
			checkList.setItems(choicesList);
			setCenter(checkList);
	
			addButton.setOnAction(e -> {
				final ParameterSet parameters = new AddLipidFragmentationRuleParameters();
				if (parameters.showSetupDialog(true) != ExitCode.OK)
					return;
	
				// Create new custom lipid class
				LipidFragmentationRule lipidFragmentationRule = new LipidFragmentationRule(//
						parameters.getParameter(AddLipidFragmentationRuleParameters.polarity).getValue(), // polarity
						parameters.getParameter(AddLipidFragmentationRuleParameters.ionizationMethod)
								.getValue(), // ionization
						parameters.getParameter(AddLipidFragmentationRuleParameters.lipidFragmentationRuleType)
								.getValue(), // rule type
						parameters
								.getParameter(
										AddLipidFragmentationRuleParameters.lipidFragmentationRuleInformationLevel) // information
																													// level
								.getValue(),
						parameters.getParameter(AddLipidFragmentationRuleParameters.formula)
								.getValue() // formula
				);
	
				// Add to list of choices (if not already present).
				if (!checkList.getItems().contains(lipidFragmentationRule)) {
					checkList.getItems().add(lipidFragmentationRule);
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
	//					final LipidFragmentationRule lipidFragmentationRule = new LipidFragmentationRule(line[0], line[1], line[2], line[3],
	//							line[4]);
	//					if (!checkList.getItems().contains(lipidFragmentationRule)) {
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
					for (final LipidFragmentationRule lipidFragmentationRule : checkList.getItems()) {
	
						writer.writeln(new String[] { //
								lipidFragmentationRule.getPolarityType().toString(), //
								lipidFragmentationRule.getIonizationType().toString(), //
								lipidFragmentationRule.getLipidFragmentationRuleType().toString(), //
								lipidFragmentationRule.getLipidFragmentInformationLevelType().toString(), //
								lipidFragmentationRule.getMolecularFormula() });
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

	void setValue(List<LipidFragmentationRule> checkedItems) {
		checkList.getSelectionModel().clearSelection();
		for (LipidFragmentationRule mod : checkedItems)
			checkList.getSelectionModel().select(mod);
	}

	public List<LipidFragmentationRule> getChoices() {
		return checkList.getCheckModel().getCheckedItems();
	}

	public List<LipidFragmentationRule> getValue() {
		return checkList.getSelectionModel().getSelectedItems();
	}

	/**
		 * Represents a fragmentation rule of a custom lipid class.
		 */
		private static class AddLipidFragmentationRuleParameters extends SimpleParameterSet {
	
			private static final ComboParameter<PolarityType> polarity = new ComboParameter<PolarityType>("Polarity",
					"Select polarity type", new PolarityType[] {PolarityType.POSITIVE, PolarityType.NEGATIVE});
	
			public static final ComboParameter<IonizationType> ionizationMethod = new ComboParameter<IonizationType>(
					"Ionization method", "Type of ion used to calculate the ionized mass", IonizationType.values());
	
			public static final ComboParameter<LipidFragmentationRuleType> lipidFragmentationRuleType = new ComboParameter<LipidFragmentationRuleType>(
					"Lipid fragmentation rule type", "Choose the type of the lipid fragmentation rule", LipidFragmentationRuleType.values());
	
			public static final ComboParameter<LipidFragmentInformationLevelType> lipidFragmentationRuleInformationLevel = new ComboParameter<LipidFragmentInformationLevelType>(
					"Lipid fragment information level", "Choose the information value of the lipid fragment, molecular formula level, or chain composition level", LipidFragmentInformationLevelType.values());
	
			private static final StringParameter formula = new StringParameter("Molecular formula",
					"Enter a molecular formula, if it is involved in the fragmentation rule. E.g. a head group fragment needs to be specified by its molecular formula.");
	
			private AddLipidFragmentationRuleParameters() {
				super(new Parameter[] { polarity,  ionizationMethod, lipidFragmentationRuleType, lipidFragmentationRuleInformationLevel, formula});
		    }
		}
	}
