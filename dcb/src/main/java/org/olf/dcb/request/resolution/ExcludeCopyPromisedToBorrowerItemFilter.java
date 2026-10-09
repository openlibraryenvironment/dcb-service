package org.olf.dcb.request.resolution;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Excludes a supplier copy when DCB's own records show that a system this request would create a
 * virtual item in already has one carrying the copy's barcode, or is about to, and that system cannot
 * hold a second. Anything those records cannot show is left to {@link VirtualItemBarcodeCheck} on the
 * copy resolution chooses, and a copy is never excluded on an answer that is not proof.
 */
@Slf4j
@Singleton
@Order(ItemFilter.SAME_COPY_ORDER)
@AllArgsConstructor
public class ExcludeCopyPromisedToBorrowerItemFilter implements ItemFilter {
	// Borrower placement runs from CONFIRMED; pickup placement follows it
	private static final Set<String> BEFORE_BORROWER_PLACEMENT = names(SUBMITTED_TO_DCB, PATRON_VERIFIED,
		RESOLVED, REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED);

	private static final Set<String> BEFORE_PICKUP_PLACEMENT = names(SUBMITTED_TO_DCB, PATRON_VERIFIED,
		RESOLVED, REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED, REQUEST_PLACED_AT_BORROWING_AGENCY);

	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		final var borrowingHostLmsCode = getValueOrNull(parameters,
			ItemFilterParameters::borrowingHostLmsCode);

		final var pickupAgencyCode = getValueOrNull(parameters,
			ItemFilterParameters::pickupAgencyCode);

		return item -> notPromised(item, borrowingHostLmsCode, pickupAgencyCode);
	}

	private Mono<Boolean> notPromised(Item item, String borrowingHostLmsCode, String pickupAgencyCode) {
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var itemLocalId = getValueOrNull(item, Item::getLocalId);

		if (itemHostLmsCode == null || itemLocalId == null || borrowingHostLmsCode == null) {
			return Mono.just(true);
		}

		final var awaitingVirtualItem = Flux.from(patronRequestRepository.findSystemsAwaitingVirtualItemForSupplierCopy(
			itemHostLmsCode, itemLocalId, borrowingHostLmsCode, pickupAgencyCode,
			BEFORE_BORROWER_PLACEMENT, BEFORE_PICKUP_PLACEMENT));

		final var recordedVirtualItem = Flux.from(patronRequestRepository.findSystemsWithRecordedVirtualItemForSupplierCopy(
				itemHostLmsCode, itemLocalId, borrowingHostLmsCode, pickupAgencyCode))
			.filterWhen(hostLmsCode -> clientFor(hostLmsCode, client -> !client.canSeeVirtualItemsByBarcode()));

		return Flux.concat(awaitingVirtualItem, recordedVirtualItem)
			.distinct()
			.filterWhen(hostLmsCode -> clientFor(hostLmsCode, client -> !client.canHoldTwoVirtualItemsForOneCopy()))
			.hasElements()
			.map(promised -> !promised)
			.onErrorResume(error -> {
				log.warn("Unable to check whether itemLms={} itemId={} is promised to borrowingLms={} or pickupAgency={} ({}), including item",
					itemHostLmsCode, itemLocalId, borrowingHostLmsCode, pickupAgencyCode, error.toString());

				return Mono.just(true);
			});
	}

	// A system DCB has no client for is judged on its records alone
	private Mono<Boolean> clientFor(String hostLmsCode, Function<HostLmsClient, Boolean> answer) {
		return hostLmsService.getClientFor(hostLmsCode)
			.map(answer)
			.defaultIfEmpty(true);
	}

	private static Set<String> names(Status... statuses) {
		return Stream.of(statuses).map(Enum::name).collect(Collectors.toUnmodifiableSet());
	}
}
