package org.olf.dcb.request.workflow;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A person retries the checkout of the lender's copy to the virtual patron, after the one made when
 * the patron borrowed the item failed. Until it succeeds, the lender's copy shows Transferred and is
 * routed again when it is checked in. See docs/polaris/lender-checkout.adoc.
 */
@Slf4j
@Singleton
public class RetryLenderCheckoutService {
	private static final String RETRY_PREFIX = "Lender checkout retry";
	static final String RETRIED = "Lender checkout retry : Success";
	static final String ALREADY_OUT = "Lender checkout retry : Skipped, the lender's copy is already checked out";
	static final String RETRY_FAILED = "Lender checkout retry : Failed";

	// Other reasons the flag is set; a successful lender checkout leaves it for them
	private static final List<String> OTHER_REASONS_FOR_ATTENTION = List.of(
		"Patron checkout failed",
		"Expedited checkout at borrower failed",
		"NO BARCODE FOR PATRON VIRTUAL IDENTITY");

	private final PatronRequestService patronRequestService;
	private final RequestWorkflowContextHelper requestWorkflowContextHelper;
	private final HostLmsService hostLmsService;
	private final HandleBorrowerItemLoaned handleBorrowerItemLoaned;
	private final PatronRequestAuditService patronRequestAuditService;
	private final PatronRequestAuditRepository patronRequestAuditRepository;
	private final PatronRequestRepository patronRequestRepository;
	private final LenderCheckoutRetryLimiter limiter;

	public RetryLenderCheckoutService(PatronRequestService patronRequestService,
		RequestWorkflowContextHelper requestWorkflowContextHelper, HostLmsService hostLmsService,
		HandleBorrowerItemLoaned handleBorrowerItemLoaned, PatronRequestAuditService patronRequestAuditService,
		PatronRequestAuditRepository patronRequestAuditRepository, PatronRequestRepository patronRequestRepository,
		LenderCheckoutRetryLimiter limiter) {

		this.patronRequestService = patronRequestService;
		this.requestWorkflowContextHelper = requestWorkflowContextHelper;
		this.hostLmsService = hostLmsService;
		this.handleBorrowerItemLoaned = handleBorrowerItemLoaned;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestAuditRepository = patronRequestAuditRepository;
		this.patronRequestRepository = patronRequestRepository;
		this.limiter = limiter;
	}

	public Mono<String> retry(UUID patronRequestId, CallerScope caller, String callerSubject) {
		if (!limiter.tryAcquireForCaller(callerSubject)) {
			return Mono.error(new LenderCheckoutRetryRefused("TOO_MANY_RETRIES",
				"You have retried as many lender checkouts as one person can in a minute.",
				LenderCheckoutRetryLimiter.WINDOW));
		}

		// A library caller is told 403 for a request that does not exist too, so it cannot probe ids
		return patronRequestService.findById(patronRequestId)
			.switchIfEmpty(Mono.error(() -> caller.requiresNarrowing()
				? forbidden(patronRequestId)
				: problem(org.zalando.problem.Status.NOT_FOUND, "No such patron request",
					"No patron request has this id.", patronRequestId)))
			.flatMap(requestWorkflowContextHelper::fromPatronRequest)
			.map(ctx -> ensureRetryableBy(ctx, caller))
			.flatMap(this::refuseIfRetriedWithinTheWindow)
			.flatMap(ctx -> limiter.tryStart(patronRequestId)
				? hostLmsService.getClientFor(ctx.getLenderSystemCode())
					.flatMap(lender -> retryAt(ctx, lender, caller))
					.doFinally(signal -> limiter.finish(patronRequestId))
				: Mono.error(new LenderCheckoutRetryRefused("RETRY_IN_PROGRESS",
					"This request's lender checkout is already being retried.", Duration.ofSeconds(30))));
	}

	// Checked after access, so a 429 never tells a library about a request it cannot see
	private Mono<RequestWorkflowContext> refuseIfRetriedWithinTheWindow(RequestWorkflowContext ctx) {
		final var since = Instant.now().minus(LenderCheckoutRetryLimiter.WINDOW);

		return Flux.from(patronRequestAuditRepository.findAllByPatronRequest(ctx.getPatronRequest()))
			.filter(audit -> audit.getBriefDescription() != null
				&& audit.getBriefDescription().startsWith(RETRY_PREFIX)
				&& audit.getAuditDate() != null && audit.getAuditDate().isAfter(since))
			.map(PatronRequestAudit::getAuditDate)
			.reduce((first, second) -> first.isAfter(second) ? first : second)
			.flatMap(latest -> Mono.<RequestWorkflowContext>error(new LenderCheckoutRetryRefused("RETRIED_TOO_RECENTLY",
				"This request's lender checkout was retried less than a minute ago.",
				Duration.between(since, latest).plusSeconds(1))))
			.defaultIfEmpty(ctx);
	}

	static RequestWorkflowContext ensureRetryableBy(RequestWorkflowContext ctx, CallerScope caller) {
		final var patronRequest = ctx.getPatronRequest();
		final var supplierAgency = getValueOrNull(ctx,
			RequestWorkflowContext::getSupplierRequest, SupplierRequest::getLocalAgency);

		if (caller.requiresNarrowing()
			&& (caller.isIncoherent() || supplierAgency == null || !caller.agencyCodes().contains(supplierAgency))) {

			throw forbidden(patronRequest.getId());
		}

		if (patronRequest.getStatus() != Status.LOANED) {
			throw problem(org.zalando.problem.Status.CONFLICT, "This request is not on loan",
				"Only a request on loan has a lender checkout to retry. This request is %s."
					.formatted(patronRequest.getStatus()), patronRequest.getId());
		}

		if (ctx.getSupplierRequest() == null || ctx.getLenderSystemCode() == null
			|| ctx.getPatronVirtualIdentity() == null) {

			throw problem(org.zalando.problem.Status.CONFLICT, "This request has no lender checkout",
				"No active supplier request or virtual patron is recorded for this request.", patronRequest.getId());
		}

		return ctx;
	}

	private Mono<String> retryAt(RequestWorkflowContext ctx, HostLmsClient lender, CallerScope caller) {
		final var patronRequest = ctx.getPatronRequest();
		final var supplierRequest = ctx.getSupplierRequest();

		if (!lender.reflectPatronLoanAtSupplier()) {
			return Mono.error(problem(org.zalando.problem.Status.CONFLICT, "The lender does not record DCB loans",
				"DCB does not check items out at %s, so there is nothing to retry.".formatted(ctx.getLenderSystemCode()),
				patronRequest.getId()));
		}

		final var auditData = new HashMap<String, Object>();
		// The library, never the person: the audit is read by every library party to the request
		auditData.put("retriedBy", caller.requiresNarrowing() ? supplierRequest.getLocalAgency() : "consortium");
		auditData.put("lenderSystemCode", ctx.getLenderSystemCode());

		// Checked live: a second checkout of a copy already out to the virtual patron would renew it
		return lender.getItem(HostLmsItem.builder()
				.localId(supplierRequest.getLocalItemId())
				.localRequestId(supplierRequest.getLocalId())
				.build())
			.flatMap(item -> {
				auditData.put("lenderItemStatusBefore", item.getStatus());

				if (HostLmsItem.ITEM_LOANED.equals(item.getStatus())) {
					return finish(patronRequest, ALREADY_OUT, auditData);
				}

				return handleBorrowerItemLoaned.checkOutAtLender(ctx, lender)
					.then(Mono.defer(() -> finish(patronRequest, RETRIED, auditData)))
					.onErrorResume(error -> {
						auditThrowable(auditData, "Throwable", error);

						return patronRequestAuditService.addAuditEntry(patronRequest, RETRY_FAILED, auditData)
							.then(Mono.error(problem(org.zalando.problem.Status.BAD_GATEWAY,
								"The lender refused the checkout",
								"%s refused the checkout again; see the request's audit for its answer."
									.formatted(ctx.getLenderSystemCode()), patronRequest.getId())));
					});
			})
			.switchIfEmpty(Mono.defer(() -> Mono.error(problem(org.zalando.problem.Status.BAD_GATEWAY,
				"The lender's copy could not be read",
				"DCB checks the lender's copy before retrying, and %s did not return it. Nothing was changed."
					.formatted(ctx.getLenderSystemCode()), patronRequest.getId()))));
	}

	private Mono<String> finish(PatronRequest patronRequest, String message, HashMap<String, Object> auditData) {
		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.then(clearAttentionUnlessFlaggedForMore(patronRequest))
			.thenReturn(message);
	}

	private Mono<Void> clearAttentionUnlessFlaggedForMore(PatronRequest patronRequest) {
		if (Boolean.TRUE.equals(patronRequest.getIsTooLong())) {
			return Mono.empty();
		}

		return Flux.from(patronRequestAuditRepository.findAllByPatronRequest(patronRequest))
			.any(audit -> audit.getBriefDescription() != null
				&& OTHER_REASONS_FOR_ATTENTION.stream().anyMatch(audit.getBriefDescription()::startsWith))
			.flatMap(flaggedForMore -> flaggedForMore
				? Mono.empty()
				// The flag alone: the request read before the retry may be behind tracking by now
				: Mono.from(patronRequestRepository.updateNeedsAttention(patronRequest.getId(), Boolean.FALSE))
					.then(Mono.fromRunnable(() -> patronRequest.setNeedsAttention(Boolean.FALSE))));
	}

	private static ThrowableProblem forbidden(UUID patronRequestId) {
		return problem(org.zalando.problem.Status.FORBIDDEN, "Access denied",
			"Only the library lending this request, or consortium staff, can retry its checkout.", patronRequestId);
	}

	private static ThrowableProblem problem(org.zalando.problem.Status status, String title, String detail,
		UUID patronRequestId) {

		return Problem.builder()
			.withTitle(title)
			.withStatus(status)
			.withDetail(detail)
			.with("patronRequestId", String.valueOf(patronRequestId))
			.build();
	}
}
