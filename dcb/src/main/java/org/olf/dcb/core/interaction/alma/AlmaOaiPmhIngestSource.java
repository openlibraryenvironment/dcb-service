package org.olf.dcb.core.interaction.alma;

import org.olf.dcb.core.ProcessStateService;
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

@Prototype
public class AlmaOaiPmhIngestSource extends OaiPmhIngestSource {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AlmaOaiPmhIngestSource.class);
	
	private static final String CONFIG_INSTITUTION_CODE = "institution-code";
	
	private static final String UUID5_PREFIX = "ingest-source:alma-oai";

	private final String oaiPath;
	
	public AlmaOaiPmhIngestSource(@Parameter("hostLms") HostLms hostLms, 
		RawSourceRepository rawSourceRepository, 
		HttpClient client, 
		ConversionService conversionService, 
		ProcessStateService processStateService,
		R2dbcOperations r2dbcOperations,
		ObjectMapper objectMapper,
		ObjectRulesService objectRulesService
	) {
		super(hostLms, rawSourceRepository, client, conversionService, processStateService, r2dbcOperations, objectMapper, objectRulesService);
		
		String institutionCode = MapUtils.getAsOptionalString(
			hostLms.getClientConfig(), CONFIG_INSTITUTION_CODE
		).get();

		oaiPath = "/view/oai/" + institutionCode + "/request";
		setIdentifierSeparator(":");
		setUuid5Prefix(UUID5_PREFIX);
	}

	@Override
	protected String oaiPath() {
		return(oaiPath);
	}
}
