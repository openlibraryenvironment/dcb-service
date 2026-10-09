package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Mono;

/** A request limit that could not be checked is not a limit that passed. */
class GlobalLimitsPreflightCheckTests {
	private PatronRequestRepository patronRequestRepository;
	private GlobalLimitsPreflightCheck check;

	@BeforeEach
	void beforeEach() {
		patronRequestRepository = mock(PatronRequestRepository.class);

		check = new GlobalLimitsPreflightCheck(25L, patronRequestRepository,
			new IntMessageService());
	}

	@Test
	void shouldPassAPatronUnderTheGlobalLimit() {
		when(patronRequestRepository.getActiveRequestCountForPatron(any(), any())).thenReturn(Mono.just(24L));

		assertThat(onlyResult().getPassed(), is(true));
	}

	@Test
	void shouldFailAPatronAtTheGlobalLimit() {
		when(patronRequestRepository.getActiveRequestCountForPatron(any(), any())).thenReturn(Mono.just(25L));

		final var result = onlyResult();

		assertThat(result.getPassed(), is(false));
		assertThat(result.getFailureCode(), is("EXCEEDS_GLOBAL_LIMIT"));
	}

	@Test
	void shouldFailWhenTheCountCannotBeRead() {
		when(patronRequestRepository.getActiveRequestCountForPatron(any(), any()))
			.thenReturn(Mono.error(new IllegalStateException("database unavailable")));

		final var result = onlyResult();

		assertThat(result.getPassed(), is(false));
		assertThat(result.getFailureCode(), is("REQUEST_LIMITS_UNCHECKED"));
		assertThat(result.getUserMessage() != null, is(true));
	}

	private CheckResult onlyResult() {
		final var command = PlacePatronRequestCommand.builder()
			.citation(PlacePatronRequestCommand.Citation.builder().bibClusterId(UUID.randomUUID()).build())
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder().code("pickup").build())
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localId("patron-1").localSystemCode("SYSTEM").build())
			.build();

		return check.check(command).block().get(0);
	}
}
