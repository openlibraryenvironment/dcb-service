package org.olf.dcb.request.workflow;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.olf.dcb.request.fulfilment.PatronRequestService.ROLLBACK_ACTIONED;
import static org.olf.dcb.request.fulfilment.SupplierPlacementGate.SUPPLIER_REFUSED;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads which suppliers refused a request when its hold was placed, from the audit entries {@link
 * org.olf.dcb.request.fulfilment.SupplierPlacementGate#refused} records. A supplier the preflight
 * ruled out is not among them.
 */
@Singleton
@AllArgsConstructor
public class SupplierRefusalHistory {
	private final PatronRequestAuditRepository patronRequestAuditRepository;

	/** Whether any supplier refused this request when its hold was placed. Reads one request's audit entries. */
	public Mono<Boolean> wasRefused(PatronRequest patronRequest) {
		return Flux.from(patronRequestAuditRepository.findAllByPatronRequestOrderByAuditDate(patronRequest))
			.any(audit -> SUPPLIER_REFUSED.equals(audit.getBriefDescription()));
	}

	/**
	 * The systems whose every refusal of this request came before staff last rolled it back from
	 * ERROR, which is how staff say a configuration was fixed. Empty when it was never rolled back.
	 * Reads one request's audit entries.
	 */
	public Mono<Set<String>> refusedOnlyBeforeLastRollback(PatronRequest patronRequest) {
		return Flux.from(patronRequestAuditRepository.findAllByPatronRequestOrderByAuditDate(patronRequest))
			.collectList()
			.map(SupplierRefusalHistory::refusedOnlyBeforeLastRollback);
	}

	static Set<String> refusedOnlyBeforeLastRollback(List<PatronRequestAudit> audits) {
		final var lastRollback = audits.stream()
			.filter(audit -> ROLLBACK_ACTIONED.equals(audit.getBriefDescription()))
			.map(PatronRequestAudit::getAuditDate)
			.filter(Objects::nonNull)
			.max(Instant::compareTo);

		if (lastRollback.isEmpty()) {
			return Set.of();
		}

		final Map<String, Instant> lastRefusalBySupplier = audits.stream()
			.filter(audit -> SUPPLIER_REFUSED.equals(audit.getBriefDescription())
				&& audit.getAuditDate() != null
				&& audit.getAuditData() != null
				&& audit.getAuditData().get("supplier") != null)
			.collect(toMap(audit -> String.valueOf(audit.getAuditData().get("supplier")),
				PatronRequestAudit::getAuditDate, BinaryOperator.maxBy(Instant::compareTo)));

		return lastRefusalBySupplier.entrySet().stream()
			.filter(lastRefusal -> lastRefusal.getValue().isBefore(lastRollback.get()))
			.map(Map.Entry::getKey)
			.collect(toUnmodifiableSet());
	}
}
