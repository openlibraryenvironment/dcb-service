package services.k_int.interaction.polaris;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpResponse.response;
import static org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState.TRANSIT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.test.matchers.ItemMatchers.hasBarcode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCallNumber;
import static org.olf.dcb.test.matchers.ItemMatchers.hasDueDate;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalBibId;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocation;
import static org.olf.dcb.test.matchers.ItemMatchers.hasStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoader;
import org.olf.dcb.test.matchers.ItemMatchers;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
public class HostLmsPolarisClientTests {
	private static final String HOST_LMS_CODE = "polaris-hostlms-tests";

	@Inject
	private ResourceLoader loader;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private PolarisTestUtils.MockPolarisPAPIHost mockPolaris;
	private TestResourceLoader resourceLoader;

	@BeforeAll
	public void addFakePolarisApis(MockServerClient mock) {
		final String BASE_URL = "https://polaris-hostlms-tests.com";
		final String KEY = "polaris-hostlms-test-key";
		final String SECRET = "polaris-hostlms-test-secret";
		final String DOMAIN = "TEST";

		resourceLoader = new TestResourceLoader("classpath:mock-responses/polaris/", loader);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, KEY,
			SECRET, BASE_URL, DOMAIN, KEY, SECRET);

		mockPolaris = PolarisTestUtils.mockFor(mock, BASE_URL);

		// papi auth
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/authenticator/staff"))
			.respond(okJson(resourceLoader.getResource("test-staff-auth.json")));

		// app services auth
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/polaris.applicationservices/api/v1/eng/20/authentication/staffuser"))
			.respond(okJson(resourceLoader.getResource("auth-response.json")));
	}

	@Test
	public void getItemsByBibIdTest() {
		// Arrange
		referenceValueMappingFixture.defineLocationToAgencyMapping( "polaris-hostlms-tests", "15", "345test");

		agencyFixture.saveAgency(DataAgency.builder().id(randomUUID()).code("345test").name("Test College").build());

		final var sourceRecordId = "643425";

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/items/bibid/" + sourceRecordId))
			.respond(okJson(resourceLoader.getResource("items-get.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/materialtypes"))
			.respond(okJson(resourceLoader.getResource("materialtypes.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemstatuses"))
			.respond(okJson(resourceLoader.getResource("itemstatuses.json")));

		// Act
		final var itemsList = hostLmsFixture.createClient(HOST_LMS_CODE)
			.getItems(sourceRecordId).block();

		// Assert
		assertThat(itemsList, is(notNullValue()));
		assertThat(itemsList.size(), is(3));

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
		assertThat(firstItem.getHostLmsCode(), is(HOST_LMS_CODE));
		assertThat(firstItem, hasLocalBibId(sourceRecordId));
		assertThat(firstItem.getLocalItemTypeCode(), is("3"));
		assertThat(firstItem, hasLocalItemType("Book"));
		assertThat(firstItem.getSuppressed(), is(false));
		assertThat(firstItem.getAgencyCode(), is("345test"));
		assertThat(firstItem.getAgencyDescription(), is("Test College"));
		assertThat(firstItem.getCanonicalItemType(), is("UNKNOWN"));
	}

	@Test
	public void patronAuth() {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/authenticator/patron"))
			.respond(okJson(resourceLoader.getResource("test-patron-auth.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/patron/"+"3100222227777"))
			.respond(okJson(resourceLoader.getResource("patron-by-barcode.json")));

		// Act
		final var patron = hostLmsFixture.createClient(HOST_LMS_CODE)
			.patronAuth("BASIC/BARCODE+PASSWORD", "3100222227777", "password123").block();

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
	public void patronFind() {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/search/patrons/boolean*"))
			.respond(okJson(resourceLoader.getResource("patron-search.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/1255217/blockssummary"))
			.respond(response().withStatusCode(200).withBody("[]"));

		final var localPatronId = 1255217;
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/"+localPatronId))
			.respond(okJson(resourceLoader.getResource("get-patron-by-local-id.json")));

		// Act
		final var patron = hostLmsFixture.createClient(HOST_LMS_CODE)
			.patronAuth("UNIQUE-ID", "dcb_unique_Id", null).block();

		// Assert
		assertThat(patron, is(notNullValue()));
		assertThat(patron.getLocalId(), is(List.of("1255193")));
		assertThat(patron.getLocalPatronType(), is("3"));
		assertThat(patron.getLocalBarcodes(), is(List.of("0077777777")));
		assertThat(patron.getLocalHomeLibraryCode(), is("39"));
	}

	@Test
	public void placeHoldRequest() {
		// Arrange

		final var recordNumber = "12345";
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/item/"+recordNumber))
			.respond(okJson(resourceLoader.getResource("items-get.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords/"+1106339+"*"))
			.respond(okJson(resourceLoader.getResource("get-bib.json")));

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds*"))
			.respond(okJson(resourceLoader.getResource("successful-place-request.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/"+2977175)) // must match id from successful hold id
			.respond(okJson(resourceLoader.getResource("get-hold.json")));

		// Act
		final var localRequest = hostLmsFixture.createClient(HOST_LMS_CODE)
			.placeHoldRequest(PlaceHoldRequestParameters.builder()
				.localPatronId("1")
				.localBibId(null)
				.localItemId(recordNumber)
				.pickupLocation("5324532")
				.note("No special note")
				.patronRequestId(UUID.randomUUID().toString())
				.build())
			.block();

		// Assert
		assertThat(localRequest, hasLocalId("2977175"));
		assertThat(localRequest, hasLocalStatus("In Processing"));
	}

	@Test
	public void getHold() {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/holds/"+2977175))
			.respond(okJson(resourceLoader.getResource("get-hold.json")));

		// Act
		final var hold = hostLmsFixture.createClient(HOST_LMS_CODE).getHold("2977175").block();

		// Assert
		assertThat(hold, is(notNullValue()));
		assertThat(hold.getLocalId(), is("2977175"));
		assertThat(hold.getStatus(), is("In Processing"));
	}

	@Test
	public void createPatron() {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/patron"))
			.respond(okJson(resourceLoader.getResource("create-patron.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/1255217/blockssummary"))
			.respond(response().withStatusCode(200).withBody("[]"));

		final var uniqueId = "dcb_unique_Id";
		final var patron = Patron.builder().uniqueIds(List.of(uniqueId))
			.localPatronType("1").localHomeLibraryCode("39")
			.localBarcodes(List.of("0088888888")).build();

		// Act
		final var response = hostLmsFixture.createClient(HOST_LMS_CODE).createPatron(patron).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("1255217"));
	}

	@Test
	public void updatePatron() {
		// Arrange
		final var localPatronId = 1255193;

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/barcodes/patrons/"+1255193))
			.respond(response().withStatusCode(200).withBody("\"0077777777\""));

		mockPolaris.whenRequest(req -> req.withMethod("PUT")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/patron/0077777777"))
			.respond(okJson(resourceLoader.getResource("update-patron.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/"+localPatronId))
			.respond(okJson(resourceLoader.getResource("get-patron-by-local-id.json")));

		// Act
		final var response = hostLmsFixture.createClient(HOST_LMS_CODE)
			.updatePatron(String.valueOf(localPatronId), "3").block();

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

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/barcodes/items/"+localItemId))
			.respond(response().withStatusCode(200).withBody("\"126448190\""));

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/patron/"+localPatronBarcode+"/itemsout"))
			.respond(okJson(resourceLoader.getResource("itemcheckoutsuccess.json")));

		// Act
		final var response = hostLmsFixture.createClient(HOST_LMS_CODE)
			.checkOutItemToPatron(localItemId, localPatronBarcode).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response, is("OK"));
	}

	@Test
	public void getPatronByLocalId() {
		// Arrange
		final var localPatronId = 1255193;

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/"+localPatronId))
			.respond(okJson(resourceLoader.getResource("get-patron-by-local-id.json")));

		// Act
		final var response = hostLmsFixture.createClient(HOST_LMS_CODE)
			.getPatronByLocalId(String.valueOf(localPatronId)).block();

		// Assert
		assertThat(response, is(notNullValue()));
		assertThat(response.getLocalId(), is(List.of("1255193")));
		assertThat(response.getLocalPatronType(), is("3"));
		assertThat(response.getLocalBarcodes(), is(List.of("0077777777")));
		assertThat(response.getLocalHomeLibraryCode(), is("39"));
	}

	@Test
	public void createBib() {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/bibliographicrecords*"))
			.respond(okJson(resourceLoader.getResource("create-bib-resp.json")));

		// Act
		final var bib = hostLmsFixture.createClient(HOST_LMS_CODE).createBib(Bib.builder().title("title").build()).block();

		// Assert
		assertThat(bib, is(notNullValue()));
		assertThat(bib, is("1203065"));
	}

	@Test
	public void createItem() {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow*"))
			.respond(okJson(resourceLoader.getResource("item-workflow-response.json")));

		mockPolaris.whenRequest(req -> req.withMethod("PUT")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow/0e4c9e68-785e-4a1e-9417-f9bd245cc147"))
			.respond(okJson(resourceLoader.getResource("create-item-resp.json")));

		// Act
		final var item = hostLmsFixture.createClient(HOST_LMS_CODE)
			.createItem(CreateItemCommand.builder().bibId("1203065").barcode("3430470102").build()).block();

		// Assert
		assertThat(item, is(notNullValue()));
		assertThat(item.getLocalId(), is("4314002"));
		//assertThat(item.getStatus(), is("1"));
	}

	@Test
	public void getItem() {
		// Arrange
		final var localItemId = "3512742";
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/item/"+localItemId))
			.respond(okJson(resourceLoader.getResource("items-get.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemstatuses"))
			.respond(okJson(resourceLoader.getResource("itemstatuses.json")));

		// Act
		final var item = hostLmsFixture.createClient(HOST_LMS_CODE).getItem(localItemId).block();

		// Assert
		assertThat(item, is(notNullValue()));
		assertThat(item.getLocalId(), is("3512742"));
		assertThat(item.getStatus(), is("LOANED"));
		assertThat(item.getBarcode(), is("3430470102"));
	}

	@Test
	public void updateItemStatus() {
		// Arrange
		final var localItemId = "3512742";
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/item/"+localItemId))
			.respond(okJson(resourceLoader.getResource("items-get.json")));

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow*"))
			.respond(okJson(resourceLoader.getResource("item-workflow-response.json")));

		mockPolaris.whenRequest(req -> req.withMethod("PUT")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/workflow/0e4c9e68-785e-4a1e-9417-f9bd245cc147"))
			.respond(okJson(resourceLoader.getResource("create-item-resp.json")));

		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/polaris.applicationservices/api/v1/eng/20/polaris/73/1/itemstatuses"))
			.respond(okJson(resourceLoader.getResource("itemstatuses.json")));

		// Act
		final var string = hostLmsFixture.createClient(HOST_LMS_CODE).updateItemStatus(localItemId, TRANSIT).block();

		// Assert
		assertThat(string, is(notNullValue()));
		assertThat(string, is("OK"));
	}
}
