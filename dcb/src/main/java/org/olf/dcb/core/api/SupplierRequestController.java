package org.olf.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.security.rules.SecurityRule.IS_AUTHENTICATED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.util.UUID;

import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.interaction.HostLmsRequest;

import javax.annotation.Nullable;

@Validated
@Secured(IS_AUTHENTICATED)
@Controller("/suppliers/requests")
@Tag(name = "Supplier Request API")
@Slf4j
public class SupplierRequestController {

	private final SupplierRequestService supplierRequestService;
	private final SupplyingAgencyService supplyingAgencyService;
	private final SupplierRequestRepository supplierRequestRepository;
	private final RequestWorkflowContextHelper ctxHelper;

	public SupplierRequestController(
		SupplierRequestService supplierRequestService,
		SupplyingAgencyService supplyingAgencyService,
		SupplierRequestRepository supplierRequestRepository,
		RequestWorkflowContextHelper ctxHelper
	) {
		this.supplierRequestService = supplierRequestService;
		this.supplyingAgencyService = supplyingAgencyService;
		this.supplierRequestRepository = supplierRequestRepository;
		this.ctxHelper = ctxHelper;
	}

  // ==== DTOs ====

	@Serdeable @Value @Builder
	@Schema(name = "CancelSupplierHoldResponse",
		description = "Pauses re-resolution and attempts to cancel the supplier hold.")
	public static class CancelSupplierHoldResponse {
		@Schema(description = "SupplierRequest UUID") UUID supplierRequestId;
		@Schema(description = "Linked PatronRequest UUID") UUID patronRequestId;
		@Schema(description = "True if we set isActive=false on the SupplierRequest in this call") boolean paused;
		@Schema(description = "Supplier status after cancel attempt; 'MISSING' means no longer present")
		String supplierStatus; // may be null if not checked
		@Schema(description = "True if cancelled/absent, false if still present, null if unknown")
		Boolean cancelled;
		@Schema(description = "Optional message (e.g., skipped/verification error)") String message;
	}

  @Serdeable @Value
  @Schema(name="CancelHoldBody")
  public static class CancelHoldBody {
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description="Optional reason to include in audit trails/logs") String reason;
  }

  @Serdeable @Value
  @Schema(name="ActiveToggleBody")
  public static class ActiveToggleBody {
    @Schema(required = true, description="Set the SupplierRequest active state") boolean active;
  }

  @Serdeable @Value @Builder
  @Schema(name="ActiveToggleResponse")
  public static class ActiveToggleResponse {
    @Schema(description = "SupplierRequest UUID") UUID supplierRequestId;
    @Schema(description = "Active flag after update") boolean activeNow;
  }

  // ==== Endpoints ====

  /**
   * Use when borrowing-side has errored AFTER the supplier request was placed.
   * This will (1) pause re-resolution by setting isActive=false, (2) cancel the supplier hold,
   * and (3) attempt to verify the cancel on the remote.
   */
  @Secured(CONSORTIUM_ADMIN)
  @SingleResult
  @Operation(
    summary = "Cancel supplier hold (pause re-resolution)",
    description = """
      Scenario: borrowing request failed after supplier request was placed. \
      This endpoint suppresses re-resolution by setting `isActive=false` on the SupplierRequest, \
      then cancels the supplier hold using a context built from the linked PatronRequest. \
      Use the /active toggle later to re-enable re-resolution once the borrowing error is fixed.
      """
  )
  @Post(uri="/{supplierRequestId}/cancel", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
  public Mono<CancelSupplierHoldResponse> cancelSupplierHold(
    @Parameter(name="supplierRequestId", in=ParameterIn.PATH, required = true) @NotNull UUID supplierRequestId,
    @Body @Valid @Nullable CancelHoldBody body
  ) {

		final var reason = getValueOrNull(body, CancelHoldBody::getReason);
		log.warn("Supplier cancellation triggered by API : {}", reason);

    return Mono.from(supplierRequestRepository.findById(supplierRequestId))
			.switchIfEmpty(Mono.error(
				new HttpStatusException(HttpStatus.NOT_FOUND, "SupplierRequest not found: " + supplierRequestId)
			))
			.flatMap(sr -> {
        // 1) Pause re-resolution
        sr.setIsActive(Boolean.FALSE); // null-safe write
        return supplierRequestService.updateSupplierRequest(sr)
          // 2) Build context from the linked PatronRequest, then ensure SR is present in context
          .then(ctxHelper.fetchWorkflowContext(sr.getPatronRequest().getId()))
					// 3) Cancel hold at supplier
					.flatMap(supplyingAgencyService::cancelHold)
					// 4) Check supplier status in local system
					.flatMap(ctx -> checkSupplierStatus(ctx.getSupplierRequest()))
					.map(check -> CancelSupplierHoldResponse.builder()
						.supplierRequestId(sr.getId())
						.patronRequestId(sr.getPatronRequest() != null ? sr.getPatronRequest().getId() : null)
						.paused(true) // we set isActive=false earlier in this call
						.supplierStatus(check.getSupplierStatus())
						.cancelled(check.getCancelled())
						.message(check.getMessage())
						.build())
          // We keep isActive=false even if cancel/verify fails; pausing the flow intentionally.
          .doOnError(e -> log.error("Cancel supplier hold failed for SR {}: {}", supplierRequestId, e.toString()));
      })
			.onErrorResume(e -> Mono.error(
				new HttpStatusException(HttpStatus.BAD_REQUEST, e.toString() != null ? e.toString() : "Unexpected error")
			));
  }

  /**
   * Explicitly re-enable (or keep disabled) the SupplierRequest’s participation in re-resolution.
   * Set active=true AFTER fixing the borrowing-side error to allow the system to resolve again.
   */
  @Secured(CONSORTIUM_ADMIN)
  @SingleResult
  @Operation(
    summary = "Toggle SupplierRequest active flag",
    description = """
      Sets `isActive` on the SupplierRequest. Use `false` to suppress downstream handling \
      (e.g., skip canceled-handlers / re-resolution). Set `true` to reactivate once you’ve fixed \
      the upstream error and are ready to re-resolve / place a new supplier request.
      """
  )
  @Patch(uri="/{supplierRequestId}/active", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
  public Mono<ActiveToggleResponse> setActive(
    @Parameter(name="supplierRequestId", in=ParameterIn.PATH, required = true) @NotNull UUID supplierRequestId,
    @Body @Valid ActiveToggleBody body
  ) {
    return Mono.from(supplierRequestRepository.findById(supplierRequestId))
			.switchIfEmpty(Mono.error(
				new HttpStatusException(HttpStatus.NOT_FOUND, "SupplierRequest not found: " + supplierRequestId)
			))
      .flatMap(sr -> {
        sr.setIsActive(body.isActive());
        return supplierRequestService.updateSupplierRequest(sr);
      })
      .map(sr -> ActiveToggleResponse.builder()
          .supplierRequestId(sr.getId())
          .activeNow(Boolean.TRUE.equals(sr.getIsActive()))
          .build())
			.onErrorResume(e -> Mono.error(
				new HttpStatusException(HttpStatus.BAD_REQUEST, e.toString() != null ? e.toString() : "Unexpected error")
			));
  }

  // ==== helpers ====

	@Value
	private static class StatusCheck {
		String supplierStatus;   // e.g. "CANCELLED", "PLACED", or "MISSING"
		Boolean cancelled;       // true|false|null (null = unknown)
		String message;          // optional note
	}

	private Mono<StatusCheck> checkSupplierStatus(SupplierRequest sr) {
		final var code = sr.getHostLmsCode();
		final var reqId = sr.getLocalId();

		if (code == null || reqId == null) {
			return Mono.just(new StatusCheck(null, null, "Skipped verification (missing hostLmsCode/localId)"));
		}

		final var localRequestId = sr.getLocalId();
		final var supplierPatronId = getValueOrNull(sr, SupplierRequest::getVirtualIdentity, PatronIdentity::getLocalId);
		final var hostlmsRequest = HostLmsRequest.builder().localId(localRequestId).localPatronId(supplierPatronId).build();

		return supplyingAgencyService.getRequest(code, hostlmsRequest)
			.map(remote -> {
				final var status = getValueOrNull(remote, HostLmsRequest::getStatus);
				final var isCancelled = (status.equals(HOLD_MISSING) || status.equals(HOLD_CANCELLED));
				return new StatusCheck(status, isCancelled, null);
			})
			// No request found at supplier - treat as "MISSING" and cancelled=true
			.switchIfEmpty(Mono.just(new StatusCheck(null, null, "Verification error: response was empty")))
			.onErrorResume(e -> Mono.just(new StatusCheck(null, null, "Verification error: " + e.getMessage())));
	}
}
