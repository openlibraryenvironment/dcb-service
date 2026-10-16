package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.request.resolution.SupplierRequestService;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/**
 * Decides, before any placement strategy runs, whether a request may be placed at its supplier.
 * A supplier that cannot supply gets no hold; one that was unreachable is placed at, and the audit says so.
 */
@Slf4j
@Singleton
@AllArgsConstructor
public class SupplierPlacementGate {
	public static final String SUPPLIER_CANNOT_SUPPLY = "Supplier cannot supply: request not placed";
	public static final String SUPPLIER_UNREACHABLE = "Supplier unreachable at its last check: placing anyway";

	private static final Duration SUPPLIER_ALARM_LIFETIME = Duration.ofDays(7);

	private final SupplierPreflightService supplierPreflightService;
	private final SupplierRequestService supplierRequestService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final AlarmsService alarmsService;

	/** Anything the check cannot establish lets the request be placed. */
	public Mono<SupplierPreflight> check(RequestWorkflowContext context) {
		final var supplier = getValueOrNull(context, RequestWorkflowContext::getSupplierRequest,
			SupplierRequest::getHostLmsCode);

		if (supplier == null) {
			return Mono.just(SupplierPreflight.notChecked());
		}

		return supplierPreflightService.check(supplier, SupplierPreflightService.queryFor(context))
			.defaultIfEmpty(SupplierPreflight.notChecked())
			.onErrorResume(error -> {
				log.warn("Supplier preflight for {} could not run, placing anyway ({})", supplier, error.toString());
				return Mono.just(SupplierPreflight.notChecked());
			});
	}

	/**
	 * No hold is placed. NOT_SUPPLIED_CURRENT_SUPPLIER hands the request to re-resolution, which tries
	 * another supplier or, where it is off, ends the request with no valid item.
	 * A copy staff chose is not swapped for another: the request errors with the reason instead.
	 */
	public Mono<RequestWorkflowContext> refuse(RequestWorkflowContext context, SupplierPreflight preflight) {
		final var patronRequest = context.getPatronRequest();
		final var supplierRequest = context.getSupplierRequest();
		final var supplier = supplierRequest.getHostLmsCode();

		if (Boolean.TRUE.equals(patronRequest.getIsManuallySelectedItem())) {
			return Mono.error(new SupplierCannotSupplyException(supplier, preflight));
		}

		final Map<String, Object> auditData = new HashMap<>();
		auditData.put("supplier", supplier);
		auditData.put("reason", preflight.reason());
		auditData.put("howToFix", preflight.remedy());
		auditData.put("next", "DCB looks for another supplier where re-resolution is enabled; "
			+ "otherwise no valid item can be found for this request");

		return patronRequestAuditService.addAuditEntry(patronRequest, SUPPLIER_CANNOT_SUPPLY, auditData)
			.then(raiseCannotSupplyAlarm(supplier, preflight))
			.then(Mono.defer(() -> supplierRequestService.updateSupplierRequest(
				supplierRequest.setStatusCode(SupplierRequestStatusCode.CANCELLED))))
			.map(cancelled -> {
				context.setSupplierRequest(cancelled);
				context.setPatronRequest(patronRequest.setStatus(PatronRequest.Status.NOT_SUPPLIED_CURRENT_SUPPLIER));
				return context;
			});
	}

	public Mono<RequestWorkflowContext> warnIfUnreachable(RequestWorkflowContext context, SupplierPreflight preflight) {
		if (!preflight.unreachable()) {
			return Mono.just(context);
		}

		final Map<String, Object> auditData = new HashMap<>();
		auditData.put("supplier", context.getSupplierRequest().getHostLmsCode());
		auditData.put("reason", preflight.reason());

		return patronRequestAuditService.addAuditEntry(context.getPatronRequest(), SUPPLIER_UNREACHABLE, auditData)
			.thenReturn(context);
	}

	private Mono<Void> raiseCannotSupplyAlarm(String supplier, SupplierPreflight preflight) {
		final var alarmCode = "ILS." + supplier + ".SUPPLIER_CANNOT_SUPPLY";

		return alarmsService.raise(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(alarmCode))
				.code(alarmCode)
				.expires(Instant.now().plus(SUPPLIER_ALARM_LIFETIME))
				.alarmDetails(Map.of("reason", preflight.reason(), "howToFix", preflight.remedy()))
				.build())
			.then()
			.onErrorResume(error -> {
				log.warn("Unable to raise {} ({})", alarmCode, error.toString());
				return Mono.empty();
			});
	}
}
