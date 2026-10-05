package org.olf.dcb.core.interaction.folio;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.interaction.OaiPmhResumptionPolicy;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.storage.RawSourceRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
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

	private static final String CONFIG_METADATA_PREFIX = "metadata-prefix";

	private static final String DEFAULT_METADATA_PREFIX = "marc21_withholdings";

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
		super(withDefaultMetadataPrefix(hostLms), rawSourceRepository, client, conversionService, processStateService, r2dbcOperations, objectMapper, objectRulesService, cacheInvalidator, hostLmsService);

		recordSyntax = MapUtils.getAsOptionalString(hostLms.getClientConfig(), CONFIG_RECORD_SYNTAX)
			.filter(StringUtils::hasText)
			.orElseThrow(() -> new IllegalArgumentException(String.format(
				"OAI-PMH ingest for Host LMS \"%s\" requires client config \"%s\"",
				hostLms.getCode(), CONFIG_RECORD_SYNTAX)));
		
		apiKey = MapUtils.getAsOptionalString(
			hostLms.getClientConfig(), CONFIG_API_KEY
		).get();

		setIdentifierSeparator("/");
		setUuid5Prefix(UUID5_PREFIX);
	}

	/**
	 * The FOLIO OAI-PMH prefix that carries holdings, which is what a FOLIO harvest has always
	 * been configured with. The shared base class requires the key and is not the place for a
	 * FOLIO default.
	 */
	private static HostLms withDefaultMetadataPrefix(HostLms hostLms) {
		final var stored = hostLms.getClientConfig();

		if (stored != null && MapUtils.getAsOptionalString(stored, CONFIG_METADATA_PREFIX)
			.filter(StringUtils::hasText).isPresent()) {

			return hostLms;
		}

		final var config = new HashMap<String, Object>();
		if (stored != null) {
			config.putAll(stored);
		}
		config.put(CONFIG_METADATA_PREFIX, DEFAULT_METADATA_PREFIX);

		return new HostLms() {
			@Override public UUID getId() { return hostLms.getId(); }
			@Override public String getCode() { return hostLms.getCode(); }
			@Override public String getName() { return hostLms.getName(); }
			@Override public Class<?> getClientType() { return hostLms.getClientType(); }
			@Override public Class<?> getIngestSourceType() { return hostLms.getIngestSourceType(); }
			@Override public String getSuppressionRulesetName() { return hostLms.getSuppressionRulesetName(); }
			@Override public String getItemSuppressionRulesetName() { return hostLms.getItemSuppressionRulesetName(); }
			@Override public Map<String, Object> getClientConfig() { return config; }
		};
	}

	@Override
	public String getConcurrencyGroupKey() {
		return CONCURRENCY_GROUP_KEY;
	}

	@Override
	protected OaiPmhResumptionPolicy resumptionPolicy() {
		// FOLIO commonly exposes only second-resolution datestamps. Preserve its
		// established clock checkpoint to avoid replaying a potentially full second.
		return OaiPmhResumptionPolicy.INTERNAL_CLOCK;
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
