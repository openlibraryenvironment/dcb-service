package services.k_int.interaction.polaris;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HostLmsPAPIClientTests {
	private final Logger log = LoggerFactory.getLogger(HostLmsPAPIClientTests.class);
	private static final String HOST_LMS_CODE = "polaris-hostlms-tests";
	@Inject
	private ResourceLoader loader;
	@Inject
	private IngestService ingestService;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;
	@Inject
	private HostLmsService hostLmsService;
	@Inject
	private BibRecordFixture bibRecordFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HttpClient client;
	private static final String CP_RESOURCES_POLARIS = "classpath:mock-responses/polaris/";
	private PolarisTestUtils.MockPolarisPAPIHost mockPolaris;
	private DataHostLms polarisHostLms;

	private String getResourceAsString(String cp_resources, String resourceName) throws IOException {
		return new String(loader.getResourceAsStream(cp_resources + resourceName).get().readAllBytes());
	}

	@BeforeAll
	public void addFakePolarisApis(MockServerClient mock) throws IOException {
		final String BASE_URL = "https://polaris-hostlms-tests.com";
		final String KEY = "polaris-hostlms-test-key";
		final String SECRET = "polaris-hostlms-test-secret";
		final String DOMAIN = "TEST";

		hostLmsFixture.deleteAll();

		polarisHostLms = hostLmsFixture.createPAPIHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE, DOMAIN, KEY, SECRET);
		mockPolaris = PolarisTestUtils.mockFor(mock, BASE_URL);

		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/authenticator/staff"))
			.respond(okJson(getResourceAsString(CP_RESOURCES_POLARIS, "test-staff-auth.json")));
	}

	@Test
	public void getItemsByBibIdTest() throws IOException {
		// Arrange
		referenceValueMappingFixture.saveReferenceValueMapping(
			ReferenceValueMapping.builder().id(UUID.randomUUID())
				.fromCategory("ShelvingLocation").fromContext("polaris-hostlms-tests").fromValue("15")
				.toCategory("AGENCY").toContext("DCB").toValue("345test").reciprocal(false)
				.build() );
		agencyFixture.saveAgency(DataAgency.builder().id(randomUUID()).code("345test").name("Test College").build());
		final var clusterRecord = clusterRecordFixture.createClusterRecord(randomUUID());
		final var bibRecordId = randomUUID();
		bibRecordFixture.createBibRecord(bibRecordId, polarisHostLms.getId(), "465675", clusterRecord);
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/protected/v1/1033/100/1/string/synch/items/bibid/"+bibRecordId))
			.respond(okJson(getResourceAsString(CP_RESOURCES_POLARIS, "items-get.json")));
		// Act
		final var itemsList = hostLmsFixture.createClient(HOST_LMS_CODE)
			.getItems(String.valueOf(bibRecordId)).block();
		// Assert
		assertThat(itemsList, is(notNullValue()));
		assertThat(itemsList.size(), is(3));
		final var firstItem = itemsList.stream()
			.filter(item -> "3512742".equals(item.getId()))
			.findFirst()
			.orElse(null);
		assertThat(firstItem, is(notNullValue()));
		assertThat(firstItem.getStatus(), is(new ItemStatus(ItemStatusCode.UNAVAILABLE)));
		assertThat(firstItem.getDueDate().getClass(), is(Instant.class));
		assertThat(firstItem.getLocation().getCode(), is("15"));
		assertThat(firstItem.getLocation().getName(), is("SLPL Kingshighway"));
		assertThat(firstItem.getBarcode(), is("3430470102"));
		assertThat(firstItem.getCallNumber(), is("E Bellini Mario"));
		assertThat(firstItem.getHostLmsCode(), is(HOST_LMS_CODE));
		assertThat(firstItem.getLocalBibId(), is(String.valueOf(bibRecordId)));
		assertThat(firstItem.getLocalItemType(), is("Book"));
		assertThat(firstItem.getSuppressed(), is(false));
		assertThat(firstItem.getAgencyCode(), is("345test"));
		assertThat(firstItem.getAgencyDescription(), is("Test College"));
		assertThat(firstItem.getCanonicalItemType(), is("UNKNOWN"));
	}

	@Test
	public void patronAuth() throws IOException {
		// Arrange
		mockPolaris.whenRequest(req -> req.withMethod("POST")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/authenticator/patron"))
			.respond(okJson(getResourceAsString(CP_RESOURCES_POLARIS, "test-patron-auth.json")));
		mockPolaris.whenRequest(req -> req.withMethod("GET")
				.withPath("/PAPIService/REST/public/v1/1033/100/1/patron/"+"3100222227777"))
			.respond(okJson(getResourceAsString(CP_RESOURCES_POLARIS, "patron-by-barcode.json")));
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
}
