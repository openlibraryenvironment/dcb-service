package org.olf.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
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
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.time.Instant;
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

	@Serdeable
	public record ApiError(
		String code,
		String message,
		Integer status,
		String path,
		String method,
		String timestamp,
		@Nullable String correlationId,
		@Nullable String detail
	) {}

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
	public Mono<MutableHttpResponse<Object>> cancelSupplierHold(
		@Parameter(name="supplierRequestId", in=ParameterIn.PATH, required = true) @NotNull UUID supplierRequestId,
		@Body @Valid @Nullable CancelHoldBody body, HttpRequest<?> request
	) {

		final var reason = getValueOrNull(body, CancelHoldBody::getReason);
		log.warn("Supplier cancellation triggered by API : {}", reason);

		return Mono.from(supplierRequestRepository.findById(supplierRequestId))
			.flatMap(sr -> {
				// 1) Build context from the linked PatronRequest, then ensure SR is present in context
				return ctxHelper.fetchWorkflowContext(sr.getPatronRequest().getId())
					// 2) Cancel hold at supplier
					.flatMap(supplyingAgencyService::cancelHold)
					// 3) Check supplier status in local system
					.flatMap(ctx -> checkSupplierStatus(ctx.getSupplierRequest()))
					// 4) Confirm cancel, then pause re-resolution
					.flatMap(check -> {
						final boolean cancelled = Boolean.TRUE.equals(check.getCancelled());

						// If not cancelled, do not pause; just report back.
						if (!cancelled) {
							return Mono.just(ok(buildCancelResponse(sr, false, check)));
						}

						// Cancelled: only write if we actually need to flip the flag.
						if (!Boolean.FALSE.equals(sr.getIsActive())) {
							sr.setIsActive(Boolean.FALSE);
							return supplierRequestService.updateSupplierRequest(sr)
								.thenReturn(ok(buildCancelResponse(sr, true, check)));
						}

						// Already paused; report consistent outcome.
						return Mono.just(ok(buildCancelResponse(sr, true, check)));
					})
					// We keep isActive=false even if cancel/verify fails; pausing the flow intentionally.
					.onErrorResume(e -> {
						log.error("Cancel supplier hold failed for SR {}: {}", supplierRequestId, e.toString(), e);
						return Mono.just(error(HttpStatus.BAD_GATEWAY, request,
							"SUPPLIER_ERROR", "Failed to cancel hold at supplier", e.getMessage()));
					});
			})
			.switchIfEmpty(Mono.defer(() -> Mono.just(
				error(HttpStatus.NOT_FOUND, request, "SR_NOT_FOUND",
					"SupplierRequest not found: " + supplierRequestId, null)
			)))
			.onErrorResume(e -> Mono.just(
				error(HttpStatus.INTERNAL_SERVER_ERROR, request, "CANCEL_FAILED",
					"Unexpected error during cancellation", e.getMessage())
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
	public Mono<MutableHttpResponse<Object>> setActive(
		@Parameter(name="supplierRequestId", in=ParameterIn.PATH, required = true) @NotNull UUID supplierRequestId,
		@Body @Valid ActiveToggleBody body, HttpRequest<?> request
	) {
		return Mono.from(supplierRequestRepository.findById(supplierRequestId))
			.flatMap(sr -> {
				sr.setIsActive(body.isActive());
				return supplierRequestService.updateSupplierRequest(sr)
					.map(updated -> ok(ActiveToggleResponse.builder()
						.supplierRequestId(updated.getId())
						.activeNow(TRUE.equals(updated.getIsActive()))
						.build()))
					.onErrorResume(e -> Mono.just(
						error(HttpStatus.BAD_REQUEST, request, "ACTIVE_TOGGLE_FAILED",
							"Failed to update isActive flag", e.getMessage())
					));
			})
			.switchIfEmpty(Mono.defer(() -> Mono.just(
				error(HttpStatus.NOT_FOUND, request, "SR_NOT_FOUND",
					"SupplierRequest not found: " + supplierRequestId, null)
			)))
			.onErrorResume(e -> Mono.just(
				error(HttpStatus.INTERNAL_SERVER_ERROR, request, "ACTIVE_TOGGLE_ERROR",
					"Unexpected error toggling active flag", e.getMessage())
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

	private static String ts() { return Instant.now().toString(); }

	private static String reqId(HttpRequest<?> req) {
		return req != null ? req.getHeaders().get("X-Request-Id") : null;
	}

	private MutableHttpResponse<Object> error(HttpStatus status, HttpRequest<?> req,
																						String code, String message, @Nullable String detail) {
		return HttpResponse.status(status).body(
			new ApiError(code, message, status.getCode(),
				req != null ? req.getPath() : null,
				req != null ? req.getMethodName() : null,
				ts(), reqId(req), detail)
		);
	}

	private MutableHttpResponse<Object> ok(Object body) {
		return HttpResponse.ok(body);
	}

	private CancelSupplierHoldResponse buildCancelResponse(
		SupplierRequest sr, boolean paused, StatusCheck check) {
		return CancelSupplierHoldResponse.builder()
			.supplierRequestId(sr.getId())
			.patronRequestId(sr.getPatronRequest() != null ? sr.getPatronRequest().getId() : null)
			.paused(paused)
			.supplierStatus(check.getSupplierStatus())
			.cancelled(check.getCancelled())
			.message(check.getMessage())
			.build();
	}

}
