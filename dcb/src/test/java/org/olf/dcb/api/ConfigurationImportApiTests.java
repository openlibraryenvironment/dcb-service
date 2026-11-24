package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.olf.dcb.utils.DCBConfigurationService.ConfigImportResult;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ConfigurationImportApiTests {
	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	@SneakyThrows
	public void beforeAll(MockServerClient mock) {
		String itemTypeMappings = testResourceLoaderProvider.forBasePath("classpath:mock-responses/config/")
			.getResource("numericRangeMappingImport.tsv");

		mock.when(
			request()
				.withHeader("host", "some.tsv.url")
				.withMethod("GET")
				.withPath("/")
		)
		.respond( response()
				.withStatusCode(200)
				.withContentType(org.mockserver.model.MediaType.TEXT_PLAIN)
				.withBody(itemTypeMappings)
		);
	}

	@Test
	void numericRangeMappingImportWorks() {
		final var accessToken = "config-import-admin-token";
		TestStaticTokenValidator.add(accessToken, "test-admin", List.of(RoleNames.ADMINISTRATOR));

		final var importRequest = HttpRequest.POST("/admin/cfg",
				Map.of("profile", "numericRangeMappingImport", "url", "http://some.tsv.url/"))
			.bearerAuth(accessToken);

		final var importResponse = client.toBlocking().exchange(importRequest, ConfigImportResult.class);

		assertThat(importResponse.getStatus(), is(OK));
		assertThat(importResponse.body().getMessage(), is("OK"));
		assertThat("rows in numericRangeMappingImport.tsv", importResponse.body().getRecordsImported(), is(3L));
	}
}
