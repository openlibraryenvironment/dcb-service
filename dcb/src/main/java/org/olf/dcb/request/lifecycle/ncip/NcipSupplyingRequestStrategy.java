package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import java.util.Optional;
import java.util.UUID;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestShippingContextProjector;
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
	private final HostLmsService hostLmsService;
	private final NcipIdentityConfiguration ncipIdentityConfiguration;
	private final NcipAddressResolver addressResolver;
	private final NcipBibliographicMetadataResolver bibliographicMetadataResolver;

	public NcipSupplyingRequestStrategy(
		DeclarativeRequestTransport transport,
		NcipPayloadBuilder payloadBuilder,
		HostLmsService hostLmsService,
		NcipIdentityConfiguration ncipIdentityConfiguration,
		NcipAddressResolver addressResolver,
		NcipBibliographicMetadataResolver bibliographicMetadataResolver) {

		this.transport = transport;
		this.payloadBuilder = payloadBuilder;
		this.hostLmsService = hostLmsService;
		this.ncipIdentityConfiguration = ncipIdentityConfiguration;
		this.addressResolver = addressResolver;
		this.bibliographicMetadataResolver = bibliographicMetadataResolver;
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

		return hostLmsService.findByCode(hostLmsCode)
			.switchIfEmpty(Mono.error(new IllegalArgumentException(
				"Cannot create NCIP RequestItem without HostLMS " + hostLmsCode)))
			.flatMap(hostLms -> bibliographicMetadataResolver.resolve(context).flatMap(metadata -> {
				final var toAgencyId = addressResolver.agencyIdForHost(hostLms);
				return addressResolver.agencyIdForLocalAgencyCode(
						context.getPatronAgencyCode(),
						ncipIdentityConfiguration.getAgencyId())
					.map(fromAgencyId -> payloadBuilder.requestItem(new NcipRequestItemPayload(
						party(hostLms, fromAgencyId, toAgencyId),
						fromAgencyId,
						userIdentifierValueFor(context),
						bibliographicRecordIdentifierFor(patronRequest, supplierRequest),
						toAgencyId,
						toAgencyId,
						"barcode",
						itemIdentifierValueFor(supplierRequest),
						localItemIdentifierValueFor(supplierRequest),
						correlationId,
						REQUEST_TYPE,
						REQUEST_SCOPE_TYPE,
						bibliographicDescriptionFor(
							patronRequest,
							supplierRequest,
							toAgencyId,
							metadata),
						RequestShippingContextProjector.project(context),
						isOrsAppliance(hostLms))));
			}))
			.flatMap(payload -> transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				LifecycleRole.SUPPLIER,
				LifecycleOperation.PLACE_REQUEST,
				hostLmsCode,
				agencyCode,
				correlationId,
				NcipProtocol.REQUEST_ITEM,
				payload)))
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

	private static boolean isOrsAppliance(HostLms hostLms) {
		return hostLms != null
			&& hostLms.getClientType() != null
			&& ORSApplianceHostLMS.class.isAssignableFrom(hostLms.getClientType());
	}

	private NcipParty party(
		HostLms hostLms,
		String fromAgencyId,
		String toAgencyId) {

		return new NcipParty(
			fromAgencyId,
			toAgencyId,
			ncipIdentityConfiguration.getSystemId(),
			addressResolver.systemIdForHost(hostLms));
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
		PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		return Optional.ofNullable(supplierRequest)
			.map(SupplierRequest::getLocalBibId)
			.filter(NcipSupplyingRequestStrategy::hasText)
			.or(() -> Optional.ofNullable(patronRequest)
			.map(PatronRequest::getBibClusterId)
			.map(UUID::toString))
			.or(() -> Optional.ofNullable(patronRequest)
				.map(PatronRequest::getId)
				.map(UUID::toString))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create NCIP RequestItem without bibliographic identity"));
	}

	private static String localItemIdentifierValueFor(SupplierRequest supplierRequest) {
		return Optional.ofNullable(supplierRequest)
			.map(SupplierRequest::getLocalItemId)
			.filter(NcipSupplyingRequestStrategy::hasText)
			.orElse(null);
	}

	private static NcipBibliographicDescription bibliographicDescriptionFor(
		PatronRequest patronRequest,
		SupplierRequest supplierRequest,
		String bibliographicRecordAgencyId,
		NcipBibliographicMetadata metadata) {

		return new NcipBibliographicDescription(
			metadata.title(),
			metadata.author(),
			bibliographicRecordIdentifierFor(
				patronRequest,
				supplierRequest),
			bibliographicRecordAgencyId,
			itemIdentifierValueFor(supplierRequest),
			metadata.edition());
	}

	private static String itemIdentifierValueFor(SupplierRequest supplierRequest) {
		final var barcode = Optional.ofNullable(supplierRequest)
			.map(SupplierRequest::getLocalItemBarcode)
			.filter(NcipSupplyingRequestStrategy::hasText)
			.orElse(null);
		if (barcode != null) {
			return barcode;
		}

		if (Optional.ofNullable(supplierRequest)
			.map(SupplierRequest::getLocalItemId)
			.filter(NcipSupplyingRequestStrategy::hasText)
			.isPresent()) {
			throw new IllegalArgumentException(
				"Cannot create NCIP RequestItem item identifier without item barcode");
		}

		return null;
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
