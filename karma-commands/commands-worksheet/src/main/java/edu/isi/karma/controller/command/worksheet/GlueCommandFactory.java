package edu.isi.karma.controller.command.worksheet;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.JSONInputCommandFactory;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.util.CommandInputJSONUtil;
import edu.isi.karma.webserver.KarmaException;

public class GlueCommandFactory extends JSONInputCommandFactory{

	public enum Arguments {
		worksheetId, hTableId, hNodeId, newColumnName, defaultValue
	}
	
	@Override
	public Command createCommand(JSONArray inputJson, Workspace workspace)
			throws JSONException, KarmaException {
		// TODO Auto-generated method stub
		String hNodeID = CommandInputJSONUtil.getStringValue(Arguments.hNodeId.name(), inputJson);
		String worksheetId = CommandInputJSONUtil.getStringValue(Arguments.worksheetId.name(), inputJson);
		String hTableId = "";
		//System.out.println(worksheetId);
		GlueCommand glueCmd = new GlueCommand(getNewId(workspace), worksheetId,
				hTableId, hNodeID);
		glueCmd.setInputParameterJson(inputJson.toString());
		return glueCmd;
	}

	@Override
	public Command createCommand(HttpServletRequest request, Workspace workspace) {
		// TODO Auto-generated method stub
		String hNodeId = request.getParameter(Arguments.hNodeId.name());
		String hTableId = request.getParameter(Arguments.hTableId.name());
		String worksheetId = request.getParameter(Arguments.worksheetId.name());
		return new GlueCommand(getNewId(workspace), worksheetId, 
				hTableId, hNodeId);
	}

	@Override
	public Class<? extends Command> getCorrespondingCommand() {
		// TODO Auto-generated method stub
		return GlueCommand.class;
	}

}
