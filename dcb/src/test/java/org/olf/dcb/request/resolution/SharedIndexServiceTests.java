package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService.UnknownHostLmsException;
import org.olf.dcb.test.BibRecordFixture;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class SharedIndexServiceTests {
	@Inject
	private SharedIndexService sharedIndexService;

	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@Inject
	private BibRecordFixture bibRecordFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
		hostLmsFixture.deleteAll();
	}

	@AfterEach
	void afterEach() {
		bibRecordFixture.deleteAllBibRecords();
		clusterRecordFixture.deleteAllClusterRecords();
		hostLmsFixture.deleteAll();
	}

	@Test
	void canFindClusterRecordWithAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		final var firstBibRecordId = randomUUID();
		final var secondBibRecordId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var firstHostLms = hostLmsFixture.createSierraHostLms("FIRST-HOST-LMS");
		final var secondHostLms = hostLmsFixture.createSierraHostLms("SECOND-HOST-LMS");

		final var firstHostLmsId = firstHostLms.getId();
		final var secondHostLmsId = secondHostLms.getId();

		bibRecordFixture.createBibRecord(firstBibRecordId, secondHostLmsId,
			"798472", clusterRecord);

		bibRecordFixture.createBibRecord(secondBibRecordId, firstHostLmsId,
			"896857", clusterRecord);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib.getId(), is(clusterRecordId));

		assertThat("Should have multiple bib records",
			clusteredBib.getBibs(), containsInAnyOrder(
				allOf(
					hasId(firstBibRecordId),
					hasSourceRecordId("798472"),
					hasHostLmsCode("SECOND-HOST-LMS")
				),
				allOf(
					hasProperty("id", is(secondBibRecordId)),
					hasSourceRecordId("896857"),
					hasHostLmsCode("FIRST-HOST-LMS")
				)
			));
	}

	@Test
	void canFindClusterRecordWithNoAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		clusterRecordFixture.createClusterRecord(clusterRecordId);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib.getId(), is(clusterRecordId));

		final var bibs = clusteredBib.getBibs();

		assertThat(bibs, is(notNullValue()));
		assertThat(bibs, hasSize(0));
	}

	@Test
	void failsWhenCannotFindClusterRecordById() {
		final var clusterRecordId = randomUUID();

		final var exception = assertThrows(CannotFindClusterRecordException.class,
			() -> sharedIndexService.findClusteredBib(clusterRecordId).block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is(
			"Cannot find cluster record for: " + clusterRecordId));
	}

	@Test
	void failsWhenCannotFindHostLmsForBib() {
		final var clusterRecordId = randomUUID();
		final var bibRecordId = randomUUID();
		final var unknownHostId = randomUUID();

		final var clusterRecord = clusterRecordFixture.createClusterRecord(clusterRecordId);

		bibRecordFixture.createBibRecord(bibRecordId, unknownHostId,
			"7657673", clusterRecord);

		final var exception = assertThrows(UnknownHostLmsException.class,
			() -> sharedIndexService.findClusteredBib(clusterRecordId).block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is(
			"No Host LMS found for ID: " + unknownHostId));
	}

	private static Matcher<Bib> hasId(UUID expectedId) {
		return hasProperty("id", is(expectedId));
	}

	private static Matcher<Bib> hasSourceRecordId(String expectedId) {
		return hasProperty("sourceRecordId", is(expectedId));
	}

	private static Matcher<Bib> hasHostLmsCode(String expectedCode) {
		return hasProperty("hostLms",
			hasProperty("code", is(expectedCode)));
	}
}
