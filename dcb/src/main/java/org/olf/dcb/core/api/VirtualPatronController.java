package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.interaction.VirtualPatronRemoval;
import org.olf.dcb.request.fulfilment.VirtualPatronRemovalRefused;
import org.olf.dcb.request.fulfilment.VirtualPatronRemovalResult;
import org.olf.dcb.request.fulfilment.VirtualPatronRemovalService;
import org.olf.dcb.security.CallerScope;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Controller("/patrons")
@Validated
@Tag(name = "Virtual Patron API")
public class VirtualPatronController {
	private final VirtualPatronRemovalService virtualPatronRemovalService;

	public VirtualPatronController(VirtualPatronRemovalService virtualPatronRemovalService) {
		this.virtualPatronRemovalService = virtualPatronRemovalService;
	}

	@Serdeable
	public record RemovalRequest(@Nullable VirtualPatronRemoval mode) {}

	@Operation(
		summary = "Delete or expire one virtual patron DCB created in a library's system",
		description = "Deletes it where that system can delete a patron, otherwise expires it; a mode can be asked for. "
			+ "Refused for a patron's own account, for a borrower with any request not yet finalised, and for a record "
			+ "the library's system does not confirm as DCB's virtual patron for that borrower. A library caller must be "
			+ "the borrower's library or the library whose system holds the virtual patron."
	)
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN})
	@SingleResult
	@Post(value = "/identities/{identityId}/virtual/retire", consumes = APPLICATION_JSON)
	public Mono<VirtualPatronRemovalResult> removeOne(@NotNull UUID identityId, @Nullable @Body RemovalRequest body,
		Authentication authentication) {

		log.info("Remove virtual patron identity {}", identityId);

		return virtualPatronRemovalService.removeOne(identityId, body == null ? null : body.mode(),
			CallerScope.from(authentication.getRoles(), authentication.getAttributes()), authentication.getName());
	}

	@Operation(
		summary = "Delete or expire every virtual patron of one borrower",
		description = "One library system at a time, each as the single removal would; each gets its own outcome. "
			+ "Refused for a borrower with any request not yet finalised. A library caller must be the borrower's library."
	)
	@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN})
	@SingleResult
	@Post(value = "/{patronId}/virtual-identities/retire", consumes = APPLICATION_JSON)
	public Mono<List<VirtualPatronRemovalResult>> removeAll(@NotNull UUID patronId, Authentication authentication) {
		log.info("Remove every virtual patron of patron {}", patronId);

		return virtualPatronRemovalService.removeAll(patronId,
			CallerScope.from(authentication.getRoles(), authentication.getAttributes()), authentication.getName());
	}

	@Error
	public HttpResponse<Map<String, String>> onRefused(VirtualPatronRemovalRefused refusal) {
		final MutableHttpResponse<Map<String, String>> response = HttpResponse.<Map<String, String>>status(refusal.getStatus())
			.body(Map.of("code", refusal.getCode(), "message", refusal.getMessage()));

		return refusal.getRetryAfter() == null
			? response
			: response.header("Retry-After", String.valueOf(refusal.getRetryAfter().toSeconds()));
	}
}
