package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.graphql.GraphQLSecurityContextCustomizer.AGENCY_CODES;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import jakarta.inject.Inject;

/**
 * A library's audit events across its requests (S-18): only requests it is party to, a page
 * of at most 200, and pages that continue without overlap.
 */
@DcbTest
@TestInstance(PER_CLASS)
class PatronRequestAuditQueryTests {
	private static final List<String> LIBRARY_ADMIN = List.of("LIBRARY_ADMIN");
	private static final int OWN_EVENTS = 230;

	private static final String AUDITS = """
		query($q: String, $page: Int, $size: Int) {
		  audits(query: $q, pageno: $page, pagesize: $size, order: "auditDate", orderBy: "DESC") {
		    totalSize
		    pageable { number }
		    content { id auditDate patronRequest { id } }
		  }
		}""";

	@Inject
	private GraphQL graphQL;
	@Inject
	private PatronRequestAuditRepository audits;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private UUID ownRequestId;

	@BeforeAll
	void beforeAll() {
		deleteAll();

		final var hostLms = hostLmsFixture.createDummyHostLms("AUDIT_Q_LMS");
		final var own = agencyFixture.defineAgency("AUDIT_Q_OWN", "Own Library", hostLms);
		final var other = agencyFixture.defineAgency("AUDIT_Q_OTHER", "Other Library", hostLms);

		final var ownRequest = request(hostLms, own, "own-patron");
		final var otherRequest = request(hostLms, other, "other-patron");
		ownRequestId = ownRequest.getId();

		final var start = Instant.parse("2026-10-01T00:00:00Z");
		for (int index = 0; index < OWN_EVENTS; index++) {
			audit(ownRequest, start.plusSeconds(index * 60L));
		}
		for (int index = 0; index < 5; index++) {
			audit(otherRequest, start.plusSeconds(index * 60L));
		}
	}

	@AfterAll
	void deleteAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void aLibrarySeesEventsOnlyOnRequestsItIsPartyTo() {
		final var page = page(null, 0, 100);

		assertThat(page.get("totalSize"), is(OWN_EVENTS));
		assertThat(requestIdsOf(page), everyItem(is(ownRequestId.toString())));
	}

	@Test
	void aPageHoldsAtMost200WhateverIsAskedFor() {
		final var page = page(null, 0, 5000);

		assertThat(contentOf(page), hasSize(200));
		assertThat(page.get("totalSize"), is(OWN_EVENTS));
	}

	@Test
	void theNextPageContinuesWithoutOverlap() {
		final var first = idsOf(page(null, 0, 100));
		final var second = idsOf(page(null, 1, 100));

		final var both = new HashSet<>(first);
		both.retainAll(second);

		assertThat(first, hasSize(100));
		assertThat(second, hasSize(100));
		assertThat(both, is(empty()));
	}

	private PatronRequest request(org.olf.dcb.core.model.DataHostLms hostLms,
		org.olf.dcb.core.model.DataAgency agency, String patronHome) {

		final var patron = patronFixture.savePatron(patronHome);
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, patronHome + "-id",
			true, "-", patronHome, agency);

		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.requestingIdentity(identity)
			.status(PatronRequest.Status.CONFIRMED)
			.build());
	}

	private void audit(PatronRequest request, Instant when) {
		singleValueFrom(audits.save(PatronRequestAudit.builder()
			.id(randomUUID())
			.patronRequest(request)
			.auditDate(when)
			.briefDescription("Checked")
			.fromStatus(PatronRequest.Status.CONFIRMED)
			.toStatus(PatronRequest.Status.CONFIRMED)
			.build()));
	}

	private Map<String, Object> page(String query, int pageno, int pagesize) {
		final ExecutionResult result = graphQL.execute(ExecutionInput.newExecutionInput()
			.query(AUDITS)
			.variables(query == null
				? Map.of("page", pageno, "size", pagesize)
				: Map.of("q", query, "page", pageno, "size", pagesize))
			.graphQLContext(Map.of("roles", LIBRARY_ADMIN, AGENCY_CODES, List.of("AUDIT_Q_OWN"),
				"userName", "staff", "currentUser", "staff-sub"))
			.build());

		assertThat("errors: " + result.getErrors(), result.getErrors(), is(empty()));
		final Map<String, Object> data = result.getData();
		return cast(data.get("audits"));
	}

	private static List<Map<String, Object>> contentOf(Map<String, Object> page) {
		return cast(page.get("content"));
	}

	private static List<String> idsOf(Map<String, Object> page) {
		return contentOf(page).stream().map(event -> (String) event.get("id")).toList();
	}

	private static List<String> requestIdsOf(Map<String, Object> page) {
		return contentOf(page).stream()
			.map(event -> (String) ((Map<?, ?>) event.get("patronRequest")).get("id"))
			.toList();
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object value) {
		return (T) value;
	}
}
