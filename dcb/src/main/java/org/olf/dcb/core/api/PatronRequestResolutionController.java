package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItem;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItems;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.ResolutionParameters;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Controller("/patrons/requests/resolution")
@Validated
@Secured(IS_ANONYMOUS)
@Tag(name = "Patron Request Resolution API")
public class PatronRequestResolutionController {
	@Inject
	private PatronRequestResolutionService patronRequestResolutionService;

	@Operation(
		summary = "Patron Request Resolution Preview",
		description = "Preview resolution for a given set of parameters"
	)
	@SingleResult
	@Post(value = "/preview", produces = APPLICATION_JSON, consumes = APPLICATION_JSON)
	public Mono<ResolutionPreview> previewResolution(@Body @Valid ResolutionParameters parameters) {
		// Using the same class as the service is a compromise that avoids additional mapping
		// whilst exposing the structure in an external API, making unintentional changes more likely
		log.debug("previewResolution({})", parameters);

		return patronRequestResolutionService.resolve(parameters)
			.map(resolution -> ResolutionPreview.builder()
				.itemWasSelected(getValue(resolution, Resolution::successful, false))
				.selectedItem(toPresentableItem(getValueOrNull(resolution, Resolution::getChosenItem)))
				.allItemsFromAvailability(toPresentableItems(getValueOrNull(resolution, Resolution::getAllItems)))
				.filteredItems(toPresentableItems(getValueOrNull(resolution, Resolution::getFilteredItems)))
				.sortedItems(toPresentableItems(getValueOrNull(resolution, Resolution::getSortedItems)))
				.build());

	}
}
