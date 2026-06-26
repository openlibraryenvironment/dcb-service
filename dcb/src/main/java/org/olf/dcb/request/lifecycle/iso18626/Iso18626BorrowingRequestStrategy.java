package org.olf.dcb.request.lifecycle.iso18626;

import io.micronaut.context.annotation.Prototype;
import java.util.Optional;
import java.util.UUID;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestResult;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestStrategy;
import reactor.core.publisher.Mono;

@Prototype
public class Iso18626BorrowingRequestStrategy
	implements BorrowingAgencyRequestStrategy {
	private static final String PROTOCOL = "iso18626";
	private static final String REQUEST_MESSAGE_KIND = "request";
	private static final String REVISION_MESSAGE_KIND = "requestingAgencyMessage";
	private final DeclarativeRequestTransport transport;

	public Iso18626BorrowingRequestStrategy(
		DeclarativeRequestTransport transport) {

		this.transport = transport;
	}

	@Override
	public StrategyType type() {
		return StrategyType.DECLARATIVE;
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		return send(context, LifecycleOperation.PLACE_REQUEST,
			REQUEST_MESSAGE_KIND);
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> revise(
		RequestWorkflowContext context) {

		return send(context, LifecycleOperation.REVISE_REQUEST,
			REVISION_MESSAGE_KIND);
	}

	private Mono<BorrowingAgencyRequestResult> send(
		RequestWorkflowContext context,
		LifecycleOperation operation,
		String messageKind) {

		final var correlationId = correlationIdFor(
			context, LifecycleRole.BORROWER);
		final var patronRequest = context.getPatronRequest();
		final var hostLmsCode = patronRequest != null
			? patronRequest.getPatronHostlmsCode()
			: null;
		final var agencyCode = context.getPatronAgencyCode();

		return transport.send(new DeclarativeTransportRequest(
				PROTOCOL,
				LifecycleRole.BORROWER,
				operation,
				hostLmsCode,
				agencyCode,
				correlationId,
				messageKind,
				null))
			.map(response -> new BorrowingAgencyRequestResult(
				patronRequest,
				hostLmsCode,
				response.remoteRequestId(),
				response.status(),
				response.rawStatus(),
				null,
				null,
				null,
				false,
				false,
				LifecycleRole.BORROWER,
				PROTOCOL,
				correlationId,
				response.remoteRequestId(),
				response.status(),
				response.rawStatus(),
				response.rawMessageReference()));
	}

	private static String correlationIdFor(
		RequestWorkflowContext context,
		LifecycleRole role) {

		return Optional.ofNullable(context)
			.map(RequestWorkflowContext::getPatronRequest)
			.map(request -> request.getId())
			.map(UUID::toString)
			.map(id -> "%s:%s".formatted(id, role))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create ISO18626 correlation id without patron request id"));
	}
}
