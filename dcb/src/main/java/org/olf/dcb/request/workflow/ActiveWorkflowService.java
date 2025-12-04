package org.olf.dcb.request.workflow;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.resolution.Resolution;

import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
@Singleton
@AllArgsConstructor
public class ActiveWorkflowService {
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;

	public Mono<Tuple2<Resolution, PatronRequest>> setActiveWorkflow(
		Resolution resolution, PatronRequest patronRequest) {

		if (!resolution.successful()) {
			return Mono.just(Tuples.of(resolution, patronRequest));
		}

		final var borrowingAgencyCode = getValueOrNull(resolution, Resolution::getBorrowingAgencyCode);
		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);
		final var itemAgencyCode = getValueOrNull(chosenItem, Item::getAgencyCode);

		return updatePatronRequest(patronRequest, borrowingAgencyCode, itemAgencyCode)
			.map(updatedRequest -> Tuples.of(resolution, updatedRequest));
	}

	public Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest,
		String borrowingAgencyCode, String itemAgencyCode) {

		log.debug("setActiveWorkflow(PatronRequest: {}, BorrowingAgencyCode: {}, ItemAgencyCode: {})",
			patronRequest, borrowingAgencyCode, itemAgencyCode);

		// build a temporary context to allow the active workflow to be set
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setPatronAgencyCode(borrowingAgencyCode)
			.setLenderAgencyCode(itemAgencyCode);

		return requestWorkflowContextHelper.resolvePickupLocationAgency(context)
			.flatMap(requestWorkflowContextHelper::setPatronRequestWorkflow)
			.map(RequestWorkflowContext::getPatronRequest);
	}
}
