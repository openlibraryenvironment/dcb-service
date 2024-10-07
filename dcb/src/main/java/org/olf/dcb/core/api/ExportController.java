package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.export.ExportService;
import org.olf.dcb.security.RoleNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/export")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Export API")
public class ExportController {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ExportController.class);

	private final ExportService exportService;

	public ExportController(ExportService exportService) {

		this.exportService = exportService;
	}

	@Get(uri = "/", produces = APPLICATION_JSON)
	public Map<String, Object> export(@Parameter String ids) {
		Map<String, Object> result = new HashMap<String, Object>(); 
		List<String> errors = new ArrayList<String>();
		result.put("errors", errors);
		
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
				result = exportService.export(idsList, result, errors);
			}
		}

		// Finally return the result
		return(result);
	}
}
