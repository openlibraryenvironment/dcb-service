package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.MappingVocabulary;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.KohaItemType;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronCategory;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.rules.ObjectRulesService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class KohaVocabularyTests {
	private final KohaApiClient apiClient = mock(KohaApiClient.class);
	private final KohaHostLmsClient client = client();

	@Test
	void shouldAnswerEmptyWhenThisKohaHasNoSuchList() {
		when(apiClient.getPatronCategories()).thenReturn(Mono.error(
			new HttpClientResponseException("HTTP 404", HttpResponse.notFound())));

		assertThat(client.fetchVocabulary(MappingVocabulary.PATRON_TYPE).block(), is(empty()));
	}

	@Test
	void shouldStillFailWhenKohaFails() {
		when(apiClient.getPatronCategories()).thenReturn(Mono.error(
			new HttpClientResponseException("HTTP 500", HttpResponse.serverError())));

		StepVerifier.create(client.fetchVocabulary(MappingVocabulary.PATRON_TYPE))
			.expectError(HttpClientResponseException.class)
			.verify();
	}

	@Test
	void shouldReportTheOtherListsWhenOneAnswersWithNoBody() {
		when(apiClient.getItemTypes()).thenReturn(Mono.empty());
		when(apiClient.getPatronCategories()).thenReturn(Mono.just(new KohaPatronCategory[] {
			new KohaPatronCategory("PT", "Patron")}));
		when(apiClient.getLibraries()).thenReturn(Mono.just(new KohaLibrary[0]));

		final var report = client.checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(report.vocabularies().get(1).entries(),
			is(List.of(new ConfigurationReport.Entry("PT", "Patron"))));
	}

	private KohaHostLmsClient client() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("KOHA");
		when(hostLms.getClientConfig()).thenReturn(Map.<String, Object>of(
			"api-url", "https://koha.example.com",
			"client_id", "any-id",
			"client_secret", "any-secret",
			"sharing-library-code", "DCB-SHARING"));

		final var clientFactory = mock(KohaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(apiClient);

		final var objectRulesService = mock(ObjectRulesService.class);
		when(objectRulesService.findByName(any())).thenReturn(Mono.empty());

		return new KohaHostLmsClient(hostLms, mock(ReferenceValueMappingService.class),
			clientFactory, mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), objectRulesService,
			new RulesetCacheInvalidator(), mock(HostLmsService.class));
	}
}
