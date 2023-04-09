package org.olf.reshare.dcb.request.resolution;

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService.UnknownHostLmsException;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.ClusterRecordRepository;
import org.olf.reshare.dcb.storage.HostLmsRepository;
import org.olf.reshare.dcb.test.DcbTest;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@DcbTest
class SharedIndexServiceTests {
	@Inject
	private SharedIndexService sharedIndexService;
	@Inject
	private ClusterRecordRepository clusterRecordRepository;
	@Inject
	private BibRepository bibRepository;
	@Inject
	private HostLmsRepository hostLmsRepository;

	@Test
	void canFindClusterRecordWithAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		final var firstHostLmsId = randomUUID();
		final var secondHostLmsId = randomUUID();

		final var firstBibRecordId = randomUUID();
		final var secondBibRecordId = randomUUID();

		final var clusterRecord = createClusterRecord(clusterRecordId);

		createHostLms(firstHostLmsId, "FIRST-HOST-LMS");
		createHostLms(secondHostLmsId, "SECOND-HOST-LMS");

		createBibRecord(firstBibRecordId, secondHostLmsId, "798472", clusterRecord);
		createBibRecord(secondBibRecordId, firstHostLmsId, "896857", clusterRecord);

		final var clusteredBib = sharedIndexService
			.findClusteredBib(clusterRecordId).block();

		assertThat(clusteredBib, is(notNullValue()));
		assertThat(clusteredBib.getId(), is(clusterRecordId));

		final var bibs = clusteredBib.getBibs();

		assertThat(bibs, is(notNullValue()));
		assertThat(bibs, hasSize(2));

		final var firstBib = bibs.get(0);

		assertThat(firstBib, is(notNullValue()));
		assertThat(firstBib.getId(), is(firstBibRecordId));
		assertThat(firstBib.getBibRecordId(), is("798472"));

		assertThat(firstBib.getHostLms(), is(notNullValue()));
		assertThat(firstBib.getHostLms().getCode(), is("SECOND-HOST-LMS"));

		final var secondBib = bibs.get(1);

		assertThat(secondBib, is(notNullValue()));
		assertThat(secondBib.getId(), is(secondBibRecordId));
		assertThat(secondBib.getBibRecordId(), is("896857"));

		assertThat(secondBib.getHostLms(), is(notNullValue()));
		assertThat(secondBib.getHostLms().getCode(), is("FIRST-HOST-LMS"));
	}

	@Test
	void canFindClusterRecordWithNoAssociatedBibs() {
		final var clusterRecordId = randomUUID();

		createClusterRecord(clusterRecordId);

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

		final var clusterRecord = createClusterRecord(clusterRecordId);

		createBibRecord(bibRecordId, unknownHostId, "7657673", clusterRecord);

		final var exception = assertThrows(UnknownHostLmsException.class,
			() -> sharedIndexService.findClusteredBib(clusterRecordId).block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is(
			"No Host LMS found for ID: " + unknownHostId));
	}

	private ClusterRecord createClusterRecord(UUID clusterRecordId) {
		return Mono.from(clusterRecordRepository.save(new ClusterRecord(clusterRecordId,
			now(), now(), "Brain of the Firm", new java.util.HashSet()))).block();
	}

	private void createHostLms(UUID id, String code) {
		Mono.from(hostLmsRepository.save(new DataHostLms(id, code,
			"Test Host LMS", "", Map.of()))).block();
	}

	private void createBibRecord(UUID bibRecordId, UUID sourceSystemId,
		String sourceRecordId, ClusterRecord clusterRecord) {

		Mono.from(bibRepository.save(new BibRecord(bibRecordId, now(), now(),
			sourceSystemId, sourceRecordId, "Brain of the Firm", clusterRecord, "", "")))
			.block();
	}
}
