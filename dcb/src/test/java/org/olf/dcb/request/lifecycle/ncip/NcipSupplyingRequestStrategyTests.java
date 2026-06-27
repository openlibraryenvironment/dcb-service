package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;
import reactor.core.publisher.Mono;

class NcipSupplyingRequestStrategyTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(schemaPath());

	@Test
	void sendsRequestItemAndReturnsCanonicalEvidence() {
		final var patronRequestId = UUID.randomUUID();
		final var bibClusterId = UUID.randomUUID();
		final var transport = new CapturingTransport(new DeclarativeTransportResponse(
			"supplier-remote-request-1",
			"PLACED",
			"placed",
			"raw-message-1"));
		final var strategy = new NcipSupplyingRequestStrategy(
			transport, new NcipPayloadBuilder());
		final var supplierRequest = new SupplierRequest()
			.setHostLmsCode("supplier-host")
			.setLocalAgency("supplier-agency");
		final var context = new RequestWorkflowContext()
			.setPatronHomeIdentity(new PatronIdentity()
				.setLocalId("patron-local-id")
				.setLocalBarcode("patron-barcode"))
			.setPatronRequest(new PatronRequest()
				.setId(patronRequestId)
				.setBibClusterId(bibClusterId))
			.setSupplierRequest(supplierRequest);

		final var result = singleValueFrom(strategy.place(context));
		final var request = transport.onlyRequest();

		assertThat(strategy.type(), is(StrategyType.DECLARATIVE));
		assertThat(strategy.supportsProtocol(NcipProtocol.PROTOCOL), is(true));
		assertThat(strategy.supportsProtocol("iso18626"), is(false));
		assertThat(request.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(request.role(), is(LifecycleRole.SUPPLIER));
		assertThat(request.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(request.hostLmsCode(), is("supplier-host"));
		assertThat(request.agencyCode(), is("supplier-agency"));
		assertThat(request.correlationId(), is(patronRequestId + ":SUPPLIER"));
		assertThat(request.messageKind(), is(NcipProtocol.REQUEST_ITEM));
		assertThat(request.payload(), containsString("<RequestItem"));
		assertThat(request.payload(),
			containsString("<UserIdentifierValue>patron-barcode</UserIdentifierValue>"));
		assertThat(request.payload(),
			containsString("<BibliographicRecordIdentifier>" + bibClusterId
				+ "</BibliographicRecordIdentifier>"));
		assertDoesNotThrow(() -> validator.validate(request.payload()));
		assertThat(result.role(), is(LifecycleRole.SUPPLIER));
		assertThat(result.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(result.correlationId(), is(patronRequestId + ":SUPPLIER"));
		assertThat(result.remoteRequestId(), is("supplier-remote-request-1"));
		assertThat(result.status(), is("PLACED"));
		assertThat(result.rawStatus(), is("placed"));
		assertThat(result.rawMessageReference(), is("raw-message-1"));
		assertThat(result.localRequestId(), is("supplier-remote-request-1"));
		assertThat(result.localItemId(), nullValue());
		assertThat(result.localItemBarcode(), nullValue());
	}

	private static Path schemaPath() {
		final var workingDirectory = Paths.get("").toAbsolutePath();
		final var repositorySchema = workingDirectory.resolve(
			"src/xsd/ncip_v2_02.xsd");

		if (Files.exists(repositorySchema)) {
			return repositorySchema;
		}

		final var moduleSchema = workingDirectory.resolve(
			"../src/xsd/ncip_v2_02.xsd").normalize();

		if (Files.exists(moduleSchema)) {
			return moduleSchema;
		}

		throw new IllegalStateException(
			"Could not find NCIP schema from " + workingDirectory);
	}

	private static class CapturingTransport implements DeclarativeRequestTransport {
		private final DeclarativeTransportResponse response;
		private final List<DeclarativeTransportRequest> requests = new ArrayList<>();

		CapturingTransport(DeclarativeTransportResponse response) {
			this.response = response;
		}

		@Override
		public Mono<DeclarativeTransportResponse> send(
			DeclarativeTransportRequest request) {

			requests.add(request);
			return Mono.just(response);
		}

		DeclarativeTransportRequest onlyRequest() {
			assertThat(requests.size(), is(1));
			return requests.getFirst();
		}
	}
}
