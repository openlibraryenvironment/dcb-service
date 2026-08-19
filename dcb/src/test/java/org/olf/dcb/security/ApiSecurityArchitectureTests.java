package org.olf.dcb.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

/**
 * The rule that makes the escalation unrepresentable rather than merely fixed.
 *
 * A patron-held role once sat on a controller whose class default was
 * IS_AUTHENTICATED, and five unguarded methods on that controller placed requests as
 * arbitrary patrons, ran expedited checkouts and rewrote state-machine history. Every
 * one of those is fixed. None of that stops somebody typing IS_AUTHENTICATED on a new
 * route next month, which is what this test is for.
 *
 * It reads Micronaut's COMPILED bean definitions rather than starting an application
 * context, so it needs no database and no Testcontainer: a build-time check that runs
 * in milliseconds.
 *
 * Deliberately ASCII-only in its messages. Assertion text lands in CI console output
 * and in Windows developer terminals, where non-ASCII punctuation arrives as mojibake
 * and makes a security failure harder to read than it needs to be.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiSecurityArchitectureTests {

	/** Only our own controllers. Framework and management endpoints are not ours to police. */
	private static final String OUR_PACKAGE = "org.olf.dcb";

	/** Verbs that change something. No justification is sufficient to open these to everyone. */
	private static final Set<Class<? extends Annotation>> MUTATING_VERBS = Set.of(
		Post.class, Put.class, Patch.class, Delete.class);

	/**
	 * Deliberately EMPTY, and it should stay that way.
	 *
	 * This briefly held PatronRequestResolutionController, which this test found to be
	 * @Secured(IS_ANONYMOUS) while running full resolution against member LMS APIs and
	 * returning consortium-wide item detail. It has since been secured, so the exemption
	 * is gone. Adding an entry here means shipping a known-open route on the staff API:
	 * do it only with a reason written down, and treat it as a defect to be removed.
	 */
	private static final Set<String> KNOWN_OPEN_STAFF_ROUTES = Set.of();

	private List<Route> routes;

	/** One HTTP route, with the security rule that actually applies to it. */
	private record Route(
		String controllerClass,
		String methodName,
		String path,
		boolean mutating,
		Set<String> effectiveRules,
		AnnotationMetadata methodMetadata) {

		String describe() {
			return controllerClass + "#" + methodName + " (" + (path.isBlank() ? "/" : path) + ")";
		}
	}

	@BeforeAll
	void collectRoutes() {
		routes = new ArrayList<>();

		for (final ServiceDefinition<BeanDefinitionReference> service
			: SoftServiceLoader.load(BeanDefinitionReference.class)) {

			// Filter on the name BEFORE loading: this walks every bean definition on the
			// classpath, and the framework's own are not ours to police.
			if (!service.getName().startsWith(OUR_PACKAGE) || !service.isPresent()) {
				continue;
			}

			final BeanDefinitionReference<?> reference = service.load();

			if (!reference.isPresent()) {
				continue;
			}

			final BeanDefinition<?> definition = reference.load();

			// Skip AOP-generated intercepted definitions: they mirror the real controller
			// and would report every finding twice.
			if (!definition.hasAnnotation(Controller.class)
				|| definition.getBeanType().getSimpleName().startsWith("$")) {

				continue;
			}

			final var controllerPath = definition.stringValue(Controller.class).orElse("");
			final var classRules = toSet(definition.stringValues(Secured.class));

			for (final ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
				final var metadata = method.getAnnotationMetadata();

				// Route methods carry an @Get/@Post/... which is stereotyped HttpMethodMapping.
				// @Error handlers come through the same way but are not routes a client can
				// call: they only run when a route on this controller already threw.
				if (!metadata.hasStereotype(HttpMethodMapping.class)
					|| metadata.hasAnnotation(io.micronaut.http.annotation.Error.class)) {

					continue;
				}

				// Micronaut's own override semantics: a method-level @Secured REPLACES the
				// class-level one rather than adding to it.
				//
				// getDeclaredMetadata() matters here. Plain stringValues() on method metadata
				// returns the class and method values MERGED, so a method narrowing
				// {CONSORTIUM_ADMIN, ADMIN, LIBRARY_ADMIN, INTERNAL_API} down to
				// {CONSORTIUM_ADMIN, ADMIN, LIBRARY_ADMIN} would read as the union, and a
				// method that overrode IS_AUTHENTICATED with a real role would still look
				// open to everyone. Declared-only gives the override the route actually gets.
				final var methodRules = toSet(metadata.getDeclaredMetadata().stringValues(Secured.class));
				final var effective = methodRules.isEmpty() ? classRules : methodRules;

				routes.add(new Route(
					definition.getBeanType().getSimpleName(),
					method.getMethodName(),
					controllerPath,
					MUTATING_VERBS.stream().anyMatch(metadata::hasStereotype),
					effective,
					metadata));
			}
		}
	}

	/** Set.of rejects duplicates, and merged annotation metadata routinely contains them. */
	private static Set<String> toSet(String[] values) {
		return Arrays.stream(values).collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Guards against this test silently passing because it found nothing. A refactor
	 * that moves controllers or changes how Micronaut emits definitions would otherwise
	 * turn every assertion below into a no-op.
	 */
	@Test
	void theTestCanActuallySeeTheControllers() {
		assertTrue(routes.size() > 50,
			"expected to find the API surface, found " + routes.size() + " routes. "
				+ "This test has stopped checking anything.");

		assertTrue(routes.stream()
				.anyMatch(route -> route.controllerClass().equals("PatronRequestController")),
			"PatronRequestController not found: the route scan is broken");
	}

	/**
	 * RULE 1. IS_AUTHENTICATED is legitimate on a route that confers no authority and
	 * returns no tenant-scoped data. It is never legitimate as an unnoticed default. So
	 * it must be accompanied by an explicit marker carrying a reason.
	 */
	@Test
	void everyRouteOpenToAllPrincipalsIsADeliberateDocumentedDecision() {
		final var undocumented = routes.stream()
			.filter(route -> route.effectiveRules().contains(SecurityRule.IS_AUTHENTICATED))
			.filter(route -> !route.methodMetadata().hasAnnotation(OpenToAllPrincipals.class))
			.map(Route::describe)
			.toList();

		assertTrue(undocumented.isEmpty(),
			"""
			These routes are open to EVERY authenticated principal, including discovery \
			service credentials and anything else the Keycloak realm authenticates, with \
			no recorded reason.

			Name the roles the route is actually for. If it genuinely confers no authority \
			and returns no tenant-scoped data, annotate it @OpenToAllPrincipals with a \
			justification so the decision is visible in review:

			""" + String.join("\n", undocumented));
	}

	/** RULE 1b. A marker with no reason in it is not a decision, it is a rubber stamp. */
	@Test
	void everyOpenToAllPrincipalsMarkerCarriesARealJustification() {
		final var unjustified = routes.stream()
			.filter(route -> route.methodMetadata().hasAnnotation(OpenToAllPrincipals.class))
			.filter(route -> route.methodMetadata()
				.stringValue(OpenToAllPrincipals.class, "justification")
				.orElse("")
				.isBlank())
			.map(Route::describe)
			.toList();

		assertTrue(unjustified.isEmpty(),
			"@OpenToAllPrincipals needs a justification explaining why:\n"
				+ String.join("\n", unjustified));
	}

	/**
	 * RULE 2. No justification is sufficient for a mutation. There is always a role that
	 * names the intent better, and "everyone may change this" is not a sentence anyone
	 * should be able to write here.
	 */
	@Test
	void noMutatingRouteIsOpenToAllPrincipals() {
		final var openMutations = routes.stream()
			.filter(Route::mutating)
			.filter(route -> route.effectiveRules().contains(SecurityRule.IS_AUTHENTICATED)
				|| route.methodMetadata().hasAnnotation(OpenToAllPrincipals.class))
			.map(Route::describe)
			.toList();

		assertTrue(openMutations.isEmpty(),
			"""
			These routes CHANGE something and are reachable by every authenticated \
			principal. This is the exact shape of the defect that let a patron token \
			place requests as arbitrary patrons. Name the roles:

			""" + String.join("\n", openMutations));
	}

	/**
	 * RULE 3. DISCOVERY_SERVICE is held by discovery backends, including third parties'.
	 * It buys the patron self-service surface and nothing else. Confining it to
	 * /discovery/ by construction means a future method cannot hand it staff powers by
	 * being added to the wrong file.
	 */
	@Test
	void theDiscoveryRoleReachesNothingOutsideTheDiscoverySurface() {
		final var escaped = routes.stream()
			.filter(route -> route.effectiveRules().contains(RoleNames.DISCOVERY_SERVICE))
			.filter(route -> !route.path().startsWith("/discovery"))
			.map(Route::describe)
			.toList();

		assertTrue(escaped.isEmpty(),
			"""
			DISCOVERY_SERVICE appears outside /discovery/. That role is held by discovery \
			backends, including third parties', and it must not reach the staff API:

			""" + String.join("\n", escaped));
	}

	/**
	 * The other half of rule 3: the discovery surface is for that role alone. An admin
	 * role added here would create a second way in whose patron-scoping nobody checked.
	 */
	@Test
	void theDiscoverySurfaceIsReachableOnlyByTheDiscoveryRole() {
		final var allowed = Set.of(RoleNames.DISCOVERY_SERVICE, SecurityRule.IS_ANONYMOUS);

		final var unexpected = routes.stream()
			.filter(route -> route.path().startsWith("/discovery"))
			.filter(route -> !allowed.containsAll(route.effectiveRules()))
			.map(route -> route.describe() + " -> " + route.effectiveRules())
			.toList();

		assertTrue(unexpected.isEmpty(),
			"""
			Routes under /discovery/ must be @Secured(DISCOVERY_SERVICE), or IS_ANONYMOUS \
			for the public library directory. Anything else is a second entrance:

			""" + String.join("\n", unexpected));
	}

	/**
	 * The specific regression. These are the routes a patron-held role reached when
	 * PatronRequestController defaulted to IS_AUTHENTICATED.
	 */
	@Test
	void theStaffRequestApiIsNeverOpenToEveryPrincipal() {
		final var staffRoutes = routes.stream()
			.filter(route -> route.path().startsWith("/patrons/requests")
				|| route.path().startsWith("/suppliers/requests"))
			.toList();

		assertFalse(staffRoutes.isEmpty(), "the staff request API was not found by the scan");

		final var open = staffRoutes.stream()
			.filter(route -> !KNOWN_OPEN_STAFF_ROUTES.contains(route.controllerClass()))
			.filter(route -> route.effectiveRules().contains(SecurityRule.IS_AUTHENTICATED)
				|| route.effectiveRules().contains(SecurityRule.IS_ANONYMOUS)
				|| route.effectiveRules().isEmpty())
			.map(route -> route.describe() + " -> " + route.effectiveRules())
			.toList();

		assertTrue(open.isEmpty(),
			"""
			The staff request API places holds, checks items out and rewrites request \
			state. No route on it may be anonymous, open to every authenticated \
			principal, or ungated:

			""" + String.join("\n", open));
	}

	/**
	 * Not a rule so much as a tripwire: the role is gone and the escalation went with
	 * it. A string constant is cheap to reintroduce and expensive to notice.
	 */
	@Test
	void noPatronHeldRoleHasBeenReintroduced() {
		final var patronRoles = routes.stream()
			.flatMap(route -> route.effectiveRules().stream())
			.filter(rule -> rule.equals("PATRON") || rule.startsWith("PATRON_"))
			.distinct()
			.toList();

		assertTrue(patronRoles.isEmpty(),
			"""
			A patron-held role is back in a @Secured. Patrons must not hold a credential \
			dcb-service accepts: their identity travels as a signed assertion that DCB \
			verifies, and the calling service authenticates separately. Found: \
			""" + patronRoles);
	}

	/**
	 * Multipart decoding is on for the WHOLE SERVER, because it cannot be turned on for one
	 * route.
	 *
	 * Micronaut resolves micronaut.server.multipart.enabled into a final boolean field on
	 * RoutingInBoundHandler when that handler is constructed - it is a Netty channel handler
	 * sitting in the pipeline before any route is matched, so by the time a controller is
	 * known the decode decision has already been taken. There is no annotation that scopes
	 * it and no per-route override to reach for.
	 *
	 * So the scope is asserted here instead: EVERY route that can trigger multipart decoding
	 * must name its roles. An open one would let any caller make the server buffer and parse
	 * a multipart body, which is a denial-of-service primitive rather than a feature.
	 *
	 * Deliberately not "there is exactly one". Writing that first was instructive: it found
	 * LocationController#importLocations and UploadedMappingsController#post, which have
	 * been on main for far longer than brand asset upload. Multipart is not new here, and a
	 * rule that pretended otherwise would have been a rule about this branch rather than
	 * about the server.
	 */
	@Test
	void everyMultipartRouteNamesItsRoles() {
		final var open = routes.stream()
			.filter(ApiSecurityArchitectureTests::consumesMultipart)
			.filter(route -> route.effectiveRules().contains(SecurityRule.IS_ANONYMOUS)
				|| route.effectiveRules().contains(SecurityRule.IS_AUTHENTICATED)
				|| route.effectiveRules().isEmpty())
			.map(route -> route.describe() + " -> " + route.effectiveRules())
			.toList();

		assertTrue(open.isEmpty(),
			"""
			Multipart decoding is enabled for the whole server, because Micronaut resolves \
			micronaut.server.multipart.enabled into a final field on the Netty channel \
			handler before any route is matched and cannot scope it to one controller. \
			Every route that can reach it must therefore name its roles:

			""" + String.join("\n", open));
	}

	/**
	 * A body-carrying route that consumes multipart. Reads both forms - @Consumes on the
	 * method or its class, and the consumes member of @Post.
	 *
	 * GET is excluded because a GET has no request body to decode.
	 * UploadedMappingsController carries a CLASS-level @Consumes(MULTIPART_FORM_DATA), which
	 * merged metadata then reports on its @Get methods too; counting those would be
	 * reporting an annotation rather than a capability.
	 */
	private static boolean consumesMultipart(Route route) {
		if (!route.mutating()) {
			return false;
		}

		final var metadata = route.methodMetadata();

		return Stream.concat(
				Arrays.stream(metadata.stringValues(Consumes.class)),
				Arrays.stream(metadata.stringValues(Post.class, "consumes")))
			.anyMatch(MediaType.MULTIPART_FORM_DATA::equalsIgnoreCase);
	}
}
