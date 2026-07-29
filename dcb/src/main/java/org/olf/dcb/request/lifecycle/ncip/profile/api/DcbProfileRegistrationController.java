package org.olf.dcb.request.lifecycle.ncip.profile.api;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.HttpStatus;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationException;
import org.olf.dcb.request.lifecycle.ncip.profile.application.DcbProfileRegistrationService;
import org.olf.dcb.security.RoleNames;

@Controller("/api/v1/dcb-profile-ncip2")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
@Tag(name = "DCB Profile NCIP2.02+ membership")
public class DcbProfileRegistrationController {
	private final DcbProfileRegistrationService registrationService;
	private final SecurityService securityService;

	public DcbProfileRegistrationController(
		DcbProfileRegistrationService registrationService,
		SecurityService securityService
	) {
		this.registrationService = registrationService;
		this.securityService = securityService;
	}

	@Post(value = "/membership-invitations", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Status(HttpStatus.CREATED)
	@Secured({RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN})
	@Operation(
		summary = "Issue a DCB Profile NCIP2.02+ membership invitation",
		description = "Returns the opaque 30-minute invitation once. DCB policy is fixed at issuance.",
		responses = {
			@ApiResponse(responseCode = "201", description = "Invitation issued",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.InvitationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid invitation policy",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.Problem.class))),
			@ApiResponse(responseCode = "403", description = "DCB administrator required")
		}
	)
	public DcbProfileRegistrationApi.InvitationResponse issue(
		@Body @Valid DcbProfileRegistrationApi.IssueInvitationRequest request
	) {
		return registrationService.issue(
			request,
			securityService.username().orElse("unknown-dcb-administrator"));
	}

	@Get(value = "/membership-invitations/current", produces = MediaType.APPLICATION_JSON)
	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Inspect invitation metadata",
		description = "Returns non-secret DCB node and fixed policy metadata needed to create the ORS proof.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Invitation metadata",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.InvitationMetadata.class))),
			@ApiResponse(responseCode = "401", description = "Invalid invitation",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.Problem.class)))
		}
	)
	public DcbProfileRegistrationApi.InvitationMetadata invitation(
		@Header(HttpHeaders.AUTHORIZATION) String authorization
	) {
		return registrationService.invitation(bearer(authorization));
	}

	@Post(value = "/membership-validations", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Validate a proposed membership without consuming the invitation",
		description = "Pulls the ORS directory and JWKS, verifies proof, prerequisites and all DCB object conflicts.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Complete proposed membership is valid",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.ValidationResponse.class))),
			@ApiResponse(responseCode = "400", description = "Prerequisite missing",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.Problem.class))),
			@ApiResponse(responseCode = "409", description = "DCB object or identity conflict",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.Problem.class)))
		}
	)
	public DcbProfileRegistrationApi.ValidationResponse validate(
		@Header(HttpHeaders.AUTHORIZATION) String authorization,
		@Header(DcbProfileRegistrationApi.PROOF_HEADER) String proof,
		@Body @Valid DcbProfileRegistrationApi.RegistrationRequest request
	) {
		return registrationService.validate(bearer(authorization), proof, request);
	}

	@Post(value = "/memberships", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	@Status(HttpStatus.CREATED)
	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(
		summary = "Redeem an invitation",
		description = "Repeats full validation, then creates all DCB objects and consumes the invitation atomically.",
		responses = {
			@ApiResponse(responseCode = "201", description = "Membership active",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.MembershipResponse.class))),
			@ApiResponse(responseCode = "400", description = "Prerequisite missing; invitation remains usable",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.Problem.class))),
			@ApiResponse(responseCode = "409", description = "Conflict or invitation already redeemed",
				content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.Problem.class)))
		}
	)
	public DcbProfileRegistrationApi.MembershipResponse redeem(
		@Header(HttpHeaders.AUTHORIZATION) String authorization,
		@Header(DcbProfileRegistrationApi.PROOF_HEADER) String proof,
		@Body @Valid DcbProfileRegistrationApi.RegistrationRequest request
	) {
		return registrationService.redeem(bearer(authorization), proof, request);
	}

	@Get(value = "/memberships/{id}", produces = MediaType.APPLICATION_JSON)
	@Secured({RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN})
	@Operation(
		summary = "Read DCB Profile NCIP2.02+ membership status",
		responses = @ApiResponse(responseCode = "200", description = "Membership status",
			content = @Content(schema = @Schema(implementation = DcbProfileRegistrationApi.MembershipResponse.class)))
	)
	public DcbProfileRegistrationApi.MembershipResponse status(UUID id) {
		return registrationService.status(id);
	}

	@Post(value = "/memberships/{id}/sync", produces = MediaType.APPLICATION_JSON)
	@Secured({RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN})
	@Operation(summary = "Pull and reconcile current ORS directory metadata")
	public DcbProfileRegistrationApi.MembershipResponse sync(UUID id) {
		return registrationService.sync(id);
	}

	@Post(value = "/memberships/{id}/approve-change", produces = MediaType.APPLICATION_JSON)
	@Secured({RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN})
	@Operation(summary = "Approve a pending sensitive directory change")
	public DcbProfileRegistrationApi.MembershipResponse approveChange(UUID id) {
		return registrationService.approveChange(id);
	}

	@Post(value = "/memberships/{id}/reject-change", produces = MediaType.APPLICATION_JSON)
	@Secured({RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN})
	@Operation(summary = "Reject a pending sensitive directory change")
	public DcbProfileRegistrationApi.MembershipResponse rejectChange(UUID id) {
		return registrationService.rejectChange(id);
	}

	@Post(value = "/memberships/{id}/revoke", produces = MediaType.APPLICATION_JSON)
	@Secured({RoleNames.ADMINISTRATOR, RoleNames.CONSORTIUM_ADMIN})
	@Operation(summary = "Revoke membership while preserving historical DCB data")
	public DcbProfileRegistrationApi.MembershipResponse revoke(UUID id) {
		return registrationService.revoke(id);
	}

	private String bearer(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			throw DcbProfileRegistrationException.unauthorized(
				"INVITATION_REQUIRED", "Invitation bearer token is required.");
		}
		String token = authorization.substring(7).trim();
		if (token.isBlank()) {
			throw DcbProfileRegistrationException.unauthorized(
				"INVITATION_REQUIRED", "Invitation bearer token is required.");
		}
		return token;
	}
}
