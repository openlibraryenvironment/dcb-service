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

/**
 * Harvests a Koha catalogue over OAI-PMH.
 *
 * <p>What the Koha has to have set, all in Administration &gt; System preferences &gt;
 * Web services:
 * <ul>
 *   <li><b>OAI-PMH</b> = Enable. Off by default, and while it is off oai.pl answers
 *       every verb with an error rather than a 404, so the harvest fails rather than
 *       returning nothing.</li>
 *   <li><b>OAI-PMH:archiveID</b> - anything site-specific. It is the prefix in the
 *       record identifier <code>&lt;archiveID&gt;:&lt;biblionumber&gt;</code>, which is
 *       why the separator below is ":". The trailing segment is the biblionumber, and
 *       that is the same id KohaHostLmsClient.getItems calls
 *       /api/v1/biblios/{biblio_id}/items with - so an identifier scheme that does not
 *       end in the biblionumber ingests bibs whose items can never be found.</li>
 *   <li><b>OAI-PMH:MaxCount</b> - page size, defaulting to 50. DCB follows resumption
 *       tokens, so this bounds each request rather than the harvest.</li>
 * </ul>
 *
 * <p>No OAI set is needed to harvest the whole catalogue: Koha only joins
 * oai_sets_biblios when the request carries a set, so omitting <code>oai-set</code>
 * lists every biblio. Configure one only to harvest a subset, and remember that
 * membership is materialised - it is populated by OAI-PMH:AutoUpdateSets or by
 * misc/migration_tools/build_oai_sets.pl, so an unbuilt set harvests nothing.
 *
 * <p>Client config read here and by {@link OaiPmhIngestSource}:
 * <ul>
 *   <li><code>base-url</code> (required) - the OPAC origin, e.g.
 *       https://catalogue.example.org. This is <em>not</em> Koha's <code>api-url</code>:
 *       oai.pl is served by the OPAC, while the REST API is commonly reached through
 *       the staff interface, so neither key can stand in for the other.</li>
 *   <li><code>metadata-prefix</code> (required) - "marcxml" on a stock Koha. Only
 *       marcxml and oai_dc exist unless OAI-PMH:ConfFile defines more, and oai_dc
 *       carries no MARC for DCB to ingest. Confirm with
 *       <code>?verb=ListMetadataFormats</code> before assuming "marc21" is available.</li>
 *   <li><code>oai-set</code> (optional) - the setSpec to restrict the harvest to.</li>
 *   <li><code>oai-path</code> (optional) - overrides the path below where the site
 *       fronts Koha with URL rewrites.</li>
 * </ul>
 *
 * <p>Item data is deliberately not requested through OAI (Koha's include_items, which
 * needs an OAI-PMH:ConfFile): availability comes live from the REST API at resolution
 * time, so embedding a snapshot of items in the harvested bib would only age.
 */
@Slf4j
@Prototype
public class KohaOaiPmhIngestSource extends OaiPmhIngestSource {

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
