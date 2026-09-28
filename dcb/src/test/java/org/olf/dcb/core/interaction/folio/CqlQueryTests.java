package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class CqlQueryTests {
	@Test
	void shouldLeaveAnOrdinaryValueAsItIs() {
		assertThat(CqlQuery.exactEqualityQuery("barcode", "30642000027790").toString(),
			is("barcode==\"30642000027790\""));
	}

	@Test
	void shouldEscapeWhatCqlReadsInsideQuotes() {
		// An unescaped * matched every barcode starting "39" and the first match was checked out
		assertThat(CqlQuery.exactEqualityQuery("barcode", "39*").toString(), is("barcode==\"39\\*\""));

		assertThat(CqlQuery.exactEqualityQuery("barcode", "a\"b?c^d\\e").toString(),
			is("barcode==\"a\\\"b\\?c\\^d\\\\e\""));
	}
}
