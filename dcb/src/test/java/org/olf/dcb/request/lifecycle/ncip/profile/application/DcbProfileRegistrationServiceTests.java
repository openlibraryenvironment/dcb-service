package org.olf.dcb.request.lifecycle.ncip.profile.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.lifecycle.ncip.NcipIdentityConfiguration;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;
import org.olf.dcb.request.lifecycle.ncip.profile.persistence.DcbProfileMembershipRepository;
import org.olf.dcb.request.lifecycle.ncip.profile.support.DcbProfileDirectoryPullService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LocationRepository;
import reactor.core.publisher.Mono;

class DcbProfileRegistrationServiceTests {
	@Test
	void configuresRegisteredOrsPeersForDeclarativeNcipLifecycle() {
		assertEquals(
			Map.of(
				"strategy", "declarative",
				"protocol", "ncip-v202"),
			DcbProfileRegistrationService.lifecycleCapabilities()
				.get("supplying-agency-request"));
		assertEquals(
			Map.of(
				"strategy", "declarative",
				"protocol", "ncip-v202"),
			DcbProfileRegistrationService.lifecycleCapabilities()
				.get("borrowing-agency-request"));
		assertEquals(
			Map.of(
				"mode", "event-driven",
				"protocol", "ncip-v202"),
			DcbProfileRegistrationService.lifecycleCapabilities()
				.get("supplier-tracking"));
		assertEquals(
			Map.of(
				"mode", "event-driven",
				"protocol", "ncip-v202"),
			DcbProfileRegistrationService.lifecycleCapabilities()
				.get("borrower-tracking"));
	}

	@Test
	void blocksDirectInvitationIssuanceWhenTheDcbNodeIsNotReady() {
		DcbProfileMembershipRepository membershipRepository = mock(DcbProfileMembershipRepository.class);
		DcbProfileReadinessService readinessService = mock(DcbProfileReadinessService.class);
		doThrow(DcbProfileRegistrationException.notReady(
			"PROFILE_REGISTRATION_NOT_READY",
			"Not ready."))
			.when(readinessService).requireReady();
		DcbProfileRegistrationService service = service(membershipRepository, readinessService);

		DcbProfileRegistrationException exception = assertThrows(
			DcbProfileRegistrationException.class,
			() -> service.issue(validRequest(), "admin"));

		assertEquals("PROFILE_REGISTRATION_NOT_READY", exception.code());
		verify(readinessService).requireReady();
		verifyNoInteractions(membershipRepository);
	}

	@Test
	void issuesAnInvitationAfterReadinessPasses() {
		DcbProfileMembershipRepository membershipRepository = mock(DcbProfileMembershipRepository.class);
		when(membershipRepository.save(any(DcbProfileMembership.class)))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		DcbProfileReadinessService readinessService = mock(DcbProfileReadinessService.class);
		DcbProfileRegistrationService service = service(membershipRepository, readinessService);

		var response = service.issue(validRequest(), "admin");

		assertEquals(DcbProfileRegistrationApi.PROFILE_ID, response.profile());
		assertEquals("TECH-DEMO-001", response.policy().hostLmsCode());
		verify(readinessService).requireReady();
		verify(membershipRepository).save(any(DcbProfileMembership.class));
	}

	private static DcbProfileRegistrationService service(
		DcbProfileMembershipRepository membershipRepository,
		DcbProfileReadinessService readinessService
	) {
		return new DcbProfileRegistrationService(
			membershipRepository,
			mock(HostLmsRepository.class),
			mock(AgencyRepository.class),
			mock(LibraryRepository.class),
			mock(LocationRepository.class),
			mock(R2dbcOperations.class),
			mock(DcbProfileDirectoryPullService.class),
			new DcbProfileRegistrationProperties(),
			new DcbPeerAuthProperties(),
			new NcipIdentityConfiguration(),
			readinessService
		);
	}

	private static DcbProfileRegistrationApi.IssueInvitationRequest validRequest() {
		return new DcbProfileRegistrationApi.IssueInvitationRequest(
			DcbProfileRegistrationApi.PROFILE_ID,
			DcbProfileRegistrationApi.PROFILE_VERSION,
			new DcbProfileRegistrationApi.InvitationPolicy(
				"TECH-DEMO-001",
				"TECH-DEMO-001",
				"tech-demo-001",
				true,
				true,
				true,
				null,
				null,
				null,
				null
			)
		);
	}
}
