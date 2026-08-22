package org.olf.dcb.request.lifecycle.ncip.profile.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationException;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;

class DcbProfileAuthPolicyTests {
	@Test
	void defaultsLegacyPolicyToBarcodeAndPin() {
		var normalized = DcbProfileAuthPolicy.normalize(policy(null, null));

		assertEquals(DataAgency.BASIC_BARCODE_AND_PIN, normalized.authProfile());
		assertEquals(List.of(DataAgency.BASIC_BARCODE_AND_PIN), normalized.allowedAuthProfiles());
	}

	@Test
	void normalizesAndResolvesAnAllowedSelection() {
		var normalized = DcbProfileAuthPolicy.normalize(policy(
			" BASIC/BARCODE+PIN ",
			List.of(" BASIC/BARCODE+PIN ", "OIDC")));
		DcbProfileMembership invitation = membership(normalized);

		assertEquals("BASIC/BARCODE+PIN", normalized.authProfile());
		assertEquals(List.of("BASIC/BARCODE+PIN", "OIDC"), normalized.allowedAuthProfiles());
		assertEquals("OIDC", DcbProfileAuthPolicy.resolve(invitation, " OIDC "));
		assertEquals("BASIC/BARCODE+PIN", DcbProfileAuthPolicy.resolve(invitation, null));
	}

	@Test
	void rejectsASelectionNotAllowedByTheInvitation() {
		DcbProfileMembership invitation = membership(DcbProfileAuthPolicy.normalize(policy(
			"BASIC/BARCODE+PIN",
			List.of("BASIC/BARCODE+PIN", "OIDC"))));

		DcbProfileRegistrationException exception = assertThrows(
			DcbProfileRegistrationException.class,
			() -> DcbProfileAuthPolicy.resolve(invitation, "SAML"));

		assertEquals("AUTH_PROFILE_NOT_INVITED", exception.code());
		assertEquals("authProfile", exception.field());
	}

	@Test
	void rejectsADefaultOutsideTheAllowedSet() {
		DcbProfileRegistrationException exception = assertThrows(
			DcbProfileRegistrationException.class,
			() -> DcbProfileAuthPolicy.normalize(policy("OIDC", List.of("SAML"))));

		assertEquals("AUTH_PROFILE_DEFAULT_NOT_ALLOWED", exception.code());
	}

	@Test
	void rejectsDuplicateAllowedProfiles() {
		DcbProfileRegistrationException exception = assertThrows(
			DcbProfileRegistrationException.class,
			() -> DcbProfileAuthPolicy.normalize(policy("OIDC", List.of("OIDC", "OIDC"))));

		assertEquals("AUTH_PROFILE_DUPLICATE", exception.code());
	}

	@Test
	void preservesApprovedSelectionAndDefaultsLegacyDescriptors() {
		DcbProfileMembership selected = membership(policy("OIDC", List.of("OIDC")))
			.setApprovedDescriptor(Map.of("authProfile", "OIDC"));
		DcbProfileMembership legacy = membership(policy(null, null))
			.setApprovedDescriptor(Map.of());

		assertEquals("OIDC", DcbProfileAuthPolicy.approvedOrDefault(selected));
		assertEquals(DataAgency.BASIC_BARCODE_AND_PIN, DcbProfileAuthPolicy.approvedOrDefault(legacy));
	}

	private static DcbProfileRegistrationApi.InvitationPolicy policy(
		String authProfile,
		List<String> allowedAuthProfiles
	) {
		return new DcbProfileRegistrationApi.InvitationPolicy(
			"HOST",
			"AGENCY",
			"symbol",
			true,
			true,
			true,
			authProfile,
			allowedAuthProfiles,
			null,
			null,
			null
		);
	}

	private static DcbProfileMembership membership(
		DcbProfileRegistrationApi.InvitationPolicy policy
	) {
		Map<String, Object> storedPolicy = new LinkedHashMap<>();
		if (policy.authProfile() != null) {
			storedPolicy.put("authProfile", policy.authProfile());
		}
		if (policy.allowedAuthProfiles() != null) {
			storedPolicy.put("allowedAuthProfiles", policy.allowedAuthProfiles());
		}
		return DcbProfileMembership.builder()
			.policy(storedPolicy)
			.build();
	}
}
