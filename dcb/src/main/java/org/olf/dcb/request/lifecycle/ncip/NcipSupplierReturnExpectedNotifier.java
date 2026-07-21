package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.SupplierReturnExpectedNotifier;
import reactor.core.publisher.Mono;

@Prototype
public class NcipSupplierReturnExpectedNotifier
	implements SupplierReturnExpectedNotifier {
	private final DeclarativeRequestTransport transport;
	private final NcipPayloadBuilder payloadBuilder;
	private final HostLmsService hostLmsService;
	private final NcipIdentityConfiguration identityConfiguration;
	private final NcipAddressResolver addressResolver;

	public NcipSupplierReturnExpectedNotifier(
		DeclarativeRequestTransport transport,
		NcipPayloadBuilder payloadBuilder,
		HostLmsService hostLmsService,
		NcipIdentityConfiguration identityConfiguration,
		NcipAddressResolver addressResolver) {

		this.transport = transport;
		this.payloadBuilder = payloadBuilder;
		this.hostLmsService = hostLmsService;
		this.identityConfiguration = identityConfiguration;
		this.addressResolver = addressResolver;
	}

	@Override
	public Mono<RequestWorkflowContext> notifyExpectedReturn(
		RequestWorkflowContext context) {

		final var supplierRequest = context.getSupplierRequest();
		if (supplierRequest == null
			|| !NcipProtocol.PROTOCOL.equals(supplierRequest.getProtocol())) {
			return Mono.just(context);
		}

		final var hostLmsCode = required(
			supplierRequest.getHostLmsCode(), "supplier HostLMS code");
		final var correlationId = correlationIdFor(context, supplierRequest);
		final var itemBarcode = required(
			supplierRequest.getLocalItemBarcode(), "supplier item barcode");

		return hostLmsService.findByCode(hostLmsCode)
			.switchIfEmpty(Mono.error(new IllegalArgumentException(
				"Cannot notify expected return without HostLMS " + hostLmsCode)))
			.flatMap(hostLms -> payload(context, hostLms, correlationId, itemBarcode))
			.flatMap(payload -> transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				LifecycleRole.SUPPLIER,
				LifecycleOperation.REVISE_REQUEST,
				hostLmsCode,
				supplierRequest.getLocalAgency(),
				correlationId,
				NcipProtocol.ITEM_SHIPPED,
				payload)))
			.thenReturn(context);
	}

	private Mono<String> payload(
		RequestWorkflowContext context,
		HostLms hostLms,
		String correlationId,
		String itemBarcode) {

		final var supplierAgencyId = addressResolver.agencyIdForHost(hostLms);
		return addressResolver.agencyIdForLocalAgencyCode(
				context.getPatronAgencyCode(), identityConfiguration.getAgencyId())
			.map(borrowerAgencyId -> payloadBuilder.itemShipped(
				new NcipItemShippedPayload(
					new NcipParty(
						borrowerAgencyId,
						supplierAgencyId,
						identityConfiguration.getSystemId(),
						addressResolver.systemIdForHost(hostLms)),
					correlationId,
					supplierAgencyId,
					itemBarcode,
					Instant.now())));
	}

	private static String correlationIdFor(
		RequestWorkflowContext context,
		SupplierRequest supplierRequest) {

		return Optional.ofNullable(supplierRequest.getLocalId())
			.filter(NcipSupplierReturnExpectedNotifier::hasText)
			.or(() -> Optional.ofNullable(context.getPatronRequest())
				.map(PatronRequest::getId)
				.map(UUID::toString)
				.map(id -> "%s:%s".formatted(id, LifecycleRole.SUPPLIER)))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot notify expected return without supplier request correlation"));
	}

	private static String required(String value, String description) {
		if (!hasText(value)) {
			throw new IllegalArgumentException(
				"Cannot notify expected return without " + description);
		}
		return value;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
