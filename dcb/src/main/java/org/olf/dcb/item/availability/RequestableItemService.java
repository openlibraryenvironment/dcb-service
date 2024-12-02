package org.olf.dcb.item.availability;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.Item;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;

@Slf4j
@Prototype
public class RequestableItemService {

	private static final String NONCIRC = "NONCIRC";
	private final ConsortiumService consortiumService;

	public RequestableItemService(ConsortiumService consortiumService) {
		this.consortiumService = consortiumService;
	}

	public Mono<Boolean> isRequestable(Item item) {
		return Mono.fromSupplier(() -> initialchecks(item))
			.flatMap(isRequestable -> {

				if (isRequestable) {
					return includeCheckedOutItems(item);
				}

				return Mono.just(false);
			});
	}

	private boolean initialchecks(Item item) {
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
