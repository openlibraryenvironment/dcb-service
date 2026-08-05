package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import lombok.Builder;

/**
 * The filter exists to stop DCB creating a virtual copy of a record in the very
 * database that record already lives in, which is what happens when two Host LMS
 * records front one server.
 * <p>
 * It had no tests at all, and until this branch it reached for a "base-url" config
 * key that only three of the six adapters actually use - so on Koha and Alma it
 * failed outright rather than filtering anything.
 */
@DcbTest
class SameServerItemFilterTests {
	private static final String SHARED_SERVER_URL = "https://shared-server.example.com";

	@Inject
	HostLmsFixture hostLmsFixture;

	@Inject
	SameServerItemFilter filter;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();

		// Two Host LMS records, one physical Sierra
		hostLmsFixture.createSierraHostLms("FIRST-ON-SHARED-SERVER",
			"key", "secret", SHARED_SERVER_URL, "item");

		hostLmsFixture.createSierraHostLms("SECOND-ON-SHARED-SERVER",
			"key", "secret", SHARED_SERVER_URL, "item");

		// Same server, but each fronting a distinct logical circulation system
		hostLmsFixture.createSierraHostLms("FIRST-QUALIFIED",
			"key", "secret", SHARED_SERVER_URL, "item", "library-a");

		hostLmsFixture.createSierraHostLms("SECOND-QUALIFIED",
			"key", "secret", SHARED_SERVER_URL, "item", "library-b");

		hostLmsFixture.createSierraHostLms("SEPARATE-SERVER",
			"key", "secret", "https://separate-server.example.com", "item");
	}

	@Test
	void shouldIncludeItemFromAnotherAgencyOnTheSameHostLms() {
		// The ordinary shared-system shape - one Koha, many libraries. Both agencies
		// are on one Host LMS record, which is supported and must not be filtered:
		// workflow routing sends this down RET-LOCAL and no virtual records are made.
		final var hostLms = hostLmsFixture.findByCode("FIRST-ON-SHARED-SERVER");

		assertThat("Item from a co-tenant agency on the same Host LMS should be included",
			evaluate(itemFrom(hostLms), "FIRST-ON-SHARED-SERVER"), is(true));
	}

	@Test
	void shouldIncludeItemFromASeparateServer() {
		final var hostLms = hostLmsFixture.findByCode("SEPARATE-SERVER");

		assertThat("Item from a genuinely separate system should be included",
			evaluate(itemFrom(hostLms), "FIRST-ON-SHARED-SERVER"), is(true));
	}

	@Test
	void shouldExcludeItemFromAnotherHostLmsOnTheSameServer() {
		final var hostLms = hostLmsFixture.findByCode("SECOND-ON-SHARED-SERVER");

		assertThat("Item from a different Host LMS on the same server should be excluded",
			evaluate(itemFrom(hostLms), "FIRST-ON-SHARED-SERVER"), is(false));
	}

	@Test
	void shouldIncludeItemFromASeparatelyQualifiedSystemOnTheSameServer() {
		// An appliance fronting several libraries on one URL: these are separate
		// circulation systems and can genuinely lend to each other.
		final var hostLms = hostLmsFixture.findByCode("SECOND-QUALIFIED");

		assertThat("Differently qualified systems on one URL should be included",
			evaluate(itemFrom(hostLms), "FIRST-QUALIFIED"), is(true));
	}

	@Test
	void shouldExcludeItemWithoutAHostLms() {
		// An item that never resolved to an agency has no Host LMS. Excluding it is
		// the only safe answer, and it must not raise - a filter that errors aborts
		// resolution for the entire cluster over one unattributable item.
		assertThat("Item without a Host LMS should be excluded rather than raise",
			evaluate(Item.builder().build(), "FIRST-ON-SHARED-SERVER"), is(false));
	}

	@Test
	void shouldExcludeWhenTheBorrowingHostLmsIsUnknown() {
		final var hostLms = hostLmsFixture.findByCode("FIRST-ON-SHARED-SERVER");

		assertThat("Unknown borrowing Host LMS should exclude rather than raise",
			evaluate(itemFrom(hostLms), null), is(false));

		assertThat("Unresolvable borrowing Host LMS should exclude rather than raise",
			evaluate(itemFrom(hostLms), "NO-SUCH-HOST-LMS"), is(false));
	}

	private boolean evaluate(Item item, String borrowingHostLmsCode) {
		final var parameters = Parameters.builder()
			.borrowingHostLmsCode(borrowingHostLmsCode)
			.build();

		return singleValueFrom(filter.filterItem(parameters).apply(item));
	}

	private Item itemFrom(DataHostLms hostLms) {
		// Item derives its Host LMS through the agency, which is how resolution sees it
		return Item.builder()
			.agency(DataAgency.builder()
				.code(hostLms.getCode() + "-agency")
				.hostLms(hostLms)
				.build())
			.build();
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode) implements ItemFilterParameters { }
}
