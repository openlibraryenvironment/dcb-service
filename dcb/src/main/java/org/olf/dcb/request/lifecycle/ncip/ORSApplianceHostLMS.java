package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Creator;
import java.util.List;
import org.olf.dcb.core.interaction.AbstractHostLmsClient;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import reactor.core.publisher.Mono;

@Prototype
public class ORSApplianceHostLMS extends AbstractHostLmsClient {
	public static final String NCIP_ENDPOINT_URL_KEY
		= NcipHostLmsConfiguration.ENDPOINT_URL_KEY;

	private static final String REQUEST_TYPE = "Hold";
	private static final String REQUEST_SCOPE_TYPE = "Bibliographic Item";
	private static final String REQUESTED_ACTION_TYPE = "Accept For Loan";

	private final DeclarativeRequestTransport transport;
	private final NcipPayloadBuilder payloadBuilder;

	@Creator
	public ORSApplianceHostLMS(
		@Parameter HostLms hostLms,
		DeclarativeRequestTransport transport,
		NcipPayloadBuilder payloadBuilder) {

		super(hostLms);
		this.transport = transport;
		this.payloadBuilder = payloadBuilder;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(NcipHostLmsConfiguration.ENDPOINT_URL);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		final var correlationId = correlationIdFor(
			parameters,
			LifecycleRole.SUPPLIER);
		final var payload = payloadBuilder.requestItem(new NcipRequestItemPayload(
			firstText(
				parameters.getLocalPatronBarcode(),
				parameters.getLocalPatronId(),
				parameters.getPatronRequestId()),
			firstText(
				parameters.getLocalBibId(),
				parameters.getSupplyingLocalBibId(),
				parameters.getPatronRequestId()),
			firstText(parameters.getSupplyingAgencyCode(), getHostLmsCode()),
			correlationId,
			REQUEST_TYPE,
			REQUEST_SCOPE_TYPE));

		return send(
			parameters,
			LifecycleRole.SUPPLIER,
			NcipProtocol.REQUEST_ITEM,
			correlationId,
			payload);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {

		final var correlationId = correlationIdFor(
			parameters,
			LifecycleRole.BORROWER);
		final var payload = payloadBuilder.acceptItem(new NcipAcceptItemPayload(
			correlationId,
			REQUESTED_ACTION_TYPE,
			firstText(
				parameters.getLocalPatronBarcode(),
				parameters.getLocalPatronId(),
				parameters.getPatronRequestId()),
			firstText(
				parameters.getSupplyingLocalItemId(),
				parameters.getLocalItemId(),
				parameters.getSupplyingLocalItemBarcode(),
				parameters.getLocalItemBarcode())));

		return send(
			parameters,
			LifecycleRole.BORROWER,
			NcipProtocol.ACCEPT_ITEM,
			correlationId,
			payload);
	}

	private Mono<LocalRequest> send(
		PlaceHoldRequestParameters parameters,
		LifecycleRole role,
		String messageKind,
		String correlationId,
		String payload) {

		return transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				role,
				LifecycleOperation.PLACE_REQUEST,
				getHostLmsCode(),
				parameters.getSupplyingAgencyCode(),
				correlationId,
				messageKind,
				payload))
			.map(ORSApplianceHostLMS::toLocalRequest);
	}

	private static LocalRequest toLocalRequest(
		DeclarativeTransportResponse response) {

		return LocalRequest.builder()
			.localId(response.remoteRequestId())
			.localStatus(response.status())
			.rawLocalStatus(response.rawStatus())
			.build();
	}

	private static String correlationIdFor(
		PlaceHoldRequestParameters parameters,
		LifecycleRole role) {

		return "%s:%s".formatted(
			firstText(parameters.getPatronRequestId()),
			role.name());
	}

	private static String firstText(String... values) {
		for (final var value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}

		throw new IllegalArgumentException("Expected at least one non-blank value");
	}
}
