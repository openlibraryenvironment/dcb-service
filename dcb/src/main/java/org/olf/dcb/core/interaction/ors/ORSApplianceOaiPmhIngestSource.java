package org.olf.dcb.core.interaction.ors;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.ObjectMapper;
import java.net.URI;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.storage.RawSourceRepository;
import services.k_int.utils.MapUtils;

@Prototype
public class ORSApplianceOaiPmhIngestSource extends OaiPmhIngestSource {
	private static final String CONFIG_TENANT_ID = "tenant-id";
	private static final String CONFIG_OAI_ENDPOINT_URL = "oai-endpoint-url";
	private static final String UUID5_PREFIX = "ingest-source:ors-appliance-oai";

	private final String oaiPath;

	public ORSApplianceOaiPmhIngestSource(@Parameter("hostLms") HostLms hostLms,
		RawSourceRepository rawSourceRepository,
		HttpClient client,
		ConversionService conversionService,
		ProcessStateService processStateService,
		R2dbcOperations r2dbcOperations,
		ObjectMapper objectMapper,
		ObjectRulesService objectRulesService,
		RulesetCacheInvalidator cacheInvalidator,
		HostLmsService hostLmsService) {

		super(hostLms, rawSourceRepository, client, conversionService,
			processStateService, r2dbcOperations, objectMapper, objectRulesService,
			cacheInvalidator, hostLmsService);

		oaiPath = MapUtils.getAsOptionalString(hostLms.getClientConfig(), CONFIG_OAI_ENDPOINT_URL)
			.map(URI::create)
			.map(uri -> uri.getRawPath()
				+ (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : ""))
			.orElseGet(() -> {
				String tenantId = MapUtils.getAsOptionalString(
					hostLms.getClientConfig(), CONFIG_TENANT_ID
				).orElseThrow(() -> new IllegalArgumentException(
					"ORS-Appliance OAI ingest requires client.oai-endpoint-url or client.tenant-id"));
				return "/ors-appliance/api/V1/public/" + tenantId + "/oai";
			});
		setIdentifierSeparator(":");
		setUuid5Prefix(UUID5_PREFIX);
	}

	@Override
	protected String oaiPath() {
		return oaiPath;
	}
}
