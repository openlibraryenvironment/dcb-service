package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PLACED;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.AgencyMatchers.hasCode;
import static org.olf.dcb.test.matchers.ModelMatchers.hasId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasNoResolvedAgency;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasResolvedAgency;
import static org.olf.dcb.test.matchers.SupplierRequestMatchers.hasStatusCode;
import static org.olf.dcb.utils.CollectionUtils.firstValueOrNull;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
public class SupplierRequestServiceTests {
	private final String DEFAULT_HOST_LMS_CODE = "default-host-lms";
	private final String DEFAULT_AGENCY_CODE = "default-agency";

	@Inject
	PatronRequestsFixture patronRequestsFixture;
	@Inject
	SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;
	@Inject
	AgencyFixture agencyFixture;
	@Inject
	HostLmsFixture hostLmsFixture;

	@Inject
	SupplierRequestService supplierRequestService;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		agencyFixture.defineAgency(DEFAULT_AGENCY_CODE, "Default Agency",
			hostLmsFixture.createDummyHostLms(DEFAULT_HOST_LMS_CODE));
	}

	@Test
	void shouldFindOnlyActiveSupplierRequest() {
		// Arrange
		final var patronRequest = createPatronRequest();

		final var activeSupplierRequest = createSupplierRequest(patronRequest, true);

		// Act
		final var foundSupplierRequest = findActiveSupplierRequestFor(patronRequest);

		// Assert
		assertThat(foundSupplierRequest, allOf(
			notNullValue(),
			hasId(activeSupplierRequest.getId()),
			hasResolvedAgency(agencyFixture.findByCode(DEFAULT_AGENCY_CODE))
		));
	}

	@Test
	void shouldNotFindInactiveSupplierRequest() {
		// Arrange
		final var patronRequest = createPatronRequest();

		createSupplierRequest(patronRequest, false);
		createInactiveSupplierRequest(patronRequest);

		// Act
		final var foundSupplierRequest = findActiveSupplierRequestFor(patronRequest);

		// Assert
		assertThat("Should be empty when no active supplier request found",
			foundSupplierRequest, nullValue());
	}

	@Test
	void shouldTolerateActiveSupplierRequestWithoutResolvedAgency() {
		// Arrange
		final var patronRequest = createPatronRequest();

		final var activeSupplierRequest = supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(randomUUID())
				.patronRequest(patronRequest)
				.localItemId(randomUUID().toString())
				.hostLmsCode("fakeHostLmsCode")
				.isActive(true)
				.build());

		// Act
		final var foundSupplierRequest = findActiveSupplierRequestFor(patronRequest);

		// Assert
		assertThat(foundSupplierRequest, allOf(
			hasId(activeSupplierRequest.getId()),
			hasNoResolvedAgency()
		));
	}

	@Test
	void shouldTolerateActiveSupplierRequestWithMissingResolvedAgency() {
		// Arrange
		final var patronRequest = createPatronRequest();

		final var missingAgency = createAgency("missing-agency", "Missing agency");

		final var supplierRequest = SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId(randomUUID().toString())
			.hostLmsCode(DEFAULT_HOST_LMS_CODE)
			.resolvedAgency(missingAgency)
			.isActive(true)
			.build();

		supplierRequestsFixture.saveSupplierRequest(supplierRequest);

		agencyFixture.delete(missingAgency);

		// Act
		final var foundSupplierRequest = findActiveSupplierRequestFor(patronRequest);

		// Assert
		assertThat(foundSupplierRequest, hasId(supplierRequest.getId()));
	}

	@Test
	void shouldOnlyFindActiveSupplierRequestsForPatronRequest() {
		// Arrange
		final var patronRequest = createPatronRequest();

		final var activeSupplierRequest = createSupplierRequest(patronRequest, true);

		createSupplierRequest(patronRequest, false);
		createInactiveSupplierRequest(patronRequest);

		// Act
		final var allActiveSupplierRequests = findActiveSupplierRequestsFor(patronRequest);

		// Assert
		assertThat(allActiveSupplierRequests, hasSize(1));

		final var firstSupplierRequest = firstValueOrNull(allActiveSupplierRequests);

		assertThat(firstSupplierRequest, allOf(
			notNullValue(),
			hasId(activeSupplierRequest.getId())
		));
	}

	@Test
	void shouldNotFindSupplierRequestsForOtherPatronRequest() {
		// Arrange
		final var patronRequest = createPatronRequest();
		final var otherPatronRequest = createPatronRequest();

		createSupplierRequest(otherPatronRequest, true);

		// Act
		final var foundSupplierRequest = findActiveSupplierRequestFor(patronRequest);

		// Assert
		assertThat("Should be empty when no supplier request found for that patron request",
			foundSupplierRequest, nullValue());
	}

	@Test
	void shouldFindAgencyFromActiveSupplierRequest() {
		// Arrange
		final var patronRequest = createPatronRequest();

		final var activeAgency = createAgency("active-agency", "Active Agency");

		createSupplierRequest(patronRequest, activeAgency, true);

		// Act
		final var possiblySupplyingAgencies = manyValuesFrom(
			supplierRequestService.findPossiblySupplyingAgencies(patronRequest));

		// Assert
		assertThat(possiblySupplyingAgencies, allOf(
			notNullValue(),
			hasSize(1),
			containsInAnyOrder(
				hasCode("active-agency")
			)
		));
	}

	@Test
	void shouldFindAgenciesFromAllSupplierRequestsForPatronRequest() {
		// Arrange
		final var patronRequest = createPatronRequest();
		final var otherPatronRequest = createPatronRequest();

		final var activeAgency = createAgency("active-agency", "Active Agency");
		final var activeFalseAgency = createAgency("active-false-agency", "Active False Agency");
		final var inactiveAgency = createAgency("inactive-agency", "Inactive Agency");

		createSupplierRequest(patronRequest, activeAgency, true);
		createSupplierRequest(patronRequest, activeFalseAgency, false);
		createInactiveSupplierRequest(patronRequest, inactiveAgency);

		createSupplierRequest(otherPatronRequest, activeAgency, true);
		createInactiveSupplierRequest(otherPatronRequest, inactiveAgency);

		// Act
		final var possiblySupplyingAgencies = manyValuesFrom(
			supplierRequestService.findPossiblySupplyingAgencies(patronRequest));

		// Assert
		assertThat("Should include agencies from different kinds of supplier request",
			possiblySupplyingAgencies, allOf(
				notNullValue(),
				hasSize(3),
				containsInAnyOrder(
					hasCode("active-agency"),
					hasCode("active-false-agency"),
					hasCode("inactive-agency")
				)
		));
	}

	@Test
	void shouldTolerateNullResolvedAgencyWhenFindingAgenciesForAllSupplierRequests() {
		// Arrange
		final var patronRequest = createPatronRequest();

		createSupplierRequest(patronRequest, null, true);
		createSupplierRequest(patronRequest, null, false);
		createInactiveSupplierRequest(patronRequest, null);

		// Act
		final var possiblySupplyingAgencies = manyValuesFrom(
			supplierRequestService.findPossiblySupplyingAgencies(patronRequest));

		// Assert
		assertThat("Should ignore null resolved agencies",
			possiblySupplyingAgencies, empty());
	}

	@Test
	void shouldUpdateSupplierRequestAndFetchTheUpdatedSupplierRequest() {
		// Create a new PatronRequest and SupplierRequest
		final var patronRequest = createPatronRequest();

		final var supplierRequestId = randomUUID();

		// Create a new SupplierRequest
		var initialSupplierRequest = SupplierRequest.builder()
			.id(supplierRequestId)
			.patronRequest(patronRequest)
			.localItemId("itemId")
			.localItemBarcode("itemBarcode")
			.localItemLocationCode("ItemLocationCode")
			.hostLmsCode(DEFAULT_HOST_LMS_CODE)
			.isActive(true)
			.build();

		// Save the initialSupplierRequest
		supplierRequestsFixture.saveSupplierRequest(initialSupplierRequest);

		// Set the supplier request values
		initialSupplierRequest.setStatusCode(PLACED);
		initialSupplierRequest.setLocalId("37024897");
		initialSupplierRequest.setLocalStatus("0");

		// Update the supplierRequest and retrieve the updated supplierRequest
		singleValueFrom(supplierRequestService.updateSupplierRequest(initialSupplierRequest));

		// Fetch the supplier requests for the patronRequest and assert the values of the updated supplierRequest
		final var updatedSupplierRequest = findActiveSupplierRequestFor(patronRequest);

		// Assert the values of the updated supplierRequest
		assertThat(updatedSupplierRequest, allOf(
			hasId(supplierRequestId),
			hasStatusCode(PLACED),
			hasLocalId("37024897"),
			hasLocalStatus("0")
		));
	}

	private PatronRequest createPatronRequest() {
		var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.build();

		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private SupplierRequest createSupplierRequest(PatronRequest patronRequest, boolean active) {
		return createSupplierRequest(patronRequest, agencyFixture.findByCode(DEFAULT_AGENCY_CODE), active);
	}

	private SupplierRequest createSupplierRequest(PatronRequest patronRequest,
		DataAgency agency, boolean active) {

		final var activeSupplierRequest = SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId(randomUUID().toString())
			.hostLmsCode(DEFAULT_HOST_LMS_CODE)
			.resolvedAgency(agency)
			.isActive(active)
			.build();

		return supplierRequestsFixture.saveSupplierRequest(activeSupplierRequest);
	}

	private void createInactiveSupplierRequest(PatronRequest patronRequest) {
		createInactiveSupplierRequest(patronRequest, agencyFixture.findByCode(DEFAULT_AGENCY_CODE));
	}

	private void createInactiveSupplierRequest(PatronRequest patronRequest, DataAgency agency) {
		final var inactiveSupplierRequest = InactiveSupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId(randomUUID().toString())
			.hostLmsCode(DEFAULT_HOST_LMS_CODE)
			.resolvedAgency(agency)
			// Does not set the active flag as the production code does not set it either
			.build();

		inactiveSupplierRequestsFixture.save(inactiveSupplierRequest);
	}

	private DataAgency createAgency(String code, String name) {
		return agencyFixture.defineAgency(code, name,
			hostLmsFixture.findByCode(DEFAULT_HOST_LMS_CODE));
	}

	private SupplierRequest findActiveSupplierRequestFor(PatronRequest patronRequest) {
		return singleValueFrom(supplierRequestService.findActiveSupplierRequestFor(patronRequest));
	}

	private List<SupplierRequest> findActiveSupplierRequestsFor(PatronRequest patronRequest) {
		return singleValueFrom(supplierRequestService.findAllActiveSupplierRequestsFor(patronRequest));
	}
}
