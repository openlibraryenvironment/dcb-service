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

class NcipBorrowingRequestStrategyTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(schemaPath());

	@Test
	void sendsAcceptItemAndReturnsCanonicalEvidenceWithoutVirtualArtifacts() {
		final var patronRequestId = UUID.randomUUID();
		final var transport = new CapturingTransport(new DeclarativeTransportResponse(
			"borrower-remote-request-1",
			"ACCEPTED",
			"accepted",
			"raw-message-2"));
		final var strategy = new NcipBorrowingRequestStrategy(
			transport, new NcipPayloadBuilder());
		final var context = new RequestWorkflowContext()
			.setPatronAgencyCode("borrower-agency")
			.setPatronRequest(new PatronRequest()
				.setId(patronRequestId)
				.setPatronHostlmsCode("borrower-host")
				.setRequestingIdentity(new PatronIdentity()
					.setLocalId("borrower-patron-id")
					.setLocalBarcode("borrower-barcode")))
			.setSupplierRequest(new SupplierRequest()
				.setLocalItemId("supplier-item-1")
				.setLocalItemBarcode("supplier-barcode-1"));

		final var result = singleValueFrom(strategy.place(context));
		final var request = transport.onlyRequest();

		assertThat(strategy.type(), is(StrategyType.DECLARATIVE));
		assertThat(strategy.supportsProtocol(NcipProtocol.PROTOCOL), is(true));
		assertThat(strategy.supportsProtocol("other-protocol"), is(false));
		assertThat(request.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(request.role(), is(LifecycleRole.BORROWER));
		assertThat(request.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(request.hostLmsCode(), is("borrower-host"));
		assertThat(request.agencyCode(), is("borrower-agency"));
		assertThat(request.correlationId(), is(patronRequestId + ":BORROWER"));
		assertThat(request.messageKind(), is(NcipProtocol.ACCEPT_ITEM));
		assertThat(request.payload(), containsString("<AcceptItem"));
		assertThat(request.payload(),
			containsString("<RequestIdentifierValue>" + patronRequestId
				+ ":BORROWER</RequestIdentifierValue>"));
		assertThat(request.payload(),
			containsString("<UserIdentifierValue>borrower-barcode</UserIdentifierValue>"));
		assertThat(request.payload(),
			containsString("<ItemIdentifierValue>supplier-item-1</ItemIdentifierValue>"));
		assertDoesNotThrow(() -> validator.validate(request.payload()));
		assertThat(result.role(), is(LifecycleRole.BORROWER));
		assertThat(result.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(result.correlationId(), is(patronRequestId + ":BORROWER"));
		assertThat(result.remoteRequestId(), is("borrower-remote-request-1"));
		assertThat(result.localRequestId(), is("borrower-remote-request-1"));
		assertThat(result.createdVirtualBib(), is(false));
		assertThat(result.createdVirtualItem(), is(false));
		assertThat(result.localBibId(), nullValue());
		assertThat(result.localItemId(), nullValue());
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
