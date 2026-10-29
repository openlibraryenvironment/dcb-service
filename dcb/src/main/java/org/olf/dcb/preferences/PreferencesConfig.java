package org.olf.dcb.preferences;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Staff preferences. On when a secret is set, unless {@code enabled} is false; never a reason to
 * stop startup. docs/operational/admin-preferences.md.
 */
@ConfigurationProperties("dcb.preferences")
public class PreferencesConfig {
	/** Never defaulted: a default would make every deployment's keys guessable. */
	private String secret;

	/** False turns the feature off whatever the secret; anything else leaves it to the secret. */
	private Boolean enabled;

	private int maxSavedViews = 50;

	private int maxWatched = 200;

	/** Per app slice. */
	private int maxAppStateBytes = 16 * 1024;

	private int maxGrids = 50;

	private int maxQueryStateBytes = 8 * 1024;

	/** Measured from last use, not last change. */
	private Duration retention = Duration.ofDays(365);

	/** A watch on a request finished this long ago is swept, so the limit is not filled by history. */
	private Duration finishedWatchRetention = Duration.ofDays(90);

	/** How stale {@code date_last_seen} must be before a read rewrites it: at most one write a day. */
	private Duration touchInterval = Duration.ofDays(1);

	private int sweepBatchSize = 500;

	public boolean isOn() {
		return secret != null && !secret.isBlank() && !Boolean.FALSE.equals(enabled);
	}

	public boolean isExplicitlyDisabled() {
		return Boolean.FALSE.equals(enabled);
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}

	public int getMaxSavedViews() {
		return maxSavedViews;
	}

	public void setMaxSavedViews(int maxSavedViews) {
		this.maxSavedViews = maxSavedViews;
	}

	public int getMaxWatched() {
		return maxWatched;
	}

	public void setMaxWatched(int maxWatched) {
		this.maxWatched = maxWatched;
	}

	public int getMaxAppStateBytes() {
		return maxAppStateBytes;
	}

	public void setMaxAppStateBytes(int maxAppStateBytes) {
		this.maxAppStateBytes = maxAppStateBytes;
	}

	public int getMaxGrids() {
		return maxGrids;
	}

	public void setMaxGrids(int maxGrids) {
		this.maxGrids = maxGrids;
	}

	public int getMaxQueryStateBytes() {
		return maxQueryStateBytes;
	}

	public void setMaxQueryStateBytes(int maxQueryStateBytes) {
		this.maxQueryStateBytes = maxQueryStateBytes;
	}

	public Duration getRetention() {
		return retention;
	}

	public void setRetention(Duration retention) {
		this.retention = retention;
	}

	public Duration getFinishedWatchRetention() {
		return finishedWatchRetention;
	}

	public void setFinishedWatchRetention(Duration finishedWatchRetention) {
		this.finishedWatchRetention = finishedWatchRetention;
	}

	public Duration getTouchInterval() {
		return touchInterval;
	}

	public void setTouchInterval(Duration touchInterval) {
		this.touchInterval = touchInterval;
	}

	public int getSweepBatchSize() {
		return sweepBatchSize;
	}

	public void setSweepBatchSize(int sweepBatchSize) {
		this.sweepBatchSize = sweepBatchSize;
	}
}
