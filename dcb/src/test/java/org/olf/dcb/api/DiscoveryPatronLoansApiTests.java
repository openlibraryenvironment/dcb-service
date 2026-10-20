package org.olf.dcb.api;

import static java.time.Instant.now;
import static java.util.Collections.emptySet;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.api.serde.PatronLoan;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraItem;
import org.olf.dcb.core.interaction.sierra.SierraItemsAPIFixture;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.security.discovery.PatronAssertion;
import org.olf.dcb.security.discovery.PatronAssertionVerifier;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * What a patron has out through DCB, and what happens when the library that knows cannot be
 * asked.
 *
 * <p>DCB stores no due date. Every date here is read live from the borrowing library at the
 * moment the patron opens their account, so the degradation cases matter as much as the
 * happy one: a member system that is down, or that answers without a date, must still leave
 * the patron a loan they can see and a date the client knows is unknown.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
// The discovery routes exist only with discovery on
@Property(name = "dcb.discovery.enabled", value = "true")
class DiscoveryPatronLoansApiTests {

	private static final String HOST_LMS_CODE = "discovery-loans-tests";
	private static final String ACCESS_TOKEN = "discovery-loans-token";
	private static final String PATRON_LOCAL_ID = "872321";

	private static final Instant DUE = Instant.parse("2026-10-01T23:59:00Z");

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	private SierraItemsAPIFixture sierraItemsAPIFixture;

	/**
	 * The assertion is mocked rather than signed. What is under test here is what DCB does
	 * once it knows which patron is asking; PatronAssertionVerifierTests owns how it comes
	 * to know, and DiscoveryApiSecurityTests owns what happens without one.
	 */
	@MockBean(PatronAssertionVerifier.class)
	PatronAssertionVerifier assertionVerifier() {
		final var verifier = mock(PatronAssertionVerifier.class);

		doAnswer(invocation ->
			new PatronAssertion(HOST_LMS_CODE, PATRON_LOCAL_ID, "a-discovery-service"))
			.when(verifier).verify(any());

		return verifier;
	}

	@BeforeAll
	@SneakyThrows
	void beforeAll(MockServerClient mockServerClient) {
		final var host = "discovery-loans-tests.com";
		final var baseUrl = "https://" + host;

		SierraTestUtils.mockFor(mockServerClient, baseUrl)
			.setValidCredentials("key", "secret", "test-token", 3600);

		sierraItemsAPIFixture = sierraApiFixtureProvider.items(mockServerClient, host);

		TestStaticTokenValidator.add(ACCESS_TOKEN, "a-discovery-service",
			List.of(DISCOVERY_SERVICE));

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, "key", "secret", baseUrl, "item");
	}

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		clusterRecordFixture.deleteAll();
	}

	@Test
	void aLoanCarriesTheDateTheBorrowingLibraryGives() {
		final var itemId = "1000001";

		sierraItemsAPIFixture.mockGetItemById(itemId, SierraItem.builder()
			.id(itemId)
			.barcode("6565750674")
			.statusCode("-")
			.dueDate(DUE)
			.build());

		saveLoanedRequest(itemId, "Dune");

		final var loans = myLoans();

		assertThat(loans, hasSize(1));
		assertThat(loans.get(0).title(), is("Dune"));
		assertThat(loans.get(0).dueDate(), is(DUE));
		assertThat(loans.get(0).dueDateKnown(), is(true));
	}

	@Test
	void aLibraryThatCannotBeAskedStillLeavesTheLoanVisible() {
		// No mock for this item, so the read fails. The patron is holding the book either
		// way, and dropping the row because we could not read a date would hide something
		// they have to return.
		saveLoanedRequest("no-such-item", "Dune Messiah");

		final var loans = myLoans();

		assertThat(loans, hasSize(1));
		assertThat(loans.get(0).title(), is("Dune Messiah"));
		assertThat(loans.get(0).dueDate(), is(nullValue()));
		assertThat(loans.get(0).dueDateKnown(), is(false));
	}

	@Test
	void anItemWithNoDateIsUnknownRatherThanUndated() {
		// Sierra answers, and the status block carries no date. Deliberately
		// indistinguishable at the wire from the case above: both mean "we cannot tell
		// you", and a client rendering a blank cell for one and a date for the other would
		// be inventing the difference.
		final var itemId = "1000002";

		sierraItemsAPIFixture.mockGetItemById(itemId, SierraItem.builder()
			.id(itemId)
			.barcode("6565750675")
			.statusCode("-")
			.build());

		saveLoanedRequest(itemId, "Children of Dune");

		final var loans = myLoans();

		assertThat(loans, hasSize(1));
		assertThat(loans.get(0).dueDateKnown(), is(false));
	}

	@Test
	void onlyWhatIsActuallyOutIsALoan() {
		// A request waiting on the hold shelf is not a loan. The patron has not got it,
		// and each row here costs the member library one live read - so a list that
		// included everything would charge them for rows nobody can answer.
		final var itemId = "1000003";

		sierraItemsAPIFixture.mockGetItemById(itemId, SierraItem.builder()
			.id(itemId)
			.statusCode("-")
			.dueDate(DUE)
			.build());

		saveRequest(itemId, "Waiting for collection", READY_FOR_PICKUP);
		saveRequest(itemId, "Actually out", LOANED);

		assertThat(myLoans().stream().map(PatronLoan::title).toList(),
			contains("Actually out"));
	}

	@Test
	void anotherPatronsLoansAreNotMine() {
		final var itemId = "1000004";

		sierraItemsAPIFixture.mockGetItemById(itemId, SierraItem.builder()
			.id(itemId)
			.statusCode("-")
			.dueDate(DUE)
			.build());

		final var somebodyElse = patronFixture.definePatron("not-me", "home",
			hostLmsFixture.findByCode(HOST_LMS_CODE));

		saveRequestFor(somebodyElse, itemId, "Somebody else's book", LOANED);

		assertThat(myLoans(), is(empty()));
	}

	private List<PatronLoan> myLoans() {
		return client.toBlocking().retrieve(
			HttpRequest.GET("/discovery/loans").bearerAuth(ACCESS_TOKEN),
			Argument.listOf(PatronLoan.class));
	}

	private void saveLoanedRequest(String itemId, String title) {
		saveRequest(itemId, title, LOANED);
	}

	private void saveRequest(String itemId, String title, PatronRequest.Status status) {
		saveRequestFor(thePatron(), itemId, title, status);
	}

	private void saveRequestFor(Patron patron, String itemId, String title,
		PatronRequest.Status status) {

		// The title comes from the cluster record rather than the request, which is what
		// the query joins to - a request with no cluster would report a null title and
		// pass an assertion about the wrong thing.
		final var clusterId = randomUUID();

		clusterRecordFixture.createClusterRecord(ClusterRecord.builder()
			.id(clusterId)
			.title(title)
			.bibs(emptySet())
			.isDeleted(false)
			.dateCreated(now())
			.dateUpdated(now())
			.build());

		patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.bibClusterId(clusterId)
			.patronHostlmsCode(HOST_LMS_CODE)
			.localItemId(itemId)
			.build());
	}

	private Patron thePatron() {
		final var existing = patronFixture.findPatron(HOST_LMS_CODE, PATRON_LOCAL_ID);

		return existing != null
			? existing
			: patronFixture.definePatron(PATRON_LOCAL_ID, "home",
				hostLmsFixture.findByCode(HOST_LMS_CODE));
	}
}
