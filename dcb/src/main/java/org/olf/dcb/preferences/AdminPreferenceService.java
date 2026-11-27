package org.olf.dcb.preferences;

import static org.olf.dcb.core.model.PatronRequest.Status.ARCHIVED;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.COMPLETED;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.core.api.serde.PreferenceInputs.SaveDisplay;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveView;
import org.olf.dcb.core.api.serde.PreferencesView;
import org.olf.dcb.core.api.serde.SavedViewView;
import org.olf.dcb.core.api.serde.WatchedRequestPage;
import org.olf.dcb.core.model.AdminPreference;
import org.olf.dcb.core.model.AdminSavedView;
import org.olf.dcb.request.notes.RequestNoteAccess;
import org.olf.dcb.request.notes.RequestNoteCaller;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.AdminPreferenceRepository;
import org.olf.dcb.storage.AdminSavedViewRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The signed-in member of staff's own preferences, saved views and watched requests. Every method
 * takes the key derived from their verified claims; none takes an owner from the request.
 */
@Slf4j
@Singleton
public class AdminPreferenceService {
	// ERROR is left out, as for notes: a request in error can still be rolled back into the workflow
	static final Set<String> FINISHED_STATUSES = Stream.of(FINALISED, CANCELLED, COMPLETED, ARCHIVED)
		.map(Enum::name)
		.collect(Collectors.toUnmodifiableSet());

	public static final int MAX_WATCHED_PAGE = 100;

	private final AdminPreferenceRepository preferences;
	private final AdminSavedViewRepository savedViews;
	private final PatronRequestRepository patronRequests;
	private final RequestNoteAccess requestAccess;
	private final PreferenceValidator validator;
	private final PreferencesConfig config;
	private final Clock clock;

	public AdminPreferenceService(AdminPreferenceRepository preferences, AdminSavedViewRepository savedViews,
		PatronRequestRepository patronRequests, RequestNoteAccess requestAccess, PreferenceValidator validator,
		PreferencesConfig config) {

		this(preferences, savedViews, patronRequests, requestAccess, validator, config, Clock.systemUTC());
	}

	AdminPreferenceService(AdminPreferenceRepository preferences, AdminSavedViewRepository savedViews,
		PatronRequestRepository patronRequests, RequestNoteAccess requestAccess, PreferenceValidator validator,
		PreferencesConfig config, Clock clock) {

		this.preferences = preferences;
		this.savedViews = savedViews;
		this.patronRequests = patronRequests;
		this.requestAccess = requestAccess;
		this.validator = validator;
		this.config = config;
		this.clock = clock;
	}

	/** Empty when nothing is stored yet: the signal for the app to offer its local settings. */
	public Mono<PreferencesView> find(String adminKey) {
		return touch(adminKey)
			.then(Mono.from(preferences.findById(adminKey)))
			.map(AdminPreferenceService::toView);
	}

	public Mono<PreferencesView> saveDisplay(String adminKey, SaveDisplay input) {
		final var display = validator.display(input);

		return Mono.from(preferences.upsertDisplay(adminKey, display.themeMode(), display.textSize(), display.density(),
				display.fontName(), display.motion(), display.language(), clock.instant()))
			.then(Mono.from(preferences.findById(adminKey)))
			.map(AdminPreferenceService::toView);
	}

	public Mono<PreferencesView> saveApp(String adminKey, String app, @Nullable Map<String, Object> slice) {
		final var checkedApp = validator.app(app);
		final var json = validator.appSlice(checkedApp, slice);

		return Mono.from(preferences.upsertAppSlice(adminKey, checkedApp, json, clock.instant()))
			.then(Mono.from(preferences.findById(adminKey)))
			.map(AdminPreferenceService::toView);
	}

	/** Forget everything: views and watches go with the record. Idempotent. */
	public Mono<Void> delete(String adminKey) {
		return Mono.from(preferences.deleteByAdminKey(adminKey)).then();
	}

	/** Bounded by the per-user limit, so not paged. */
	public Flux<SavedViewView> listViews(String adminKey, @Nullable String viewType) {
		final var views = viewType == null
			? savedViews.findByAdminKeyOrderByNameAsc(adminKey)
			: savedViews.findByAdminKeyAndViewTypeOrderByNameAsc(adminKey, validator.viewType(viewType));

		return touch(adminKey)
			.thenMany(Flux.from(views))
			.map(AdminPreferenceService::toView);
	}

	public Mono<SavedViewView> createView(String adminKey, SaveView input) {
		final var viewType = validator.viewType(input.viewType());
		final var queryState = validator.queryState(viewType, input.queryState());
		final var now = clock.instant();

		return Mono.from(savedViews.countByAdminKey(adminKey))
			.defaultIfEmpty(0L)
			.flatMap(count -> count >= config.getMaxSavedViews()
				? Mono.error(PreferenceException.quotaReached("You have " + count + " saved filters, the limit. "
					+ "Delete one to save another."))
				: Mono.from(preferences.ensureExists(adminKey, now)))
			.then(Mono.from(savedViews.save(AdminSavedView.builder()
				.id(UUID.randomUUID())
				.adminKey(adminKey)
				.name(input.name().trim())
				.viewType(viewType)
				.queryState(queryState)
				.dateCreated(now)
				.dateUpdated(now)
				.build())))
			.map(AdminPreferenceService::toView)
			.onErrorMap(AdminPreferenceService::isDuplicateName, error -> duplicateName(input.name()));
	}

	public Mono<SavedViewView> updateView(String adminKey, UUID id, SaveView input) {
		final var viewType = validator.viewType(input.viewType());
		final var queryState = validator.queryState(viewType, input.queryState());
		final var now = clock.instant();

		return Mono.from(savedViews.findByIdAndAdminKey(id, adminKey))
			.switchIfEmpty(Mono.error(() -> PreferenceException.notFound("You have no saved filter " + id)))
			.map(existing -> {
				existing.setName(input.name().trim());
				existing.setViewType(viewType);
				existing.setQueryState(queryState);
				existing.setDateUpdated(now);
				return existing;
			})
			.flatMap(updated -> Mono.from(savedViews.update(updated)))
			.map(AdminPreferenceService::toView)
			.onErrorMap(AdminPreferenceService::isDuplicateName, error -> duplicateName(input.name()));
	}

	public Mono<Void> deleteView(String adminKey, UUID id) {
		return Mono.from(savedViews.deleteByIdAndAdminKey(id, adminKey)).then();
	}

	public Mono<WatchedRequestPage> watched(String adminKey, int pageno, int pagesize) {
		final int size = Math.max(1, Math.min(pagesize, MAX_WATCHED_PAGE));
		final long offset = (long) Math.max(0, pageno) * size;

		return touch(adminKey)
			.then(Mono.zip(
				Flux.from(preferences.findWatched(adminKey, size, offset))
					.map(row -> new WatchedRequestPage.Watched(row.patron_request_id(), row.date_created(), row.date_last_viewed()))
					.collectList(),
				Mono.from(preferences.countWatched(adminKey)).defaultIfEmpty(0L)))
			.map(both -> new WatchedRequestPage(both.getT1(), both.getT2()));
	}

	/**
	 * Watching is reading, so it follows the read rule notes use (RequestInvolvement). A library user
	 * gets the same 403 whether the request is another library's or does not exist.
	 */
	public Mono<Void> watch(String adminKey, UUID patronRequestId, Authentication authentication) {
		final var now = clock.instant();

		return mayWatch(patronRequestId, authentication)
			.then(Mono.from(preferences.countWatchedRequest(adminKey, patronRequestId)).defaultIfEmpty(0L))
			.flatMap(already -> already > 0
				? Mono.<Void>empty()
				: Mono.from(preferences.countWatched(adminKey)).defaultIfEmpty(0L)
					.flatMap(count -> count >= config.getMaxWatched()
						? Mono.error(PreferenceException.quotaReached("You watch " + count
							+ " requests, the limit. Stop watching one to watch another."))
						: Mono.from(preferences.ensureExists(adminKey, now))
							.then(Mono.from(preferences.watch(adminKey, patronRequestId, now)))
							.then()));
	}

	public Mono<Void> unwatch(String adminKey, UUID patronRequestId) {
		return Mono.from(preferences.unwatch(adminKey, patronRequestId)).then();
	}

	public Mono<Void> markViewed(String adminKey, UUID patronRequestId) {
		return Mono.from(preferences.markViewed(adminKey, patronRequestId, clock.instant())).then();
	}

	private Mono<Void> mayWatch(UUID patronRequestId, Authentication authentication) {
		final var scope = CallerScope.from(authentication.getRoles(), authentication.getAttributes());

		if (!scope.requiresNarrowing()) {
			return Mono.from(patronRequests.findById(patronRequestId))
				.switchIfEmpty(Mono.error(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such patron request.")))
				.then();
		}

		return requestAccess.canRead(patronRequestId, new RequestNoteCaller(false, scope.agencyCodes()))
			.flatMap(allowed -> allowed
				? Mono.<Void>empty()
				: Mono.error(new HttpStatusException(HttpStatus.FORBIDDEN,
					"Access denied: this request does not belong to your library.")));
	}

	/** At most one write a touch interval; a failure never fails the read it rides on. */
	private Mono<Void> touch(String adminKey) {
		final var now = clock.instant();

		return Mono.from(preferences.touch(adminKey, now, now.minus(config.getTouchInterval())))
			.onErrorResume(error -> {
				log.warn("Could not record preference use; retention may see a stale timestamp", error);
				return Mono.empty();
			})
			.then();
	}

	// The unique index is the authority on duplicate names, so a clash arrives as its violation
	private static boolean isDuplicateName(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			if (current.getMessage() != null && current.getMessage().contains("admin_saved_view_key_name_uq")) {
				return true;
			}
		}
		return false;
	}

	private static PreferenceException duplicateName(String name) {
		return PreferenceException.duplicateName("You already have a saved filter called '" + name + "' here.");
	}

	private static PreferencesView toView(AdminPreference preference) {
		return new PreferencesView(preference.getThemeMode(), preference.getTextSize(), preference.getDensity(),
			preference.getFontName(), preference.getMotion(), preference.getLanguage(),
			preference.getAppState() != null ? preference.getAppState() : Map.of(), preference.getDateUpdated());
	}

	private static SavedViewView toView(AdminSavedView view) {
		return new SavedViewView(view.getId(), view.getName(), view.getViewType(),
			view.getQueryState() != null ? view.getQueryState() : Map.of(), view.getDateCreated(), view.getDateUpdated());
	}
}
