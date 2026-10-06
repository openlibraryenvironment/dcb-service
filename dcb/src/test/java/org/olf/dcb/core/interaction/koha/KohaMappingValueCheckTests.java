package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.olf.dcb.core.interaction.MappingVocabulary.LOCATION;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.interaction.MappingValueCheck.Result;
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
class KohaMappingValueCheckTests {
	private KohaApiClient apiClient;

	@BeforeEach
	void beforeEach() {
		// Real default methods, so the query each vocabulary call sends is what is under test
		apiClient = mock(KohaApiClient.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
	}

	@Test
	void shouldAskKohaForEveryLibraryRatherThanItsDefaultPageOfTwenty() {
		doReturn(Mono.just(new KohaLibrary[] {
			KohaLibrary.builder().libraryId("CPL").name("Centerville").build() }))
			.when(apiClient).get(eq("/api/v1/libraries"), eq(KohaLibrary[].class), eq(Map.of("_per_page", -1)));

		final var check = client().checkMappingValue(LOCATION, "CPL").block();

		assertThat(check.result(), is(Result.PRESENT));
	}

	@Test
	void shouldReportWhyAVocabularyCouldNotBeRead() {
		doReturn(Mono.error(new RuntimeException("Koha is unwell")))
			.when(apiClient).get(eq("/api/v1/libraries"), eq(KohaLibrary[].class), any());

		final var check = client().checkMappingValue(LOCATION, "CPL").block();

		assertThat(check.result(), is(Result.UNKNOWN));
		assertThat(check.detail(), containsString("Koha is unwell"));
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
