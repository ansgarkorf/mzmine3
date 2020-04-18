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
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils.LipidChainType;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
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
	
public class CustomLipidChainChoiceComponent extends BorderPane {
	
	// Logger.
	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final CheckListView<LipidChainType> checkList = new CheckListView<LipidChainType>();
	private final FlowPane buttonsPane = new FlowPane(Orientation.VERTICAL);
	private final Button addButton = new Button("Add...");
	private final Button importButton = new Button("Import...");
	private final Button exportButton = new Button("Export...");
	private final Button removeButton = new Button("Remove");
	
	// Filename extension.
	private static final String FILENAME_EXTENSION = "*.csv";
	
	public CustomLipidChainChoiceComponent(LipidChainType[] choices) {

		ObservableList<LipidChainType> choicesList = FXCollections.observableArrayList(Arrays.asList(choices));

		checkList.setItems(choicesList);
		setCenter(checkList);

		addButton.setOnAction(e -> {
			final ParameterSet parameters = new AddLipidChainTypeParameters();
			if (parameters.showSetupDialog(true) != ExitCode.OK)
				return;

			// Create new custom lipid class
			LipidChainType lipidChainType = parameters.getParameter(AddLipidChainTypeParameters.lipidChainType)
					.getValue();

			// Add to list of choices
				checkList.getItems().add(lipidChainType);
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

					// // Create new custom lipid cöass and add it to the choices if it's
					// // new.
					// final LipidChainType lipidChainType = new LipidChainType(line[0],
					// line[1], line[2], line[3],
					// line[4]);
					// if (!checkList.getItems().contains(lipidChainType)) {
					// checkList.getItems();
					// }
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
						for (final LipidChainType lipidChainType : checkList.getItems()) {

							writer.writeln(new String[] { //
									lipidChainType.getName() //
							});
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
	
			void setValue(List<LipidChainType> checkedItems) {
				checkList.getSelectionModel().clearSelection();
				for (LipidChainType mod : checkedItems)
					checkList.getSelectionModel().select(mod);
			}
	
			public List<LipidChainType> getChoices() {
				return checkList.getCheckModel().getCheckedItems();
			}
	
			public List<LipidChainType> getValue() {
				return checkList.getSelectionModel().getSelectedItems();
			}
	
			/**
			 * Represents a fragmentation rule of a custom lipid class.
			 */
			private static class AddLipidChainTypeParameters extends SimpleParameterSet {
	
				private static final ComboParameter<LipidChainType> lipidChainType = new ComboParameter<LipidChainType>(
						"Select lipid chain type", "Select lipid chain type",
						new LipidChainType[] { LipidChainType.ACYL_CHAIN, LipidChainType.ALKYL_CHAIN });
	
				private AddLipidChainTypeParameters() {
					super(new Parameter[] { lipidChainType });
				}
			}
		}