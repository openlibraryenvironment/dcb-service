package org.olf.dcb.core.interaction.folio;

public class LikelyInvalidApiKeyException extends RuntimeException {
	public LikelyInvalidApiKeyException(String instanceId, String hostLmsCode) {
		super("No errors or outer holdings (instances) returned from RTAC for instance ID: \""
			+ instanceId + "\". Likely caused by invalid API key for Host LMS: \"" + hostLmsCode + "\"");
	}
}
