package org.olf.dcb.request.lifecycle.ncip.profile.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.request.lifecycle.ncip.profile.api.DcbProfileRegistrationApi;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationException;
import org.olf.dcb.request.lifecycle.ncip.profile.domain.DcbProfileMembership;

public final class DcbProfileAuthPolicy {
	private static final int MAX_AUTH_PROFILE_LENGTH = 64;

	private DcbProfileAuthPolicy() {
	}

	public static DcbProfileRegistrationApi.InvitationPolicy normalize(
		DcbProfileRegistrationApi.InvitationPolicy policy
	) {
		NormalizedAuthPolicy auth = normalize(policy.authProfile(), policy.allowedAuthProfiles());
		return new DcbProfileRegistrationApi.InvitationPolicy(
			policy.hostLmsCode(),
			policy.agencyCode(),
			policy.expectedSymbol(),
			policy.borrowingAllowed(),
			policy.supplyingAllowed(),
			policy.ingestAllowed(),
			auth.defaultProfile(),
			auth.allowedProfiles(),
			policy.maxConsortialLoans(),
			policy.suppressionRulesetName(),
			policy.itemSuppressionRulesetName()
		);
	}

	public static String resolve(DcbProfileMembership invitation, String requestedProfile) {
		NormalizedAuthPolicy auth = normalize(
			text(invitation.getPolicy(), "authProfile"),
			strings(invitation.getPolicy().get("allowedAuthProfiles")));
		String selected = blank(requestedProfile)
			? auth.defaultProfile()
			: requestedProfile.trim();
		if (!auth.allowedProfiles().contains(selected)) {
			throw DcbProfileRegistrationException.conflict(
				"AUTH_PROFILE_NOT_INVITED",
				"Selected authentication profile is not allowed by the invitation.",
				"authProfile");
		}
		return selected;
	}

	public static String approvedOrDefault(DcbProfileMembership membership) {
		String approved = text(membership.getApprovedDescriptor(), "authProfile");
		return blank(approved) ? resolve(membership, null) : approved;
	}

	private static NormalizedAuthPolicy normalize(String defaultProfile, List<String> allowedProfiles) {
		String normalizedDefault = blank(defaultProfile)
			? DataAgency.BASIC_BARCODE_AND_PIN
			: defaultProfile.trim();
		requireValidProfile(normalizedDefault, "policy.authProfile");

		List<String> normalizedAllowed;
		if (allowedProfiles == null || allowedProfiles.isEmpty()) {
			normalizedAllowed = List.of(normalizedDefault);
		} else {
			LinkedHashSet<String> unique = new LinkedHashSet<>();
			for (String allowed : allowedProfiles) {
				String normalized = allowed == null ? "" : allowed.trim();
				requireValidProfile(normalized, "policy.allowedAuthProfiles");
				if (!unique.add(normalized)) {
					throw DcbProfileRegistrationException.invalid(
						"AUTH_PROFILE_DUPLICATE",
						"Allowed authentication profiles must not contain duplicates.",
						"policy.allowedAuthProfiles");
				}
			}
			normalizedAllowed = List.copyOf(unique);
		}

		if (!normalizedAllowed.contains(normalizedDefault)) {
			throw DcbProfileRegistrationException.invalid(
				"AUTH_PROFILE_DEFAULT_NOT_ALLOWED",
				"Default authentication profile must be in the allowed set.",
				"policy.authProfile");
		}
		return new NormalizedAuthPolicy(normalizedDefault, normalizedAllowed);
	}

	private static void requireValidProfile(String profile, String field) {
		if (blank(profile) || profile.length() > MAX_AUTH_PROFILE_LENGTH) {
			throw DcbProfileRegistrationException.invalid(
				"AUTH_PROFILE_INVALID",
				"Authentication profiles must contain 1 to 64 characters.",
				field);
		}
	}

	private static List<String> strings(Object value) {
		if (!(value instanceof List<?> list)) {
			return List.of();
		}
		return list.stream().map(item -> item == null ? null : String.valueOf(item)).toList();
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map != null ? map.get(key) : null;
		return value == null ? null : String.valueOf(value);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private record NormalizedAuthPolicy(String defaultProfile, List<String> allowedProfiles) {
	}
}
