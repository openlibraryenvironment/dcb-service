package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.interaction.LocalPatronService;
import org.olf.dcb.core.interaction.PatronStatusUncheckedException;
import org.olf.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;

class ResolvePatronPreflightCheckStatusUncheckedTests {
	private static final String HOST_LMS_CODE = "status-unchecked-host-lms";

	@Test
	void patronWhoseLibraryCannotSayWhetherTheyMayBorrowIsToldWhatToDo() {
		final var localPatronService = mock(LocalPatronService.class);

		when(localPatronService.findLocalPatronAndAgency(any(), any()))
			.thenReturn(Mono.error(new PatronStatusUncheckedException("7362541", HOST_LMS_CODE,
				new IllegalStateException("PAPI -1"))));

		final var check = new ResolvePatronPreflightCheck(localPatronService, new IntMessageService(),
			mock(PatronRequestRepository.class));

		final var results = singleValueFrom(check.check(PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localId("7362541")
				.localSystemCode(HOST_LMS_CODE)
				.build())
			.build()));

		assertThat(results, contains(allOf(
			hasProperty("passed", is(false)),
			hasProperty("failureCode", is("PATRON_STATUS_UNCHECKED")),
			hasProperty("failureDescription",
				is("Could not check whether the patron may borrow: \"%s\" did not answer clearly".formatted(HOST_LMS_CODE))),
			hasProperty("userMessage", is("We couldn't check your library account just now, so your request has not been placed. "
				+ "Please try again in a few minutes. If this keeps happening, contact your library: they can check your account.")))));
	}
}
