package org.olf.dcb.core.svc;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.AgencyMatchers.hasCode;
import static org.olf.dcb.test.matchers.AgencyMatchers.hasHostLms;
import static org.olf.dcb.test.matchers.AgencyMatchers.hasNoHostLms;
import static org.olf.dcb.test.matchers.ModelMatchers.hasId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class AgencyServiceTests {
	@Inject
	AgencyFixture agencyFixture;
	@Inject
	HostLmsFixture hostLmsFixture;

	@Inject
	AgencyService agencyService;

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldFindAgencyByCode() {
		// Arrange
		final var dummyHostLms = hostLmsFixture.createDummyHostLms("dummy-host-lms");

		final var exampleAgency = agencyFixture.defineAgency("example-agency",
			"Example Agency", dummyHostLms);

		// Act
		final var foundAgency = findByCode("example-agency");

		// Assert
		assertThat(foundAgency, allOf(
			hasId(exampleAgency.getId()),
			hasCode("example-agency"),
			hasHostLms(dummyHostLms)
		));
	}

	@Test
	void shouldFindAgencyThatDoesNotHaveHostLMSByCode() {
		// Arrange
		final var exampleAgency = agencyFixture.defineAgencyWithNoHostLms(
			"example-agency", "Example Agency");

		// Act
		final var foundAgency = findByCode("example-agency");

		// Assert
		assertThat(foundAgency, allOf(
			hasId(exampleAgency.getId()),
			hasCode("example-agency"),
			hasNoHostLms()
		));
	}

	@Test
	void shouldNotFindUnknownAgencyByCode() {
		// Act
		final var foundAgency = findByCode("unknown-agency");

		// Assert
		assertThat(foundAgency, is(nullValue()));
	}

	private DataAgency findByCode(String code) {
		return singleValueFrom(agencyService.findByCode(code));
	}
}
