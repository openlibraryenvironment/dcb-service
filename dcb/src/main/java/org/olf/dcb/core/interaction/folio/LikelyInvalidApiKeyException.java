package org.olf.dcb.core.interaction.folio;

public class LikelyInvalidApiKeyException extends RuntimeException {
	public LikelyInvalidApiKeyException(String instanceId) {
		super("No outer holdings (instances) returned from RTAC for instance ID: \""
			+ instanceId + "\". Likely caused by invalid API key");
	}
}
