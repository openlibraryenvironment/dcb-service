package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class UnreadableConfigurationTests {
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();

		hostLmsFixture.createFolioHostLms("folio-unreadable", "https://folio-unreadable.com",
			"api-key", "marc21", "marc21_withholdings");

		hostLmsFixture.createORSApplianceHostLms("ors-unreadable", "https://ors-unreadable.com/ncip");
	}

	@Test
	void shouldSayFolioCannotBeAskedForItsConfiguration() {
		final var client = hostLmsFixture.createClient("folio-unreadable");

		final var report = client.checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.NOT_SUPPORTED));
		assertThat(report.detail(), containsString("edge modules"));

		assertThat(client.checkMappingValue(MappingVocabulary.PATRON_TYPE, "staff").block().result(),
			is(MappingValueCheck.Result.NOT_SUPPORTED));
	}

	@Test
	void shouldSayTheOrsApplianceCannotBeAskedForItsConfiguration() {
		final var client = hostLmsFixture.createClient("ors-unreadable");

		final var report = client.checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.NOT_SUPPORTED));
		assertThat(report.detail(), containsString("NCIP"));

		assertThat(client.checkMappingValue(MappingVocabulary.LOCATION, "MAIN").block().result(),
			is(MappingValueCheck.Result.NOT_SUPPORTED));
	}
}
