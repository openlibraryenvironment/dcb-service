package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import java.util.Optional;
import java.util.UUID;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
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
public class NcipBorrowingRequestStrategy
	implements BorrowingAgencyRequestStrategy {
	private static final String REQUESTED_ACTION_TYPE = "Accept For Loan";

	private final DeclarativeRequestTransport transport;
	private final NcipPayloadBuilder payloadBuilder;
	private final HostLmsService hostLmsService;
	private final NcipIdentityConfiguration ncipIdentityConfiguration;
	private final NcipAddressResolver addressResolver;

	public NcipBorrowingRequestStrategy(
		DeclarativeRequestTransport transport,
		NcipPayloadBuilder payloadBuilder,
		HostLmsService hostLmsService,
		NcipIdentityConfiguration ncipIdentityConfiguration,
		NcipAddressResolver addressResolver) {

		this.transport = transport;
		this.payloadBuilder = payloadBuilder;
		this.hostLmsService = hostLmsService;
		this.ncipIdentityConfiguration = ncipIdentityConfiguration;
		this.addressResolver = addressResolver;
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
	public Mono<BorrowingAgencyRequestResult> place(
		RequestWorkflowContext context) {

		return send(context, LifecycleOperation.PLACE_REQUEST);
	}

	@Override
	public Mono<BorrowingAgencyRequestResult> revise(
		RequestWorkflowContext context) {

		return send(context, LifecycleOperation.REVISE_REQUEST);
	}

	private Mono<BorrowingAgencyRequestResult> send(
		RequestWorkflowContext context,
		LifecycleOperation operation) {

		final var correlationId = correlationIdFor(context, LifecycleRole.BORROWER);
		final var patronRequest = context.getPatronRequest();
		final var hostLmsCode = patronRequest != null
			? patronRequest.getPatronHostlmsCode()
			: null;
		final var agencyCode = context.getPatronAgencyCode();
		final var supplyingAgencyCode = Optional.ofNullable(context)
			.map(RequestWorkflowContext::getSupplierRequest)
			.map(SupplierRequest::getResolvedAgency)
			.map(org.olf.dcb.core.model.Agency::getCode)
			.orElseGet(context::getLenderAgencyCode);

		return hostLmsService.findByCode(hostLmsCode)
			.switchIfEmpty(Mono.error(new IllegalArgumentException(
				"Cannot create NCIP AcceptItem without HostLMS " + hostLmsCode)))
			.flatMap(hostLms -> {
				final var toAgencyId = addressResolver.agencyIdForHost(hostLms);
				return Mono.zip(
						addressResolver.agencyIdForLocalAgencyCode(
							supplyingAgencyCode,
							ncipIdentityConfiguration.getAgencyId()),
						addressResolver.agencyIdForLocalAgencyCode(
							agencyCode,
							toAgencyId))
					.map(tuple -> payloadBuilder.acceptItem(new NcipAcceptItemPayload(
						party(hostLms, tuple.getT1(), toAgencyId),
						correlationId,
						REQUESTED_ACTION_TYPE,
						tuple.getT2(),
						userIdentifierValueFor(context),
						tuple.getT1(),
						"barcode",
						requiredItemBarcodeFor(context),
						bibliographicDescriptionFor(context, tuple.getT1()))));
			})
			.flatMap(payload -> transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				LifecycleRole.BORROWER,
				operation,
				hostLmsCode,
				agencyCode,
				correlationId,
				NcipProtocol.ACCEPT_ITEM,
				payload)))
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
				NcipProtocol.PROTOCOL,
				correlationId,
				response.remoteRequestId(),
				response.status(),
				response.rawStatus(),
				response.rawMessageReference()));
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
			.map(RequestWorkflowContext::getPatronRequest)
			.map(PatronRequest::getRequestingIdentity)
			.map(PatronIdentity::getFirstBarcode)
			.filter(NcipBorrowingRequestStrategy::hasText)
			.or(() -> Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronRequest)
				.map(PatronRequest::getRequestingIdentity)
				.map(PatronIdentity::getLocalId)
				.filter(NcipBorrowingRequestStrategy::hasText))
			.or(() -> Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronHomeIdentity)
				.map(PatronIdentity::getFirstBarcode)
				.filter(NcipBorrowingRequestStrategy::hasText))
			.or(() -> Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronHomeIdentity)
				.map(PatronIdentity::getLocalId)
				.filter(NcipBorrowingRequestStrategy::hasText))
			.or(() -> Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronRequest)
				.map(PatronRequest::getId)
				.map(UUID::toString))
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create NCIP AcceptItem without patron identity"));
	}

	private static String requiredItemBarcodeFor(RequestWorkflowContext context) {
		return Optional.ofNullable(context)
			.map(RequestWorkflowContext::getSupplierRequest)
			.map(SupplierRequest::getLocalItemBarcode)
			.filter(NcipBorrowingRequestStrategy::hasText)
			.orElseThrow(() -> new IllegalArgumentException(
				"Cannot create NCIP AcceptItem without supplier item barcode"));
	}

	private static NcipBibliographicDescription bibliographicDescriptionFor(
		RequestWorkflowContext context,
		String bibliographicRecordAgencyId) {

		return new NcipBibliographicDescription(
			Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPickupBibTitle)
				.filter(NcipBorrowingRequestStrategy::hasText)
				.orElse(null),
			null,
			Optional.ofNullable(context)
				.map(RequestWorkflowContext::getPatronRequest)
				.map(PatronRequest::getLocalBibId)
				.filter(NcipBorrowingRequestStrategy::hasText)
				.or(() -> Optional.ofNullable(context)
					.map(RequestWorkflowContext::getPatronRequest)
					.map(PatronRequest::getBibClusterId)
					.map(UUID::toString))
				.orElse(null),
			bibliographicRecordAgencyId,
			requiredItemBarcodeFor(context),
			null);
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
