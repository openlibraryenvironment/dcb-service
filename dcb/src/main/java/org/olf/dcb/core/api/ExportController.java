package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.export.ExportService;
import org.olf.dcb.export.IngestConfigurationService;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller("/export")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Export API")
public class ExportController {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ExportController.class);

	private final AgencyRepository agencyRepository;
	private final ExportService exportService;
	private final HostLmsRepository hostLmsRepository;
	private final IngestConfigurationService ingestConfigurationService;

	public ExportController(
		AgencyRepository agencyRepository,
		ExportService exportService,
		HostLmsRepository hostLmsRepository,
		IngestConfigurationService ingestConfigurationService
	) {
		this.agencyRepository = agencyRepository;
		this.exportService = exportService;
		this.hostLmsRepository = hostLmsRepository;
		this.ingestConfigurationService = ingestConfigurationService;
	}

	@Get(uri = "/", produces = APPLICATION_JSON)
	public SiteConfiguration export(@Parameter String ids, @Parameter String agencyCodes) {
		SiteConfiguration siteConfiguration = SiteConfiguration.create(); 
		List<String> errors = siteConfiguration.errors;
		List<UUID> idsList = new ArrayList<UUID>();

		// Have we been supplied any host lms ids
		if ((ids != null) && !ids.isBlank()) {
			String[] idsSeparated = ids.split(",");
			for (String id : idsSeparated) {
				try {
					UUID uuid = UUID.fromString(id);
					
					// It must be a valid uuid
					idsList.add(uuid);
				} catch (Exception e) {
					errors.add("Exception thrown converting \"" + id + "\" to a UUID: " + e.toString());
				}
			}
		}
		
		// Have we been supplied any agency codes
		if ((agencyCodes != null) && !agencyCodes.isBlank()) {
			try {
				// We need to convert the codes to host lms ids
				List<String> agencyCodesList = Arrays.asList(agencyCodes.split(","));
				Flux.from(agencyRepository.findHostLmsIdByAgencyCodes(agencyCodesList))
					.map((hostLmsId) -> {
						idsList.add(hostLmsId);
						return(hostLmsId);
					})
					.blockLast();

			} catch (Exception e) {
				errors.add("Exception thrown while trying to convert agency codes to host lms ids: " + e.toString());
			}
		}

		// Did we find any host lms uuids
		if (idsList.isEmpty()) {
			// Nothing has been explicitly specified, so we export everything
			Flux.from(hostLmsRepository.queryAll())
				.map((DataHostLms dataHostLms) -> {
					idsList.add(dataHostLms.id);
					return(dataHostLms);
				})
				.blockLast();
		} else {
			// Do any of these have a parent host lms that does the catalogueing for them
			Flux.from(hostLmsRepository.findParentsByIds(idsList))
			.map((DataHostLms dataHostLms) -> {
				idsList.add(dataHostLms.id);
				return(dataHostLms);
			})
			.blockLast();
		}

		// Is there anything to export
		if (idsList.isEmpty()) {
			errors.add("Failed to determine any libraries to export the configuration for");
		} else {
			// We did so export these host lms and related data
			exportService.export(idsList, siteConfiguration);
		}

		// Finally return the result
		return(siteConfiguration);
	}
	
	@Post("/")
	public IngestResult ingest(@Body SiteConfiguration siteConfiguration) {
		IngestResult ingestResult = new IngestResult();
		if (siteConfiguration == null) {
			ingestResult.messages.add("No site configuration supplied");
		} else {
			Mono.just(siteConfiguration)
				.map((SiteConfiguration siteConfig) -> {
					ingestConfigurationService.ingest(siteConfiguration, ingestResult);
					return(siteConfig);
				})
				.block();
		}
		return(ingestResult);
	}
}
