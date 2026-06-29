package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import java.util.Optional;
import java.util.UUID;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
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
public class NcipSupplyingRequestStrategy
	implements SupplyingAgencyRequestStrategy {
	private static final String REQUEST_TYPE = "Hold";
	private static final String REQUEST_SCOPE_TYPE = "Bibliographic Item";

	private final DeclarativeRequestTransport transport;
	private final NcipPayloadBuilder payloadBuilder;

	public NcipSupplyingRequestStrategy(
		DeclarativeRequestTransport transport,
		NcipPayloadBuilder payloadBuilder) {

		this.transport = transport;
		this.payloadBuilder = payloadBuilder;
	}

	@Override
	public StrategyType type() {
		return StrategyType.DECLARATIVE;
	}

	@Override
	public boolean supportsProtocol(String protocol) {
		return NcipProtocol.PROTOCOL.equals(protocol);
	}

	@Override
	public Mono<SupplyingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		final var supplierRequest = context.getSupplierRequest();
		final var patronRequest = context.getPatronRequest();
		final var correlationId = correlationIdFor(context, LifecycleRole.SUPPLIER);
		final var hostLmsCode = supplierRequest != null
			? supplierRequest.getHostLmsCode()
			: null;
		final var agencyCode = supplierRequest != null
			? supplierRequest.getLocalAgency()
			: context.getLenderAgencyCode();
		final var payload = payloadBuilder.requestItem(new NcipRequestItemPayload(
			userIdentifierValueFor(context),
			bibliographicRecordIdentifierFor(patronRequest),
			agencyCode != null ? agencyCode : hostLmsCode,
			supplierRequest != null ? supplierRequest.getLocalItemId() : null,
			correlationId,
			REQUEST_TYPE,
			REQUEST_SCOPE_TYPE));

		return transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				LifecycleRole.SUPPLIER,
				LifecycleOperation.PLACE_REQUEST,
				hostLmsCode,
				agencyCode,
				correlationId,
				NcipProtocol.REQUEST_ITEM,
				payload))
			.map(response -> new SupplyingAgencyRequestResult(
				patronRequest,
				supplierRequest,
				hostLmsCode,
				response.remoteRequestId(),
				response.status(),
				response.rawStatus(),
				null,
				null,
				LifecycleRole.SUPPLIER,
				NcipProtocol.PROTOCOL,
				correlationId,
				response.remoteRequestId(),
				response.status(),
				response.rawStatus(),
				response.rawMessageReference()));
	}

	private static String userIdentifierValueFor(RequestWorkflowContext context) {
		return Optional.ofNullable(context)
			.map(RequestWorkflowContext::getPatronHomeIdentity)
			.map(PatronIdentity::getFirstBarcode)
			.filter(NcipSupplyingRequestStrategy::hasText)
			.or(() -> Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronHomeIdentity)
				.map(PatronIdentity::getLocalId)
				.filter(NcipSupplyingRequestStrategy::hasText))
			.or(() -> Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronRequest)
				.map(PatronRequest::getId)
				.map(UUID::toString))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create NCIP RequestItem without patron identity"));
	}

	private static String bibliographicRecordIdentifierFor(
		PatronRequest patronRequest) {

		return Optional.ofNullable(patronRequest)
			.map(PatronRequest::getBibClusterId)
			.map(UUID::toString)
			.or(() -> Optional.ofNullable(patronRequest)
				.map(PatronRequest::getId)
				.map(UUID::toString))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create NCIP RequestItem without bibliographic identity"));
	}

	private static String correlationIdFor(
		RequestWorkflowContext context,
		LifecycleRole role) {

		return Optional.ofNullable(context)
			.map(RequestWorkflowContext::getPatronRequest)
			.map(PatronRequest::getId)
			.map(UUID::toString)
			.map(id -> "%s:%s".formatted(id, role))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create NCIP correlation id without patron request id"));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
