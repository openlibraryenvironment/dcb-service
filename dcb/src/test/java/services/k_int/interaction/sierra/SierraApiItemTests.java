package services.k_int.interaction.sierra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.model.HttpRequest.request;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraItemsAPIFixture;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class SierraApiItemTests {
	@Inject
	private SierraApiClient client;

	@Inject
	private ResourceLoader loader;

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/items/?limit=3&offset=1&fields=id%2CupdatedDate%2CcreatedDate%2Cdeleted%2CbibIds%2Clocation%2Cstatus%2Cvolumes%2Cbarcode%2CcallNumber&deleted=false&status=-&suppressed=false&locations=*
	*/
	@Test
	@SneakyThrows
	void sierraCanRespondWithMultipleItems(MockServerClient mock) {
		final var sierraItemsAPIFixture = new SierraItemsAPIFixture(mock, loader);

		// Does not use the fixture for request due to a variation of the path
		// between these tests and other tests
		// (the trailing forward slash is not present for other tests)
		mock.when(request()
			.withMethod("GET")
			.withPath("/iii/sierra-api/v6/items/")
			.withQueryStringParameter("limit", "3")
			.withQueryStringParameter("suppressed", "false")
			.withQueryStringParameter("offset", "1")
			.withQueryStringParameter("deleted", "false")
			.withQueryStringParameter("fields", "id,updatedDate,createdDate,deleted,bibIds,location,status,volumes,barcode,callNumber")
			.withQueryStringParameter("status", "-"))
		.respond(sierraItemsAPIFixture.threeItemsResponse());

		// Fetch from sierra and block
		var response = Mono.from(client.items(
			Params.builder()
				.limit(3)
				.suppressed(false)
				.offset(1)
				.deleted(false)
				.fields(List.of("id","updatedDate","createdDate","deleted","bibIds","location","status","volumes","barcode","callNumber"))
				.status("-")
				.build()))
			.block();

		assertNotNull(response);
		assertEquals(response.getClass(), ResultSet.class);
		assertEquals(response.getTotal(), 3);

		final var items = response.getEntries();

		assertEquals(items.get(0).getId(), "f2010365-e1b1-4a5d-b431-a3c65b5f23fb");
		assertEquals(items.get(1).getId(), "c5bc9cd0-fc23-48be-9d52-647cea8c63ca");
		assertEquals(items.get(2).getId(), "69415d0a-ace5-49e4-96fd-f63855235bf0");
	}
}
