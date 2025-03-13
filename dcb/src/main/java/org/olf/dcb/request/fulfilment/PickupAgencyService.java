package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.function.Function;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Slf4j
@Prototype
public class PickupAgencyService {
	private final PatronRequestAuditService patronRequestAuditService;

	public PickupAgencyService(PatronRequestAuditService patronRequestAuditService) {
		this.patronRequestAuditService = patronRequestAuditService;
	}

	public Mono<RequestWorkflowContext> cleanUp(RequestWorkflowContext requestWorkflowContext) {

		final var patronRequest = getValueOrNull(requestWorkflowContext, RequestWorkflowContext::getPatronRequest);
		final var pickupSystem = getValueOrNull(requestWorkflowContext, RequestWorkflowContext::getPickupSystem);
		final var pickupSystemCode = getValueOrNull(requestWorkflowContext, RequestWorkflowContext::getPickupSystemCode);

		log.info("WORKFLOW pickup system cleanUp {}", patronRequest);

		if (pickupSystem != null && patronRequest != null) {
			return Mono.just(pickupSystem)
				.flatMap(client -> deleteItemIfPresent(client, patronRequest) )
				.flatMap(client -> deleteBibIfPresent(client, patronRequest) )
				.flatMap(client -> deleteHoldIfPresent(client, patronRequest))
				.thenReturn(requestWorkflowContext);
		}

		final var message = "Pickup system cleanup : Skipped";
		final var auditData = new HashMap<String, Object>();
		auditData.put("pickupSystem", getValue(pickupSystemCode, "No value present"));
		auditData.put("patronRequest", patronRequest != null ? "Exists" : "No value present");
		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.flatMap(audit -> Mono.just(requestWorkflowContext));
	}

	private Mono<String> deleteHoldIfPresent(HostLmsClient client, PatronRequest patronRequest) {

		return checkHoldExists(client, patronRequest, "Delete")
			.flatMap(client::deleteHold)
			// Catch any skipped deletions
			.switchIfEmpty(Mono.defer(() -> Mono.just("OK")));
	}

	public Mono<HostLmsClient> deleteItemIfPresent(HostLmsClient client, PatronRequest patronRequest) {

		final var pickupItemId = getValueOrNull(patronRequest, PatronRequest::getPickupItemId);
		final var pickupItemStatus = getValueOrNull(patronRequest, PatronRequest::getPickupItemStatus);

		if (pickupItemId != null && !"MISSING".equals(pickupItemStatus)) {

			return checkItemExists(client, pickupItemId, patronRequest)
				.flatMap(_client -> _client.deleteItem(pickupItemId))

				// Catch any skipped deletions
				.switchIfEmpty(Mono.defer(() -> Mono.just("OK")))

				// Genuine error we didn't account for
				.onErrorResume(logAndReturnErrorString("Delete pickup item : Failed", patronRequest))
				.thenReturn(client);
		}
		else {
			final var message = "Delete pickup item : Skipped";
			final var auditData = new HashMap<String, Object>();
			auditData.put("pickupItemId", patronRequest.getPickupItemId() != null ? patronRequest.getPickupItemId() : "No value present");
			auditData.put("pickupItemStatus", getValue(pickupItemStatus, "No value present"));
			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just(client));
		}
	}

	private Function<Throwable, Mono<String>> logAndReturnErrorString(String message, PatronRequest patronRequest) {

		return error -> {
			final var auditData = new HashMap<String, Object>();
			auditThrowable(auditData, "Throwable", error);

			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just("Error"));
		};
	}

	private Mono<HostLmsClient> checkItemExists(HostLmsClient client, String pickupItemId, PatronRequest patronRequest) {

		final var pickupRequestId = getValueOrNull(patronRequest, PatronRequest::getPickupRequestId);

		return client.getItem(pickupItemId, pickupRequestId)
			.flatMap(item -> {

				// if the item exists a local id will be present
				if (item != null && item.getLocalId() != null) {

					// return the client to proceed with deletion
					return Mono.just(client);
				}

				// no local id to delete, skip delete by passing back an empty
				final var message = "Delete pickup item : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditData.put("item", item);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			})
			.onErrorResume(error -> {
				// we encountered an error when confirming the item exists
				final var message = "Delete pickup item : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditThrowable(auditData, "Throwable", error);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			});
	}

	public Mono<HostLmsClient> deleteBibIfPresent(HostLmsClient client, PatronRequest patronRequest) {
		if (patronRequest.getPickupBibId() != null) {

			final var pickupBibId = patronRequest.getPickupBibId();

			return client.deleteBib(pickupBibId)
				// Genuine error we didn't account for
				.onErrorResume(logAndReturnErrorString("Delete pickup bib : Failed", patronRequest))
				.thenReturn(client);
		}
    else {
			final var message = "Delete pickup bib : Skipped";
			final var auditData = new HashMap<String, Object>();
			auditData.put("pickupBibId", patronRequest.getPickupBibId() != null ? patronRequest.getPickupBibId() : "No value present");
			return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
				.flatMap(audit -> Mono.just(client));
    }
  }

	private Mono<String> checkHoldExists(HostLmsClient client, PatronRequest patronRequest, String operation) {

		final var pickupRequestId = patronRequest.getPickupRequestId();

		return client.getRequest(pickupRequestId)
			.flatMap(hostLmsRequest -> {

				// if the hold exists a local id will be present
				if (hostLmsRequest != null && hostLmsRequest.getLocalId() != null) {

					// return the pickupRequestId to proceed with operation
					return Mono.just(pickupRequestId);
				}

				// no local id, skip operation by passing back an empty
				final var message = operation + " pickup hold : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditData.put("hold", hostLmsRequest);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			})
			.onErrorResume(error -> {

				// we encountered an error when confirming the hold exists
				final var message = operation + " pickup hold : Skipped";
				final var auditData = new HashMap<String, Object>();
				auditThrowable(auditData, "Throwable", error);
				return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData).flatMap(audit -> Mono.empty());
			});
	}

	public Mono<HostLmsItem> getItem(RequestWorkflowContext requestWorkflowContext) {

		final var pickupSystem = requestWorkflowContext.getPickupSystem();
		final var patronRequest = requestWorkflowContext.getPatronRequest();
		final var pickupItemId = patronRequest.getPickupItemId();
		final var pickupRequestId = patronRequest.getPickupRequestId();

		return pickupSystem.getItem(pickupItemId, pickupRequestId);
	}

	public Mono<Patron> getPatron(RequestWorkflowContext requestWorkflowContext) {

		final var pickupSystem = requestWorkflowContext.getPickupSystem();
		final var patronRequest = requestWorkflowContext.getPatronRequest();
		final var pickupPatronId = patronRequest.getPickupPatronId();

		return pickupSystem.getPatronByLocalId(pickupPatronId);
	}

	public Mono<String> cancelHoldIfPresent(RequestWorkflowContext requestWorkflowContext) {

		final var pickupSystem = requestWorkflowContext.getPickupSystem();
		final var patronRequest = requestWorkflowContext.getPatronRequest();
		final var pickupItemId = patronRequest.getPickupItemId();
		final var pickupPatronId = patronRequest.getPickupPatronId();

		return checkHoldExists(pickupSystem, patronRequest, "CANCEL")
			.flatMap(pickupRequestId -> pickupSystem.cancelHoldRequest(CancelHoldRequestParameters.builder()
				.localRequestId(pickupRequestId)
				.localItemId(pickupItemId)
				.patronId(pickupPatronId)
				.build()))
			// Catch any skipped cancellations
			.switchIfEmpty(Mono.defer(() -> Mono.just("Ok")));
	}
}
