package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.FunctionalSettingType.TRIGGER_SUPPLIER_RENEWAL;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsRenewal;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class SupplierRenewalTransition implements PatronRequestStateTransition {
	private final PatronRequestAuditService patronRequestAuditService;
	private final ConsortiumService consortiumService;
	private final HostLmsService hostLmsService;

	private static final List<Status> possibleSourceStatus = List.of(LOANED);
	private static final String RENEWAL_TRIGGERED = "Supplier renewal : Triggered";
	private static final String RENEWAL_PLACED = "Supplier renewal : Placed";

	public SupplierRenewalTransition(
		PatronRequestAuditService patronRequestAuditService,
		ConsortiumService consortiumService,
		HostLmsService hostLmsService) {

		this.patronRequestAuditService = patronRequestAuditService;
		this.consortiumService = consortiumService;
		this.hostLmsService = hostLmsService;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext context) {
		return consortiumService.isEnabled(TRIGGER_SUPPLIER_RENEWAL)
			.flatMap(enabled -> {
				if (enabled) {
					if (isApplicableFor(context)) {
						return triggerSupplierRenewal(context);
					}
					else {
						log.warn("Supplier renewal was attempted but wasn't triggered");
						return Mono.just(context);
					}
				}
				else {
					// Update the renewal count so that the transition is not repeatedly triggered
					// when the setting is disabled
					return Mono.just(context)
						.map(RequestWorkflowContext::getPatronRequest)
						.flatMap(this::auditDisabled)
						.map(request -> request
							.setRenewalCount(request.getLocalRenewalCount())
							.setOutOfSequenceFlag(true))
						.map(context::setPatronRequest);
				}
			});
	}

	private Mono<PatronRequest> auditDisabled(PatronRequest patronRequest) {
		final var message = "Supplier renewal : Skipping supplier renewal as setting disabled";

		final var auditData = new HashMap<String, Object>();

		auditData.put("renewalCount", getValueOrNull(patronRequest, PatronRequest::getRenewalCount));
		auditData.put("localRenewalCount", getValueOrNull(patronRequest, PatronRequest::getLocalRenewalCount));

		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.map(audit -> patronRequest);
	}

	private Mono<RequestWorkflowContext> triggerSupplierRenewal(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();
		final var supplierRequest = ctx.getSupplierRequest();

		final var auditData = createAuditData(patronRequest, supplierRequest);

		log.info(RENEWAL_TRIGGERED);

		return patronRequestAuditService.addAuditEntry(patronRequest, RENEWAL_TRIGGERED, auditData)
			.flatMap(audit -> processRenewal(patronRequest, supplierRequest, ctx));
	}

	private Mono<RequestWorkflowContext> processRenewal(
		PatronRequest patronRequest, SupplierRequest supplierRequest, RequestWorkflowContext ctx) {

		return performRenewal(supplierRequest, ctx)
			.flatMap(renewalResponse -> renewalSuccess(patronRequest, ctx))
			.onErrorResume(error -> renewalFailure(patronRequest, error, ctx));
	}

	private Mono<HostLmsRenewal> performRenewal(SupplierRequest supplierRequest, RequestWorkflowContext ctx) {

		final var supplierHostLms = ctx.getLenderSystemCode();
		final var virtualPatron = ctx.getPatronVirtualIdentity();

		return hostLmsService.getClientFor(supplierHostLms)
			.flatMap(client -> client.renew( createHostLmsRenewal(supplierRequest, virtualPatron) ));
	}

	private Mono<RequestWorkflowContext> renewalSuccess(PatronRequest patronRequest, RequestWorkflowContext ctx) {

		patronRequest.setRenewalCount(patronRequest.getLocalRenewalCount());

		final var auditData = new HashMap<String, Object>();
		auditData.put("DCB.renewalCount", patronRequest.getRenewalCount());

		log.info(RENEWAL_PLACED);

		return patronRequestAuditService.addAuditEntry(patronRequest, RENEWAL_PLACED, auditData)
			.flatMap(audit -> Mono.just(ctx));
	}

	private Mono<RequestWorkflowContext> renewalFailure(
		PatronRequest patronRequest, Throwable error, RequestWorkflowContext ctx) {

		return Mono.just(patronRequest)
			.flatMap(request -> auditRenewalFailure(request, error))
			.map(request -> request
				.setRenewalCount(request.getLocalRenewalCount())
				.setOutOfSequenceFlag(true))
			.flatMap(audit -> Mono.just(ctx));
	}

	private Mono<PatronRequest> auditRenewalFailure(PatronRequest patronRequest, Throwable error) {
		final var renewalFailedMessage = "Supplier renewal : Failed";

		log.info(renewalFailedMessage);

		final var auditData = new HashMap<String, Object>();
		auditThrowable(auditData, "Throwable", error);

		return patronRequestAuditService.addAuditEntry(patronRequest, renewalFailedMessage, auditData)
			.map(audit -> patronRequest);
	}

	private Map<String, Object> createAuditData(PatronRequest patronRequest, SupplierRequest supplierRequest) {
		final var auditData = new HashMap<String, Object>();
		auditData.put("DCB.renewalCount", getValueOrNull(patronRequest, PatronRequest::getRenewalCount));
		auditData.put("PR.localRenewalCount", getValueOrNull(patronRequest, PatronRequest::getLocalRenewalCount));
		auditData.put("SR.localRenewalCount", getValueOrNull(supplierRequest, SupplierRequest::getLocalRenewalCount));
		return auditData;
	}

	private HostLmsRenewal createHostLmsRenewal(SupplierRequest supplierRequest, PatronIdentity virtualPatron) {
		return HostLmsRenewal.builder()
			.localRequestId(getValueOrNull(supplierRequest, SupplierRequest::getLocalId))
			.localItemId(supplierRequest.getLocalItemId())
			.localItemBarcode(supplierRequest.getLocalItemBarcode())
			.localPatronId(virtualPatron.getLocalId())
			.localPatronBarcode(virtualPatron.getLocalBarcode())
			.build();
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();
		return isStatusApplicable(patronRequest)
			&& isLocalItemStatusApplicable(patronRequest)
			&& isRenewalCountApplicable(patronRequest);
	}

	private boolean isStatusApplicable(PatronRequest patronRequest) {
		return getPossibleSourceStatus().contains(patronRequest.getStatus());
	}

	private boolean isLocalItemStatusApplicable(PatronRequest patronRequest) {
		return "LOANED".equals(patronRequest.getLocalItemStatus());
	}

	private boolean isRenewalCountApplicable(PatronRequest patronRequest) {
		final var renewalCount = patronRequest.getRenewalCount();
		final var localRenewalCount = patronRequest.getLocalRenewalCount();
		return localRenewalCount != null
			&& localRenewalCount > 0
			&& !Objects.equals(localRenewalCount, renewalCount);
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(LOANED);
	}

	@Override
	public String getName() {
		return "SupplierRenewalTransition";
	}

	@Override
	public boolean attemptAutomatically() {
		return true;
	}

}
