package plugineclipse.handlers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import api.Backend;

import org.eclipse.jface.dialogs.MessageDialog;

public class CreateDatabaseHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Backend.getInstance().createOrUpdateDatabase();
		String output = Backend.getInstance().getLastOutput();
		
		if (!output.isEmpty()) {
			IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
			MessageDialog.openInformation(
					window.getShell(),
					"Plugin-Eclipse",
					output.toString());
		}
		
		return null;
	}
}
