package org.olf.dcb.core.interaction.koha;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.storage.RawSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.ObjectMapper;
import services.k_int.utils.MapUtils;

@Slf4j
@Prototype
public class KohaOaiPmhIngestSource extends OaiPmhIngestSource {

// WIP Koha OAI
	// Needs OAI-PMH enabled
	// May also need include_items enabled but not sure

	// Allows overriding the OAI path if the Koha instance uses URL rewrites
	private static final String CONFIG_OAI_PATH = "oai-path";
	private static final String DEFAULT_OAI_PATH = "/cgi-bin/koha/oai.pl";

	private static final String UUID5_PREFIX = "ingest-source:koha-oai";

	private final String oaiPath;

	public KohaOaiPmhIngestSource(@Parameter("hostLms") HostLms hostLms,
																RawSourceRepository rawSourceRepository,
																HttpClient client,
																ConversionService conversionService,
																ProcessStateService processStateService,
																R2dbcOperations r2dbcOperations,
																ObjectMapper objectMapper,
																ObjectRulesService objectRulesService,
																RulesetCacheInvalidator cacheInvalidator,
																HostLmsService hostLmsService
	) {
		super(hostLms, rawSourceRepository, client, conversionService, processStateService, r2dbcOperations, objectMapper, objectRulesService, cacheInvalidator, hostLmsService);

		this.oaiPath = MapUtils.getAsOptionalString(
			hostLms.getClientConfig(), CONFIG_OAI_PATH
		).orElse(DEFAULT_OAI_PATH);

		// Koha OAI identifiers are usually formatted as oai:hostname:biblionumber
		// Splitting by ":" ensures we grab the actual biblionumber at the end
		setIdentifierSeparator(":");
		setUuid5Prefix(UUID5_PREFIX);
	}

	@Override
	protected String oaiPath() {
		return oaiPath;
	}
}
