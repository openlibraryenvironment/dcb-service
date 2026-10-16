package org.olf.dcb.core.api.discovery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.api.serde.PatronLoan;
import org.olf.dcb.core.api.serde.PatronLoanProjection;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.security.discovery.DiscoveryServiceProperties;
import org.olf.dcb.security.discovery.PatronAssertion;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.http.HttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DiscoveryPatronLoansControllerTests {

	private PatronRequestRepository repository;
	private HostLmsService hostLmsService;
	private HostLmsClient client;
	private DiscoveryPatronLoansController controller;

	@BeforeEach
	void beforeEach() {
		repository = mock(PatronRequestRepository.class);
		hostLmsService = mock(HostLmsService.class);
		client = mock(HostLmsClient.class);

		final var verifier = mock(PatronAssertionVerifier.class);
		when(verifier.verify(any())).thenReturn(new PatronAssertion("home-lms", "patron-1", "service"));

		final var properties = new DiscoveryServiceProperties();
		properties.setLoanLookupTimeout(Duration.ofMillis(200));
		properties.setLoanLookupConcurrency(1);

		controller = new DiscoveryPatronLoansController(repository, verifier, hostLmsService, properties);
	}

	@Test
	void theLoanRowsAreAllReadBeforeAnyLibraryIsAsked() {
		final var rowsDrained = new AtomicBoolean(false);
		final var drainedWhenFirstAsked = new AtomicBoolean(false);

		when(repository.findLoansForPatron(any(), any(), anyInt()))
			.thenReturn(Flux.just(loan("lms-a"), loan("lms-a"), loan("lms-b"))
				.doOnComplete(() -> rowsDrained.set(true)));
		when(hostLmsService.getClientFor(any(String.class))).thenAnswer(invocation -> {
			drainedWhenFirstAsked.compareAndSet(false, rowsDrained.get());
			return Mono.just(client);
		});
		when(client.getItem(any())).thenReturn(Mono.empty());

		final var loans = controller.listOwnLoans(HttpRequest.GET("/discovery/loans")).block();

		assertThat(loans, hasSize(3));
		assertThat("the repository's transaction must end before a library is called",
			drainedWhenFirstAsked.get(), is(true));
		// One client per library system on the page, not one per loan
		verify(hostLmsService, times(2)).getClientFor(any(String.class));
	}

	@Test
	void aHungLibraryCostsThePageAtMostItsOverallDeadline() {
		when(repository.findLoansForPatron(any(), any(), anyInt()))
			.thenReturn(Flux.just(loan("lms-a"), loan("lms-a"), loan("lms-a"), loan("lms-a"), loan("lms-a")));
		when(hostLmsService.getClientFor(any(String.class))).thenReturn(Mono.just(client));
		when(client.getItem(any())).thenReturn(Mono.never());

		final var started = System.nanoTime();
		final var loans = controller.listOwnLoans(HttpRequest.GET("/discovery/loans")).block();
		final var elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

		// Five sequential 200ms timeouts would be a second; the 400ms budget stops it short
		assertThat(elapsedMillis, lessThan(800L));
		assertThat(loans, hasSize(5));
		assertThat(loans.stream().map(PatronLoan::dueDateKnown).toList(), everyItem(is(false)));
	}

	private static PatronLoanProjection loan(String hostLmsCode) {
		return new PatronLoanProjection(UUID.randomUUID(), "Title", UUID.randomUUID(), "pickup",
			"Pickup", "item-" + UUID.randomUUID(), null, null, null, hostLmsCode, 0, null, null);
	}
}
