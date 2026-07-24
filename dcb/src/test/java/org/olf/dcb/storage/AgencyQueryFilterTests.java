package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import services.k_int.data.querying.QueryService;

/**
 * Covers the filter syntax DCB Admin sends to the GraphQL agencies query, which had no
 * test coverage at all.
 *
 * <p>Filtering on an association -- {@code hostLms: <uuid>} -- previously compared the
 * association path itself against the text, which asks the framework to turn a UUID into a
 * DataHostLms and fails with "Invalid bean [<uuid>] for type: class ...DataHostLms". The
 * whole agencies query failed as a result.
 */
@DcbTest
class AgencyQueryFilterTests {

	@Inject
	private PostgresAgencyRepository agencyRepository;

	@Inject
	private QueryService queryService;

	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldFilterAgenciesByHostLmsIdentifier() throws Exception {
		// Arrange
		final var wantedHostLms = hostLmsFixture.createSierraHostLms("wanted-host-lms");
		final var otherHostLms = hostLmsFixture.createSierraHostLms("other-host-lms");

		defineAgency("wanted-agency", wantedHostLms);
		defineAgency("other-agency", otherHostLms);

		// Act
		final var specification = queryService.evaluate(
			"hostLms: " + wantedHostLms.getId(), DataAgency.class);

		final var agencies = manyValuesFrom(
			agencyRepository.findAll(specification, Pageable.from(0, 10)));

		// Assert
		assertThat(agencies, hasSize(1));
		assertThat(agencies.get(0).getContent().stream().map(DataAgency::getCode).toList(),
			contains("wanted-agency"));
	}

	@Test
	void shouldStillFilterAgenciesByAScalarField() throws Exception {
		// Guards against the association handling breaking ordinary field filters.
		final var hostLms = hostLmsFixture.createSierraHostLms("some-host-lms");

		defineAgency("findable-agency", hostLms);
		defineAgency("ignorable-agency", hostLms);

		final var specification = queryService.evaluate("code: findable-agency", DataAgency.class);

		final var agencies = manyValuesFrom(
			agencyRepository.findAll(specification, Pageable.from(0, 10)));

		assertThat(agencies, hasSize(1));
		assertThat(agencies.get(0).getContent().stream().map(DataAgency::getCode).toList(),
			contains("findable-agency"));
	}

	@Test
	void shouldReportAnInvalidIdentifierClearlyRatherThanFailingInsideTheFramework() throws Exception {
		final var specification = queryService.evaluate("hostLms: not-a-uuid", DataAgency.class);

		final var problem = assertThrows(Exception.class,
			() -> manyValuesFrom(agencyRepository.findAll(specification, Pageable.from(0, 10))));

		assertThat(causalChainMessagesOf(problem), containsString("not a valid identifier"));
	}

	/**
	 * The whole chain, not just the root cause: the useful message is the one added on the
	 * way out, and the root cause is only the JDK's "Invalid UUID string".
	 */
	private static String causalChainMessagesOf(Throwable problem) {
		final var messages = new StringBuilder();

		for (Throwable current = problem; current != null && current != current.getCause();
				current = current.getCause()) {

			messages.append(current.getMessage()).append('\n');
		}

		return messages.toString();
	}

	private void defineAgency(String code, org.olf.dcb.core.model.DataHostLms hostLms) {
		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(code)
			.hostLms(hostLms)
			.build());
	}
}
