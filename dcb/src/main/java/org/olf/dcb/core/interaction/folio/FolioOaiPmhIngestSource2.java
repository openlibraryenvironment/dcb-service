package org.olf.dcb.core.interaction.folio;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.storage.RawSourceRepository;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.MapUtils;

@Slf4j
public class FolioOaiPmhIngestSource2 extends OaiPmhIngestSource {
	
	private static final String CONCURRENCY_GROUP_KEY = "folio-oai";
	
	private static final String CONFIG_API_KEY = "apikey";

	private static final String CONFIG_RECORD_SYNTAX = "record-syntax";

	private static final String PARAM_RECORD_SYNTAX = "recordSyntax";

	private static final String UUID5_PREFIX = "ingest-source:folio-oai";

	private static final String DEFAULT_SUPPRESSION_RULESET_NAME = "folio-default";

	private final String recordSyntax;
	
	private final String apiKey;

	FolioOaiPmhIngestSource2(
		HostLms hostLms,
		RawSourceRepository rawSourceRepository,
		HttpClient client,
		ConversionService conversionService,
		ProcessStateService processStateService,
		R2dbcOperations r2dbcOperations,
		ObjectMapper objectMapper,
		ObjectRulesService objectRulesService,
		RulesetCacheInvalidator cacheInvalidator, HostLmsService hostLmsService
	) {
		super(hostLms, rawSourceRepository, client, conversionService, processStateService, r2dbcOperations, objectMapper, objectRulesService, cacheInvalidator, hostLmsService);

		recordSyntax = MapUtils.getAsOptionalString(
			hostLms.getClientConfig(), CONFIG_RECORD_SYNTAX
		).get();
		
		apiKey = MapUtils.getAsOptionalString(
			hostLms.getClientConfig(), CONFIG_API_KEY
		).get();

		setIdentifierSeparator("/");
		setUuid5Prefix(UUID5_PREFIX);
	}

	@Override
	public String getConcurrencyGroupKey() {
		return CONCURRENCY_GROUP_KEY;
	}

	@Override
	protected void additionalQueryParameters(UriBuilder params) {
		// We need to add the record syntax
		params.queryParam(PARAM_RECORD_SYNTAX, recordSyntax);
	}

	@Override
	protected String getAuthorizationContent() {
		return(apiKey);
	}
	
	@Override
	protected Mono<String> getSuppressionRulesetName() {
		// Use the specified name if one exists
		return super.getSuppressionRulesetName()
			.defaultIfEmpty(DEFAULT_SUPPRESSION_RULESET_NAME);
	}
}
