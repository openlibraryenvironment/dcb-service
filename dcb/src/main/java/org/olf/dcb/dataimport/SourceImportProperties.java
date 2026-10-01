package org.olf.dcb.dataimport;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

/**
 * Limits on the manual source import recovery sweeps. What each one guards: docs/polaris_notes.md
 */
@ConfigurationProperties("dcb.source-import")
@Getter
public class SourceImportProperties {

	// Losing more than this share of a host's bibs at once is likelier to be a wrong set or a broken
	// provider than real withdrawals, so a vanished sweep reports it and deletes nothing.
	private double vanishedMaxShare = 0.10;

	public void setVanishedMaxShare(double vanishedMaxShare) {
		if (vanishedMaxShare < 0 || vanishedMaxShare > 1) {
			throw new IllegalArgumentException(
				"dcb.source-import.vanished-max-share must be between 0 and 1, not " + vanishedMaxShare);
		}

		this.vanishedMaxShare = vanishedMaxShare;
	}
}
