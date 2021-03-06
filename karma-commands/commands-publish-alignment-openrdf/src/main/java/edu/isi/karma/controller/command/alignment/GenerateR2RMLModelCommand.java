/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.controller.command.alignment;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.UriBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandType;
import edu.isi.karma.controller.command.ICommand;
import edu.isi.karma.controller.command.transformation.SubmitEditPythonTransformationCommand;
import edu.isi.karma.controller.command.transformation.SubmitPythonTransformationCommand;
import edu.isi.karma.controller.history.CommandHistory;
import edu.isi.karma.controller.history.CommandHistory.HistoryArguments;
import edu.isi.karma.controller.history.HistoryJsonUtil;
import edu.isi.karma.controller.update.AbstractUpdate;
import edu.isi.karma.controller.update.ErrorUpdate;
import edu.isi.karma.controller.update.HistoryUpdate;
import edu.isi.karma.controller.update.InfoUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.controller.update.WorksheetUpdateFactory;
import edu.isi.karma.modeling.ModelingConfiguration;
import edu.isi.karma.modeling.alignment.Alignment;
import edu.isi.karma.modeling.alignment.AlignmentManager;
import edu.isi.karma.modeling.alignment.SemanticModel;
import edu.isi.karma.modeling.alignment.learner.ModelLearningGraph;
import edu.isi.karma.modeling.semantictypes.SemanticTypeUtil;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.HNode.HNodeType;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.rep.metadata.WorksheetProperties;
import edu.isi.karma.rep.metadata.WorksheetProperties.Property;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.webserver.ServletContextParameterMap;
import edu.isi.karma.webserver.ServletContextParameterMap.ContextParameter;

public class GenerateR2RMLModelCommand extends Command {

	private final String worksheetId;
	private String worksheetName;
	private String tripleStoreUrl;
	private String graphContext;
	private String RESTserverAddress;
	private static Logger logger = LoggerFactory.getLogger(GenerateR2RMLModelCommand.class);

	public enum JsonKeys {
		updateType, fileUrl, worksheetId
	}

	public enum PreferencesKeys {
		rdfPrefix, rdfNamespace, modelSparqlEndPoint
	}

	protected GenerateR2RMLModelCommand(String id, String worksheetId, String url, String context) {
		super(id);
		this.worksheetId = worksheetId;
		this.tripleStoreUrl = url;
		this.graphContext = context;
	}

	public String getTripleStoreUrl() {
		return tripleStoreUrl;
	}

	public void setTripleStoreUrl(String tripleStoreUrl) {
		this.tripleStoreUrl = tripleStoreUrl;
	}

	public String getGraphContext() {
		return graphContext;
	}

	public void setGraphContext(String graphContext) {
		this.graphContext = graphContext;
	}

	public void setRESTserverAddress(String RESTserverAddress) {
		this.RESTserverAddress = RESTserverAddress;
	}

	@Override
	public String getCommandName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String getTitle() {
		return "Generate R2RML Model";
	}

	@Override
	public String getDescription() {
		return this.worksheetName;
	}

	@Override
	public CommandType getCommandType() {
		return CommandType.notUndoable;
	}


	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {
		UpdateContainer uc = new UpdateContainer();
		//save the preferences 
		savePreferences(workspace);

		Worksheet worksheet = workspace.getWorksheet(worksheetId);
		this.worksheetName = worksheet.getTitle();

		// Prepare the model file path and names
		final String modelFileName = workspace.getCommandPreferencesId() + worksheetId + "-" + 
				this.worksheetName +  "-model.ttl"; 
		final String modelFileLocalPath = ServletContextParameterMap.getParameterValue(
				ContextParameter.R2RML_PUBLISH_DIR) +  modelFileName;

		// Get the alignment for this Worksheet
		Alignment alignment = AlignmentManager.Instance().getAlignment(AlignmentManager.
				Instance().constructAlignmentId(workspace.getId(), worksheetId));

		if (alignment == null) {
			logger.info("Alignment is NULL for " + worksheetId);
			return new UpdateContainer(new ErrorUpdate(
					"Please align the worksheet before generating R2RML Model!"));
		}

		// mohsen: my code to enable Karma to leran semantic models
		// *****************************************************************************************
		// *****************************************************************************************

		SemanticModel semanticModel = new SemanticModel(worksheetName, alignment.getSteinerTree());
		semanticModel.setName(worksheetName);
		try {
			semanticModel.writeJson(ServletContextParameterMap.getParameterValue(ContextParameter.JSON_MODELS_DIR) + 
					semanticModel.getName() + 
					".model.json");
		} catch (Exception e) {
			logger.error("error in exporting the model to JSON!");
			//			e.printStackTrace();
		}
		try {
			semanticModel.writeGraphviz(ServletContextParameterMap.getParameterValue(ContextParameter.GRAPHVIZ_DIRECTORY) + 
					semanticModel.getName() + 
					".model.dot", false, false);
		} catch (Exception e) {
			logger.error("error in exporting the model to GRAPHVIZ!");
			//			e.printStackTrace();
		}

		if (ModelingConfiguration.isLearnerEnabled())
			ModelLearningGraph.getInstance(workspace.getOntologyManager()).addModelAndUpdateGraphJson(semanticModel);

		// *****************************************************************************************
		// *****************************************************************************************

		try {
			R2RMLAlignmentFileSaver fileSaver = new R2RMLAlignmentFileSaver(workspace);
			CommandHistory history = workspace.getCommandHistory();
			List<ICommand> copyCommands = new ArrayList<ICommand>(history._getHistory());
			List<Command> commands = consolidateSubmitEditPyTransform(getCommandsInHistory(copyCommands), workspace, copyCommands);
			JSONArray refinedhistory = new JSONArray();
			for (Command refined : commands)
				refinedhistory.put(history.getCommandJSON(workspace, refined));
			Set<String> inputColumns = generateGraph(commands, workspace);
			JSONArray array = new JSONArray();
			for (String hNodeId : inputColumns) {
				HNode hnode = workspace.getFactory().getHNode(hNodeId);
				JSONArray hNodeRepresentation = hnode.getJSONArrayRepresentation(workspace.getFactory());
				array.put(hNodeRepresentation);
			}
			if (checkDependency(commands, workspace)) {
//			System.out.println(array.toString(4));
//			System.out.println(history);
				fileSaver.saveAlignment(alignment, refinedhistory, modelFileLocalPath);
				history._getHistory().clear();
				history._getHistory().addAll(copyCommands);
				uc.add(new InfoUpdate("Optimize command history successful!"));
			}
			else {
				logger.error("Optimize command history failed!");
				fileSaver.saveAlignment(alignment, modelFileLocalPath);
				uc.add(new InfoUpdate("Optimize command history failed!"));
			}

			// Write the model to the triple store
			//TripleStoreUtil utilObj = new TripleStoreUtil();

			// Get the graph name from properties
			String graphName = worksheet.getMetadataContainer().getWorksheetProperties()
					.getPropertyValue(Property.graphName);
			if (graphName == null || graphName.isEmpty()) {
				// Set to default
				worksheet.getMetadataContainer().getWorksheetProperties().setPropertyValue(
						Property.graphName, WorksheetProperties.createDefaultGraphName(worksheet.getTitle()));
				graphName = WorksheetProperties.createDefaultGraphName(worksheet.getTitle());
			}

			boolean result = true;//utilObj.saveToStore(modelFileLocalPath, tripleStoreUrl, graphName, true, null);
			if (tripleStoreUrl != null && tripleStoreUrl.trim().compareTo("") != 0) {
				UriBuilder builder = UriBuilder.fromPath(modelFileName);
				String url = RESTserverAddress + "/R2RMLMapping/local/" + builder.build().toString();
				SaveR2RMLModelCommandFactory factory = new SaveR2RMLModelCommandFactory();
				SaveR2RMLModelCommand cmd = factory.createCommand(workspace, url, tripleStoreUrl, graphName, "URL");
				cmd.setInputColumns(array);
				cmd.doIt(workspace);
				result &= cmd.getSuccessful();
				workspace.getWorksheet(worksheetId).getMetadataContainer().getWorksheetProperties().setPropertyValue(Property.modelUrl, url);
				workspace.getWorksheet(worksheetId).getMetadataContainer().getWorksheetProperties().setPropertyValue(Property.modelContext, graphName);
				workspace.getWorksheet(worksheetId).getMetadataContainer().getWorksheetProperties().setPropertyValue(Property.modelRepository, tripleStoreUrl);
			}
			if (result) {
				logger.info("Saved model to triple store");
				uc.add(new AbstractUpdate() {
					public void generateJson(String prefix, PrintWriter pw,	
							VWorkspace vWorkspace) {
						JSONObject outputObject = new JSONObject();
						try {
							outputObject.put(JsonKeys.updateType.name(), "PublishR2RMLUpdate");

							outputObject.put(JsonKeys.fileUrl.name(), ServletContextParameterMap.getParameterValue(
									ContextParameter.R2RML_PUBLISH_RELATIVE_DIR) + modelFileName);
							outputObject.put(JsonKeys.worksheetId.name(), worksheetId);
							pw.println(outputObject.toString());
						} catch (JSONException e) {
							logger.error("Error occured while generating JSON!");
						}
					}
				});
				uc.add(new HistoryUpdate(workspace.getCommandHistory()));
				uc.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(worksheetId));
				SemanticTypeUtil.computeSemanticTypesSuggestion(workspace.getWorksheet(worksheetId), workspace
						.getCrfModelHandler(), workspace.getOntologyManager());
				uc.append(WorksheetUpdateFactory.createSemanticTypesAndSVGAlignmentUpdates(worksheetId, workspace, alignment));
				return uc;
			} 

			return new UpdateContainer(new ErrorUpdate("Error occured while generating R2RML model!"));

		} catch (Exception e) {
			logger.error("Error occured while generating R2RML Model!", e);
			return new UpdateContainer(new ErrorUpdate("Error occured while generating R2RML model!"));
		}
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		// Not required
		return null;
	}




	private void savePreferences(Workspace workspace){
		try{
			JSONObject prefObject = new JSONObject();
			prefObject.put(PreferencesKeys.modelSparqlEndPoint.name(), tripleStoreUrl);
			workspace.getCommandPreferences().setCommandPreferences(
					"GenerateR2RMLModelCommandPreferences", prefObject);

		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private Set<String> generateGraph(List<Command> commands, Workspace workspace) {
		
		Map<Command, List<Command> > dag = new HashMap<Command, List<Command>>();
		Map<String, List<Command> > outputMapping = new HashMap<String, List<Command> >();
		for (Command command : commands) {
			Set<String> outputs = command.getOutputColumns();
			for (String output : outputs) {
				List<Command> tmp = outputMapping.get(output);
				if (tmp == null)
					tmp = new ArrayList<Command>();
				tmp.add(command);
				outputMapping.put(output, tmp);
			}
		}
		for (Command command : commands) {
			Set<String> inputs = command.getInputColumns();
			for (String input : inputs) {
				List<Command> outputCommands = outputMapping.get(input);
				if (outputCommands != null) {
					List<Command> edges = dag.get(command);
					if (edges == null)
						edges = new ArrayList<Command>();
					for (Command tmp : outputCommands) {
						if (tmp != command)
							edges.add(tmp);
					}
					dag.put(command, edges);
				}
			}					
		}
		
//		for (Entry<Command, List<Command>> entry : dag.entrySet()) {
//			Command key = entry.getKey();
//			List<Command> value = entry.getValue();
//			System.out.print(key.getCommandName() + "inputs: " + key.getInputColumns() + "outputs: " + key.getOutputColumns());
//			System.out.print("=");
//			for (Command command : value)
//				System.out.print(command.getCommandName() + "inputs: " + command.getInputColumns() + "outputs: " + command.getOutputColumns());
//			System.out.println();
//		}
		Set<String> inputColumns = new HashSet<String>();
		for (Command t : commands) {
			if (t instanceof SetSemanticTypeCommand || t instanceof SetMetaPropertyCommand) {
				inputColumns.addAll(getParents(t, dag, workspace));
			}
		}
//		System.out.println(inputColumns);
//		System.out.println("breakpoint!");
		return inputColumns;
	}
	
	
	private Set<String> getParents(Command c, Map<Command, List<Command> >dag, Workspace workspace) {
		List<Command> parents = dag.get(c);
		Set<String> terminalColumns = new HashSet<String>();
		if (parents == null || parents.size() == 0)
			terminalColumns.addAll(c.getInputColumns());
		else {
			for (Command t : parents) {
				terminalColumns.addAll(getParents(t, dag, workspace));
				for (String hNodeId : c.getInputColumns()) {
					HNode hn = workspace.getFactory().getHNode(hNodeId);
					if (hn.getHNodeType() == HNodeType.Regular)
						terminalColumns.add(hNodeId);
				}
			}
		}
		return terminalColumns;
	}
	private List<Command> getCommandsInHistory(List<ICommand> commands) {
		List<Command> refinedCommands = new ArrayList<Command>();
		for (ICommand c : commands) {
			if (c instanceof Command) {
				Command command = (Command)c;
//				System.out.println(command.getCommandName() + "inputs: " + command.getInputColumns() + "outputs: " + command.getOutputColumns());
				if (command.hasTag(CommandTag.Modeling) || command.hasTag(CommandTag.Transformation)) {
					JSONArray json = new JSONArray(command.getInputParameterJson());
					String worksheetId = HistoryJsonUtil.getStringValue(HistoryArguments.worksheetId.name(), json);
					if (worksheetId.compareTo(this.worksheetId) == 0)  {
						refinedCommands.add(command);
					}
				}
			}
		}
		
		return refinedCommands;
	}

	private List<Command> consolidateSubmitEditPyTransform(List<Command> commands, Workspace workspace, List<ICommand> copyCommands) throws CommandException {
		List<Command> refinedCommands = new ArrayList<Command>();
		//
		for (Command command : commands) {
			if (command instanceof SubmitEditPythonTransformationCommand) {
				Iterator<Command> itr = refinedCommands.iterator();
				boolean flag = true;
				boolean found = false;
				while(itr.hasNext()) {
					Command tmp = itr.next();
					if (tmp.getOutputColumns().equals(command.getOutputColumns()) && tmp instanceof SubmitPythonTransformationCommand && !(tmp instanceof SubmitEditPythonTransformationCommand)) {
//						System.out.println("May Consolidate");
						SubmitPythonTransformationCommand py = (SubmitPythonTransformationCommand)tmp;
						SubmitPythonTransformationCommand edit = (SubmitPythonTransformationCommand)command;
						JSONArray inputJSON = new JSONArray(py.getInputParameterJson());
						HistoryJsonUtil.setArgumentValue("transformationCode", edit.getTransformationCode(), inputJSON);
						py.setInputParameterJson(inputJSON.toString());
						py.setTransformationCode(edit.getTransformationCode());
						flag = false;
//						System.out.println(py.getInputParameterJson());
						py.doIt(workspace);
						copyCommands.remove(command);
						found = true;
						//PlaceHolder
					}
					if (tmp.getOutputColumns().equals(command.getOutputColumns()) && tmp instanceof SubmitEditPythonTransformationCommand && command instanceof SubmitEditPythonTransformationCommand) {
//						System.out.println("Here");
						copyCommands.remove(tmp);
						found = true;
						itr.remove();
					}
				}
				if (flag && found)
					refinedCommands.add(command);
			}
			else
				refinedCommands.add(command);
		}
		return refinedCommands;
	}
	
	private boolean checkDependency(List<Command> commands, Workspace workspace) {
		Set<String> OutputhNodeIds = new HashSet<String>();
		for (HNode hnode : workspace.getFactory().getAllHNodes()) {
			if (hnode.getHNodeType() == HNodeType.Regular)
				OutputhNodeIds.add(hnode.getId());
		}
		boolean dependency = true;
		for (Command command : commands) {
			if (command.getInputColumns().size() > 0) {
				for (String hNodeId : command.getInputColumns()) {
					if (!OutputhNodeIds.contains(hNodeId))
						dependency = false;
				}
			}
			if (command.getOutputColumns().size() > 0) {
				OutputhNodeIds.addAll(command.getOutputColumns());
			}
		}
		return dependency;
	}
}
