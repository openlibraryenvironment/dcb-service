package org.olf.dcb.request.lifecycle.ncip.profile.api;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DcbProfileRegistrationApi {
	public static final String PROFILE_ID = "DCB-NCIP2.02+";
	public static final int PROFILE_VERSION = 1;
	public static final String PROOF_HEADER = "X-OpenRS-Registration-JWT";

	private DcbProfileRegistrationApi() {
	}

	@Serdeable
	@Introspected
	public record InvitationPolicy(
		@NotBlank String hostLmsCode,
		@NotBlank String agencyCode,
		String expectedSymbol,
		boolean borrowingAllowed,
		boolean supplyingAllowed,
		boolean ingestAllowed,
		String authProfile,
		Integer maxConsortialLoans,
		String suppressionRulesetName,
		String itemSuppressionRulesetName
	) {
	}

	@Serdeable
	@Introspected
	public record IssueInvitationRequest(
		String profile,
		Integer profileVersion,
		@NotNull @Valid InvitationPolicy policy
	) {
	}

	@Serdeable
	@Introspected
	public record InvitationResponse(
		UUID invitationId,
		String invitation,
		String profile,
		int profileVersion,
		Instant expiresAt,
		String dcbNodeId,
		String dcbNodeName,
		InvitationPolicy policy
	) {
	}

	@Serdeable
	@Introspected
	public record ReadinessCheck(
		String code,
		String status,
		String message,
		String remediation
	) {
	}

	@Serdeable
	@Introspected
	public record ReadinessResponse(
		boolean ready,
		String profile,
		int profileVersion,
		String dcbBaseUrl,
		List<ReadinessCheck> checks
	) {
		public ReadinessResponse {
			checks = checks == null ? List.of() : List.copyOf(checks);
		}
	}

	@Serdeable
	@Introspected
	public record InvitationMetadata(
		UUID invitationId,
		String profile,
		int profileVersion,
		Instant expiresAt,
		String state,
		String dcbNodeId,
		String dcbNodeName,
		InvitationPolicy policy
	) {
	}

	@Serdeable
	@Introspected
	public record LocationSelection(
		@NotBlank String sourceCode,
		String dcbCode,
		boolean pickup,
		boolean supplying
	) {
	}

	@Serdeable
	@Introspected
	public record RegistrationRequest(
		@NotBlank String directoryUrl,
		@NotBlank String selectedSymbol,
		@NotEmpty List<@Valid LocationSelection> locations,
		@NotBlank String descriptorHash,
		@NotBlank String idempotencyKey
	) {
		public RegistrationRequest {
			locations = locations == null ? List.of() : List.copyOf(locations);
		}
	}

	@Serdeable
	@Introspected
	public record ObjectBinding(
		UUID id,
		String code
	) {
	}

	@Serdeable
	@Introspected
	public record ValidationResponse(
		boolean valid,
		UUID invitationId,
		String descriptorHash,
		Map<String, Object> descriptor,
		List<String> warnings,
		Instant expiresAt
	) {
	}

	@Serdeable
	@Introspected
	public record MembershipResponse(
		UUID membershipId,
		String state,
		String profile,
		int profileVersion,
		String descriptorHash,
		ObjectBinding hostLms,
		ObjectBinding agency,
		ObjectBinding library,
		List<ObjectBinding> locations,
		DcbConnectionMetadata dcb,
		Instant nextSyncAt
	) {
		public MembershipResponse {
			locations = locations == null ? List.of() : List.copyOf(locations);
		}
	}

	@Serdeable
	@Introspected
	public record DcbConnectionMetadata(
		String nodeId,
		String nodeName,
		String baseUrl,
		String ncipUrl,
		String issuer,
		String jwksUrl,
		String outboundAudience,
		String inboundAudience,
		String systemId,
		String agencyId
	) {
	}

	@Serdeable
	@Introspected
	public record Problem(
		String type,
		String title,
		int status,
		String detail,
		String instance,
		String code,
		String field,
		String prerequisite,
		boolean retryable,
		String correlationId
	) {
	}
}
