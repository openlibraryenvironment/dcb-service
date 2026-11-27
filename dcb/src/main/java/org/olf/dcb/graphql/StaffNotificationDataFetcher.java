package org.olf.dcb.graphql;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.olf.dcb.core.notifications.StaffNotificationKind;
import org.olf.dcb.core.notifications.StaffNotificationService;

import graphql.GraphQLException;
import graphql.schema.DataFetcher;
import jakarta.inject.Singleton;

/**
 * {@code staffNotifications}: one library's feed ({@link org.olf.dcb.core.notifications.StaffNotificationService}).
 * A library user reads their own libraries' only; consortium staff name the library.
 */
@Singleton
public class StaffNotificationDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {
	private static final Set<String> KINDS = Arrays.stream(StaffNotificationKind.values())
		.map(Enum::name)
		.collect(Collectors.toUnmodifiableSet());

	private final StaffNotificationService service;

	public StaffNotificationDataFetcher(StaffNotificationService service) {
		this.service = service;
	}

	@Override
	public CompletableFuture<Map<String, Object>> get(graphql.schema.DataFetchingEnvironment env) {
		GraphQLRoles.require(env, "staffNotifications", GraphQLRoles.STAFF);

		final String requested = env.getArgument("agencyCode");
		final List<String> kindsArgument = env.getArgument("kinds");
		final Collection<String> own = AgencyAccessScope.visibleAgencyCodes(env);

		final String agencyCode;
		if (requested != null) {
			agencyCode = requested;
		} else if (own.size() == 1) {
			agencyCode = own.iterator().next();
		} else {
			throw new GraphQLException("Say which library's notifications: this account has " + own.size());
		}

		if (!AgencyAccessScope.permitsAgency(env, agencyCode)) {
			throw new GraphQLException("Notifications can only be read for your own library");
		}

		final Set<String> kinds = kindsArgument == null ? KINDS : Set.copyOf(kindsArgument);
		if (!KINDS.containsAll(kinds)) {
			throw new GraphQLException("Unknown notification kind; known kinds are " + KINDS);
		}

		return service.feedFor(agencyCode)
			.map(items -> Map.<String, Object>of(
				"generatedAt", Instant.now().toString(),
				"items", items.stream().filter(item -> kinds.contains(item.kind())).toList()))
			.toFuture();
	}
}
