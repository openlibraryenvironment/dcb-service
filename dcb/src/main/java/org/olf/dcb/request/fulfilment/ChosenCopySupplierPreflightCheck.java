package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.IntMessageService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Refuses a request for a chosen copy when its supplier cannot supply, before the request exists,
 * so staff can choose another copy. A request for any copy is left to resolution, which skips them.
 */
@Singleton
@AllArgsConstructor
@Requires(property = "dcb.requests.preflight-checks.chosen-copy-supplier.enabled", defaultValue = "true", notEquals = "false")
public class ChosenCopySupplierPreflightCheck implements PreflightCheck {
	public static final String SUPPLIER_CANNOT_SUPPLY = "SUPPLIER_CANNOT_SUPPLY";

	private final SupplierPreflightService supplierPreflightService;
	private final IntMessageService intMessageService;

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var supplierHostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getItem,
			PlacePatronRequestCommand.Item::getLocalSystemCode);

		if (supplierHostLmsCode == null) {
			return Mono.just(List.of(passed()));
		}

		return supplierPreflightService.checkSupplier(supplierHostLmsCode)
			.map(preflight -> preflight.cannotSupply()
				? failedUm(SUPPLIER_CANNOT_SUPPLY, "%s. To fix: %s".formatted(preflight.reason(), preflight.remedy()),
					intMessageService.getMessage(SUPPLIER_CANNOT_SUPPLY))
				: passed())
			.onErrorReturn(passed())
			.map(List::of);
	}
}
