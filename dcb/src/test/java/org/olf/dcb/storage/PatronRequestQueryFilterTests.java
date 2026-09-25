package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import services.k_int.data.querying.QueryService;

/**
 * The filter syntax a drill-down link sends to the patron requests query.
 *
 * <p>Insights panels link into the request grid with a composed filter, and the frontend
 * cannot tell a filter the parser rejects from one that legitimately matches nothing - both
 * arrive as an empty grid. Every field a panel composes on is therefore asserted here
 * rather than assumed, and the one that needed the fix is {@code bibClusterId}: a plain
 * {@code UUID} column, compared by {@link services.k_int.data.querying.lucene
 * .LuceneFieldQueryNodeBuilder} against the query's TEXT. Nothing converts it, so the
 * comparison is UUID against String.
 */
@DcbTest
class PatronRequestQueryFilterTests {

	@Inject
	private PostgresPatronRequestRepository patronRequestRepository;

	@Inject
	private QueryService queryService;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldFilterByBibClusterIdentifier() throws Exception {
		// Arrange
		final var wantedCluster = randomUUID();

		define(wantedCluster, "wanted", PatronRequest.Status.SUBMITTED_TO_DCB);
		define(randomUUID(), "other", PatronRequest.Status.SUBMITTED_TO_DCB);

		// Act
		final var found = evaluate("bibClusterId: " + wantedCluster);

		// Assert
		assertThat(found, contains("wanted"));
	}

	@Test
	void shouldFilterByStatus() throws Exception {
		define(randomUUID(), "errored", PatronRequest.Status.ERROR);
		define(randomUUID(), "fine", PatronRequest.Status.SUBMITTED_TO_DCB);

		assertThat(evaluate("status: \"ERROR\""), contains("errored"));
	}

	@Test
	void shouldFilterByPatronHostLmsCode() throws Exception {
		define(randomUUID(), "wanted", PatronRequest.Status.SUBMITTED_TO_DCB, "wanted-lms");
		define(randomUUID(), "other", PatronRequest.Status.SUBMITTED_TO_DCB, "other-lms");

		assertThat(evaluate("patronHostlmsCode: \"wanted-lms\""), contains("wanted"));
	}

	/**
	 * The composition a drill-down actually sends: the tab's own preset, ANDed with the
	 * panel's filter. A parser that accepts each half separately can still reject the pair.
	 */
	@Test
	void shouldFilterByAPresetComposedWithAPanelFilter() throws Exception {
		final var wantedCluster = randomUUID();

		define(wantedCluster, "wanted", PatronRequest.Status.ERROR);
		define(wantedCluster, "wrong-status", PatronRequest.Status.SUBMITTED_TO_DCB);
		define(randomUUID(), "wrong-cluster", PatronRequest.Status.ERROR);

		final var found = evaluate(
			"status: \"ERROR\" AND (bibClusterId: %s)".formatted(wantedCluster));

		assertThat(found, hasSize(1));
		assertThat(found, contains("wanted"));
	}

	private void define(UUID clusterId, String description, PatronRequest.Status status) {
		define(clusterId, description, status, "a-host-lms");
	}

	private void define(UUID clusterId, String description, PatronRequest.Status status,
		String patronHostLmsCode) {

		patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.bibClusterId(clusterId)
			.description(description)
			.status(status)
			.patronHostlmsCode(patronHostLmsCode)
			.build());
	}

	private java.util.List<String> evaluate(String query) throws Exception {
		final var specification = queryService.evaluate(query, PatronRequest.class);

		final var page = manyValuesFrom(
			patronRequestRepository.findAll(specification, Pageable.from(0, 100)));

		return page.get(0).getContent().stream()
			.map(PatronRequest::getDescription)
			.toList();
	}
}
