package org.olf.dcb.core.api;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.INTEROP_TESTER;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItem;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItems;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.ResolutionParameters;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Resolution preview: "which item would DCB choose for these parameters, and why".
 *
 * Staff and implementation tooling only. It was @Secured(IS_ANONYMOUS), which was not
 * benign on two counts:
 *
 *   - it runs FULL resolution, which makes live availability calls out to member LMS
 *     APIs. An unauthenticated POST was therefore an amplification vector into
 *     libraries we do not own, and one anybody could aim.
 *   - it returns allItemsFromAvailability / filteredItems / sortedItems, i.e.
 *     item-level holdings across the consortium, to whoever asked.
 *
 * INTEROP_TESTER is included because diagnosing "why did it pick that item" is exactly
 * what implementation testing needs, matching ImplementationToolsController.
 */
@Slf4j
@Controller("/patrons/requests/resolution")
@Validated
@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN, INTEROP_TESTER})
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

	@Error
	public HttpResponse<String> onError(Exception exception) {
		return badRequest(getValueOrNull(exception, Exception::getMessage));
	}
}
