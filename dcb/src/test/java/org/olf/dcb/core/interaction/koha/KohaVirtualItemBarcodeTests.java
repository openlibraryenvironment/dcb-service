package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The barcode a Koha virtual item carries: the lender's, or the lender's after
 * virtual-item-barcode-prefix. Koha holds one item per barcode, in at most 20 characters.
 */
@MockServerMicronautTest
class KohaVirtualItemBarcodeTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-virtual-barcode";
	private static final String KOHA_HOST = "fake-koha-virtual-barcode";
	private static final String BIBLIO_ID = "4001";
	private static final String ITEM_ID = "55";
	private static final String LENDER_BARCODE = "30000012345678";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(KOHA_HOST_LMS_CODE, "BKM", "DCB_VIRTUAL");
	}

	@Test
	void shouldGiveTheVirtualItemTheLendersBarcodeWithoutAPrefix() {
		koha.mockCreateItem(BIBLIO_ID, KohaItem.builder().itemId(Long.valueOf(ITEM_ID)).build());

		clientWith(Map.of()).createItem(createItem(LENDER_BARCODE)).block();

		koha.verifyCreateItem(BIBLIO_ID, "{\"external_id\": \"" + LENDER_BARCODE + "\"}");
	}

	@Test
	void shouldPutThePrefixBeforeTheLendersBarcode() {
		koha.mockCreateItem(BIBLIO_ID, KohaItem.builder().itemId(Long.valueOf(ITEM_ID)).build());

		clientWith(Map.of("virtual-item-barcode-prefix", "DCB-")).createItem(createItem(LENDER_BARCODE)).block();

		koha.verifyCreateItem(BIBLIO_ID, "{\"external_id\": \"DCB-" + LENDER_BARCODE + "\"}");
	}

	@Test
	void shouldRefuseABarcodeKohaCannotHoldWithoutAskingKoha() {
		final var problem = assertThrows(ThrowableProblem.class,
			() -> clientWith(Map.of("virtual-item-barcode-prefix", "CONSORTIUM-"))
				.createItem(createItem(LENDER_BARCODE)).block());

		assertThat(problem.getTitle(), is("The virtual item's barcode is too long for Koha"));
		assertThat(problem.getParameters().get("remedy"), is("Shorten virtual-item-barcode-prefix"));
		koha.verifyNoVirtualRecordsCreated();
	}

	@Test
	void shouldNameADuplicateBarcodeAndSuggestThePrefix() {
		koha.mockCreateItemDuplicateBarcode(BIBLIO_ID);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> clientWith(Map.of()).createItem(createItem(LENDER_BARCODE)).block());

		assertThat(problem.getTitle(), is("Koha already has an item with this barcode"));
		assertThat(problem.getDetail(), containsString(LENDER_BARCODE));
		assertThat(String.valueOf(problem.getParameters().get("remedy")),
			containsString("set virtual-item-barcode-prefix"));
	}

	@Test
	void shouldNameADuplicatePrefixedBarcodeAsAVirtualItemLeftBehind() {
		koha.mockCreateItemDuplicateBarcode(BIBLIO_ID);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> clientWith(Map.of("virtual-item-barcode-prefix", "DCB-"))
				.createItem(createItem(LENDER_BARCODE)).block());

		assertThat(problem.getDetail(), containsString("DCB-" + LENDER_BARCODE));
		assertThat(String.valueOf(problem.getParameters().get("remedy")),
			containsString("A DCB virtual item with this barcode is still in this Koha"));
	}

	@Test
	void shouldPrefixTheNextLendersBarcodeOnReResolution() {
		koha.mockUpdateItem(BIBLIO_ID, ITEM_ID, KohaItem.builder().itemId(Long.valueOf(ITEM_ID)).build());

		clientWith(Map.of("virtual-item-barcode-prefix", "DCB-")).updateHoldRequest(LocalRequest.builder()
			.localId("501")
			.requestedItemId(ITEM_ID)
			.requestedItemBarcode("next-lender-1")
			.bibId(BIBLIO_ID)
			.build()).block();

		koha.verifyItemUpdated(BIBLIO_ID, ITEM_ID, "{\"external_id\": \"DCB-next-lender-1\"}");
	}

	@Test
	void shouldSayWhichBarcodeAVirtualItemForACopyCarries() {
		assertThat(clientWith(Map.of("virtual-item-barcode-prefix", "DCB-")).virtualItemBarcodeFor(LENDER_BARCODE),
			is("DCB-" + LENDER_BARCODE));
		assertThat(clientWith(Map.of()).virtualItemBarcodeFor(LENDER_BARCODE), is(LENDER_BARCODE));
	}

	private static CreateItemCommand createItem(String barcode) {
		return CreateItemCommand.builder()
			.bibId(BIBLIO_ID)
			.barcode(barcode)
			.canonicalItemType("BKM")
			.patronHomeLocation("BRANCH-N")
			.build();
	}

	private HostLmsClient clientWith(Map<String, Object> extraConfig) {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST, extraConfig);

		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}
}
