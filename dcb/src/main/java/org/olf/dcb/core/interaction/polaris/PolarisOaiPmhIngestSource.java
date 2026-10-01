package org.olf.dcb.core.interaction.polaris;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.UUID5_PREFIX;

import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.storage.RawSourceRepository;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import services.k_int.interaction.oaipmh.OaiRecord;
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
}
