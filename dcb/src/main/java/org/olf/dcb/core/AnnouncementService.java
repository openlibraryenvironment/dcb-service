package org.olf.dcb.core;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.LibraryGroup;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.AnnouncementRepository;
import org.olf.dcb.storage.LibraryGroupRepository;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What a patron of one library should be shown, right now: at most two notices, the narrowest
 * live one first and the newest urgent one from a broader level beneath it. Why:
 * docs/discovery-service-approach.md §6.4 ({@code GET /discovery/announcements}).
 */
@Singleton
public class AnnouncementService {

	private final AnnouncementRepository announcements;
	private final ConsortiumService consortiumService;
	private final LibraryGroupRepository libraryGroups;
	private final Clock clock;

	@Inject
	public AnnouncementService(AnnouncementRepository announcements,
		ConsortiumService consortiumService, LibraryGroupRepository libraryGroups) {

		this(announcements, consortiumService, libraryGroups, Clock.systemUTC());
	}

	// Package-private, for a test that needs to stand at a moment rather than wait for one.
	// The same seam BrandAssetSweep uses, and for the same reason: there is no Clock bean in
	// this application and adding one to make a window testable would be a wider change than
	// the window.
	AnnouncementService(AnnouncementRepository announcements,
		ConsortiumService consortiumService, LibraryGroupRepository libraryGroups, Clock clock) {

		this.announcements = announcements;
		this.consortiumService = consortiumService;
		this.libraryGroups = libraryGroups;
		this.clock = clock;
	}

	/**
	 * The library's newest live notice, else the newest across its groups, else the
	 * consortium's; beneath a library's or a group's, the newest urgent one from a broader
	 * level. A null library (a patron who has not said which) sees the consortium's.
	 */
	public Mono<List<Announcement>> forPatronOf(@Nullable Library library) {
		final var now = clock.instant();

		if (library == null) {
			return listOf(consortiumNewest(now, false));
		}

		// A library is in a handful of groups, read once per call
		final var groupIds = Flux.from(libraryGroups.findByLibraryIds(List.of(library.getId())))
			.map(LibraryGroup::getId)
			.collectList()
			.cache();

		return newestAt(ScopeLevel.LIBRARY, library.getId(), now, false)
			.flatMap(own -> withBeneath(own, newer(groupsNewest(groupIds, now, true),
				consortiumNewest(now, true))))
			.switchIfEmpty(Mono.defer(() -> groupsNewest(groupIds, now, false)
				.flatMap(group -> withBeneath(group, consortiumNewest(now, true)))))
			.switchIfEmpty(Mono.defer(() -> listOf(consortiumNewest(now, false))));
	}

	private static Mono<List<Announcement>> withBeneath(Announcement shown, Mono<Announcement> beneath) {
		return beneath.map(urgent -> List.of(shown, urgent)).defaultIfEmpty(List.of(shown));
	}

	private static Mono<List<Announcement>> listOf(Mono<Announcement> only) {
		return only.map(List::of).defaultIfEmpty(List.of());
	}

	// The one that started most recently, of a group's and the consortium's
	private static Mono<Announcement> newer(Mono<Announcement> first, Mono<Announcement> second) {
		return Flux.concat(first, second)
			.reduce((a, b) -> a.getStartsAt().isAfter(b.getStartsAt()) ? a : b);
	}

	private Mono<Announcement> groupsNewest(Mono<List<UUID>> groupIds, Instant now, boolean urgentOnly) {
		return groupIds
			.filter(ids -> !ids.isEmpty())
			.flatMap(ids -> Mono.from(announcements.findNewestLiveIn(ScopeLevel.LIBRARY_GROUP.name(),
				ids, now, urgentOnly)));
	}

	/** The consortium's newest live announcement, or empty on a standalone DCB. */
	private Mono<Announcement> consortiumNewest(Instant now, boolean urgentOnly) {
		return consortiumService.findOneConsortiumId()
			.flatMap(id -> newestAt(ScopeLevel.CONSORTIUM, id, now, urgentOnly));
	}

	private Mono<Announcement> newestAt(ScopeLevel scopeType, UUID scopeId, Instant now,
		boolean urgentOnly) {

		return Mono.from(announcements.findNewestLive(scopeType.name(), scopeId, now, urgentOnly));
	}
}
