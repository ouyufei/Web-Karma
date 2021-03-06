package edu.isi.karma.controller.command.alignment;

import javax.servlet.http.HttpServletRequest;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandFactory;
import edu.isi.karma.rep.Workspace;

public class SearchForDataToAugmentCommandFactory extends CommandFactory{

	private enum Arguments {
		tripleStoreUrl, context, nodeUri, worksheetId, columnUri
	}

	@Override
	public Command createCommand(HttpServletRequest request, Workspace workspace) {
		String url = request.getParameter(Arguments.tripleStoreUrl.name());
		String context = request.getParameter(Arguments.context.name());
		String nodeUri = request.getParameter(Arguments.nodeUri.name());
		String worksheetId = request.getParameter(Arguments.worksheetId.name());
		String columnUri = request.getParameter(Arguments.columnUri.name());
		return new SearchForDataToAugmentCommand(getNewId(workspace), url, context, nodeUri, worksheetId, columnUri);
	}

	@Override
	public Class<? extends Command> getCorrespondingCommand() {
		// TODO Auto-generated method stub
		return SearchForDataToAugmentCommand.class;
	}

}
