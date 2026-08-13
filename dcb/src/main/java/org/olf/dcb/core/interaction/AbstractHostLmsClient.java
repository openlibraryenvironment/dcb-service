package org.olf.dcb.core.interaction;

import io.micronaut.core.annotation.NonNull;
import java.util.List;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import reactor.core.publisher.Mono;

public abstract class AbstractHostLmsClient implements HostLmsClient {
	private final HostLms hostLms;

	protected AbstractHostLmsClient(HostLms hostLms) {
		this.hostLms = hostLms;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of();
	}

	@Override
	public Mono<HostLmsRenewal> renew(@NonNull HostLmsRenewal hostLmsRenewal) {
		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(@NonNull LocalRequest localRequest) {
		return Mono.empty();
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bibRecord) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		return Mono.empty();
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		return Mono.empty();
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		return Mono.empty();
	}

	/**
	 * WARNING: this default is a SILENT NO-OP, not a cancellation. FoundationClient and
	 * ORSApplianceHostLMS inherit it, so for those systems an empty result means nothing
	 * was cancelled.
	 *
	 * Every caller MUST treat an empty result as failure. PatronRequestCancellationService
	 * does (switchIfEmpty -> CancellationFailedException) because reporting success to a
	 * patron whose hold is still live is the worst outcome available. The supplier and
	 * pickup cleanup paths (SupplyingAgencyService, PickupAgencyService,
	 * ResolveNextSupplierTransition) currently do NOT, and should be given the same
	 * treatment under their own ticket — changing this default to raise would alter those
	 * flows and needs its own test sweep, so it is deliberately not done here.
	 */
	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.empty();
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {
		return Mono.empty();
	}

	@Override
	public Mono<String> findCanonicalPatronType(String localPatronType, String localId) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> patronAuth(
		String authProfile,
		String patronPrinciple,
		String secret) {

		return Mono.empty();
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand) {
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem item) {
		return Mono.empty();
	}

	@Override
	public Mono<String> updateItemStatus(
		HostLmsItem hostLmsItem,
		CanonicalItemState crs) {

		return Mono.empty();
	}

	@Override
	public Mono<String> checkOutItemToPatron(
		CheckoutItemCommand checkoutItemCommand) {

		return Mono.empty();
	}

	@Override
	public Mono<String> checkInItem(CheckInItemCommand checkInItemCommand) {
		return Mono.empty();
	}

	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {
		return Mono.empty();
	}

	@Override
	public Mono<String> deleteBib(String id) {
		return Mono.empty();
	}

	@Override
	public Mono<String> deleteHold(DeleteCommand deleteCommand) {
		return Mono.empty();
	}

	@Override
	public Mono<String> deletePatron(String id) {
		return Mono.empty();
	}

	@Override
	public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		return Mono.empty();
	}

	@Override
	public Mono<Boolean> supplierPreflight(
		String borrowingAgencyCode,
		String supplyingAgencyCode,
		String canonicalItemType,
		String canonicalPatronType) {

		return Mono.empty();
	}

	/**
	 * Fallback identity for adapters that have not declared how to identify their
	 * system. The Host LMS code is unique per record, so this is safe in the sense
	 * that two systems never compare equal - but it can never detect that two Host
	 * LMS records share one server. Subclasses reaching a real system over HTTP
	 * MUST override with the system's URL. See {@link HostLmsClient#getClientId()}.
	 */
	@Override
	public @NonNull String getClientId() {
		final var hostLmsCode = getHostLmsCode();

		return qualifySystemIdentity(hostLmsCode != null
			? hostLmsCode
			: getClass().getSimpleName());
	}

	@Override
	public Mono<HostLmsItem> getItemByBarcode(String barcode) {
		return Mono.empty();
	}
}
