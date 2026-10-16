package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.interaction.SupplierPreflight;

import reactor.core.publisher.Mono;

/** No database: a chosen copy is refused before the request exists, with what to do about it. */
class ChosenCopySupplierPreflightCheckTests extends AbstractPreflightCheckTests {
	private final SupplierPreflightService service = mock(SupplierPreflightService.class);
	private final ChosenCopySupplierPreflightCheck check =
		new ChosenCopySupplierPreflightCheck(service, new IntMessageService());

	@Test
	void shouldRefuseAChosenCopyWhoseSupplierCannotSupply() {
		when(service.checkSupplier("SUPPLIER")).thenReturn(Mono.just(
			SupplierPreflight.cannotSupply("Host LMS SUPPLIER refused DCB's credentials", "Correct the credentials")));

		assertThat(check(chosenCopyAt("SUPPLIER")), containsInAnyOrder(failedCheck("SUPPLIER_CANNOT_SUPPLY",
			"Host LMS SUPPLIER refused DCB's credentials. To fix: Correct the credentials")));
	}

	@Test
	void shouldPassAChosenCopyWhoseSupplierCanSupply() {
		when(service.checkSupplier("SUPPLIER")).thenReturn(Mono.just(SupplierPreflight.clear()));

		assertThat(check(chosenCopyAt("SUPPLIER")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenTheSupplierCannotBeChecked() {
		when(service.checkSupplier("SUPPLIER")).thenReturn(Mono.error(new RuntimeException("database unavailable")));

		assertThat(check(chosenCopyAt("SUPPLIER")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldLeaveARequestForAnyCopyToResolution() {
		final var untouched = mock(SupplierPreflightService.class);

		final var results = new ChosenCopySupplierPreflightCheck(untouched, new IntMessageService())
			.check(placeRequestCommand("pickup", "SYS", "patron", UUID.randomUUID()))
			.block();

		assertThat(results, containsInAnyOrder(passedCheck()));

		verifyNoInteractions(untouched);
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return check.check(command).block();
	}

	private static PlacePatronRequestCommand chosenCopyAt(String supplier) {
		return placeRequestCommand("pickup", "SYS", "patron", UUID.randomUUID()).toBuilder()
			.item(PlacePatronRequestCommand.Item.builder().localId("copy").localSystemCode(supplier).agencyCode("agency").build())
			.build();
	}
}
