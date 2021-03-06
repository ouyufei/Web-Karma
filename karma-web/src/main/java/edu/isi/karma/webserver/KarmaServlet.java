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
package edu.isi.karma.webserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.config.UIConfiguration;
import edu.isi.karma.controller.command.alignment.R2RMLAlignmentFileSaver;
import edu.isi.karma.controller.history.CommandHistory;
import edu.isi.karma.controller.update.AbstractUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.controller.update.WorksheetListUpdate;
import edu.isi.karma.controller.update.WorksheetUpdateFactory;
import edu.isi.karma.metadata.CRFModelMetadata;
import edu.isi.karma.metadata.CSVMetadata;
import edu.isi.karma.metadata.GraphVizMetadata;
import edu.isi.karma.metadata.JSONMetadata;
import edu.isi.karma.metadata.JSONModelsMetadata;
import edu.isi.karma.metadata.KarmaMetadataManager;
import edu.isi.karma.metadata.ModelLearnerMetadata;
import edu.isi.karma.metadata.OntologyMetadata;
import edu.isi.karma.metadata.PythonTransformationMetadata;
import edu.isi.karma.metadata.R2RMLMetadata;
import edu.isi.karma.metadata.R2RMLPublishedMetadata;
import edu.isi.karma.metadata.RDFMetadata;
import edu.isi.karma.metadata.ReportMetadata;
import edu.isi.karma.metadata.UserConfigMetadata;
import edu.isi.karma.metadata.UserPreferencesMetadata;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.rep.WorkspaceManager;
import edu.isi.karma.rep.metadata.Tag;
import edu.isi.karma.rep.metadata.TagsContainer.Color;
import edu.isi.karma.rep.metadata.TagsContainer.TagName;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.view.VWorkspaceRegistry;

public class KarmaServlet extends HttpServlet {
	private enum Arguments {
		hasPreferenceId, workspacePreferencesId
	}

	private static final long serialVersionUID = 1L;
	
	private static Logger logger = LoggerFactory.getLogger(KarmaServlet.class);

	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		KarmaMetadataManager metadataManager = null;
		UpdateContainer updateContainer = new  UpdateContainer();
		try {
			metadataManager = new KarmaMetadataManager();
			metadataManager.register(new UserPreferencesMetadata(), updateContainer);
			metadataManager.register(new UserConfigMetadata(), updateContainer);
		} catch (KarmaException e) {
			logger.error("Unable to complete Karma set up: ", e);
		}
		
		/* Check if any workspace id is set in cookies. */
		boolean hasWorkspaceCookieId = false;
		String hasPrefId = request.getParameter(Arguments.hasPreferenceId.name());
		if(hasPrefId != null && hasPrefId.equals("true"))
			hasWorkspaceCookieId = true;
		Workspace workspace = null;
		VWorkspace vwsp = null;
		
		/* If set, pick the right preferences and CRF Model file */
		if(hasWorkspaceCookieId) {
			String cachedWorkspaceId = request.getParameter(Arguments.workspacePreferencesId.name());
			workspace = WorkspaceManager.getInstance().createWorkspaceWithPreferencesId(cachedWorkspaceId);
			vwsp = new VWorkspace(workspace, cachedWorkspaceId);
		} else {
			workspace = WorkspaceManager.getInstance().createWorkspace();
			vwsp = new VWorkspace(workspace);
		}

		workspace.setMetadataManager(metadataManager);
		WorkspaceRegistry.getInstance().register(new ExecutionController(workspace));
		VWorkspaceRegistry.getInstance().registerVWorkspace(workspace.getId(), vwsp);
		
		logger.info("Start Metadata Setup");
		try {
			metadataManager.register(new CRFModelMetadata(workspace), updateContainer);
			metadataManager.register(new OntologyMetadata(workspace), updateContainer);
			metadataManager.register(new JSONModelsMetadata(workspace), updateContainer);
			metadataManager.register(new PythonTransformationMetadata(workspace), updateContainer);
			metadataManager.register(new GraphVizMetadata(workspace), updateContainer);
			metadataManager.register(new ModelLearnerMetadata(workspace), updateContainer);
			metadataManager.register(new R2RMLMetadata(workspace), updateContainer);
			metadataManager.register(new R2RMLPublishedMetadata(workspace), updateContainer);
			metadataManager.register(new RDFMetadata(workspace), updateContainer);
			metadataManager.register(new CSVMetadata(workspace), updateContainer);
			metadataManager.register(new JSONMetadata(workspace), updateContainer);
			metadataManager.register(new ReportMetadata(workspace), updateContainer);
		} catch (KarmaException e) {
			logger.error("Unable to complete Karma set up: ", e);
		}

		CommandHistory.setIsHistoryEnabled(true);
		CommandHistory.setHistorySaver(workspace.getId(), new R2RMLAlignmentFileSaver(workspace));
						
		// Initialize the Outlier tag
		Tag outlierTag = new Tag(TagName.Outlier, Color.Red);
		workspace.getTagsContainer().addTag(outlierTag);

		// Put all created worksheet models in the view.
		updateContainer.add(new WorksheetListUpdate());
		
		for (Worksheet w : vwsp.getWorkspace().getWorksheets()) {
			updateContainer.append(WorksheetUpdateFactory.createWorksheetHierarchicalUpdates(w.getId())); 
		}

		updateContainer.add(new AbstractUpdate() {

			@Override
			public void generateJson(String prefix, PrintWriter pw,
					VWorkspace vWorkspace) {
				UIConfiguration.Instance().loadConfig();
				pw.println("{");
				pw.println("\"updateType\": \"UISettings\", ");
				pw.println("\"settings\": {");
				pw.println("  \"googleEarthEnabled\" : " + UIConfiguration.Instance().isGoogleEarthEnabled() + ",");
				pw.println("  \"maxLoadedClasses\" : " + UIConfiguration.Instance().getMaxClassesToLoad() + ",");
				pw.println("  \"maxLoadedProperties\" : " + UIConfiguration.Instance().getMaxPropertiesToLoad());
				pw.println("  }");
				pw.println("}");
			}
			
		});
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		updateContainer.applyUpdates(vwsp);
		updateContainer.generateJson("", pw, vwsp);
		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		response.getWriter().println(sw.toString());
	}
}
