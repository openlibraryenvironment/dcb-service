package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;
import static io.micronaut.http.HttpStatus.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.MediaType;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.test.mockserver.MockServerMicronautTest;

import org.olf.dcb.utils.DCBConfigurationService.ConfigImportResult;
import org.olf.dcb.core.api.AdminController.ImportCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minidev.json.JSONObject;

import org.mockserver.client.MockServerClient;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import io.micronaut.core.io.ResourceLoader;
import java.util.Optional;
import java.io.InputStream;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationImportApiTests {

        private final Logger log = LoggerFactory.getLogger(ConfigurationImportApiTests.class);

        @Inject
        private ResourceLoader loader;

        @Inject
        @Client("/")
        private HttpClient client;

	private String itemTypeMappings = null;

        @BeforeAll
        @SneakyThrows
        public void beforeAll(MockServerClient mock) {

		Optional<InputStream> ois = loader.getResourceAsStream("classpath:mock-responses/config/numericRangeMappingImport.tsv");
		ois.ifPresent(is -> { try { itemTypeMappings = new String(is.readAllBytes()); } catch ( Exception e ) {}  } );

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
                               // .withBody("a	b	c\n1	2	3")
		);

        }

        @BeforeEach
        void beforeEach() {
        }

	@Test
        void numericRangeMappingImportWorks() {

		log.debug("Admin Login..");
		// See ./dcb/src/main/java/org/olf/dcb/security/DcbAuthenticationProvider.java
		// https://guides.micronaut.io/latest/micronaut-security-jwt-gradle-groovy.html
		UsernamePasswordCredentials creds = new UsernamePasswordCredentials("admin", "password");
                HttpRequest<?> request = HttpRequest.POST("/login", creds);
                HttpResponse<BearerAccessRefreshToken> rsp = client.toBlocking().exchange(request, BearerAccessRefreshToken.class);
                assertEquals(OK, rsp.getStatus());

                BearerAccessRefreshToken bearerAccessRefreshToken = rsp.body();
	 	String accessToken = bearerAccessRefreshToken.getAccessToken();
		log.debug("Got login response: {} {} {}",accessToken,bearerAccessRefreshToken.getUsername(),bearerAccessRefreshToken.getRoles());

		HttpRequest<?> requestWithAuthorization = HttpRequest.GET("/secured").accept(MediaType.TEXT_PLAIN).bearerAuth(accessToken);
                HttpResponse<String> response = client.toBlocking().exchange(requestWithAuthorization, String.class);
		log.debug("Secured response {}",response);

		final var importRequestBody = new JSONObject() {
		  {
                    put("profile", "numericRangeMappingImport");
                    put("url", "http://some.tsv.url/");
		  }
		};

		HttpRequest<?> cfg_request = HttpRequest.POST("/admin/cfg", importRequestBody).bearerAuth(accessToken);
		final var importResponse = client.toBlocking().exchange(cfg_request, ConfigImportResult.class);


		log.debug("Got result {}",importResponse.body());

		assert 1==1;
	}

}
