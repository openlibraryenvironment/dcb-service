package org.olf.dcb.core;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

@ConfigurationProperties(AppConfig.ROOT)
public class AppConfig {
	
	public static final String ROOT = "dcb";

	@ConfigurationProperties("scheduled-tasks")
	public static class ScheduledTasks implements Toggleable {

		private boolean enabled = true;

		@Override
		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	private ScheduledTasks scheduledTasks = new AppConfig.ScheduledTasks();

	public ScheduledTasks getScheduledTasks() {
		return scheduledTasks;
	}

	public void setScheduledTasks(ScheduledTasks scheduledTasks) {
		this.scheduledTasks = scheduledTasks;
	};
}
