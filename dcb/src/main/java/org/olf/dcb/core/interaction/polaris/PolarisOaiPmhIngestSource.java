package org.olf.dcb.core.interaction.polaris;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.UUID5_PREFIX;

import java.time.Instant;
import java.util.BitSet;
import java.util.OptionalInt;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.dataimport.job.VanishedRecords;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.storage.RawSourceRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.oaipmh.OaiRecord;
import services.k_int.interaction.oaipmh.OaiRecord.Header;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

/**
 * Harvests Polaris bibs over its OAI-PMH data provider instead of the PAPI Synch_Bibs* walk.
 * Selected per Host LMS by setting ingest_source_class to this class; circulation stays on
 * PolarisLmsClient. Configuration and switch-over: docs/polaris_notes.md
 */
@Prototype
public class PolarisOaiPmhIngestSource extends OaiPmhIngestSource {
	private static final String CONFIG_OAI_PATH = "oai-path";
	private static final String DEFAULT_OAI_PATH = "/polaris.oaipmh.dataprovider/polaris/bibliographic";

	private static final int VANISHED_SAMPLE_SIZE = 20;

	// A row the PAPI harvest wrote has no OAI identifier; this one ends in its bib id like a real one
	private static final String PAPI_ROW_IDENTIFIER_PREFIX = "papi:";

	private final String hostLmsCode;
	private final String oaiPath;

	public PolarisOaiPmhIngestSource(@Parameter("hostLms") HostLms hostLms,
		RawSourceRepository rawSourceRepository,
		HttpClient client,
		ConversionService conversionService,
		ProcessStateService processStateService,
		R2dbcOperations r2dbcOperations,
		ObjectMapper objectMapper,
		ObjectRulesService objectRulesService,
		RulesetCacheInvalidator cacheInvalidator,
		HostLmsService hostLmsService) {

		super(hostLms, rawSourceRepository, client, conversionService, processStateService,
			r2dbcOperations, objectMapper, objectRulesService, cacheInvalidator, hostLmsService);

		this.hostLmsCode = hostLms.getCode();
		this.oaiPath = MapUtils.getAsOptionalString(hostLms.getClientConfig(), CONFIG_OAI_PATH)
			.orElse(DEFAULT_OAI_PATH);

		setIdentifierSeparator(":");
		setUuid5Prefix(UUID5_PREFIX);
	}

	@Override
	protected String oaiPath() {
		return oaiPath;
	}

	// Keyed exactly as PolarisLmsClient keys a PAPI bib, so switching a host that has already
	// harvested over PAPI updates its bib records in place rather than duplicating them.
	@Override
	public UUID uuid5ForOAIResult(@NotNull final OaiRecord result) {
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
			UUID5_PREFIX + ":" + hostLmsCode + ":" + extractRecordId(result));
	}

	/**
	 * Costs one ListIdentifiers walk plus one GetRecord per missing bib, one request at a time.
	 */
	@Override
	public Flux<SourceRecord> findMissingRecords(Publisher<String> knownRemoteIds) {
		return Flux.from(knownRemoteIds)
			// reduceWith, not reduce: the accumulator must be created per subscription.
			.reduceWith(BitSet::new, PolarisOaiPmhIngestSource::markBib)
			.flatMapMany(held -> {
				final Instant fetchedAt = Instant.now();

				return listAllIdentifiers()
					.filter(header -> isLive(header) && !contains(held, header.identifier()))
					.concatMap(header -> getRecord(header.identifier()))
					.concatMap(record -> sourceRecordFor(record, fetchedAt));
			});
	}

	/**
	 * A bib vanishes when it is held but not listed as live, so one the provider reports as deleted
	 * vanishes too. Every stored row for it is replaced, the PAPI row as well as the OAI one, so
	 * neither can bring the bib back when it is reprocessed.
	 */
	@Override
	public Mono<VanishedRecords> findVanishedRecords(Publisher<String> liveSourceRecordIds,
		Publisher<String> storedRemoteIds) {

		return Flux.from(liveSourceRecordIds)
			.reduceWith(BitSet::new, PolarisOaiPmhIngestSource::markBib)
			.flatMap(held -> listAllIdentifiers()
				.filter(PolarisOaiPmhIngestSource::isLive)
				.map(Header::identifier)
				.reduceWith(BitSet::new, PolarisOaiPmhIngestSource::markBib)
				.map(listed -> {
					final BitSet vanished = (BitSet) held.clone();
					vanished.andNot(listed);

					final Instant at = Instant.now();

					return new VanishedRecords(held.cardinality(), vanished.cardinality(),
						vanished.stream().limit(VANISHED_SAMPLE_SIZE).mapToObj(String::valueOf).toList(),
						Flux.from(storedRemoteIds)
							.filter(remoteId -> contains(vanished, remoteId))
							.concatMap(remoteId -> deletedSourceRecord(remoteId, identifierFor(remoteId), at)));
				}));
	}

	private static BitSet markBib(BitSet bibs, String id) {
		bibIdOf(id).ifPresent(bibs::set);
		return bibs;
	}

	private static boolean contains(BitSet bibs, String id) {
		final OptionalInt bibId = bibIdOf(id);
		return bibId.isPresent() && bibs.get(bibId.getAsInt());
	}

	// "oai:<host>:12345" from the OAI harvest; "12345" from the PAPI harvest or a bib record
	private static OptionalInt bibIdOf(String id) {
		try {
			final int bibId = Integer.parseInt(id.substring(id.lastIndexOf(':') + 1).trim());
			return bibId > 0 ? OptionalInt.of(bibId) : OptionalInt.empty();
		}
		catch (NumberFormatException e) {
			return OptionalInt.empty();
		}
	}

	private static boolean isLive(Header header) {
		return !"deleted".equalsIgnoreCase(header.status());
	}

	private static String identifierFor(String remoteId) {
		return remoteId.contains(":") ? remoteId : PAPI_ROW_IDENTIFIER_PREFIX + remoteId;
	}
}
