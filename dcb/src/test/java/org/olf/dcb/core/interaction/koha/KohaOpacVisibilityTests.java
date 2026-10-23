package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A copy the library hides from its own OPAC with OpacHiddenItems is not offered to the
 * consortium. Koha applies the rules itself on its anonymous public item list.
 */
@MockServerMicronautTest
class KohaOpacVisibilityTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-opac-visibility";
	private static final String KOHA_HOST = "fake-koha-opac-visibility";
	private static final String BIBLIO_ID = "77";
	private static final KohaItem SHELVED = item(701L);
	private static final KohaItem HIDDEN = item(702L);

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		koha.mockItemsForBiblio(BIBLIO_ID, SHELVED, HIDDEN);
		koha.mockNoActiveHolds();
	}

	@Test
	void shouldNotOfferACopyTheOpacHides() {
		koha.replaceOpacVisibleItems(BIBLIO_ID, SHELVED);

		final var items = itemsById(client(Map.of()));

		assertThat(items.get("701").getSuppressed(), is(false));
		assertThat(items.get("702").getSuppressed(), is(true));
	}

	@Test
	void shouldAskAsAnAnonymousOpacUser() {
		// A token would be the API patron, whose category may be exempt from the rules
		koha.replaceOpacVisibleItems(BIBLIO_ID, SHELVED);

		itemsById(client(Map.of()));

		koha.verifyOpacVisibleItemsReadWithoutAToken(BIBLIO_ID);
	}

	@Test
	void shouldFailTheCheckRatherThanOfferCopiesItCannotSeeAreHidden() {
		koha.replaceOpacVisibleItemsRefused(BIBLIO_ID);

		final var client = client(Map.of());
		final var problem = assertThrows(ThrowableProblem.class, () -> itemsById(client));

		assertThat(problem.getTitle(), is("Koha would not say which copies its OPAC hides"));
	}

	@Test
	void shouldOfferEveryCopyWhenTheHostLmsDoesNotHonourOpacHiddenItems() {
		koha.replaceOpacVisibleItems(BIBLIO_ID, SHELVED);

		final var items = itemsById(client(Map.of("honour-opac-hidden-items", "false")));

		assertThat(items.get("702").getSuppressed(), is(false));
		koha.verifyOpacVisibleItemsNeverRead(BIBLIO_ID);
	}

	@Test
	void shouldReportARefusedPublicApiAtSetup() {
		koha.mockItemTypes();
		koha.mockPatronCategories();
		koha.mockLibraries(KohaLibrary.builder().libraryId("DCB-SHARING").pickupLocation(true).build());
		koha.mockNoSystemPreferencesRoute();
		koha.mockPublicLibrariesRefused();

		final var check = check(client(Map.of()).checkConfiguration().block());

		assertThat(check.result(), is(ConfigurationReport.CheckResult.MISSING));
	}

	@Test
	void shouldReportAnOpenPublicApiAtSetup() {
		koha.mockConfigurationLists(KohaLibrary.builder().libraryId("DCB-SHARING").pickupLocation(true).build());

		final var check = check(client(Map.of()).checkConfiguration().block());

		assertThat(check.result(), is(ConfigurationReport.CheckResult.PRESENT));
	}

	private HostLmsClient client(Map<String, Object> extraConfig) {
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST, extraConfig);

		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	private static Map<String, Item> itemsById(HostLmsClient client) {
		final List<Item> items = client.getItems(BibRecord.builder().sourceRecordId(BIBLIO_ID).build()).block();

		return items.stream().collect(Collectors.toMap(Item::getLocalId, Function.identity()));
	}

	private static ConfigurationReport.Check check(ConfigurationReport report) {
		return report.checks().stream()
			.filter(check -> check.setting().equals("honour-opac-hidden-items"))
			.findFirst()
			.orElseThrow();
	}

	private static KohaItem item(long itemId) {
		return KohaItem.builder()
			.itemId(itemId)
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("barcode-" + itemId)
			.homeLibraryId("BRANCH-N")
			.build();
	}
}
