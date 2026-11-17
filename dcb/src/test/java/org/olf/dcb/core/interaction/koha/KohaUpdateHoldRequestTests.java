package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Re-resolution on a Koha borrower: the virtual item takes the new supplier's barcode so
 * the copy that arrives can be checked in against it. Only the changed fields are sent,
 * and the hold, which is on the item, is left alone.
 */
@MockServerMicronautTest
class KohaUpdateHoldRequestTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String BIBLIO_ID = "4001";
	private static final String ITEM_ID = "55";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
		mockKohaFixture.mockUpdateItem(BIBLIO_ID, ITEM_ID, item().externalId("new-barcode").build());
	}

	@Test
	void shouldGiveTheVirtualItemTheNewSuppliersBarcodeAndNothingElse() {
		final var updated = client.updateHoldRequest(request().build()).block();

		assertThat(updated.getRequestedItemBarcode(), is("new-barcode"));
		mockKohaFixture.verifyItemUpdated(BIBLIO_ID, ITEM_ID, "{\"external_id\": \"new-barcode\"}");
	}

	@Test
	void shouldFindTheBiblioWhenTheRequestDoesNotCarryIt() {
		mockKohaFixture.mockItem(item().build());

		client.updateHoldRequest(request().bibId(null).build()).block();

		mockKohaFixture.verifyItemUpdated(BIBLIO_ID, ITEM_ID, "{\"external_id\": \"new-barcode\"}");
	}

	@Test
	void shouldSetTheItemTypeWhenTheNewCopyHasADifferentCanonicalType() {
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			KOHA_HOST_LMS_CODE, "CIRCAV", "DCB_AV");

		client.updateHoldRequest(request().canonicalItemType("CIRCAV").build()).block();

		mockKohaFixture.verifyItemUpdated(BIBLIO_ID, ITEM_ID,
			"{\"external_id\": \"new-barcode\", \"item_type_id\": \"DCB_AV\"}");
	}

	private static LocalRequest.LocalRequestBuilder request() {
		return LocalRequest.builder()
			.localId("501")
			.requestedItemId(ITEM_ID)
			.requestedItemBarcode("new-barcode")
			.bibId(BIBLIO_ID)
			.supplyingAgencyCode("other-supplier");
	}

	private static KohaItem.KohaItemBuilder item() {
		return KohaItem.builder()
			.itemId(Long.valueOf(ITEM_ID))
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("old-barcode")
			.homeLibraryId("BRANCH-N");
	}
}
