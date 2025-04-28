package org.olf.dcb.core;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import java.util.List;
import java.util.ArrayList;

@ConfigurationProperties(AppConfig.ROOT)
public class AppConfig {
	
	public static final String ROOT = "dcb";

	@ConfigurationProperties("scheduled-tasks")
	public static class ScheduledTasks implements Toggleable {

		private boolean enabled = true;

		/** A list of specific services that can be skipped for a given instance */
		private List<String> skipped = new ArrayList<String>();

		@Override
		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public List<String> getSkipped() {
			return skipped;
		}

		public void setSkipped(List<String> skipped) {
			this.skipped = skipped;
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
