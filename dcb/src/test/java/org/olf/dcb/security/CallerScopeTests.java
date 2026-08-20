package org.olf.dcb.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The claim contract, as a truth table. The row that matters is the last one in
 * each group: a library-level caller whose token carries no agency code must be
 * INCOHERENT, not unrestricted. If that ever flips to fail-open, the cross-tenant
 * read this type exists to close is back, with more ceremony around it.
 */
class CallerScopeTests {

	private static final Map<String, Object> NO_CLAIMS = Map.of();
	private static final Map<String, Object> AGENCY = Map.of(
		AgencyClaims.CODE, "library-a");

	@Nested
	@DisplayName("consortium-level callers")
	class ConsortiumLevel {
		@Test
		void administratorIsUnscopedAndIgnoresTheClaim() {
			final var scope = CallerScope.from(List.of(RoleNames.ADMINISTRATOR), AGENCY);

			assertTrue(scope.consortiumWide());
			assertFalse(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
		}

		@Test
		void consortiumAdminIsUnscoped() {
			final var scope = CallerScope.from(List.of(RoleNames.CONSORTIUM_ADMIN), NO_CLAIMS);

			assertFalse(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
		}

		@Test
		void internalApiIsUnscoped() {
			final var scope = CallerScope.from(List.of(RoleNames.INTERNAL_API), NO_CLAIMS);

			assertFalse(scope.requiresNarrowing());
		}

		@Test
		void aConsortiumAdminWhoIsALSOALibraryAdminStaysUnscoped() {
			// Otherwise adding a library role to a consortium account would silently
			// shrink what that account can see.
			final var scope = CallerScope.from(
				List.of(RoleNames.CONSORTIUM_ADMIN, RoleNames.LIBRARY_ADMIN), AGENCY);

			assertFalse(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
		}
	}

	@Nested
	@DisplayName("library-level callers")
	class LibraryLevel {
		@Test
		void libraryAdminWithTheClaimIsNarrowedToThatAgency() {
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_ADMIN), AGENCY);

			assertTrue(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
			assertEquals(List.of("library-a"), scope.agencyCodes());
		}

		@Test
		void aMultiValuedClaimNarrowsToEVERYAgencyTheCallerAdministers() {
			// The shared-Koha operator: one person, several agencies, and NOT a
			// consortium administrator. Reading this claim as a scalar refused exactly
			// these people, which is why it is a collection.
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_ADMIN),
				Map.of(AgencyClaims.CODE, List.of("library-a", "library-b")));

			assertTrue(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
			assertEquals(List.of("library-a", "library-b"), scope.agencyCodes());
		}

		@Test
		void theAgencyCodesClaimIsAcceptedAlongsideCode() {
			// For identity providers that cannot make an existing scalar claim
			// multi-valued. Both are read, and duplicates collapse.
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_ADMIN),
				Map.of(AgencyClaims.CODE, "library-a",
					AgencyClaims.AGENCY_CODES, List.of("library-a", "library-b")));

			assertEquals(List.of("library-a", "library-b"), scope.agencyCodes());
		}

		@Test
		void blankEntriesInAMultiValuedClaimAreDropped() {
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_ADMIN),
				Map.of(AgencyClaims.CODE, List.of("library-a", "  ", "")));

			assertEquals(List.of("library-a"), scope.agencyCodes());
		}

		@Test
		void aClaimOfNothingButBlanksIsIncoherentRatherThanUnrestricted() {
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_ADMIN),
				Map.of(AgencyClaims.CODE, List.of("  ", "")));

			assertTrue(scope.isIncoherent());
		}

		@Test
		void readOnlyWithTheClaimIsNarrowedToo() {
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_READ_ONLY), AGENCY);

			assertTrue(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
		}

		@Test
		void libraryAdminWithNoClaimIsIncoherentRatherThanUnrestricted() {
			final var scope = CallerScope.from(List.of(RoleNames.LIBRARY_ADMIN), NO_CLAIMS);

			assertTrue(scope.requiresNarrowing());
			assertTrue(scope.isIncoherent());
			assertTrue(scope.agencyCodes().isEmpty());
		}

		@Test
		void aBlankClaimCountsAsAbsent() {
			// An empty Keycloak attribute is the shape a half-finished backfill takes.
			final var scope = CallerScope.from(
				List.of(RoleNames.LIBRARY_ADMIN), Map.of(AgencyClaims.CODE, "   "));

			assertTrue(scope.isIncoherent());
		}

		@Test
		void aClaimOfTheWrongTypeCountsAsAbsent() {
			// Not a string and not a collection of them - nothing usable.
			final var scope = CallerScope.from(
				List.of(RoleNames.LIBRARY_ADMIN), Map.of(AgencyClaims.CODE, 42));

			assertTrue(scope.isIncoherent());
		}
	}

	@Nested
	@DisplayName("degenerate input")
	class Degenerate {
		@Test
		void noRolesAtAllRequiresNoNarrowingBecauseItReachesNothing() {
			// A caller with neither role set cannot pass @Secured in the first place;
			// this only pins that the type does not throw on the way there.
			final var scope = CallerScope.from(List.of(), NO_CLAIMS);

			assertFalse(scope.requiresNarrowing());
			assertFalse(scope.isIncoherent());
		}

		@Test
		void nullRolesAndAttributesDoNotThrow() {
			final var scope = CallerScope.from(null, null);

			assertFalse(scope.consortiumWide());
			assertTrue(scope.agencyCodes().isEmpty());
		}
	}
}
