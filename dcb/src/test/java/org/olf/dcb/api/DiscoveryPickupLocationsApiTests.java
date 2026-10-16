package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.PICKUP_ANYWHERE;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.discovery.DiscoveryPickupLocationsController.PickupLocation;
import org.olf.dcb.core.api.discovery.DiscoveryPickupLocationsController.PickupLocations;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.LibraryGroupMember;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LibraryFixture;
import org.olf.dcb.test.LocationFixture;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class DiscoveryPickupLocationsApiTests {

	private static final String HOST_LMS_CODE = "discovery-pickup-host-lms";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject private HostLmsFixture hostLmsFixture;
	@Inject private AgencyFixture agencyFixture;
	@Inject private LocationFixture locationFixture;
	@Inject private LibraryFixture libraryFixture;
	@Inject private ConsortiumFixture consortiumFixture;
	private final DataAccess dataAccess = new DataAccess();
	@Inject private LibraryGroupMemberRepository groupMembers;
	@Inject private LocationRepository locationRepository;
	@Inject private LocationService locationService;

	private DataHostLms hostLms;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();
		hostLms = hostLmsFixture.createSierraHostLms(HOST_LMS_CODE);
	}

	@BeforeEach
	void beforeEach() {
		deleteEverythingThisClassWrites();
	}

	@AfterAll
	void afterAll() {
		deleteEverythingThisClassWrites();
	}

	// Members reference libraries and groups, libraries and locations reference agencies
	private void deleteEverythingThisClassWrites() {
		dataAccess.deleteAll(groupMembers.queryAll(), member -> groupMembers.delete(member.getId()));
		consortiumFixture.deleteAll();
		locationFixture.deleteAll();
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void anAgencyWithoutPickupAnywhereOffersOnlyItsOwnPickupLocations() {
		final var home = agencyFixture.defineAgency("pickup-home", "Home Library", hostLms);
		final var other = agencyFixture.defineAgency("pickup-other", "Other Library", hostLms);

		final var main = location(home, "Main desk", "home-main", true, false);
		final var branch = location(home, "Branch desk", "home-branch", true, false);
		location(home, "Stacks", "home-stacks", false, false);
		location(other, "Other desk", "other-desk", true, true);

		final var result = list("/discovery/pickup-locations?agencyCode=pickup-home");

		assertThat(result.locations(), containsInAnyOrder(
			new PickupLocation(main.getId(), "home-main", "Main desk", "pickup-home", "Home Library"),
			new PickupLocation(branch.getId(), "home-branch", "Branch desk", "pickup-home",
				"Home Library")));
		assertThat(result.hasMore(), is(false));
	}

	@Test
	void pickupAnywhereOffersEveryLibrarysLocationsEnabledForIt() {
		final var home = agencyFixture.defineAgency("pickup-home", "Home Library", hostLms);
		final var other = agencyFixture.defineAgency("pickup-other", "Other Library", hostLms);

		enablePickupAnywhereFor(home);

		final var homeEnabled = location(home, "Main desk", "home-main", true, true);
		location(home, "Branch desk", "home-branch", true, false);
		final var otherEnabled = location(other, "Other desk", "other-desk", true, true);
		location(other, "Other stacks", "other-stacks", false, true);

		final var result = list("/discovery/pickup-locations?agencyCode=pickup-home");

		assertThat(result.locations().stream().map(PickupLocation::id).toList(),
			containsInAnyOrder(homeEnabled.getId(), otherEnabled.getId()));
	}

	@Test
	void theIdReturnedIsTheOnePlacementResolvesAsThePickupLocation() {
		final var home = agencyFixture.defineAgency("pickup-home", "Home Library", hostLms);
		final var desk = location(home, "Main desk", "home-main", true, false);

		final var offered = list("/discovery/pickup-locations?agencyCode=pickup-home")
			.locations().get(0);

		// RequestWorkflowContextHelper.resolvePickupLocationAgency resolves the placed
		// pickupLocationCode through exactly this lookup
		final var resolved = singleValueFrom(locationService.findById(offered.id().toString()));

		assertThat(resolved.getId(), is(desk.getId()));
	}

	@Test
	void pagesAreBoundedAndSayWhetherMoreFollow() {
		final var home = agencyFixture.defineAgency("pickup-home", "Home Library", hostLms);

		location(home, "A desk", "home-a", true, false);
		location(home, "B desk", "home-b", true, false);

		final var first = list("/discovery/pickup-locations?agencyCode=pickup-home&size=1");
		final var second = list("/discovery/pickup-locations?agencyCode=pickup-home&size=1&page=1");

		assertThat(first.locations().stream().map(PickupLocation::code).toList(), contains("home-a"));
		assertThat(first.hasMore(), is(true));
		assertThat(second.locations().stream().map(PickupLocation::code).toList(), contains("home-b"));
		assertThat(second.hasMore(), is(false));

		final var oversized = list("/discovery/pickup-locations?agencyCode=pickup-home&size=5000");
		assertThat(oversized.size(), is(100));
		assertThat(oversized.locations(), hasSize(2));
	}

	@Test
	void anUnknownLibraryIsNotFound() {
		final var failure = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(
				HttpRequest.GET("/discovery/pickup-locations?agencyCode=no-such-library")));

		assertThat(failure.getStatus(), is(HttpStatus.NOT_FOUND));
	}

	// No bearer: the route is as public as the library directory
	private PickupLocations list(String uri) {
		return client.toBlocking().retrieve(HttpRequest.GET(uri), PickupLocations.class);
	}

	private Location location(DataAgency agency, String name, String code, boolean isPickup,
		boolean enabledForPickupAnywhere) {

		return singleValueFrom(locationRepository.save(Location.builder()
			.id(randomUUID())
			.name(name)
			.code(code)
			.type("PICKUP")
			.isPickup(isPickup)
			.isEnabledForPickupAnywhere(enabledForPickupAnywhere)
			.isShelving(false)
			.isSupplyingLocation(false)
			.agency(agency)
			.build()));
	}

	private void enablePickupAnywhereFor(DataAgency agency) {
		final var consortium = consortiumFixture.createConsortium();
		consortiumFixture.enableSetting(consortium, PICKUP_ANYWHERE);

		final var library = libraryFixture.defineLibrary(agency.getCode(), null, null);

		singleValueFrom(groupMembers.save(LibraryGroupMember.builder()
			.id(randomUUID())
			.library(library)
			.libraryGroup(consortium.getLibraryGroup())
			.build()));
	}
}
