package org.olf.dcb.core.interaction;

import lombok.Getter;

@Getter
public class FailedToGetItemsException extends RuntimeException {
	private final String localBibId;
	private final String hostLmsCode;

	public FailedToGetItemsException(String localBibId, String hostLmsCode) {
		super("Failed to get items for ID: \"%s\" from Host LMS: \"%s\""
			.formatted(localBibId, hostLmsCode));

		this.localBibId = localBibId;
		this.hostLmsCode = hostLmsCode;
	}
}
