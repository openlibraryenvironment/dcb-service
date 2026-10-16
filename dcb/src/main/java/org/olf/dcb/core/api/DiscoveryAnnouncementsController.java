package org.olf.dcb.core.api;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import java.time.Instant;
import java.util.List;

import org.olf.dcb.core.AnnouncementService;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.security.OpenToAllPrincipals;
import org.olf.dcb.storage.LibraryRepository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

/**
 * What a patron should be told, for discovery services. Anonymous, because an outage notice
 * matters most before sign-in. At most two rows, from {@link AnnouncementService}: one
 * indexed read per level, no fan-out. An unknown library code answers the consortium's.
 */
@Controller("/discovery/announcements")
@Secured(IS_ANONYMOUS)
@OpenToAllPrincipals(justification =
	"Carries no patron data and confers no authority. The only input is a library code, the same public identifier the library directory already serves anonymously, and an unknown one answers the consortium's notices rather than an error. The response is at most two rows of operator-authored plain text intended to be read by anyone - an outage notice is most worth saying to the patron who has not signed in yet. Nothing tenant-scoped, no fan-out, and the staff-only fields on the entity are excluded from the view.")
@Tag(name = "Discovery API")
public class DiscoveryAnnouncementsController {

	private final AnnouncementService announcementService;
	private final LibraryRepository libraryRepository;

	public DiscoveryAnnouncementsController(AnnouncementService announcementService,
		LibraryRepository libraryRepository) {

		this.announcementService = announcementService;
		this.libraryRepository = libraryRepository;
	}

	@Operation(summary = "Announcements a patron should see",
		description = "At most two, in the order they should be shown: the library's own announcement, "
			+ "then the consortium's if it is marked urgent. A patron with no library named sees the "
			+ "consortium's. Expired and not-yet-started announcements are excluded in SQL and never "
			+ "reach a client. Body and headline are PLAIN TEXT and must be rendered as such - there is "
			+ "no markup here and there never will be.")
	@SingleResult
	@Get
	public Mono<List<AnnouncementView>> forPatron(@Nullable @QueryValue String libraryCode) {
		return library(libraryCode)
			.flatMap(library -> announcementService.forPatronOf(library))
			// switchIfEmpty rather than defaultIfEmpty on the library: an unknown code and an
			// absent one are the same request as far as a patron is concerned.
			.switchIfEmpty(Mono.defer(() -> announcementService.forPatronOf(null)))
			.map(announcements -> announcements.stream().map(AnnouncementView::from).toList());
	}

	/**
	 * One indexed lookup, not the whole table. Case-insensitive because the code arrives from a URL.
	 */
	private Mono<Library> library(@Nullable String libraryCode) {
		if (libraryCode == null || libraryCode.isBlank()) {
			return Mono.empty();
		}

		return Mono.from(libraryRepository.findOneByAgencyCodeIgnoringCase(libraryCode.strip()));
	}

	/**
	 * One announcement, as a patron-facing client needs it.
	 *
	 * Deliberately not the entity: {@code lastEditedBy}, {@code reason} and
	 * {@code changeReferenceUrl} name a member of staff and an internal ticket, and this
	 * route is anonymous. {@code scopeId} is absent for the same reason a patron does not
	 * need to know which internal row said this.
	 *
	 * {@code scope} IS carried, because the client renders the two differently: a
	 * library's notice and the consortium's are not the same voice.
	 */
	@Serdeable
	public record AnnouncementView(
		String id,
		String scope,
		String headline,
		String body,
		boolean urgent,
		boolean dismissible,
		Instant expiresAt) {

		static AnnouncementView from(Announcement announcement) {
			return new AnnouncementView(
				announcement.getId().toString(),
				announcement.getScopeType().name(),
				announcement.getHeadline(),
				announcement.getBody(),
				Boolean.TRUE.equals(announcement.getUrgent()),
				Boolean.TRUE.equals(announcement.getDismissible()),
				announcement.getExpiresAt());
		}
	}
}
