package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/** The consortial loan limit fails closed: a count that could not be read is not a count under the limit. */
class ResolvePatronPreflightCheckLoanLimitTests {
	@Test
	void shouldFailWhenTheActiveRequestCountCannotBeRead() {
		final var localPatronService = mock(LocalPatronService.class);
		final var patronRequestRepository = mock(PatronRequestRepository.class);

		final var patron = Patron.builder()
			.localId(List.of("patron-1"))
			.localBarcodes(List.of("barcode-1"))
			.canonicalPatronType("UNDERGRAD")
			.build();

		final var agency = DataAgency.builder()
			.id(UUID.randomUUID())
			.code("example-agency")
			.isBorrowingAgency(true)
			.maxConsortialLoans(2)
			.build();

		when(localPatronService.findLocalPatronAndAgency(any(), any()))
			.thenReturn(Mono.just(Tuples.of(patron, agency)));
		when(patronRequestRepository.getActiveRequestCountForPatron(any(), any()))
			.thenReturn(Mono.error(new IllegalStateException("database unavailable")));

		final var check = new ResolvePatronPreflightCheck(localPatronService,
			new IntMessageService(), patronRequestRepository);

		final var results = check.check(PlacePatronRequestCommand.builder()
				.requestor(PlacePatronRequestCommand.Requestor.builder()
					.localSystemCode("SYSTEM")
					.localId("patron-1")
					.build())
				.build())
			.block();

		assertThat(results.size(), is(1));
		assertThat(results.get(0).getPassed(), is(false));
		assertThat(results.get(0).getFailureCode(), is("REQUEST_LIMITS_UNCHECKED"));
	}
}
