package org.olf.dcb.search;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.LocationSymbol;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.LocationSymbolRepository;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.olf.dcb.core.QueryService;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.core.model.DataAgency;

@DcbTest
class SearchTests {

	@Inject
	@Client("/")
	HttpClient client;

	@BeforeEach
	void beforeEach() {
	}

        @Inject
        PostgresAgencyRepository agencyRepository;

	@Test
	void testSearchUtility() {
                QueryService qs = new QueryService();
                qs.evaluate("code:KCTOWERS", DataAgency.class, agencyRepository);
	}
}
