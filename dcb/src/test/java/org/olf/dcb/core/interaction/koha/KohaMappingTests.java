package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronsList;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;

/**
 * How Koha records become DCB records.
 * <p>
 * On a shared Koha the branch is the only thing that tells one participating
 * library from another, so anything that loses it collapses every co-tenant onto
 * one agency.
 */
@TestInstance(PER_CLASS)
class KohaMappingTests {
	private KohaApiClient apiClient;
	private KohaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("KOHA");
		when(hostLms.getClientConfig()).thenReturn(Map.<String, Object>of(
			"api-url", "https://koha.example.com",
			"client_id", "any-id",
			"client_secret", "any-secret"));

		apiClient = mock(KohaApiClient.class);

		final var clientFactory = mock(KohaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(apiClient);

		// Enrichment is covered by its own tests; here it passes items through so the
		// assertions are about what the Koha mapper produced and nothing else
		final var locationToAgency = mock(LocationToAgencyMappingService.class);
		when(locationToAgency.enrichItemAgencyFromLocation(any(), any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		final var materialTypeToItemType = mock(MaterialTypeToItemTypeMappingService.class);
		when(materialTypeToItemType.enrichItemWithMappedItemType(any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		client = new KohaHostLmsClient(hostLms,
			mock(ReferenceValueMappingService.class), clientFactory,
			materialTypeToItemType, locationToAgency);
	}

	@Test
	void shouldMapThePatronsHomeLibrary() {
		// library_id was modelled and used when creating a patron, but never read back.
		// Every Koha patron therefore arrived with no home library code, fell through to
		// the default agency, and on a 60-library Koha every one of them belonged to
		// whichever library happened to be configured there.
		givenPatron(KohaPatron.builder()
			.patronId(1234L)
			.cardnumber("6747664")
			.firstname("Ada")
			.surname("Lovelace")
			.categoryId("ADULT")
			.libraryId("BRANCH-NORTH")
			.build());

		final var patron = client.getPatronByIdentifier("6747664").block();

		assertThat(patron.getLocalHomeLibraryCode(), is("BRANCH-NORTH"));
	}

	@Test
	void shouldTolerateAPatronWithNoHomeLibrary() {
		givenPatron(KohaPatron.builder()
			.patronId(1234L)
			.cardnumber("6747664")
			.firstname("Ada")
			.surname("Lovelace")
			.categoryId("ADULT")
			.build());

		final var patron = client.getPatronByIdentifier("6747664").block();

		assertThat(patron.getLocalHomeLibraryCode(), is((String) null));
		assertThat(patron.getLocalPatronType(), is("ADULT"));
	}

	@Test
	void shouldMapTheOwningBranchAsTheItemLocation() {
		// Koha's "location" is a shelving classifier that every branch shares, so using
		// it as the location made location-to-agency mapping impossible on a shared
		// server: either no branch could be identified, or all of them mapped to one.
		givenItem(KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.externalId("6565750674")
			.homeLibraryId("BRANCH-NORTH")
			.holdingLibraryId("BRANCH-SOUTH")
			.location("STACKS")
			.build());

		final var item = firstItem();

		assertThat("Location is the owning branch", item.getLocationCode(), is("BRANCH-NORTH"));
		assertThat("Koha's location is the shelving location",
			item.getShelvingLocation(), is("STACKS"));
	}

	@Test
	void shouldFallBackToTheHoldingBranchWhenThereIsNoHomeBranch() {
		givenItem(KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.externalId("6565750674")
			.holdingLibraryId("BRANCH-SOUTH")
			.location("STACKS")
			.build());

		assertThat(firstItem().getLocationCode(), is("BRANCH-SOUTH"));
	}

	@Test
	void shouldTolerateAnItemWithNoBranchAtAll() {
		// No branch means no agency, and live availability drops agency-less items. It
		// must not take the rest of the bib's items down with it.
		givenItem(KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.externalId("6565750674")
			.location("STACKS")
			.build());

		final var item = firstItem();

		assertThat(item.getLocation(), is(nullValue()));
		assertThat(item.getShelvingLocation(), is("STACKS"));
	}

	private Item firstItem() {
		final var items = client.getItems(BibRecord.builder()
			.sourceRecordId("42")
			.build()).block();

		assertThat("Expected exactly one item", items.size(), is(1));

		return items.get(0);
	}

	private void givenItem(KohaItem kohaItem) {
		when(apiClient.getItemsForBiblio("42"))
			.thenReturn(Mono.just(new KohaItem[] { kohaItem }));

		when(apiClient.getActiveHoldsForItem(String.valueOf(kohaItem.getItemId())))
			.thenReturn(Mono.just(new Object[0]));
	}

	private void givenPatron(KohaPatron kohaPatron) {
		final var list = new KohaPatronsList();
		list.add(kohaPatron);

		when(apiClient.getPatronByCardnumber(kohaPatron.getCardnumber()))
			.thenReturn(Mono.just(list));
	}
}
