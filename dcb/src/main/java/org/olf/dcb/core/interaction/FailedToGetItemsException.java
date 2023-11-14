package org.olf.dcb.core.interaction;

import lombok.Getter;

@Getter
public class FailedToGetItemsException extends RuntimeException {
	private final String localBibId;

	public FailedToGetItemsException(String localBibId) {
		this.localBibId = localBibId;
	}
}
