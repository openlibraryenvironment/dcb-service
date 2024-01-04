package org.olf.dcb.core.interaction.folio;

import lombok.Value;

@Value
class CqlQuery {
	String query;

	@Override
	public String toString() {
		return query;
	}
}
