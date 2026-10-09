package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;

@TestInstance(PER_CLASS)
class KohaPingTests {
	private KohaApiClient apiClient;

	@BeforeEach
	void beforeEach() {
		// Real default methods, so the request the ping sends is what is under test
		apiClient = mock(KohaApiClient.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
	}

	@Test
	void shouldBeOkWhenOneLibraryCanBeRead() {
		doReturn(Mono.just(new KohaLibrary[] { KohaLibrary.builder().libraryId("CPL").build() }))
			.when(apiClient).get(eq("/api/v1/libraries"), eq(KohaLibrary[].class), eq(Map.of("_per_page", 1)));

		final var response = client().ping().block();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("KOHA REST API v1"));
	}

	@Test
	void shouldFailWhenKohaRefusesTheClientCredentials() {
		doReturn(Mono.error(new IllegalStateException("Forbidden")))
			.when(apiClient).get(eq("/api/v1/libraries"), eq(KohaLibrary[].class), eq(Map.of("_per_page", 1)));

		final var response = client().ping().block();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), is("Forbidden"));
	}

	private KohaHostLmsClient client() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("KOHA");
		when(hostLms.getClientConfig()).thenReturn(Map.of());

		final var clientFactory = mock(KohaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(apiClient);

		return new KohaHostLmsClient(hostLms, mock(ReferenceValueMappingService.class),
			clientFactory, mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ObjectRulesService.class), new RulesetCacheInvalidator(), mock(HostLmsService.class));
	}
}
