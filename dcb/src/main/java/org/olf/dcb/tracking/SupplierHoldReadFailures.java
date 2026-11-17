package org.olf.dcb.tracking;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/**
 * A failed read of the lender's hold is not a status. The supplier request keeps the last status
 * the lender reported; the run of failures is recorded once when it starts and once when it ends,
 * and each lender with failing reads raises one alarm.
 */
@Slf4j
@Singleton
public class SupplierHoldReadFailures {
	static final String READ_FAILED = "Tracking : Supplier hold could not be read";
	static final String READ_RECOVERED = "Tracking : Supplier hold read again";

	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	private final AlarmsService alarmsService;

	public SupplierHoldReadFailures(SupplierRequestRepository supplierRequestRepository,
		PatronRequestAuditService patronRequestAuditService, AlarmsService alarmsService) {

		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestAuditService = patronRequestAuditService;
		this.alarmsService = alarmsService;
	}

	public Mono<SupplierRequest> recordFailure(SupplierRequest sr, Throwable error) {
		log.error("TRACKING could not read supplier hold {} at {}", sr.getId(), sr.getHostLmsCode(), error);

		// Already failing: the run was recorded when it started
		if (sr.getLocalReadFailingSince() != null) {
			return Mono.just(sr);
		}

		final var since = Instant.now();
		final var kind = classify(error);

		final var auditData = new HashMap<String, Object>();
		auditData.put("failure", kind);
		auditData.put("lastKnownStatus", sr.getLocalStatus());
		auditData.put("hostLmsCode", sr.getHostLmsCode());
		auditThrowable(auditData, "Throwable", error);

		return Mono.from(supplierRequestRepository.updateLocalReadFailingSince(sr.getId(), since))
			.then(audit(sr, READ_FAILED + " (" + kind + ")", auditData))
			.then(raiseAlarm(sr))
			.thenReturn(sr.setLocalReadFailingSince(since));
	}

	public Mono<SupplierRequest> recordRecovery(SupplierRequest sr) {
		final var since = sr.getLocalReadFailingSince();

		if (since == null) {
			return Mono.just(sr);
		}

		final var auditData = new HashMap<String, Object>();
		auditData.put("failingSince", since.toString());
		auditData.put("failingFor", Duration.between(since, Instant.now()).toString());

		return Mono.from(supplierRequestRepository.updateLocalReadFailingSince(sr.getId(), null))
			.then(audit(sr, READ_RECOVERED, auditData))
			.thenReturn(sr.setLocalReadFailingSince(null));
	}

	/**
	 * How long to wait before polling a request again. While its lender's hold cannot be read and the
	 * request still waits on that hold, poll no more often than every 5 minutes, then no more often than
	 * the failures have lasted, up to an hour; a status whose own interval is longer keeps it. Once the
	 * copy has left the lender, the borrower's side drives the request and a filled hold that no longer
	 * reads must not slow it.
	 */
	public static Duration pollInterval(Duration normal, PatronRequest.Status status, SupplierRequest sr,
		Instant now) {

		final var failingSince = getValueOrNull(sr, SupplierRequest::getLocalReadFailingSince);

		if (failingSince == null || !WAITING_ON_THE_LENDERS_HOLD.contains(status)) {
			return normal;
		}

		final var backOff = clamp(Duration.between(failingSince, now), FIRST_BACK_OFF, MAX_BACK_OFF);

		return backOff.compareTo(normal) > 0 ? backOff : normal;
	}

	static final Set<PatronRequest.Status> WAITING_ON_THE_LENDERS_HOLD = Set.of(
		PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY,
		PatronRequest.Status.CONFIRMED,
		PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY);

	static final Duration FIRST_BACK_OFF = Duration.ofMinutes(5);
	static final Duration MAX_BACK_OFF = Duration.ofHours(1);

	private static Duration clamp(Duration value, Duration min, Duration max) {
		if (value.compareTo(min) < 0) return min;
		if (value.compareTo(max) > 0) return max;
		return value;
	}

	static String classify(Throwable error) {
		final var status = statusOf(error);

		if (error instanceof TimeoutException || error instanceof ReadTimeoutException
			|| (error.getMessage() != null && error.getMessage().toLowerCase().contains("timeout"))) {
			return "timeout";
		}

		if (status == null) return "other";
		if (status == 401 || status == 403) return "sign-in refused";
		if (status == 404) return "not found";
		if (status >= 500) return "server fault";

		return "HTTP " + status;
	}

	private static Integer statusOf(Throwable error) {
		if (error instanceof HttpClientResponseException responseException) {
			return responseException.getStatus().getCode();
		}

		if (error instanceof ThrowableProblem problem) {
			final var parameters = problem.getParameters();

			for (final var key : new String[] { "responseStatusCode", "causeResponseStatusCode" }) {
				if (parameters.get(key) instanceof Integer status) {
					return status;
				}
			}
		}

		return null;
	}

	private Mono<Void> audit(SupplierRequest sr, String message, HashMap<String, Object> auditData) {
		final var patronRequestId = getValueOrNull(sr, SupplierRequest::getPatronRequest, PatronRequest::getId);

		if (patronRequestId == null) {
			return Mono.empty();
		}

		return patronRequestAuditService.addAuditEntry(patronRequestId, message, auditData).then();
	}

	// One alarm per lender, listing up to 100 of its supplier requests, however many reads fail
	private Mono<Void> raiseAlarm(SupplierRequest sr) {
		final var alarmCode = "ILS." + sr.getHostLmsCode() + ".SUPPLIER_HOLD_READS_FAILING";

		return alarmsService.raiseAccumulating(Alarm.builder()
				.id(UUIDUtils.generateAlarmId(alarmCode))
				.code(alarmCode)
				.expires(Instant.now().plus(Duration.ofDays(2)))
				.build(),
			"supplierRequests", String.valueOf(sr.getId()))
			.onErrorResume(error -> {
				log.warn("Unable to record failing supplier hold reads against {}", alarmCode, error);
				return Mono.empty();
			});
	}
}
