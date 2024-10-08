package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.export.ExportService;
import org.olf.dcb.export.IngestConfigurationService;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.security.RoleNames;
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
import reactor.core.publisher.Mono;

@Controller("/export")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Export API")
public class ExportController {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ExportController.class);

	private final ExportService exportService;
	private final IngestConfigurationService ingestConfigurationService;

	public ExportController(
		ExportService exportService,
		IngestConfigurationService ingestConfigurationService
	) {
		this.exportService = exportService;
		this.ingestConfigurationService = ingestConfigurationService;
	}

	@Get(uri = "/", produces = APPLICATION_JSON)
	public Mono<SiteConfiguration> export(@Parameter String ids) {
		SiteConfiguration siteConfiguration = SiteConfiguration.create(); 
		List<String> errors = siteConfiguration.errors;
		
		if (ids == null) {
			errors.add("No ids supplied to perform export");
		} else {
			List<UUID> idsList = new ArrayList<UUID>();
			String[] idsSeparated = ids.split(",");
			for (String id : idsSeparated) {
				try {
					UUID uuid = UUID.fromString(id);
					
					// It must be a vakid uuid
					idsList.add(uuid);
				} catch (Exception e) {
					errors.add("Exception thrown converting \"" + id + "\" to a UUID: " + e.toString());
				}
			}
			
			// Did we find any uuids
			if (idsList.isEmpty()) {
				errors.add("No valid UUID ids supplied");
			} else {
				// We did so export these host lms and related data
				exportService.export(idsList, siteConfiguration);
			}
		}

		// Finally return the result
		return(Mono.just(siteConfiguration));
	}
	
	@Post("/")
	public Mono<IngestResult> ingest(@Body SiteConfiguration siteConfiguration) {
		IngestResult ingestResult = new IngestResult();
		if (siteConfiguration == null) {
			ingestResult.messages.add("No site configuration supplied");
		} else {
			return(Mono.just(siteConfiguration)
				.flatMap(siteConfig -> {
					ingestConfigurationService.ingest(siteConfiguration, ingestResult);
					return(Mono.just(ingestResult));
			}));
//			.block());
//		return(Mono.just(ingestResult));
		}
		return(Mono.just(ingestResult));
	}
}
