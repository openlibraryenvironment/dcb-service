package org.olf.dcb.processing.matching;

import java.util.UUID;

public interface MatchKey {
	public UUID getRepeatableUUID();
	public String getText();
}
