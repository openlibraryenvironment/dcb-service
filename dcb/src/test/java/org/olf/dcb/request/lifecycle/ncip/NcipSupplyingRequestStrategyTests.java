package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.BibRecord;
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
import org.olf.dcb.request.resolution.SharedIndexService;
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
			transport,
			new NcipPayloadBuilder(),
			hostLmsService(),
			ncipIdentityConfiguration(),
			addressResolver(),
			bibliographicMetadataResolver());
		final var supplierRequest = new SupplierRequest()
			.setHostLmsCode("supplier-host")
			.setLocalAgency("supplier-agency")
			.setLocalBibId("supplier-bib-1")
			.setLocalItemId("supplier-item-1")
			.setLocalItemBarcode("supplier-barcode-1");
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
		assertThat(strategy.supportsProtocol("other-protocol"), is(false));
		assertThat(request.protocol(), is(NcipProtocol.PROTOCOL));
		assertThat(request.role(), is(LifecycleRole.SUPPLIER));
		assertThat(request.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(request.hostLmsCode(), is("supplier-host"));
		assertThat(request.agencyCode(), is("supplier-agency"));
		assertThat(request.correlationId(), is(patronRequestId + ":SUPPLIER"));
		assertThat(request.messageKind(), is(NcipProtocol.REQUEST_ITEM));
		assertThat(request.payload(), containsString("<RequestItem"));
		assertThat(request.payload(), containsString("<FromSystemId>dcb-system</FromSystemId>"));
		assertThat(request.payload(), containsString("<ToSystemId>supplier-host-system</ToSystemId>"));
		assertThat(request.payload(),
			containsString("<UserIdentifierValue>patron-barcode</UserIdentifierValue>"));
		assertThat(request.payload(),
			containsString("<BibliographicRecordIdentifier>supplier-bib-1"
				+ "</BibliographicRecordIdentifier>"));
		assertThat(request.payload(),
			containsString("<ItemIdentifierValue>supplier-barcode-1</ItemIdentifierValue>"));
		assertThat(request.payload(), containsString("<ItemIdentifierType"));
		assertThat(request.payload(), containsString(">barcode</"));
		assertThat(request.payload(), containsString(">local-item-id</"));
		assertThat(request.payload(),
			containsString("<ItemIdentifierValue>supplier-item-1</ItemIdentifierValue>"));
		assertThat(request.payload(), containsString("<Title>Supplier title</Title>"));
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

	@Test
	void rejectsItemLevelRequestWithoutBarcode() {
		final var strategy = new NcipSupplyingRequestStrategy(
			new CapturingTransport(new DeclarativeTransportResponse(
				"supplier-remote-request-1",
				"PLACED",
				"placed",
				"raw-message-1")),
			new NcipPayloadBuilder(),
			hostLmsService(),
			ncipIdentityConfiguration(),
			addressResolver(),
			bibliographicMetadataResolver());
		final var context = new RequestWorkflowContext()
			.setPatronHomeIdentity(new PatronIdentity()
				.setLocalBarcode("patron-barcode"))
			.setPatronRequest(new PatronRequest()
				.setId(UUID.randomUUID())
				.setBibClusterId(UUID.randomUUID()))
			.setSupplierRequest(new SupplierRequest()
				.setHostLmsCode("supplier-host")
				.setLocalAgency("supplier-agency")
				.setLocalItemId("supplier-item-1"));

		final var error = assertThrows(IllegalArgumentException.class,
			() -> singleValueFrom(strategy.place(context)));

		assertThat(error.getMessage(),
			containsString("without item barcode"));
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

	private static HostLmsService hostLmsService() {
		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.findByCode(anyString()))
			.thenAnswer(invocation -> Mono.just(DataHostLms.builder()
				.code(invocation.getArgument(0))
				.clientConfig(Map.of(
					NcipHostLmsConfiguration.ENDPOINT_URL_KEY, "https://example.org/ncip",
					NcipHostLmsConfiguration.NCIP_SYSTEM_ID_KEY, invocation.getArgument(0) + "-system"))
				.build()));
		return hostLmsService;
	}

	private static NcipIdentityConfiguration ncipIdentityConfiguration() {
		final var configuration = new NcipIdentityConfiguration();
		configuration.setSystemId("dcb-system");
		configuration.setAgencyId("dcb-agency");
		return configuration;
	}

	private static NcipAddressResolver addressResolver() {
		return new NcipAddressResolver(
			mock(org.olf.dcb.storage.AgencyRepository.class),
			mock(HostLmsService.class));
	}

	private static NcipBibliographicMetadataResolver bibliographicMetadataResolver() {
		final var sharedIndexService = mock(SharedIndexService.class);
		when(sharedIndexService.findSelectedBib(any(UUID.class)))
			.thenReturn(Mono.just(BibRecord.builder()
				.id(UUID.randomUUID())
				.sourceSystemId(UUID.randomUUID())
				.sourceRecordId("source-record-1")
				.title("Supplier title")
				.build()));
		return new NcipBibliographicMetadataResolver(sharedIndexService);
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
