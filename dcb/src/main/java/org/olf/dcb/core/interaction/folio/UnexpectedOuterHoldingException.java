package org.olf.dcb.core.interaction.folio;

import org.olf.dcb.core.interaction.FailedToGetItemsException;

public class UnexpectedOuterHoldingException extends FailedToGetItemsException {
	public UnexpectedOuterHoldingException(String localBibId) {
		super(localBibId);
	}
}
