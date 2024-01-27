package org.olf.dcb.core;

import jakarta.inject.Singleton;


/**
 * Some services in DCB need to know when the service has received a kill or shutdown signal.
 * This class exists to be an injectable bean which services can watch to know if the app is
 * shutting down or not. For example, streams may incorporate && appState.getRunState() == AppState.RUNNING
 */
@Singleton
public class AppState {

	public enum AppStatus {
		RUNNING,
		SHUTTING_DOWN,
		SHUTDOWN
	}

	private AppStatus runState = null;

	public AppState() {
		this.runState=AppStatus.RUNNING;
	}

	public AppStatus getRunStatus() {
		return runState;
	}

	public void setRunStatus(AppStatus runState) {
		this.runState = runState;
	}
	
}
