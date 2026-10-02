package org.olf.dcb.core.interaction.folio;

import static lombok.AccessLevel.PRIVATE;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = PRIVATE)
@Serdeable
class CqlQuery {
	String query;

	// Inside quotes CQL still reads * ? ^ as masking and a bare " ends the term, so a barcode
	// carrying one would match other records or break the query
	static CqlQuery exactEqualityQuery(String index, String value) {
		final var escaped = value.replaceAll("([\\\\\"*?^])", "\\\\$1");

		return new CqlQuery(index + "==\"" + escaped + "\"");
	}

	@Override
	public String toString() {
		return query;
	}
}
