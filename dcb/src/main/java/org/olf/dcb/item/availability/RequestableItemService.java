package org.olf.dcb.item.availability;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.model.DerivedLoanPolicy;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.Item;

import java.util.Set;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

import static org.olf.dcb.core.model.DerivedLoanPolicy.LOCAL_ONLY;
import static org.olf.dcb.core.model.DerivedLoanPolicy.NO_LEND;
import static org.olf.dcb.core.model.DerivedLoanPolicy.REFERENCE_ONLY;
import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;

@Slf4j
@Prototype
public class RequestableItemService {

	private static final String NONCIRC = "NONCIRC";
	// UNKNOWN is not here: it is the default for an adapter that derives no policy
	private static final Set<DerivedLoanPolicy> NOT_LENT = Set.of(NO_LEND, LOCAL_ONLY, REFERENCE_ONLY);
	private static final Set<DerivedLoanPolicy> NOT_LENT_WITHIN_ITS_SYSTEM = Set.of(NO_LEND, REFERENCE_ONLY);
	private final ConsortiumService consortiumService;

	public RequestableItemService(ConsortiumService consortiumService) {
		this.consortiumService = consortiumService;
	}

	/** Whether any borrower may request the item; this is what availability reports. */
	public Mono<Boolean> isRequestable(Item item) {
		return isRequestable(item, NOT_LENT);
	}

	/**
	 * Whether a borrower on the system that owns the item may request it: a LOCAL_ONLY item is lent to
	 * them. Availability is answered without a borrower, so resolution asks this once it knows one.
	 */
	public Mono<Boolean> isRequestableWithinItsSystem(Item item) {
		return isRequestable(item, NOT_LENT_WITHIN_ITS_SYSTEM);
	}

	private Mono<Boolean> isRequestable(Item item, Set<DerivedLoanPolicy> notLent) {
		return Mono.fromSupplier(() -> initialchecks(item, notLent))
			.flatMap(isRequestable -> {

				if (isRequestable) {
					return includeCheckedOutItems(item);
				}

				return Mono.just(false);
			});
	}

	private boolean initialchecks(Item item, Set<DerivedLoanPolicy> notLent) {
		log.debug("about to perform initial checks for {}", item);

		if (item.isAvailable() || isCheckedOut(item)) {
			log.debug("Item passed initial status check - Status: {}", itemStatus(item));

		} else {
			log.debug("Item rejected - Status: {}", itemStatus(item));

			return false;
		}

		if ( item.getCanonicalItemType() == null ) {
			log.debug("Item has no canonical type - reject");
			return false;
		}

		if ( item.getCanonicalItemType().equals(NONCIRC) ) {
			log.debug("Item is NON-CIRCULATING - reject");
			return false;
		}

		if (notLent.contains(item.getDerivedLoanPolicy())) {
			log.debug("Item loan policy is {} - reject", item.getDerivedLoanPolicy());
			return false;
		}

		log.debug("Initial checks passed");

		return true;
	}

	private Mono<Boolean> includeCheckedOutItems(Item item) {
		return consortiumService.findOneConsortiumFunctionalSetting(SELECT_UNAVAILABLE_ITEMS)
			.filter(FunctionalSetting::isEnabled)
			.hasElement()
			.map(isSwitchedOn -> {

				boolean includeCheckedOut = isSwitchedOn || !isCheckedOut(item);
				log.debug("Include checked out items: {}, item is checked out: {}", isSwitchedOn, isCheckedOut(item));

				return includeCheckedOut;
			});
	}

	private static boolean isCheckedOut(Item item) {
		return item.getStatus() != null && CHECKED_OUT.equals(item.getStatus().getCode());
	}

	private static Object itemStatus(Item item) {
		return item.getStatus() != null ? item.getStatus().getCode() : "UNKNOWN";
	}
}
