package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.PublisherUtils;

import reactor.core.publisher.Mono;

/**
 * Shared-system is opt-in, so every path that cannot prove a system is shared has to say it
 * is not - introducing this test, as a reversion can cause issues with wildcard mappings
 * and we won't necessarily realise until we see memory spikes
 */
@TestInstance(PER_CLASS)
class HostLmsContextServiceTests {
	private static final String CONTEXT = "CALVARY_UNIVERSITY";

	@Test
	void shouldNotAssumeSharedWhenTheHostLmsCannotBeRead() {
		// What an aborted transaction looks like to this service
		final var sut = serviceWhere(Mono.error(
			new RuntimeException("current transaction is aborted, commands ignored")));

		assertThat(PublisherUtils.singleValueFrom(sut.forContext(CONTEXT)).sharedSystem(), is(false));
	}

	@Test
	void shouldNotAssumeSharedWhenThereIsNoSuchHostLms() {
		final var sut = serviceWhere(Mono.empty());

		assertThat(PublisherUtils.singleValueFrom(sut.forContext(CONTEXT)).sharedSystem(), is(false));
	}

	@Test
	void shouldNotReportSharedWhenTheSettingIsAbsent() {
		final var sut = serviceWhere(Mono.just(clientThatIsShared(false)));

		assertThat(PublisherUtils.singleValueFrom(sut.forContext(CONTEXT)).sharedSystem(), is(false));
	}

	@Test
	void shouldReportSharedOnlyWhenItIsExplicitlyConfigured() {
		final var sut = serviceWhere(Mono.just(clientThatIsShared(true)));

		assertThat(PublisherUtils.singleValueFrom(sut.forContext(CONTEXT)).sharedSystem(), is(true));
	}

	@Test
	void shouldSearchTheContextItselfWhenNoHierarchyIsConfigured() {
		final var sut = serviceWhere(Mono.just(clientThatIsShared(false)));

		assertThat(PublisherUtils.singleValueFrom(sut.forContext(CONTEXT)).sourceContexts(),
			is(java.util.List.of(CONTEXT)));
	}

	private static HostLmsContextService serviceWhere(Mono<HostLmsClient> outcome) {
		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(CONTEXT)).thenReturn(outcome);

		return new HostLmsContextService(hostLmsService);
	}

	private static HostLmsClient clientThatIsShared(boolean shared) {
		final var client = mock(HostLmsClient.class);
		when(client.isSharedSystem()).thenReturn(shared);
		when(client.getConfig()).thenReturn(Map.of());

		return client;
	}
}
