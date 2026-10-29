package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.api.serde.PreferenceAvailability;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveDisplay;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveView;
import org.olf.dcb.core.api.serde.PreferencesView;
import org.olf.dcb.core.api.serde.SavedViewView;
import org.olf.dcb.core.api.serde.WatchedRequestPage;
import org.olf.dcb.preferences.AdminKey;
import org.olf.dcb.preferences.AdminPreferenceService;
import org.olf.dcb.preferences.PreferencesConfig;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/**
 * The signed-in member of staff's own settings, in either admin app. No route takes an owner: the
 * key comes from the caller's verified claims. LIBRARY_READ_ONLY may write here, deliberately:
 * these are the person's settings, not library data. Interop testers are not in the list.
 * docs/dev/admin-preferences.md.
 */
@Controller("/preferences")
@Validated
@Secured({ ADMINISTRATOR, CONSORTIUM_ADMIN, LIBRARY_ADMIN, LIBRARY_READ_ONLY })
@Tag(name = "Admin API")
public class AdminPreferenceController {
	private final AdminPreferenceService service;
	private final AdminKey adminKey;
	private final PreferencesConfig config;

	public AdminPreferenceController(AdminPreferenceService service, AdminKey adminKey, PreferencesConfig config) {
		this.service = service;
		this.adminKey = adminKey;
		this.config = config;
	}

	@Operation(summary = "Whether this deployment stores staff preferences",
		description = "Always 200 while the route exists, so a 404 means a dcb-service without it.")
	@Get("/availability")
	public PreferenceAvailability availability() {
		return new PreferenceAvailability(adminKey.isAvailable(), config.getMaxSavedViews(), config.getMaxWatched());
	}

	@Operation(summary = "My stored preferences", description = "404 when nothing is stored yet; 503 when off.")
	@SingleResult
	@Get
	public Mono<PreferencesView> myPreferences(Authentication authentication) {
		return service.find(adminKey.forCaller(authentication));
	}

	@Operation(summary = "Store my display settings, whole")
	@SingleResult
	@Put("/display")
	public Mono<PreferencesView> saveDisplay(@Body @Valid SaveDisplay input, Authentication authentication) {
		return service.saveDisplay(adminKey.forCaller(authentication), input);
	}

	@Operation(summary = "Store one app's state",
		description = "admin or libraries; replaces that app's slice and leaves the other's alone.")
	@SingleResult
	@Put("/apps/{app}")
	public Mono<PreferencesView> saveApp(@PathVariable String app, @Body Map<String, Object> slice,
		Authentication authentication) {

		return service.saveApp(adminKey.forCaller(authentication), app, slice);
	}

	@Operation(summary = "Forget everything stored for me", description = "Settings, saved filters and watches. Idempotent.")
	@SingleResult
	@Delete
	public Mono<MutableHttpResponse<Void>> delete(Authentication authentication) {
		return service.delete(adminKey.forCaller(authentication)).thenReturn(HttpResponse.noContent());
	}

	@Operation(summary = "My saved filters", description = "Bounded by the per-user limit, so not paged.")
	@SingleResult
	@Get("/views")
	public Mono<List<SavedViewView>> myViews(@Nullable @QueryValue String viewType, Authentication authentication) {
		return service.listViews(adminKey.forCaller(authentication), viewType).collectList();
	}

	@Operation(summary = "Save a filter",
		description = "409 at the limit or on a name already used for that grid. 400 for a patron barcode, "
			+ "text people wrote, or quick filter text.")
	@SingleResult
	@Post("/views")
	@Status(HttpStatus.CREATED)
	public Mono<SavedViewView> createView(@Body @Valid SaveView input, Authentication authentication) {
		return service.createView(adminKey.forCaller(authentication), input);
	}

	@Operation(summary = "Replace one of my saved filters")
	@SingleResult
	@Put("/views/{id}")
	public Mono<SavedViewView> updateView(@PathVariable UUID id, @Body @Valid SaveView input,
		Authentication authentication) {

		return service.updateView(adminKey.forCaller(authentication), id, input);
	}

	@Operation(summary = "Delete one of my saved filters", description = "Idempotent.")
	@SingleResult
	@Delete("/views/{id}")
	public Mono<MutableHttpResponse<Void>> deleteView(@PathVariable UUID id, Authentication authentication) {
		return service.deleteView(adminKey.forCaller(authentication), id).thenReturn(HttpResponse.noContent());
	}

	@Operation(summary = "The requests I watch",
		description = "Ids and dates, newest first, at most 100 a page. The apps load the requests through "
			+ "their own scoped query, so one the caller can no longer see does not come back.")
	@SingleResult
	@Get("/watched")
	public Mono<WatchedRequestPage> watched(@Nullable @QueryValue Integer pageno, @Nullable @QueryValue Integer pagesize,
		Authentication authentication) {

		return service.watched(adminKey.forCaller(authentication), pageno == null ? 0 : pageno,
			pagesize == null ? AdminPreferenceService.MAX_WATCHED_PAGE : pagesize);
	}

	@Operation(summary = "Watch a request",
		description = "Idempotent. 409 at the limit. A library user gets 403 for a request their library is "
			+ "not party to, and for one that does not exist.")
	@SingleResult
	@Put("/watched/{patronRequestId}")
	public Mono<MutableHttpResponse<Void>> watch(@PathVariable UUID patronRequestId, Authentication authentication) {
		return service.watch(adminKey.forCaller(authentication), patronRequestId, authentication)
			.thenReturn(HttpResponse.noContent());
	}

	@Operation(summary = "Stop watching a request", description = "Idempotent.")
	@SingleResult
	@Delete("/watched/{patronRequestId}")
	public Mono<MutableHttpResponse<Void>> unwatch(@PathVariable UUID patronRequestId, Authentication authentication) {
		return service.unwatch(adminKey.forCaller(authentication), patronRequestId).thenReturn(HttpResponse.noContent());
	}

	@Operation(summary = "Mark a watched request as seen now", description = "A no-op if it is not watched.")
	@SingleResult
	@Put("/watched/{patronRequestId}/viewed")
	public Mono<MutableHttpResponse<Void>> viewed(@PathVariable UUID patronRequestId, Authentication authentication) {
		return service.markViewed(adminKey.forCaller(authentication), patronRequestId)
			.thenReturn(HttpResponse.noContent());
	}
}
