package org.olf.dcb.graphql;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.olf.dcb.core.model.DataHostLms;

import graphql.schema.DataFetcher;
import reactor.core.publisher.Mono;

/**
 * A Host LMS field that consortium staff read for every system and a library user reads
 * only for the systems behind the agencies they administer. For any other system the
 * field comes back as {@code hidden}, which is absent rather than partial, and is not loaded.
 */
final class HostLmsFieldScope {
	private HostLmsFieldScope() {
	}

	static <T> DataFetcher<CompletableFuture<T>> visibleToItsAdministrators(
		AgencyScopeResolver agencyScopeResolver, Function<DataHostLms, Mono<T>> field, T hidden) {

		return env -> {
			final DataHostLms hostLms = env.getSource();

			if (hostLms == null) {
				return CompletableFuture.completedFuture(null);
			}

			final Mono<Boolean> permitted = AgencyAccessScope.isUnrestricted(env)
				? Mono.just(true)
				: agencyScopeResolver.permittedHostLmsIds(env)
					.map(ids -> ids.contains(hostLms.getId()))
					.defaultIfEmpty(false);

			return permitted
				.flatMap(visible -> visible ? field.apply(hostLms) : Mono.justOrEmpty(hidden))
				.toFuture();
		};
	}
}
