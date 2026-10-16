package org.olf.dcb.graphql;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.AnnouncementRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

/**
 * Everything at one scope, live or not — the admin list of announcements.
 *
 * Deliberately not the patron's view. A patron reads the live ones from
 * {@code /discovery/announcements}, which is anonymous, takes a library code and no query
 * language, and answers at most two rows. This one shows an administrator the expired and
 * not-yet-started notices too, because those are the ones they need to see to manage them.
 *
 * Bounded by the scope: one library or one consortium, and the number of things an operator
 * is currently saying is a handful rather than a function of the corpus or the membership.
 */
@Singleton
public class AnnouncementsDataFetcher implements DataFetcher<CompletableFuture<List<Announcement>>> {

	private final AnnouncementRepository announcements;
	private final AnnouncementAuthorisation authorisation;

	public AnnouncementsDataFetcher(AnnouncementRepository announcements,
		AnnouncementAuthorisation authorisation) {

		this.announcements = announcements;
		this.authorisation = authorisation;
	}

	@Override
	public CompletableFuture<List<Announcement>> get(DataFetchingEnvironment env) {
		// Who may reach it at all; AnnouncementAuthorisation then decides which scope.
		GraphQLRoles.require(env, "announcements", GraphQLRoles.ADMINISTRATIVE);

		final var scopeType = scopeLevel(env.getArgument("scopeType"));
		final var scopeId = UUID.fromString(String.valueOf(env.getArgument("scopeId")));

		// Reading is gated by the same rule as writing. An administrator who may not publish
		// for a library has no business reading its unpublished notices either, and the two
		// diverging is how a read path becomes the way round a write control.
		return authorisation.assertMayWrite(env, scopeType, scopeId)
			.flatMapMany(permitted -> announcements.findForAdministration(scopeType.name(), permitted))
			.collectList()
			.toFuture();
	}

	private static ScopeLevel scopeLevel(Object raw) {
		try {
			return ScopeLevel.valueOf(String.valueOf(raw));
		}
		catch (IllegalArgumentException e) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "unknown scopeType '" + raw + "'");
		}
	}
}
