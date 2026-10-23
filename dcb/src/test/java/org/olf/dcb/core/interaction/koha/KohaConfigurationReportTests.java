package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.KohaItemType;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronCategory;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.rules.ObjectRulesService;

import reactor.core.publisher.Mono;

/**
 * What the configuration check tells an implementer about a Koha. A pickup location's
 * localId has to be a branch Koha allows collection from, and the sharing branch is one
 * such branch, so the report says which branches those are and checks the setting
 * against them rather than against every branch.
 */
class KohaConfigurationReportTests {
	private final KohaApiClient apiClient = mock(KohaApiClient.class);

	@Test
	void shouldListTheBranchesKohaAllowsCollectionFrom() {
		givenKohaLists();

		final var report = client("DCB-SHARING").checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(vocabulary(report, "Libraries").entries(), contains(
			entry("BRANCH-N", "North Branch"), entry("BRANCH-S", "South Branch"),
			entry("DCB-SHARING", "Consortium")));
		assertThat(vocabulary(report, "Pickup libraries").entries(), contains(
			entry("BRANCH-N", "North Branch"), entry("DCB-SHARING", "Consortium")));
	}

	@Test
	void shouldAcceptASharingBranchKohaAllowsCollectionFrom() {
		givenKohaLists();

		final var report = client("DCB-SHARING").checkConfiguration().block();

		assertThat(check(report, "sharing-library-code").result(),
			is(ConfigurationReport.CheckResult.PRESENT));
	}

	@Test
	void shouldReportASharingBranchKohaDoesNotAllowCollectionFrom() {
		// Every supplier hold names the sharing branch as its pickup branch; Koha refuses
		// a hold at a branch that is not a pickup location, so the setting is wrong
		givenKohaLists();

		final var report = client("BRANCH-S").checkConfiguration().block();

		assertThat(check(report, "sharing-library-code").result(),
			is(ConfigurationReport.CheckResult.MISSING));
	}

	private void givenKohaLists() {
		when(apiClient.getItemTypes()).thenReturn(Mono.just(new KohaItemType[] {
			KohaItemType.builder().itemTypeId("BK").description("Book").build()}));
		when(apiClient.getPatronCategories()).thenReturn(Mono.just(new KohaPatronCategory[] {
			KohaPatronCategory.builder().patronCategoryId("DCB").name("Consortium patron").build()}));
		when(apiClient.getLibraries()).thenReturn(Mono.just(new KohaLibrary[] {
			KohaLibrary.builder().libraryId("BRANCH-N").name("North Branch").pickupLocation(true).build(),
			KohaLibrary.builder().libraryId("BRANCH-S").name("South Branch").pickupLocation(false).build(),
			KohaLibrary.builder().libraryId("DCB-SHARING").name("Consortium").pickupLocation(true).build()}));
	}

	private static ConfigurationReport.Vocabulary vocabulary(ConfigurationReport report, String name) {
		return report.vocabularies().stream()
			.filter(vocabulary -> vocabulary.name().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private static ConfigurationReport.Check check(ConfigurationReport report, String setting) {
		return report.checks().stream()
			.filter(check -> check.setting().equals(setting))
			.findFirst()
			.orElseThrow();
	}

	private static ConfigurationReport.Entry entry(String code, String description) {
		return new ConfigurationReport.Entry(code, description);
	}

	private KohaHostLmsClient client(String sharingLibraryCode) {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("KOHA");
		when(hostLms.getClientConfig()).thenReturn(Map.<String, Object>of(
			"api-url", "https://koha.example.com",
			"client_id", "any-id",
			"client_secret", "any-secret",
			"sharing-library-code", sharingLibraryCode,
			"virtual-item-library-code", "BRANCH-N"));

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
