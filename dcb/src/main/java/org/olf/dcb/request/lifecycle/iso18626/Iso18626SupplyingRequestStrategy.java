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
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestResult;
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestStrategy;
import reactor.core.publisher.Mono;

@Prototype
public class Iso18626SupplyingRequestStrategy
	implements SupplyingAgencyRequestStrategy {
	private static final String PROTOCOL = "iso18626";
	private static final String MESSAGE_KIND = "request";
	private final DeclarativeRequestTransport transport;

	public Iso18626SupplyingRequestStrategy(
		DeclarativeRequestTransport transport) {

		this.transport = transport;
	}

	@Override
	public StrategyType type() {
		return StrategyType.DECLARATIVE;
	}

	@Override
	public boolean supportsProtocol(String protocol) {
		return PROTOCOL.equals(protocol);
	}

	@Override
	public Mono<SupplyingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		final var supplierRequest = context.getSupplierRequest();
		final var correlationId = correlationIdFor(
			context, LifecycleRole.SUPPLIER);
		final var hostLmsCode = supplierRequest != null
			? supplierRequest.getHostLmsCode()
			: null;
		final var agencyCode = supplierRequest != null
			? supplierRequest.getLocalAgency()
			: null;

		return transport.send(new DeclarativeTransportRequest(
				PROTOCOL,
				LifecycleRole.SUPPLIER,
				LifecycleOperation.PLACE_REQUEST,
				hostLmsCode,
				agencyCode,
				correlationId,
				MESSAGE_KIND,
				null))
			.map(response -> new SupplyingAgencyRequestResult(
				context.getPatronRequest(),
				supplierRequest,
				hostLmsCode,
				response.remoteRequestId(),
				response.status(),
				response.rawStatus(),
				null,
				null,
				LifecycleRole.SUPPLIER,
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
