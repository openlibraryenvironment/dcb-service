package services.k_int.interaction.sierra;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.client.HttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.HostLmsSierraApiClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.test.mockserver.MockServerMicronautTest;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MockServerMicronautTest
@MicronautTest(transactional = false, propertySources = { "classpath:configs/SierraLmsClientTests.yml" }, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SierraApiBibTests {
	private static final String SIERRA_TOKEN = "test-token-for-user";
	@Property(name = "hosts.test1.client.base-url")
	private String sierraHost;
	@Property(name = "hosts.test1.client.key")
	private String sierraUser;
	@Property(name = "hosts.test1.client.secret")
	private String sierraPass;
	@Inject
	private HttpClient client;
	@Inject
	ResourceLoader loader;
	@Inject
	private HostLmsService hostLmsService;
	private SierraBibsAPIFixture sierraBibsAPIFixture;

	@BeforeAll
	public void beforeAll(MockServerClient mock) {
		SierraTestUtils.mockFor(mock, sierraHost)
				.setValidCredentials(sierraUser, sierraPass, SIERRA_TOKEN, 60);

		sierraBibsAPIFixture = new SierraBibsAPIFixture(mock, loader);
	}

	@Test
	public void testBibsGET() throws IOException {
		// Arrange
		sierraBibsAPIFixture.createGetBibsMockWithQueryStringParameters();
		final var sierraApiClient = createClient();

		// Act
		var response = Mono.from( sierraApiClient.bibs(3, 1, "null",
			"null", null, false, null, false,
			List.of("a") )).block();

		// Assert
		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.total(), 3);
		assertEquals(response.entries().get(0).id(), "1000002");
		assertEquals(response.entries().get(1).id(), "1000003");
		assertEquals(response.entries().get(2).id(), "1000004");
	}

	@Test
	public void testBibsPOST() throws IOException {
		// Arrange
		BibPatch bibPatch = BibPatch.builder()
			.authors(new String[]{"John Smith"})
			.titles(new String[]{"The Book of John"})
			.bibCode3("n")
			.build();

		sierraBibsAPIFixture.createPostBibsMock(bibPatch);
		final var sierraApiClient = createClient();

		// Act
		var response = Mono.from( sierraApiClient.bibs(bibPatch) ).block();

		// Assert
		assertNotNull(response);
		assertEquals(response.getClass(), LinkResult.class);
		assertEquals(response.link, "https://sandbox.iii.com/iii/sierra-api/v6/bibs/7916922");
	}

	private HostLmsSierraApiClient createClient() {
		final var testHostLms = hostLmsService.findByCode("test1").block();

		// Need to create a client directly
		// because injecting gives incorrectly configured client
		return new HostLmsSierraApiClient(testHostLms, client);
	}
}
