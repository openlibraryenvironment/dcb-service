package org.olf.dcb.core.interaction.folio;

import java.util.List;

import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.OaiPmhIngestSource;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.storage.RawSourceRepository;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.oaipmh.OaiRecord;
import services.k_int.utils.MapUtils;

@Slf4j
@Prototype
public class FolioOaiPmhIngestSource2 extends OaiPmhIngestSource {
	
	private static final String CONCURRENCY_GROUP_KEY = "folio-oai";
	
	private static final String CONFIG_API_KEY = "apikey";

	private static final String CONFIG_RECORD_SYNTAX = "record-syntax";

	private static final String PARAM_RECORD_SYNTAX = "recordSyntax";

	private static final String UUID5_PREFIX = "ingest-source:folio-oai";

	private final String recordSyntax;
	
	private final String apiKey;

	public FolioOaiPmhIngestSource2(
		@Parameter("hostLms") HostLms hostLms, 
		RawSourceRepository rawSourceRepository, 
		HttpClient client, 
		ConversionService conversionService, 
		ProcessStateService processStateService,
		R2dbcOperations r2dbcOperations,
		ObjectMapper objectMapper
	) {
		super(hostLms, rawSourceRepository, client, conversionService, processStateService, r2dbcOperations, objectMapper);

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
	public Boolean inferSuppression(OaiRecord resource) {
		// FOLIO uses 999$t to infer suppression, evaluate it here and set accordingly
		if ( ( resource == null ) ||
				 ( resource.metadata() == null ) ||
				 ( resource.metadata().record() == null ) )
			return Boolean.FALSE;

			org.marc4j.marc.Record r = resource.metadata().record();
      List<org.marc4j.marc.VariableField> vf = r.getVariableFields("999");
			if ( ( vf.size() == 1 ) && ( vf.get(0) instanceof org.marc4j.marc.DataField) ) {
				org.marc4j.marc.DataField df = (org.marc4j.marc.DataField) vf.get(0);
				org.marc4j.marc.Subfield sft = df.getSubfield('t');
				if ( sft != null ) {
					if ( sft.getData().equalsIgnoreCase("1") ) {
						log.warn("FOLIO 999$t suppression detected");
						return Boolean.TRUE;
					}
					else {
						log.info("FOLIO 999$t detected byt with value {} so not suppressed",sft.getData());
					}
				}
			}
			
			// Default no suppression
			return Boolean.FALSE;
  }

}
