package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;
import java.util.Map;
import java.time.Duration;

import org.apache.commons.lang3.NotImplementedException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.olf.dcb.interops.ConfigType;
import reactor.core.publisher.Mono;

public interface HostLmsClient extends Comparable<HostLmsClient> {

	Mono<HostLmsRenewal> renew(@NonNull HostLmsRenewal hostLmsRenewal);

	Mono<LocalRequest> updateHoldRequest(@NonNull LocalRequest localRequest);

	// All implementations must understand these states and be able to translate
	// them to
	// local values when encountered via updateRequestStatus
	enum CanonicalRequestState {
		PLACED, TRANSIT;

	}
	enum CanonicalItemState {
		AVAILABLE, TRANSIT, OFFSITE, RECEIVED, MISSING, ONHOLDSHELF, COMPLETED;

	}

	HostLms getHostLms();

	default String getHostLmsCode() {
		return getValueOrNull(getHostLms(), HostLms::getCode);
	}

	default Map<String, Object> getConfig() {
		return getValue(getHostLms(), HostLms::getClientConfig, Map.of());
	}

	// Although we have config for each hostLms,
	// we may want to check the api to get the latest to see if it matches up
	default Mono<Map<String, Object>> fetchConfigurationFromAPI(ConfigType configType) {
		return Mono.error(new NotImplementedException("fetchConfigurationFromAPI is not currently implemented"));
	}

	default String getDefaultAgencyCode() {
		return (String) getConfig().get("default-agency-code");
	}

	List<HostLmsPropertyDefinition> getSettings();

	@ExecuteOn( TaskExecutors.BLOCKING)
	Mono<List<Item>> getItems(BibRecord bibRecord);

	Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron);

	Mono<String> createPatron(Patron patron);

	Mono<String> createBib(Bib bib);

	Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters);

	/**
	 * place a hold for a specified item at a supplying agency.
	 * NOTE: Different systems have different policies. We may need to place a Bib or an Item level hold. If we place a bib level
	 * hold we will not know the barcode and id of the item held until the downstream system has selected an item.
	 * implementers need to take care to return the id and barcode of the ultimately selected item once it is known.
	 */
	Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters);

	Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters);

	Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters);

	Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters);

	/** ToDo: II: The next 2 methods don't feel like they belong here */
	Mono<String> findLocalPatronType(String canonicalPatronType);

	Mono<String> findCanonicalPatronType(String localPatronType, String localId);

	// Look up patron by their internal id - e.g. 1234
	Mono<Patron> getPatronByLocalId(String localPatronId);

	Mono<Patron> getPatronByIdentifier(String id);

	// Look up patron by the string they use to identify themselves on the login screen - e.g. fred.user
	Mono<Patron> getPatronByUsername(String username);

	Mono<Patron> updatePatron(String localId, String patronType);

	Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret);

	Mono<HostLmsItem> createItem(CreateItemCommand createItemCommand);

	Mono<HostLmsRequest> getRequest(HostLmsRequest request);

	Mono<HostLmsItem> getItem(HostLmsItem item);

	Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId);

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand);

	Mono<String> deleteItem(DeleteCommand deleteCommand);

	Mono<String> deleteBib(String id);

	Mono<String> deleteHold(DeleteCommand deleteCommand);

	Mono<String> deletePatron(String id);

	/**
	 * Use whatever system specific method is appropriate to prevent a patron from renewing a loaned item. This could be
	 * setting the renewal count artificially high, issuing a recall, setting other flags on the item. The method used WILL be
   * system specific, the intent is to prevent renewal using whatever method we can coopt. Used primarily when a hold has
	 * been placed on an item at the holding library and that library wishes the item returned. Once a HostClient impl is sure that
	 * renewals have been prevented, they should set renewalStatus to DISALLOWED on PatronRequest so the system knows the call to
	 * prevent renewal has had the desired effect.
	 */
	Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc);

	/**
	 * Return a boolean value which tests if an agency is able to act as a supplier for an item identified
	 * via RTAC. Should test if the canonical item and patron types needed to fulfil the role can be mapped.
	 * Note that a primary concern is also if the item and patron types can be mapped back to the borrowing
	 * system. For example, when requesting Nonagon Infinity from STLouis the local item type is 9. We must be
	 * able to sensibly map this into a canonical item type and from the canonical item type into a type for
	 * the patron home agency.
	 */
	Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType);

	default boolean reflectPatronLoanAtSupplier() {
		return true;
	}
	
	@NonNull
	String getClientId();
	
	@Override
	default int compareTo(HostLmsClient o) {
		return getClientId().compareTo(o.getClientId());
	}

	default Mono<PingResponse> ping() {
		return Mono.just(PingResponse.builder()
			.target(getHostLmsCode())
			.status("Not implemented")
			.pingTime(Duration.ofMillis(0))
			.build());
	}
	
	default String getHostSystemType() {
		return "UNKNOWN";
	}

	default String getHostSystemVersion() {
		return "UNKNOWN";
	}

	
}
