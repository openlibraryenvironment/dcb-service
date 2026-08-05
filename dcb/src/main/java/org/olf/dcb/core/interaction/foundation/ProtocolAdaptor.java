package org.olf.dcb.core.interaction.foundation;

import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.foundation.commands.FoundationCreateItemCommand;
import org.olf.dcb.core.interaction.foundation.strategies.CirculationStrategy;
import org.olf.dcb.core.interaction.foundation.strategies.PatronStrategy;
import reactor.core.publisher.Mono;

/**
 * The base protocol surface a Foundation host speaks (NCIP today, SIP2 later).
 *
 * Operations that every concrete protocol adaptor currently implements are
 * declared abstract. Operations that are not yet wired for the baseline
 * protocols (delete/bib/cancel families) default to a fast-failing
 * not-implemented Mono so partial adaptors stay small; a concrete adaptor
 * overrides them when the corresponding protocol slice lands (e.g. NCIP
 * CancelRequestItem / DeleteItem in the shared NCIP module).
 */
public interface ProtocolAdaptor extends CirculationStrategy, PatronStrategy {

	// Handshake / Availability
	Mono<Boolean> isAvailable();

	// ========================================================================
	// 1. CREATE ITEM (NCIP: AcceptItem)
	// Used to create virtual items in the borrower's LMS
	// ========================================================================
	Mono<HostLmsItem> createItem(FoundationCreateItemCommand command);

	Mono<HostLmsItem> getItem(String localItemId);

	default Mono<String> deleteItem(DeleteCommand deleteCommand) {
		return notImplemented("deleteItem");
	}

	// Bib operations
	default Mono<String> createBib(Bib bib) {
		return notImplemented("createBib");
	}

	default Mono<String> deleteBib(String bibId) {
		return notImplemented("deleteBib");
	}

	default Mono<String> getBib(String bibId) {
		return notImplemented("getBib");
	}

	// Patron Ops
	Mono<String> createPatron(Patron patron);

	default Mono<String> deletePatron(String localId) {
		return notImplemented("deletePatron");
	}

	Mono<Patron> findPatron(String localId);
	Mono<Patron> findPatronByBarcode(String barcode);

	// Circulation Ops
	default Mono<String> checkInItem(CheckoutItemCommand command) {
		return notImplemented("checkInItem");
	}

	Mono<String> checkOutItem(CheckoutItemCommand command);

	// Request Ops
	Mono<LocalRequest> placeHold(PlaceHoldRequestParameters parameters);

	default Mono<String> deleteHold(DeleteCommand deleteCommand) {
		return notImplemented("deleteHold");
	}

	default Mono<String> cancelHold(CancelHoldRequestParameters cancelHoldRequestParameters) {
		return notImplemented("cancelHold");
	}

	static <T> Mono<T> notImplemented(String operation) {
		return Mono.error(new UnsupportedOperationException(
			"ProtocolAdaptor." + operation + " is not implemented for this protocol yet"));
	}
}
