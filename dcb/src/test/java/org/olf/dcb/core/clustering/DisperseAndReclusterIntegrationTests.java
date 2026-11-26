package org.olf.dcb.core.clustering;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.marc4j.marc.MarcFactory;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.audit.ProcessAuditService;
import org.olf.dcb.core.interaction.koha.KohaHostLmsClient;
import org.olf.dcb.core.interaction.koha.KohaOaiPmhIngestSource;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.dataimport.job.model.SourceRecord.ProcessingStatus;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.ingest.conversion.SourceToIngestRecordConverter;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import services.k_int.interaction.oaipmh.OaiRecord;

@MicronautTest(transactional = false, startApplication = false,
	environments = "recluster-integration", deduceEnvironment = false,
	contextBuilder = DisperseAndReclusterIntegrationTests.ContextBuilder.class)
@TestInstance(PER_CLASS)
class DisperseAndReclusterIntegrationTests implements TestPropertyProvider {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Inject private RecordClusteringService clustering;
	@Inject private BibRecordService bibRecords;
	@Inject private SourceRecordService sources;
	@Inject private HostLmsRepository hosts;
	@Inject private HostLmsService hostService;
	@Inject private BibRepository bibs;
	@Inject private ProcessAuditService audit;
	@Inject private R2dbcReactorTransactionOperations transactions;
	@Inject private ObjectMapper objectMapper;

	public static class ContextBuilder extends DcbTestContainerContextBuilder {
		public ContextBuilder() {
			// Enable persisted source records while keeping the ingest job out of this context.
			deduceEnvironment(false);
			exclude("org.olf.dcb.ingest.job");
		}
	}

	@Override
	public Map<String, String> getProperties() {
		return Map.of(
			"dcb.scheduled-tasks.enabled", "false",
			"r2dbc.datasources.default.options.initialSize", "0",
			"r2dbc.datasources.default.options.maxSize", "8");
	}

	@Test
	void reprocessingKeepsMatchingBibsTogetherWhileThePrimaryWouldStillBeInFlight() throws Exception {
		var host = createHost();
		var firstSource = source(host.getId(), "1");
		var secondSource = source(host.getId(), "2");

		// First establish the match without overlapping the two initial ingest operations.
		saveAndIngest(firstSource);
		saveAndIngest(secondSource);
		var first = bib(host.getId(), firstSource);
		var second = bib(host.getId(), secondSource);
		var clusterId = clusterId(first);
		assertEquals(clusterId, clusterId(second), "The source records must match before dispersion");

		var cluster = transactions.withTransactionMono(status -> clustering.findById(clusterId)).block(TIMEOUT);
		assertNotNull(cluster);
		boolean firstIsPrimary = first.getId().equals(cluster.getSelectedBib());
		var primary = firstIsPrimary ? first : second;
		var primarySource = firstIsPrimary ? firstSource : secondSource;
		var sibling = firstIsPrimary ? second : first;
		var siblingSource = firstIsPrimary ? secondSource : firstSource;
		assertEquals(cluster.getSelectedBib(), primary.getId());

		// Simulate an upgrade from an earlier clustering version, leaving the existing points in place.
		Mono.from(bibs.update(primary.setProcessVersion(IngestService.getProcessVersion() - 1))).block(TIMEOUT);
		assertNotEquals(IngestService.getProcessVersion(), bib(host.getId(), primarySource).getProcessVersion());
		assertEquals(clusterId, clustering.disperseAndRecluster(clusterId).block(TIMEOUT));
		assertEquals(ProcessingStatus.PROCESSING_REQUIRED, processingState(siblingSource));
		assertNull(Mono.from(bibs.findContributesToById(sibling.getId())).block(TIMEOUT),
			"The sibling must be detached before reprocessing");

		reprocessWithPrimaryCommitHeld(primarySource, siblingSource);

		assertEquals(ProcessingStatus.SUCCESS, processingState(primarySource));
		assertEquals(ProcessingStatus.SUCCESS, processingState(siblingSource));
		assertEquals(IngestService.getProcessVersion(), bib(host.getId(), primarySource).getProcessVersion());
		assertEquals(clusterId, clusterId(bib(host.getId(), primarySource)));
		assertEquals(clusterId, clusterId(bib(host.getId(), siblingSource)),
			() -> "The sibling must rejoin the retained primary's cluster. Ingest audit: "
				+ audit.getProcessAudits(sibling.getId()).map(entry -> entry.getMessage()).collectList().block(TIMEOUT));
	}

	private void reprocessWithPrimaryCommitHeld(SourceRecord primary, SourceRecord sibling) throws Exception {
		if (processingState(primary) != ProcessingStatus.PROCESSING_REQUIRED) {
			processSource(sibling, Mono.empty()).block(TIMEOUT);
			return;
		}

		// The original dispersion queues the stale primary. Let it choose its cluster,
		// then let the sibling finish while the primary's changes are still uncommitted.
		var primaryClustered = Sinks.<Void>one();
		var releasePrimary = Sinks.<Void>one();
		var primaryIngest = processSource(primary, Mono.defer(() -> {
			primaryClustered.tryEmitEmpty();
			return releasePrimary.asMono();
		})).toFuture();
		try {
			primaryClustered.asMono().block(TIMEOUT);
			processSource(sibling, Mono.empty()).block(TIMEOUT);
		} finally {
			releasePrimary.tryEmitEmpty();
			primaryIngest.get(TIMEOUT.toSeconds(), SECONDS);
		}
	}

	private DataHostLms createHost() {
		return Mono.from(hosts.save(DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("recluster-" + UUID.randomUUID().toString().substring(0, 8))
			.name("Reclustering integration test")
			.lmsClientClass(KohaHostLmsClient.class.getName())
			.ingestSourceClass(KohaOaiPmhIngestSource.class.getName())
			.clientConfig("base-url", "https://recluster.invalid")
			.clientConfig("metadata-prefix", "marcxml")
			.build())).block(TIMEOUT);
	}

	private SourceRecord source(UUID hostId, String bibNumber) throws Exception {
		var marc = MarcFactory.newInstance();
		var record = marc.newRecord("00000nam a2200000 a 4500");
		record.addVariableField(marc.newControlField("001", bibNumber));
		var lccn = "25%06d".formatted(Math.floorMod(hostId.hashCode(), 1_000_000));
		record.addVariableField(marc.newDataField("010", ' ', ' ', "a", lccn));
		record.addVariableField(marc.newDataField("245", '0', '0', "a", lccn + " Reclustering transaction visibility"));
		var now = Instant.now();
		var remoteId = "oai:recluster.invalid:" + bibNumber;
		var oai = new OaiRecord(new OaiRecord.Header(remoteId, now, null, null), new OaiRecord.Metadata(record));
		return SourceRecord.builder()
			.id(SourceRecord.generateSourceRecordId(hostId, remoteId))
			.hostLmsId(hostId)
			.remoteId(remoteId)
			.lastFetched(now)
			.sourceRecordData(objectMapper.writeValueToTree(oai))
			.build();
	}

	private void saveAndIngest(SourceRecord source) {
		transactions.withTransactionMono(status -> sources.save(source)).block(TIMEOUT);
		processSource(source, Mono.empty()).block(TIMEOUT);
		assertEquals(ProcessingStatus.SUCCESS, processingState(source));
	}

	private Mono<Void> processSource(SourceRecord source, Mono<Void> beforeCommit) {
		return transactions.withTransactionMono(status -> sources.getByLocalId(source.getId())
			.flatMap(saved -> hostService.findById(saved.getHostLmsId())
				.flatMap(hostService::getIngestSourceFor)
				.cast(SourceToIngestRecordConverter.class)
				.flatMap(converter -> converter.convertSourceToIngestRecord(saved)))
			.flatMap(bibRecords::process)
			.flatMap(clustering::clusterBib)
			.switchIfEmpty(Mono.error(() -> new IllegalStateException("No clustered bib for " + source.getRemoteId())))
			.then(sources.updateProcessingInformation(source.getId(), Instant.now(), ProcessingStatus.SUCCESS))
			.then(beforeCommit)
			.transform(ProcessAuditService.withNewProcessAudit("ingest")));
	}

	private ProcessingStatus processingState(SourceRecord source) {
		var saved = transactions.withTransactionMono(status -> sources.getByLocalId(source.getId())).block(TIMEOUT);
		assertNotNull(saved);
		return saved.getProcessingState();
	}

	private BibRecord bib(UUID hostId, SourceRecord source) {
		var remoteId = source.getRemoteId();
		var bib = Mono.from(bibs.findBySourceSystemIdAndSourceRecordId(hostId,
			remoteId.substring(remoteId.lastIndexOf(':') + 1))).block(TIMEOUT);
		assertNotNull(bib);
		return bib;
	}

	private UUID clusterId(BibRecord bib) {
		var cluster = Mono.from(bibs.findContributesToById(bib.getId())).block(TIMEOUT);
		assertNotNull(cluster);
		return cluster.getId();
	}
}
