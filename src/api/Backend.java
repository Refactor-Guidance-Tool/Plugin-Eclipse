package api;

import java.awt.Desktop;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.json.JSONObject;

public class Backend {
	private static Backend INSTANCE;

	private int port;
	private String containerId;
	private String currentProjectUuid;
	private String currentWorkspacePath;

	private String lastOutput;

	private Backend() {
		this.port = 1337;
		this.containerId = "";
		this.currentProjectUuid = "";
		this.currentWorkspacePath = "";

		this.lastOutput = "";
	}

	public String getLastOutput() {
		return this.lastOutput;
	}

	public void cleanup() {
		if (this.containerId.isEmpty()) {
			return;
		}

		try {
			Runtime.getRuntime().exec("docker kill " + this.containerId).waitFor();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static boolean pingUrl(String url, int timeout) {
	    try {
	        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
	        connection.setConnectTimeout(timeout);
	        connection.setReadTimeout(timeout);
	        connection.setRequestMethod("GET");
	        int responseCode = connection.getResponseCode();
	        return (responseCode != -1);
	    } catch (IOException exception) {
	        return false;
	    }
	}

	public void startBackend(String workspacePath) {
		try {
			var process = Runtime.getRuntime()
					.exec("docker run -d -p " + Integer.toString(this.port)
							+ ":80 --name refactor-guidance-tool --rm -v " + workspacePath
							+ ":/app/project isa-lab/refactor-guidance-tool");
			process.waitFor();

			this.containerId = IOUtils.toString(process.getInputStream());

			// wait until the backend has fully started (so not just the docker container,
			// but also the swagger instance)
			while (!pingUrl("http://localhost:" + Integer.toString(this.port), 100)) {
				continue;
			}

			this.currentWorkspacePath = workspacePath;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void deleteProject() {
		try {
			URL url = new URL(
					"http://localhost:" + Integer.toString(this.port) + "/Projects/" + this.currentProjectUuid);
			HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
			httpCon.setRequestMethod("DELETE");
			httpCon.connect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void createProject(String workspacePath) {
		if (!this.containerId.isEmpty() && workspacePath != this.currentWorkspacePath) {
			this.cleanup();
			this.containerId = "";
		}

		if (this.containerId.isEmpty()) {
			this.startBackend(workspacePath);
		}

		try {
			URL url = new URL("http://localhost:" + Integer.toString(this.port)
					+ "/Projects/?projectLanguage=1&projectPath=/app/project");
			HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
			httpCon.setRequestMethod("POST");
			httpCon.connect();

			if (httpCon.getResponseCode() != 200) {
				return;
			}

			JSONObject json = new JSONObject(IOUtils.toString(httpCon.getInputStream()));
			this.currentProjectUuid = json.getString("projectUuid");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private IProject getCurrentProject() {
		IProject project = null;
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

		if (window != null) {
			IWorkbenchPage activePage = window.getActivePage();

			IEditorPart activeEditor = activePage.getActiveEditor();

			if (activeEditor != null) {
				IEditorInput input = activeEditor.getEditorInput();

				project = input.getAdapter(IProject.class);
				if (project == null) {
					IResource resource = input.getAdapter(IResource.class);
					if (resource != null) {
						project = resource.getProject();
					}
				}
			}
		}

		return project;
	}

	public void createOrUpdateDatabase() {
		//var workspacePath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toString();
		var project = getCurrentProject();
		var workspacePath = project.getLocation().toOSString();

		// creating a project also creates the database.
		// so if we already have a project, delete it and add it (to simulate an update)
		// this should probably have a specific endpoint on the backend later to update
		// instead of remove + add

		if (!this.currentProjectUuid.isEmpty()) {
			this.deleteProject();
		}

		this.createProject(workspacePath);
		this.lastOutput = "Succesfully created/updated database. You can now open the dashboard and start refactoring!";
	}

	public void openDashboard() {
		try {
			Desktop.getDesktop().browse(new URI("http://localhost:3000/start?projectUuid=" + this.currentProjectUuid));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static Backend getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new Backend();
		}

		return INSTANCE;
	}
}
