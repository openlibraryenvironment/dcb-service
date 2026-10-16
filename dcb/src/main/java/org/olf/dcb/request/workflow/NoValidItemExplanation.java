package org.olf.dcb.request.workflow;

import static services.k_int.utils.StringUtils.truncate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.SupplierPreflightService;
import org.olf.dcb.request.fulfilment.SupplierPlacementGate;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.request.resolution.ExcludeSupplierThatCannotSupplyItemFilter;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Says why a request ended with no item it could be supplied with when suppliers' configuration
 * ruled them out: what is wrong at each, and what that library's staff need to do to fix it.
 */
@Slf4j
@Singleton
@AllArgsConstructor
public class NoValidItemExplanation {
	public static final String FAILED_TO_FIND_A_VALID_ITEM = "Failed to find a valid item";

	private static final String EXCLUDED_AS_UNABLE_TO_SUPPLY = "Excluded by "
		+ ExcludeSupplierThatCannotSupplyItemFilter.class.getSimpleName();

	private final SupplierPreflightService supplierPreflightService;
	private final PatronRequestAuditService patronRequestAuditService;
	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public record Fault(String supplierHostLmsCode, SupplierPreflight preflight) { }

	/** The suppliers of the copies resolution excluded because they cannot supply. */
	public Mono<List<Fault>> faultsBehind(Collection<Item> excludedItems) {
		final var suppliers = new LinkedHashSet<String>();

		excludedItems.stream()
			.filter(item -> item.getDecisionLogEntries() != null
				&& item.getDecisionLogEntries().contains(EXCLUDED_AS_UNABLE_TO_SUPPLY))
			.map(Item::getHostLmsCode)
			.forEach(suppliers::add);

		// Bounded by the systems holding copies of one title
		return Flux.fromIterable(suppliers)
			.concatMap(supplier -> supplierPreflightService.checkSupplier(supplier)
				.filter(SupplierPreflight::cannotSupply)
				.map(preflight -> new Fault(supplier, preflight)))
			.collectList()
			.onErrorReturn(List.of());
	}

	/** The suppliers that refused this request before a hold was placed, as recorded when they did. */
	public Mono<List<Fault>> faultsRecordedOn(PatronRequest patronRequest) {
		return Flux.from(patronRequestAuditRepository.findAllByPatronRequestOrderByAuditDate(patronRequest))
			.filter(audit -> SupplierPlacementGate.SUPPLIER_CANNOT_SUPPLY.equals(audit.getBriefDescription())
				&& audit.getAuditData() != null)
			.map(audit -> new Fault(String.valueOf(audit.getAuditData().get("supplier")), SupplierPreflight.cannotSupply(
				String.valueOf(audit.getAuditData().get("reason")), String.valueOf(audit.getAuditData().get("howToFix")))))
			.collect(LinkedHashMap<String, Fault>::new, (bySupplier, fault) -> bySupplier.put(fault.supplierHostLmsCode(), fault))
			.map(bySupplier -> List.copyOf(bySupplier.values()))
			.onErrorReturn(List.of());
	}

	/** Records the explanation on the request when there are faults; otherwise changes nothing. */
	public Mono<PatronRequest> explain(PatronRequest patronRequest, List<Fault> faults) {
		if (faults.isEmpty()) {
			return Mono.just(patronRequest);
		}

		patronRequest.setErrorMessage(truncate("%s: %s".formatted(FAILED_TO_FIND_A_VALID_ITEM,
			faults.get(0).preflight().reason()), 255));

		final Map<String, Object> auditData = new LinkedHashMap<>();
		auditData.put("explanation", "No item could be supplied, because the configuration of the libraries "
			+ "holding copies rules them out. Each one below says what that library's staff need to do.");
		auditData.put("suppliersThatCannotSupply", faults.stream()
			.map(fault -> Map.of(
				"supplier", fault.supplierHostLmsCode(),
				"reason", fault.preflight().reason(),
				"howToFix", fault.preflight().remedy()))
			.toList());

		return patronRequestAuditService.addAuditEntry(patronRequest, FAILED_TO_FIND_A_VALID_ITEM, auditData)
			.thenReturn(patronRequest)
			.onErrorResume(error -> {
				log.warn("Unable to record why request {} found no valid item", patronRequest.getId(), error);
				return Mono.just(patronRequest);
			});
	}
}
