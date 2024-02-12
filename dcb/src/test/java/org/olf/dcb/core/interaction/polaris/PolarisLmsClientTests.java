package org.olf.dcb.core.interaction.polaris;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.TRANSIT;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ERR0210;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.HostLmsRequestMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCanonicalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasHostLmsCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemTypeCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoHoldCount;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotDeleted;
import static org.olf.dcb.test.matchers.ItemMatchers.isNotSuppressed;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.olf.dcb.test.matchers.HostLmsRequestMatchers;
import org.olf.dcb.test.matchers.ItemMatchers;
import org.olf.dcb.test.matchers.interaction.HostLmsItemMatchers;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class PolarisLmsClientTests {
	private static final String HOST_LMS_CODE = "polaris-hostlms-tests";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockServerClient mockServerClient;
	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		final String BASE_URL = "https://polaris-hostlms-tests.com";
		final String KEY = "polaris-hostlms-test-key";
		final String SECRET = "polaris-hostlms-test-secret";
		final String DOMAIN = "TEST";

		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, DOMAIN, KEY, SECRET);

		mockPolarisFixture = new MockPolarisFixture("polaris-hostlms-tests.com",
			mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	public void beforeEach() {
		mockServerClient.reset();

		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();
	}

	@Test
	public void getItemsByBibIdTest() {
		// Arrange
		referenceValueMappingFixture.defineLocationToAgencyMapping( "polaris-hostlms-tests", "15", "345test");

		agencyFixture.defineAgency("345test", "Test College");

		final var bibId = "643425";

		mockPolarisFixture.mockGetItemsForBib(bibId);
		mockPolarisFixture.mockGetMaterialTypes();
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var itemsList = singleValueFrom(client
			.getItems(BibRecord.builder()
				.sourceRecordId(bibId)
				.build()));

		// Assert
		assertThat(itemsList, hasSize(3));

		final var firstItem = itemsList.stream()
			.filter(item -> "3512742".equals(item.getLocalId()))
			.findFirst()
			.orElse(null);

		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem, ItemMatchers.hasLocalId("3512742"));
		assertThat(firstItem, hasStatus(UNAVAILABLE));
		assertThat(firstItem, hasDueDate("2023-10-14T23:59:00Z"));
		assertThat(firstItem, hasLocation("SLPL Kingshighway", "15"));
		assertThat(firstItem, hasBarcode("3430470102"));
		assertThat(firstItem, hasCallNumber("E Bellini Mario"));
		assertThat(firstItem, hasHostLmsCode(HOST_LMS_CODE));
		assertThat(firstItem, hasLocalBibId(bibId));
		assertThat(firstItem, hasLocalItemType("Book"));
		assertThat(firstItem, hasLocalItemTypeCode("3"));
		assertThat(firstItem, hasNoHoldCount());
		assertThat(firstItem, isNotSuppressed());
		assertThat(firstItem, isNotDeleted());
		assertThat(firstItem, hasAgencyCode("345test"));
		assertThat(firstItem, hasAgencyName("Test College"));
		assertThat(firstItem, hasCanonicalItemType("UNKNOWN"));
	}

	@Test
	public void patronAuth() {
		// Arrange
		mockPolarisFixture.mockPatronAuthentication();
		mockPolarisFixture.mockGetPatronByBarcode("3100222227777");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var patron = singleValueFrom(client.patronAuth(
			"BASIC/BARCODE+PASSWORD", "3100222227777", "password123"));

		// Assert
		assertThat(patron, is(notNullValue()));
		assertThat(patron.getLocalId(), is(List.of("1255192")));
		assertThat(patron.getLocalPatronType(), is("5"));
		assertThat(patron.getLocalBarcodes(), is(List.of("0088888888")));
		assertThat(patron.getLocalHomeLibraryCode(), is("39"));
		// not returned
		assertThat(patron.getLocalNames(), is(nullValue()));
		assertThat(patron.getUniqueIds(), is(nullValue()));
	}

	@Test
	public void shouldBeAbleToFindVirtualPatron() {
		// Arrange
		final var agencyCode = "known-agency";
		final var localId = "1255193";
		final var localBarcode = "0077777777";
		final var localPatronId = "1255217";

		mockPolarisFixture.mockPatronSearch(localBarcode, localId, agencyCode);

		mockPolarisFixture.mockGetPatron(localPatronId);
		mockPolarisFixture.mockGetPatronBlocksSummary(localPatronId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId(localId)
					.localBarcode(localBarcode)
					.resolvedAgency(DataAgency.builder()
						.code(agencyCode)
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, is(notNullValue()));
		assertThat(foundPatron.getLocalId(), is(List.of(localId)));
		assertThat(foundPatron.getLocalPatronType(), is("3"));
		assertThat(foundPatron.getLocalBarcodes(), is(List.of(localBarcode)));
		assertThat(foundPatron.getLocalHomeLibraryCode(), is("39"));
	}

	@Test
	public void shouldTolerateNotFoundResponseFromPatronBlocksWhenFindVirtualPatron() {
		// Arrange
		final var agencyCode = "known-agency";
		final var localId = "1255193";
		final var localBarcode = "0077777777";
		final var localPatronId = "1255217";

		mockPolarisFixture.mockPatronSearch(localBarcode, localId, agencyCode);

		mockPolarisFixture.mockGetPatron(localPatronId);
		mockPolarisFixture.mockGetPatronBlocksSummaryNotFoundResponse(localPatronId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId(localId)
					.localBarcode(localBarcode)
					.resolvedAgency(DataAgency.builder()
						.code(agencyCode)
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, is(notNullValue()));
		assertThat(foundPatron.getLocalId(), is(List.of(localId)));
		assertThat(foundPatron.getLocalPatronType(), is("3"));
		assertThat(foundPatron.getLocalBarcodes(), is(List.of(localBarcode)));
		assertThat(foundPatron.getLocalHomeLibraryCode(), is("39"));
	}

	@Test
	public void shouldToFindVirtualPatronWhenPatronBlocksCannotBeFetched() {
		// Arrange
		final var agencyCode = "known-agency";
		final var localId = "1255193";
		final var localBarcode = "0077777777";
		final var localPatronId = "1255217";

		mockPolarisFixture.mockPatronSearch(localBarcode, localId, agencyCode);

		mockPolarisFixture.mockGetPatron(localPatronId);
		mockPolarisFixture.mockGetPatronBlocksSummaryServerErrorResponse(localPatronId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var patron = org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.localId(localId)
					.localBarcode(localBarcode)
					.resolvedAgency(DataAgency.builder()
						.code(agencyCode)
						.build())
					.homeIdentity(true)
					.build()
			))
			.build();

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessage("Unable to retrieve patron blocks from polaris: Internal Server Error"),
			hasProperty("type", is(ERR0210))
		));
	}

	@Test
	public void shouldBeAbleToPlaceRequestAtSupplyingAgency() {
		// Arrange
		final var itemId = "12345";

		mockPolarisFixture.mockGetItem(itemId);
		mockPolarisFixture.mockGetBib("1106339");
		mockPolarisFixture.mockPlaceHold();
		mockPolarisFixture.mockGetHold("2977175");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtSupplyingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId("1")
				.localBibId(null)
				.localItemId(itemId)
				.pickupLocation("5324532")
				.note("No special note")
				.patronRequestId(UUID.randomUUID().toString())
				.build()
		));

		// Assert
		assertThat(localRequest, hasLocalId("2977175"));
		assertThat(localRequest, hasLocalStatus("In Processing"));
	}

	@Test
	public void getRequest() {
		// Arrange
		mockPolarisFixture.mockGetHold("2977175");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var request = singleValueFrom(client.getRequest("2977175"));

		// Assert
		assertThat(request, allOf(
			notNullValue(),
			HostLmsRequestMatchers.hasLocalId("2977175"),
			hasStatus("In Processing")
		));
	}

	@Test
	public void createPatron() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockCreatePatron();
		mockPolarisFixture.mockGetPatronBlocksSummary("1255217");

		final var patron = Patron.builder()
			.uniqueIds(List.of("dcb_unique_Id"))
			.localPatronType("1")
			.localHomeLibraryCode("39")
			.localBarcodes(List.of("0088888888"))
			.localItemId(localItemId)
			.build();

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client.createPatron(patron));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("1255217"));
	}

	@Test
	public void updatePatron() {
		// Arrange
		final var localPatronId = "1255193";

		mockPolarisFixture.mockGetPatronBarcode(localPatronId, "0077777777");
		mockPolarisFixture.mockUpdatePatron("0077777777");
		mockPolarisFixture.mockGetPatron(localPatronId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client.updatePatron(localPatronId, "3"));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getLocalId(), is(List.of("1255193")));
		assertThat(response.getLocalPatronType(), is("3"));
		assertThat(response.getLocalBarcodes(), is(List.of("0077777777")));
		assertThat(response.getLocalHomeLibraryCode(), is("39"));
	}

	@Test
	public void checkOutItemToPatron() {
		// Arrange
		final var localItemId = "2273395";
		final var localPatronBarcode = "0077777777";

		mockPolarisFixture.mockGetItemBarcode(localItemId, "126448190");
		mockPolarisFixture.mockCheckoutItemToPatron(localPatronBarcode);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client
			.checkOutItemToPatron(localItemId, localPatronBarcode, null));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	public void getPatronByLocalId() {
		// Arrange
		final var localPatronId = "1255193";

		mockPolarisFixture.mockGetPatron(localPatronId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client.getPatronByLocalId(localPatronId));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getLocalId(), is(List.of("1255193")));
		assertThat(response.getLocalPatronType(), is("3"));
		assertThat(response.getLocalBarcodes(), is(List.of("0077777777")));
		assertThat(response.getLocalHomeLibraryCode(), is("39"));
	}

	@Test
	public void shouldBeAbleToCreateBib() {
		// Arrange
		mockPolarisFixture.mockCreateBib();

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var bib = singleValueFrom(client.createBib(
			Bib.builder()
				.title("title")
				.build()));

		// Assert
		assertThat(bib, is(notNullValue()));
		assertThat(bib, is("1203065"));
	}

	@Test
	public void shouldFailWhenUnexpectedResponseReceivedDuringBibCreation() {
		// Arrange
		mockPolarisFixture.mockCreateBibNotAuthorisedResponse();

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(client.createBib(
				Bib.builder()
					.title("title")
					.build())));

		// Assert
		assertThat(exception, allOf(
			notNullValue(),
			hasMessage("Unauthorized")
		));
	}

	@Test
	public void deleteBib() {
		// Arrange
		final var localBibId = "3214809";
		mockPolarisFixture.mockStartWorkflow("continue-bib-delete.json");

		mockPolarisFixture.mockContinueWorkflow("ba8ce734-7b49-48b2-bdc3-c42f56d60091",
			"successful-bib-delete.json");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client.deleteBib(localBibId));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	public void createItem() {
		// Arrange
		mockPolarisFixture.mock("POST", "/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow*", "item-workflow-response.json");
		mockPolarisFixture.mockContinueWorkflow("0e4c9e68-785e-4a1e-9417-f9bd245cc147",
			"create-item-resp.json");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var item = singleValueFrom(client.createItem(
			CreateItemCommand.builder()
				.bibId("1203065")
				.barcode("3430470102")
				.patronHomeLocation("37")
				.build()));

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId("4314002")
		));
	}

	@Test
	public void shouldBeAbleToGetAnItemById() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var localItem = singleValueFrom(client.getItem(localItemId, null));

		// Assert
		assertThat(localItem, allOf(
			notNullValue(),
			HostLmsItemMatchers.hasLocalId(localItemId),
			HostLmsItemMatchers.hasStatus("LOANED"),
			HostLmsItemMatchers.hasBarcode("3430470102")
		));
	}

	@Test
	public void shouldFailToGetAnItemWhenUnexpectedResponseReceived() {
		// Arrange
		final var localItemId = "628125";

		mockPolarisFixture.mockGetItemServerErrorResponse(localItemId);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var exception = assertThrows(HttpClientResponseException.class,
			() -> singleValueFrom(client.getItem(localItemId, null)));

		// Assert
		assertThat(exception, allOf(
			notNullValue(),
			hasMessage("Internal Server Error")
		));
	}

	@Test
	public void deleteItem() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockStartWorkflow("deleteSingleItemContinue.json");

		mockPolarisFixture.mockContinueWorkflow("c457e0b8-3d89-45dc-abcd-a389f0993203",
			"deleteBibIfLastItem.json");

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client.deleteItem(localItemId));

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	public void updateItemStatus() {
		// Arrange
		final var localItemId = "3512742";

		mockPolarisFixture.mockGetItem(localItemId);
		mockPolarisFixture.mockStartWorkflow("item-workflow-response.json");

		mockPolarisFixture.mockContinueWorkflow("0e4c9e68-785e-4a1e-9417-f9bd245cc147",
			"create-item-resp.json");

		mockPolarisFixture.mockGetItemStatuses();

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);
		final var string = singleValueFrom(client.updateItemStatus(localItemId, TRANSIT, null));

		// Assert
		assertThat(string, is(notNullValue()));
		assertThat(string, is("OK"));
	}
}
