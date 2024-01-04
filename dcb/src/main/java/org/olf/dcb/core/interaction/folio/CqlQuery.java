package org.olf.dcb.core.interaction.folio;

import static lombok.AccessLevel.PRIVATE;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = PRIVATE)
class CqlQuery {
	String query;

	static CqlQuery exactEqualityQuery(String index, String value) {
		return new CqlQuery(index + "==\"" + value + "\"");
	}

	@Override
	public String toString() {
		return query;
	}
}
