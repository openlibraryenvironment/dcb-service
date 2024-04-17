package org.olf.dcb.core.interaction.sierra;

import static java.lang.Integer.parseInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture.Patron;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotDeleted;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.NumericRangeMappingFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
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
	@Inject
	private NumericRangeMappingFixture numericRangeMappingFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://patron-api-tests.com";
		final String KEY = "item-key";
		final String SECRET = "item-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		final var sierraLoginFixture = sierraApiFixtureProvider.loginFixtureFor(mockServerClient);

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
			Patron.builder()
				.id(parseInt(localPatronId))
				.barcodes(List.of(barcode))
				.names(List.of("first name", "middle name", "last name"))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
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
			hasLocalPatronType(Integer.toString(localPatronType)),
			hasCanonicalPatronType(canonicalPatronType),
			hasHomeLibraryCode("home-library"),
			isNotDeleted()
		));
	}
}
