package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.ConfigurationReport.CheckResult;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MappingValueCheck;
import org.olf.dcb.core.interaction.MappingVocabulary;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisConfigurationReportTests {
	private static final String HOST_LMS_CODE = "polaris-configuration";
	// HostLmsFixture sets logon-branch-id to 73
	private static final int ILL_LOCATION_ID = 50;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockPolarisFixture mockPolarisFixture;
	private MockServerClient mockServerClient;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		final var host = "polaris-configuration-tests.com";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, "key", "secret", "https://" + host,
			"TEST", "key", "secret", "default-agency-code", ILL_LOCATION_ID);

		mockPolarisFixture = new MockPolarisFixture(host, mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();

		mockPolarisFixture.mockListBranches(PAPIClient.OrganizationsGetResult.builder()
			.papiErrorCode(2)
			.organizationsGetRows(List.of(
				PAPIClient.OrganizationsGetRow.builder().organizationID(73).name("Central").build(),
				PAPIClient.OrganizationsGetRow.builder().organizationID(12).name("Eastside").build()))
			.build());

		mockPolarisFixture.mockListPatronCodes(PAPIClient.PatronCodesGetResult.builder()
			.papiErrorCode(1)
			.patronCodesRows(List.of(
				PAPIClient.PatronCodesRow.builder().patronCodeID(3).description("Adult").build()))
			.build());

		mockPolarisFixture.mockGetMaterialTypes(List.of(
			ApplicationServicesClient.MaterialType.builder().materialTypeID(1).description("Book").build()));
	}

	@Test
	void shouldCheckTheBranchSettingsAgainstPolarisBranches() {
		final var report = client().checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(resultOf(report, "logon-branch-id"), is(CheckResult.PRESENT));

		// 50 is configured as the ILL location, and Polaris has no such branch
		assertThat(resultOf(report, "item.ill-location-id"), is(CheckResult.MISSING));
	}

	@Test
	void shouldCheckEachVocabularyByTheIdDcbMapsOn() {
		final var client = client();

		assertThat(resultOf(client, MappingVocabulary.LOCATION, "12"), is(MappingValueCheck.Result.PRESENT));
		assertThat(resultOf(client, MappingVocabulary.ITEM_TYPE, "1"), is(MappingValueCheck.Result.PRESENT));
		assertThat(resultOf(client, MappingVocabulary.PATRON_TYPE, "3"), is(MappingValueCheck.Result.PRESENT));
		assertThat(resultOf(client, MappingVocabulary.PATRON_TYPE, "4"), is(MappingValueCheck.Result.MISSING));
	}

	@Test
	void shouldReportAnUnreadableListAsUnknownRatherThanMissing() {
		mockPolarisFixture.mockListPatronCodesServerError();

		final var check = client().checkMappingValue(MappingVocabulary.PATRON_TYPE, "3").block();

		assertThat(check.result(), is(MappingValueCheck.Result.UNKNOWN));
		assertThat(check.detail(), containsString("Could not read PATRON_TYPE"));
	}

	private HostLmsClient client() {
		return hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	private static MappingValueCheck.Result resultOf(HostLmsClient client, MappingVocabulary vocabulary,
		String value) {

		return client.checkMappingValue(vocabulary, value).block().result();
	}

	private static CheckResult resultOf(ConfigurationReport report, String setting) {
		return report.checks().stream()
			.filter(check -> setting.equals(check.setting()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No check for " + setting))
			.result();
	}
}
