package org.olf.dcb.core.svc;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * What the Host LMS behind a mapping context says about how mappings resolve
 * against it.
 * <p>
 * There were three copies of this read, and they did not agree.
 * {@code LocationToAgencyMappingService} used it for locations,
 * {@link org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper} kept a private
 * one for patron types, and {@code ValidatePatronTransition} skipped the hierarchy
 * altogether - which is how a request could pass preflight against one agency and be
 * validated against another. A context hierarchy that applies to one kind of mapping
 * and not another is a configuration that behaves differently depending on which
 * question happens to be asked, and nothing tells the operator which they got.
 */
@Slf4j
@Singleton
public class HostLmsContextService {
	private static final String KEY_CONTEXT_HIERARCHY = "contextHierarchy";

	/** The target context mappings resolve into. Never a Host LMS. */
	private static final String DCB_CONTEXT = "DCB";

	private final HostLmsService hostLmsService;

	public HostLmsContextService(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	/**
	 * @param sourceContexts the contexts to search, in precedence order
	 * @param sharedSystem whether this Host LMS hosts more than one participating library
	 */
	public record MappingContext(List<String> sourceContexts, boolean sharedSystem) { }

	/**
	 * Both answers come from one Host LMS, so they are read from a single client
	 * rather than fetching one per question - this sits on the per-item availability
	 * path.
	 */
	public Mono<MappingContext> forContext(String context) {
		final var defaults = List.of(context);

		if (DCB_CONTEXT.equals(context)) {
			return Mono.just(new MappingContext(defaults, false));
		}

		return hostLmsService.getClientFor(context)
			.map(client -> new MappingContext(
				contextHierarchyOf(client, context, defaults), client.isSharedSystem()))
			.switchIfEmpty(Mono.fromSupplier(() -> unidentifiedSystem(context, defaults)))
			.onErrorResume(error -> {
				log.debug("[CONTEXT-HIERARCHY-ERROR] " +
					"- An ERROR occurred while fetching 'contextHierarchy' for context: '{}'.", context, error);

				return Mono.just(unidentifiedSystem(context, defaults));
			});
	}

	/**
	 * The contexts to search, for callers that have no use for the shared-system
	 * answer - patron type mappings are not affected by co-tenancy.
	 */
	public Mono<List<String>> contextHierarchyFor(String context) {
		return forContext(context).map(MappingContext::sourceContexts);
	}

	/**
	 * What to assume when the Host LMS behind a context cannot be loaded.
	 * <p>
	 * The wildcard collapses every location on a system onto one agency, which is only
	 * ever safe on a system serving one library. If we cannot tell which kind of system
	 * this is, we cannot tell whether that is safe - and the two outcomes are not
	 * equally bad. Suppressing it means an item or patron fails to map and says so;
	 * allowing it means silently attributing a co-tenant library's holdings or borrowers
	 * to a library they have nothing to do with.
	 */
	private static MappingContext unidentifiedSystem(String context, List<String> defaults) {
		log.warn("Could not determine whether '{}' is a shared system; "
			+ "suppressing the wildcard location mapping for this lookup", context);

		return new MappingContext(defaults, true);
	}

	@SuppressWarnings("unchecked")
	private static List<String> contextHierarchyOf(HostLmsClient client, String context,
		List<String> defaults) {

		final var configured = (List<String>) client.getConfig().get(KEY_CONTEXT_HIERARCHY);

		if (configured == null || configured.isEmpty()) {
			log.debug("[CONTEXT-HIERARCHY-EMPTY] " +
				"- Fetching 'contextHierarchy' returned an EMPTY list for context: '{}'", context);

			return defaults;
		}

		return configured;
	}
}
