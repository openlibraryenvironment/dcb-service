package org.olf.dcb.core.interaction.sierra;

import static java.lang.Integer.parseInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotDeleted;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.patrons.Block;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraHostLmsClientPatronTests {
	private static final String HOST_LMS_CODE = "sierra-patron";

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;
	private SierraItemsAPIFixture sierraItemsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-api-tests.com";
		final String KEY = "item-key";
		final String SECRET = "item-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);
		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, null);

		final var sierraLoginFixture = sierraApiFixtureProvider.login(mockServerClient, null);

		sierraLoginFixture.failLoginsForAnyOtherCredentials(KEY, SECRET);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	@SneakyThrows
	void shouldFindPatronByLocalId() {
		// Arrange
		final var localPatronId = "583634";
		final var localPatronType = 23;
		final var barcode = "5472792742";

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.barcodes(List.of(barcode))
				.names(List.of("first name", "middle name", "last name"))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.blockInfo(Block.builder()
					.code("a")
					.build())
				.build());

		final var canonicalPatronType = "UNDERGRAD";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			localPatronType, localPatronType, "DCB", canonicalPatronType);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var patron = singleValueFrom(client.getPatronByLocalId(localPatronId));

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			hasLocalIds(localPatronId),
			hasLocalNames("first name", "middle name", "last name"),
			hasLocalBarcodes(barcode),
			hasLocalPatronType(localPatronType),
			hasCanonicalPatronType(canonicalPatronType),
			hasHomeLibraryCode("home-library"),
			isBlocked(),
			isNotDeleted()
		));

		// This is important to check that the fields parameter includes the expected fields
		sierraPatronsAPIFixture.verifyGetPatronByLocalIdRequestMade(localPatronId);
	}

	@Test
	@SneakyThrows
	void shouldNotPassPinWhenThereIsNoConfigValueSetUponCheckout() {
		// Arrange
		final var itemId = "46345345";
		final var patronBarcode = "5472792742";
		final var itemBarcode = "247389084";

		sierraItemsAPIFixture.mockUpdateItem(itemId);
		sierraPatronsAPIFixture.checkOutItemToPatron(itemBarcode, patronBarcode);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var command = CheckoutItemCommand.builder()
			.itemId(itemId)
			.itemBarcode(itemBarcode)
			.patronBarcode(patronBarcode)
			.build();

		final var checkout = singleValueFrom(client.checkOutItemToPatron(command));

		// Assert
		assertThat(checkout, allOf(
			notNullValue(),
			equalToIgnoringCase("ok")
		));

		sierraPatronsAPIFixture.verifyCheckoutMade(itemBarcode, patronBarcode, null);
	}
}
