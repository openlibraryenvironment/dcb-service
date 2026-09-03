package org.olf.dcb.graphql;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import services.k_int.data.querying.QueryPath;

/**
 * The ownership paths, asserted as data.
 *
 * These are the whole of the access control for three fetchers, and they are declarative -
 * there is no branch to step through and no exception to observe. A path that silently
 * points at the wrong association does not throw; it returns somebody else's rows, or
 * nobody's, and both look like working code.
 */
class AgencyAccessScopeTests {

	@Test
	@DisplayName("A patron request belongs to both ends of the transaction")
	void patronRequestIsOwnedByBorrowerAndSupplier() {
		assertThat(AgencyAccessScope.PATRON_REQUEST_OWNERSHIP, hasSize(2));

		assertThat(AgencyAccessScope.PATRON_REQUEST_OWNERSHIP, containsInAnyOrder(
			// The supplier is recorded on the supplier request, not on the request itself
			new QueryPath(List.of("supplierRequests"), "localAgency", QueryPath.MatchMode.EQUALS),
			// The patron's agency is resolved onto the requesting identity
			new QueryPath(List.of("requestingIdentity", "resolvedAgency"), "code",
				QueryPath.MatchMode.EQUALS)));
	}

	@Test
	@DisplayName("An audit inherits its request's ownership exactly, one association out")
	void auditOwnershipIsDerivedFromTheRequestItAudits() {
		final var audit = AgencyAccessScope.PATRON_REQUEST_AUDIT_OWNERSHIP;
		final var request = AgencyAccessScope.PATRON_REQUEST_OWNERSHIP;

		// Derived, not restated. If a third owner is ever added to the request - pickup,
		// say, which AgencyAccessScope names as a candidate - this must gain it for free.
		assertThat("the audit must inherit every one of the request's owners",
			audit, hasSize(request.size()));

		for (int i = 0; i < request.size(); i++) {
			final var expected = request.get(i).under("patronRequest");

			assertThat(audit.get(i), is(expected));
		}
	}

	@Test
	@DisplayName("A supplier request is owned by its own supplier, not by every supplier of the request")
	void supplierRequestOwnershipDoesNotWidenThroughTheParent() {
		final var ownership = AgencyAccessScope.SUPPLIER_REQUEST_OWNERSHIP;

		assertThat(ownership, hasSize(2));

		// The supplying side reads the row's OWN column. Pushing the request's supplying
		// path down instead - patronRequest -> supplierRequests -> localAgency - would
		// let a library that supplied an abandoned earlier attempt read the row belonging
		// to the library that supplied the successful one.
		assertThat(ownership.get(0),
			is(new QueryPath(List.of(), "localAgency", QueryPath.MatchMode.EQUALS)));

		assertThat(ownership.get(1),
			is(new QueryPath(List.of("patronRequest", "requestingIdentity", "resolvedAgency"),
				"code", QueryPath.MatchMode.EQUALS)));
	}

	@Test
	@DisplayName("under() prefixes the association and keeps everything else")
	void underPrefixesRatherThanReplaces() {
		final var identity = QueryPath.joining("requestingIdentity", "resolvedAgency")
			.matching("code");

		assertThat(identity.under("patronRequest"),
			is(new QueryPath(List.of("patronRequest", "requestingIdentity", "resolvedAgency"),
				"code", QueryPath.MatchMode.EQUALS)));

		// A CONTAINS path must not quietly become an EQUALS one on the way out
		final var barcode = QueryPath.joining("requestingIdentity").containing("localBarcode");

		assertThat(barcode.under("patronRequest"),
			is(new QueryPath(List.of("patronRequest", "requestingIdentity"), "localBarcode",
				QueryPath.MatchMode.CONTAINS)));
	}

	@Test
	@DisplayName("property() is a path with no joins")
	void propertyHasNoJoins() {
		assertThat(QueryPath.property("localAgency"),
			is(new QueryPath(List.of(), "localAgency", QueryPath.MatchMode.EQUALS)));
	}
}
