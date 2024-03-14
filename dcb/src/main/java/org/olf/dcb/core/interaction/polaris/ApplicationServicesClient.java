package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpMethod.DELETE;
import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.valueOf;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.*;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowReply.Continue;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowReply.Retain;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse.InputRequired;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.PolarisClient.APPLICATION_SERVICES;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.extractMapValue;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.noExtraErrorHandling;
import static reactor.function.TupleUtils.function;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException;
import org.olf.dcb.core.interaction.polaris.exceptions.PatronBlockException;
import org.olf.dcb.core.interaction.polaris.exceptions.PolarisWorkflowException;
import org.zalando.problem.Problem;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@Slf4j
class ApplicationServicesClient {
	private final PolarisLmsClient client;
	private final ApplicationServicesAuthFilter authFilter;
	private final String URI_PARAMETERS;

	// ToDo align these URLs
	public static final URI ERR0210 = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/0210/Polaris/UnableToLoadPatronBlocks");

	ApplicationServicesClient(PolarisLmsClient client) {
		this.client = client;
		this.authFilter = new ApplicationServicesAuthFilter(client);
		this.URI_PARAMETERS = "/polaris.applicationservices/api" + client.getGeneralUriParameters(APPLICATION_SERVICES);
	}

	/**
	 * Based upon <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/create_hold_request">post hold request docs</a>
	 */
	Mono<Tuple4<String, String, String, String>> createILLHoldRequestWorkflow(HoldRequestParameters holdRequestParameters) {
		log.debug("createILLHoldRequestWorkflow with holdRequestParameters {}", holdRequestParameters);

		final var path = createPath("workflow");
		final String activationDate = LocalDateTime.now().format( ofPattern("yyyy-MM-dd"));
		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var workflowRequest = WorkflowRequest.builder()
			.workflowRequestType(5)
			.txnUserID(user)
			.txnBranchID(holdRequestParameters.getLocalItemLocationId())
			.txnWorkstationID(workstation)
			.requestExtension( RequestExtension.builder()
				.workflowRequestExtensionType(9)
				.data(RequestExtensionData.builder()
					.patronID(Optional.ofNullable(holdRequestParameters.getLocalPatronId()).map(Integer::valueOf).orElse(null))
					.pickupBranchID( Integer.valueOf(holdRequestParameters.getPickupLocation()) )
					.origin(2)
					.activationDate(activationDate)
					.expirationDate(LocalDateTime.now().plusDays(999).format( ofPattern("MM/dd/yyyy")))
					.staffDisplayNotes(holdRequestParameters.getNote())
					.nonPublicNotes(holdRequestParameters.getNote())
					.pACDisplayNotes(holdRequestParameters.getNote())
					.bibliographicRecordID(0)
					.itemRecordID(0)
					.itemLevelHold(FALSE)
					.ignorePatronBlocksPrompt(TRUE)
					.bulkMode(FALSE)
					.patronBlocksOnlyPrompt(FALSE)
					.ignoreMaximumHoldsPrompt(TRUE)
					.unlockedRequest(TRUE)
					.title(holdRequestParameters.getTitle())
					.browseTitle(holdRequestParameters.getTitle())
					.build())
				.build())
			.build();

		return createRequest(POST, path, uri -> {})
			.zipWith(Mono.just(workflowRequest))
			.map(function(ApplicationServicesClient::addBodyToRequest))
			.flatMap(workflowReq -> client.retrieve(workflowReq, Argument.of(WorkflowResponse.class),
				noExtraErrorHandling()))
			.map(response -> validateHoldResponse(response))
			.thenReturn(Tuples.of(
				holdRequestParameters.getLocalPatronId(),
				holdRequestParameters.getTitle(),
				holdRequestParameters.getNote(),
				activationDate));
	}

	/**
	 * Based upon <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/create_hold_request">post hold request docs</a>
	 */
	Mono<Tuple4<String, Integer, String, String>> createHoldRequestWorkflow(HoldRequestParameters holdRequestParameters) {
		log.debug("createHoldRequestWorkflow with holdRequestParameters {}", holdRequestParameters);

		final var path = createPath("workflow");

		final String activationDate = LocalDateTime.now().format( ofPattern("yyyy-MM-dd"));

		return createRequest(POST, path, uri -> {})
			.zipWith(getLocalRequestBody(holdRequestParameters, activationDate))
			.map(function(ApplicationServicesClient::addBodyToRequest))
			.flatMap(workflowReq -> client.retrieve(workflowReq, Argument.of(WorkflowResponse.class),
					noExtraErrorHandling()))
			.map(response -> validateHoldResponse(response))
			.thenReturn(Tuples.of(
				holdRequestParameters.getLocalPatronId(),
				holdRequestParameters.getBibliographicRecordID(),
				activationDate,
				holdRequestParameters.getNote() != null ? holdRequestParameters.getNote() : ""));
	}

	private WorkflowResponse validateHoldResponse(WorkflowResponse workflowResponse) {
		if (workflowResponse.getWorkflowStatus() < 1) {
			String messages = workflowResponse.getPrompt().getMessage() != null
				? workflowResponse.getPrompt().getMessage().toString()
				: "NO DETAILS";

			throw new HoldRequestException(messages);
		}

		if (workflowResponse.getWorkflowStatus() == 1) {
			// should be: "The hold request has been created."
			if (workflowResponse.getPrompt() != null && workflowResponse.getPrompt().getMessage() != null) {
				log.info(">>>>>>>" + workflowResponse.getPrompt().getMessage() + "<<<<<<<<");
			}
		}

		return workflowResponse;
	}

	Mono<LibraryHold> getLocalHoldRequest(Integer id) {
		final var path = createPath("holds", id);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, LibraryHold.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	Mono<Patron> getPatron(String localPatronId) {
		final var path = createPath("patrons", localPatronId);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, PatronData.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()))
			.map(data -> Patron.builder()
				.localId(singletonList(valueOf(data.getPatronID())))
				.localPatronType(valueOf(data.getPatronCodeID()))
				.localBarcodes(singletonList(data.getBarcode()))
				.localHomeLibraryCode(valueOf(data.getOrganizationID()))
				.build());
	}

	public Mono<Integer> handlePatronBlock(Integer localPatronId) {
		return getPatronBlocks(localPatronId)
			.filter(list -> !list.isEmpty())
			.flatMap(this::checkPatronBlock)
			.flatMap(row -> deletePatronBlock(localPatronId, row.getBlockType(), row.getBlockID()))
			.defaultIfEmpty( localPatronId );
	}

	// we only expect
	private Mono<PatronBlockGetRow> checkPatronBlock(List<PatronBlockGetRow> patronBlockGetRows) {
		final Integer VERIFY_PATRON_DATA = 3;
		return Mono.just(patronBlockGetRows)
			.filter(list -> list.size() > 0)
			.switchIfEmpty(Mono.error(new PatronBlockException("patron block list size more than 1")))
			.map(list -> list.get(0))
			.filter(block -> Objects.equals(block.getBlockType(), VERIFY_PATRON_DATA))
			.switchIfEmpty(Mono.error(new PatronBlockException("patron block was of an unexpected type")));
	}

	private Mono<List<PatronBlockGetRow>> getPatronBlocks(Integer localPatronId) {
		final var conf = client.getConfig();
		final var path = createPath("patrons", localPatronId, "blockssummary");

		return createRequest(GET, path, uri -> uri
				.queryParam("logonBranchID", conf.get(LOGON_BRANCH_ID))
				.queryParam("associatedblocks", false))
			.flatMap(request -> client.retrieve(request,
				Argument.listOf(PatronBlockGetRow.class), response -> response
					.onErrorResume(error -> {
						log.error("Error attempting to retrieve patron blocks {} : {}",
							localPatronId, error.getMessage());
						if ((error instanceof HttpClientResponseException) &&
							(((HttpClientResponseException) error).getStatus() == HttpStatus.NOT_FOUND)) {
							// Not found is not really an error WRT patron blocks
							return Mono.empty();
						} else {
							return Mono.error(
								Problem.builder()
									.withType(ERR0210)
									.withTitle(
										"Unable to retrieve patron blocks from polaris") // : "+error.getMessage())
									.withDetail(error.getMessage())
									.with("localPatronId", localPatronId)
									.build()
							);
						}
					})
			));
	}

	private Mono<Integer> deletePatronBlock(Integer localPatronId, Integer blocktype, Integer blockid) {
		final var path = createPath("patrons", localPatronId, "blocks", blocktype, blockid);

		return createRequest(DELETE, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(Boolean.class),
				noExtraErrorHandling()))
			.doOnSuccess(bool -> {
				if (!bool) {
					log.warn("Deleting patron block returned false.");
				} else {
					log.debug("Successfully deleted patron block.");
				}
			})
			.thenReturn(localPatronId);
	}

	public Mono<String> getPatronBarcode(String localId) {
		final var path = createPath("barcodes", "patrons", localId);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(String.class),
				response -> response
					.onErrorResume(error -> {
						log.error("Error attempting to retrieve patron barcode with patron id {} : {}",
							localId, error.getMessage());
						if ((error instanceof HttpClientResponseException) &&
							(((HttpClientResponseException) error).getStatus() == HttpStatus.NOT_FOUND)) {
							// Not found is not really an error
							return Mono.empty();
						} else {
							return Mono.error(new PolarisWorkflowException(
								"Error attempting to retrieve patron barcode with patron id " +localId+ " : " +error.getMessage()));
						}
					})
			))
			// remove quotes
			.map(string -> string.replace("\"", ""));
	}

	public Mono<String> getPatronIdByIdentifier(String identifier, String identifierType) {
		final var path = createPath("ids", "patrons");
		return createRequest(GET, path,
			uri -> uri
				.queryParam("id", identifier)
				.queryParam("type", identifierType))
			.flatMap(request -> client.retrieve(request, Argument.of(String.class),
				noExtraErrorHandling()));
	}

	private Mono<Integer> getHoldRequestDefaults() {
		final var path = createPath("holdsdefaults");
		final Integer defaultExpirationDatePeriod = 999;
		return createRequest(GET, path, uri -> {})
			// should the org id be pick up org id?
			.flatMap(request -> client.exchange(request, HoldRequestDefault.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()))
			.map(HoldRequestDefault::getExpirationDatePeriod)
			.doOnError(e -> log.debug("Error occurred when getting hold request defaults", e))
			.onErrorReturn(defaultExpirationDatePeriod);
	}

//	https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/patrons/get_requests_local
	public Mono<List<SysHoldRequest>> listPatronLocalHolds(String patronId) {
//		Returns list of local hold requests associated with the patron.
//		Server returns the list sorted by SysHoldStatusID and LastStatusTransitionDate in ascending order.
//		Leap client displays the list sorted by Author in ascending order.

		final var path = createPath("patrons", patronId, "requests", "local");
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(SysHoldRequest.class),
				noExtraErrorHandling()));
	}

	public Mono<List<ILLRequest>> getIllRequest(String patronLocalId) {

		final var path = createPath("patrons", patronLocalId, "requests", "ill");
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(ILLRequest.class),
				noExtraErrorHandling()));
	}


	public Mono<Integer> createBibliographicRecord(Bib bib) {
		final var path = createPath("bibliographicrecords");
		final var body = DtoBibliographicCreationData.builder()
			.recordOwnerID(1)
			.displayInPAC(FALSE)
			.doNotOverlay(TRUE)
			.record(DtoMARC21Record.builder()
				.leader(randomAlphanumeric(24))
				.controlfields(List.of(DtoMARC21ControlField.builder().tag("008").data(randomAlphanumeric(24)).build()))
				.datafields( List.of( DtoMARC21DataField.builder()
					.tag("245").ind1("0").ind2("0")
					.subfields( List.of(DtoMARC21Subfield.builder().code("a").data(bib.getTitle()).build()) )
					.build())).build()).build();

		return createRequest(POST, path, uri -> uri.queryParam("type", "create"))
			.map(request -> request.body(body))
			.flatMap(request -> client.retrieve(request, Argument.of(Integer.class),
				noExtraErrorHandling()));
	}

	public Mono<WorkflowResponse> deleteBibliographicRecord(String id) {
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var DeleteBibRecord = 11;
		final var DeleteBibRecordData = 10;
		final var body = WorkflowRequest.builder()
			.workflowRequestType(DeleteBibRecord)
			.txnUserID(user)
			.txnBranchID(branch)
			.txnWorkstationID(workstation)
			.requestExtension(RequestExtension.builder()
				.workflowRequestExtensionType(DeleteBibRecordData)
				.data(RequestExtensionData.builder()
					.bibRecordIDs( singletonList(Integer.valueOf(id)) )
					.build())
				.build())
			.build();
		return createRequest(POST, path, uri -> {
		})
			.map(request -> request.body(body))
			.flatMap(req -> client.retrieve(req, Argument.of(WorkflowResponse.class),
				noExtraErrorHandling()))
			.flatMap(resp -> handlePolarisWorkflow(resp, ConfirmBibRecordDelete, Continue));
	}


	public Mono<WorkflowResponse> convertToIll(Integer illLocationId, String localId) {
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var body = WorkflowRequest.builder()
			.workflowRequestType(20)
			.txnUserID(user)
			.txnBranchID(illLocationId)
			.txnWorkstationID(workstation)
			.requestExtension( RequestExtension.builder()
				.workflowRequestExtensionType(16)
				.data(RequestExtensionData.builder()
					.sysHoldRequestID(localId)
					.skipTotalILLLimitExceededPrompt(TRUE)
					.build())
				.build())
			.build();

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(body))
			.flatMap(this::convertToIllRequest);
	}


	public Mono<ILLRequestInfo> transferRequest(Integer illLocationId, Integer illRequestId) {
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var body = WorkflowRequest.builder()
			.workflowRequestType(18)
			.txnUserID(user)
			.txnBranchID(illLocationId)
			.txnWorkstationID(workstation)
			.requestExtension( RequestExtension.builder()
				.workflowRequestExtensionType(14)
				.data(RequestExtensionData.builder()
					.iLLRequestID(illRequestId)
					.circTranType(12)
					.build())
				.build())
			.build();

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(body))
			.flatMap(this::createTransferRequest);
	}

	private <R> Mono<ILLRequestInfo> createTransferRequest(MutableHttpRequest<WorkflowRequest> workflowReq) {

			return client.retrieve(workflowReq, Argument.of(WorkflowResponse.class), noExtraErrorHandling())
				.doOnSuccess(r -> log.info("Got transfer request response {}", r))
				.doOnError(e -> log.info("Error response for transferring ILL request {} {}", workflowReq, e))
				// when we save the virtual item we need to confirm we do not want the item to display in pac
				.flatMap(response -> handlePolarisWorkflow(response, 55, 5))
				.switchIfEmpty(
					Mono.error(new PolarisWorkflowException("transferring ILL request failed expecting workflow response to: " + workflowReq)))
				.map(workflowResponse -> workflowResponse.getAnswerExtension().getAnswerData().getILLRequestInfo());
		}

	private Mono<WorkflowResponse> convertToIllRequest(MutableHttpRequest<WorkflowRequest> workflowReq) {

		return client.retrieve(workflowReq, Argument.of(WorkflowResponse.class), noExtraErrorHandling())
			.doOnSuccess(r -> log.info(">>>>>>> {} <<<<<<<", r.getInformationMessages() != null
				? r.getInformationMessages()
				: "No information messages available."))
			.doOnError(e -> log.info("Error response for convert ILL request {} {}", workflowReq, e))
			.switchIfEmpty(Mono.error(new PolarisWorkflowException(
				"convert ILL request failed expecting workflow response to: " + workflowReq)));
	}

	public Mono<WorkflowResponse> addItemRecord(CreateItemCommand createItemCommand) {
		// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/overview
		// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/add_or_update_item_record
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var itemConfig = client.getItemConfig();
		final var barcodePrefix = extractMapValue(itemConfig, BARCODE_PREFIX, String.class);

		final var itemRecordType = 8;
		final var itemRecordData = 6;

		final var Available = 1; // In

		// The createItemCommand for polaris should use the home location of the patron placing the request
		// So that the request can be routed appropriately in house
		return Mono.zip(createRequest(POST, path, uri -> {}),
				client.getMappedItemType(createItemCommand.getCanonicalItemType()))
			.map(tuple -> {
				final var request = tuple.getT1();
				final var itemtype = Integer.parseInt(tuple.getT2());

				// used for branches - note: different combinations important here
				final Integer interLibraryLoanBranch = getInterLibraryLoanBranch(itemConfig);
				final Integer patronHomeBranch = getPatronHomeBranch(createItemCommand);

				final var body = WorkflowRequest.builder()
					.workflowRequestType(itemRecordType)
					.txnUserID(user)
					.txnBranchID(branch)
					.txnWorkstationID(workstation)
					.requestExtension( RequestExtension.builder()
						.workflowRequestExtensionType(itemRecordData)
						.data(RequestExtensionData.builder()
							.associatedBibRecordID(Integer.parseInt(createItemCommand.getBibId()))
							.barcode((barcodePrefix!=null?barcodePrefix:"") + createItemCommand.getBarcode())
							.isNew(TRUE)
							.displayInPAC(FALSE)
							.assignedBranchID( isInterLibraryLoanBranchIfNotNull(interLibraryLoanBranch, patronHomeBranch) )
							.owningBranchID( isInterLibraryLoanBranchIfNotNull(interLibraryLoanBranch, patronHomeBranch) )
							.homeBranchID( isInterLibraryLoanBranchIfNotNull(interLibraryLoanBranch, patronHomeBranch) )
							.renewalLimit(extractMapValue(itemConfig, RENEW_LIMIT, Integer.class))
							.fineCodeID(extractMapValue(itemConfig, FINE_CODE_ID, Integer.class))
							.itemRecordHistoryActionID(extractMapValue(itemConfig, HISTORY_ACTION_ID, Integer.class))
							.loanPeriodCodeID(extractMapValue(itemConfig, LOAN_PERIOD_CODE_ID, Integer.class))
							.shelvingSchemeID(extractMapValue(itemConfig, SHELVING_SCHEME_ID, Integer.class))
							.isProvisionalSave(FALSE)
							.nonCircluating(FALSE)
							.loneableOutsideSystem(TRUE)
							.holdable(TRUE)
							.itemStatusID(Available)
							.materialTypeID(itemtype)
							.build())
						.build())
					.build();

				log.info("create item workflow request: {}", body);
				return request.body(body);
			})
			.flatMap(this::createItemRequest);
	}

	private static Integer isInterLibraryLoanBranchIfNotNull(Integer interLibraryLoanBranch, Integer patronHomeBranch) {
		return interLibraryLoanBranch != null ? interLibraryLoanBranch : patronHomeBranch;
	}

	private Integer getPatronHomeBranch(CreateItemCommand createItemCommand) {
		final var patronHomeLocation = createItemCommand.getPatronHomeLocation();
		if (patronHomeLocation == null) {
			throw new IllegalArgumentException(
				"Missing patron home location for polaris user - createItemCommand=" + createItemCommand);
		}

		return Integer.valueOf(patronHomeLocation);
	}

	private static Integer getInterLibraryLoanBranch(Map<String, Object> itemConfig) {

		return extractMapValue(itemConfig, ILL_LOCATION_ID, Integer.class);
	}

	public Mono<Void> updateItemRecord(String itemId, Integer fromStatus, Integer toStatus) {
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var itemRecordType = 8;
		final var itemRecordData = 6;

		return createRequest(POST, path, uri -> {})
			.map(request -> {
				final var body = WorkflowRequest.builder()
					.workflowRequestType(itemRecordType)
					.txnUserID(user)
					.txnBranchID(branch)
					.txnWorkstationID(workstation)
					.requestExtension( RequestExtension.builder()
						.workflowRequestExtensionType(itemRecordData)
						.data(RequestExtensionData.builder()
							.itemRecordID( Integer.valueOf(itemId) )
							.originalItemStatusID(fromStatus)
							.itemStatusID(toStatus)
							.build())
						.build())
					.build();
				return request.body(body);
			})
			.flatMap(this::createItemRequest)
			.then();
	}

	public Mono<WorkflowResponse> deleteItemRecord(String id) {
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var DeleteItemRecord = 10;
		final var DeleteItemRecordData = 8;
		final var body = WorkflowRequest.builder()
			.workflowRequestType(DeleteItemRecord)
			.txnUserID(user)
			.txnBranchID(branch)
			.txnWorkstationID(workstation)
			.requestExtension(RequestExtension.builder()
				.workflowRequestExtensionType(DeleteItemRecordData)
				.data(RequestExtensionData.builder()
					.isAutoDelete(FALSE)
					.itemRecordIDs( singletonList(Integer.valueOf(id)) )
					.build())
				.build())
			.build();
		return createRequest(POST, path, uri -> {
		})
			.map(request -> request.body(body))
			.flatMap(req -> client.retrieve(req, Argument.of(WorkflowResponse.class),
				noExtraErrorHandling()))
			.flatMap(response -> handlePolarisWorkflow(response, ConfirmItemRecordDelete, Continue))
			.flatMap(response -> handlePolarisWorkflow(response, LastCopyOrRecordOptions, Retain));
	}

	// https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/overview
	private Mono<WorkflowResponse> handlePolarisWorkflow(
		WorkflowResponse response, Integer promptID, Integer promptResult) {

		if (!Objects.equals(response.getWorkflowStatus(), InputRequired)) {
			log.debug("Input was not required for workflow response: {}", response.getWorkflowRequestGuid());
			return Mono.just(response);
		}

		log.info("Trying to handle polaris workflow status: {}", response.getWorkflowStatus());
		return Mono.just(response)
			.filter(workflowResponse -> workflowResponse.getPrompt() != null)
			.filter(workflowResponse -> Objects.equals(workflowResponse.getPrompt().getWorkflowPromptID(), promptID))
			.flatMap(resp -> createItemWorkflowReply(resp.getWorkflowRequestGuid(), promptID, promptResult))
			.flatMap(req -> client.retrieve(req, Argument.of(WorkflowResponse.class),
				noExtraErrorHandling()))
			.switchIfEmpty(Mono.error(new PolarisWorkflowException("Failed to handle polaris workflow. " +
				"Response was: " + response + ". Expected to reply to promptID: " + promptID + " with reply: " + promptResult)));
	}

	private Mono<WorkflowResponse> createItemRequest(MutableHttpRequest<WorkflowRequest> workflowReq) {

		return client.retrieve(workflowReq, Argument.of(WorkflowResponse.class), noExtraErrorHandling())
			.doOnSuccess(r -> log.info("Got create item response {}", r))
			.doOnError(e -> log.info("Error response for create item {} {}", workflowReq, e))
			// when we save the virtual item we need to confirm we do not want the item to display in pac
			.flatMap(response -> handlePolarisWorkflow(response, NoDisplayInPAC, Continue))
			// if the item barcode exists we still want to continue saving the item
			.flatMap(resp -> handlePolarisWorkflow(resp, DuplicateRecords, Continue))
			.switchIfEmpty(Mono.error(new PolarisWorkflowException("item request failed expecting workflow response to: " + workflowReq)));
	}

	private Mono<MutableHttpRequest<WorkflowReply>> createItemWorkflowReply(
		String guid, Integer promptID, Integer promptResult) {

		return createRequest(PUT, createPath("workflow", guid), uri -> {})
			.map(request -> request.body(WorkflowReply.builder()
				.workflowPromptID(promptID)
				.workflowPromptResult(promptResult)
				.build()
			));
	}

	public Mono<String> getItemBarcode(String itemId) {
		final var path = createPath("barcodes", "items", itemId);

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(String.class),
				noExtraErrorHandling()))
			// remove quotes
			.map(string -> string.replace("\"", ""));
	}

	public Mono<List<MaterialType>> listMaterialTypes() {
		final var path = createPath("materialtypes");

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(MaterialType.class),
				noExtraErrorHandling()));
	}

	public Mono<List<PolarisLmsClient.PolarisItemStatus>> listItemStatuses() {
		final var path = createPath("itemstatuses");

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request,
				Argument.listOf(PolarisLmsClient.PolarisItemStatus.class), noExtraErrorHandling()));
	}

	public Mono<BibliographicRecord> getBibliographicRecordByID(String localBibId) {
		final var path = createPath("bibliographicrecords", localBibId);

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, BibliographicRecord.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private Mono<WorkflowRequest> getLocalRequestBody(HoldRequestParameters holdRequestParameters, String activationDate) {

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesConfig = client.getServicesConfig();
		final var workstation = extractMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class);

		final var placeHoldRequest = 5;
		final var holdRequestData = 9;

		return getHoldRequestDefaults()
			.map(expiration -> WorkflowRequest.builder()
				.workflowRequestType(placeHoldRequest)
				.txnUserID(user)
				.txnBranchID(branch)
				.txnWorkstationID(workstation)
				.requestExtension( RequestExtension.builder()
					.workflowRequestExtensionType(holdRequestData)
					.data(RequestExtensionData.builder()
						.patronID(Optional.ofNullable(holdRequestParameters.getLocalPatronId()).map(Integer::valueOf).orElse(null))
						//.patronBranchID( branch )
						.pickupBranchID( checkPickupBranchID(extractMapValue(conf, ILL_LOCATION_ID, Integer.class)) )
						.origin(2)
						.activationDate(activationDate)
						.expirationDate(LocalDateTime.now().plusDays(expiration).format( ofPattern("MM/dd/yyyy")))
						.staffDisplayNotes(holdRequestParameters.getNote())
						.nonPublicNotes(holdRequestParameters.getNote())
						.pACDisplayNotes(holdRequestParameters.getNote())
						.bibliographicRecordID(holdRequestParameters.getBibliographicRecordID())
						.itemRecordID(Optional.ofNullable(holdRequestParameters.getRecordNumber()).map(Integer::valueOf).orElse(null))
						.itemBarcode(holdRequestParameters.getItemBarcode())
						.itemLevelHold(TRUE)
						.ignorePatronBlocksPrompt(TRUE)
						.bulkMode(FALSE)
						.patronBlocksOnlyPrompt(FALSE)
						.ignoreMaximumHoldsPrompt(FALSE)
						.build())
					.build())
				.build());
	}

	private static Integer checkPickupBranchID(Integer pickupLocation) {
		log.debug("checking pickup branch id from passed pickup location: '{}'", pickupLocation);

		try {
			return Optional.ofNullable( pickupLocation )
				.orElseThrow(() -> new NumberFormatException("Invalid number format"));
		} catch (NumberFormatException e) {
			throw new HoldRequestException("Cannot use pickup location '" + pickupLocation + "' for pickupBranchID.");
		}
	}

	private Mono<MutableHttpRequest<?>> createRequest(HttpMethod httpMethod, String path,
		Consumer<UriBuilder> uriBuilderConsumer) {

		return client.createRequest(httpMethod,path).map(req -> req.uri(uriBuilderConsumer)).flatMap(authFilter::basicAuth);
	}

	private String createPath(Object... pathSegments) {
		return URI_PARAMETERS + "/" + Arrays.stream(pathSegments).map(Object::toString).collect(Collectors.joining("/"));
	}

	private static MutableHttpRequest<WorkflowRequest> addBodyToRequest(
		MutableHttpRequest<?> request, WorkflowRequest body) {

		log.debug("trying addLocalHoldRequest with body {}", body);
		return request.body(body);
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class ILLRequestInfo {
		@JsonProperty("ILLRequestID")
		private Integer illRequestID;
		@JsonProperty("PatronName")
		private String patronName;
		@JsonProperty("PatronBarcode")
		private String patronBarcode;
		@JsonProperty("PatronPhone")
		private String patronPhone;
		@JsonProperty("PatronEmail")
		private String patronEmail;
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("PickupBranchID")
		private Integer pickupBranchID;
		@JsonProperty("CreationDate")
		private String creationDate;
		@JsonProperty("NeedByDate")
		private String needByDate;
		@JsonProperty("ActivationDate")
		private String activationDate;
		@JsonProperty("Author")
		private String author;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("BrowseAuthor")
		private String browseAuthor;
		@JsonProperty("BrowseTitle")
		private String browseTitle;
		@JsonProperty("MARCTOMID")
		private Integer marcTomID;
		@JsonProperty("MediumType")
		private String mediumType;
		@JsonProperty("Publisher")
		private String publisher;
		@JsonProperty("PublicationYear")
		private Integer publicationYear;
		@JsonProperty("LCCN")
		private String lccn;
		@JsonProperty("ISBN")
		private String isbn;
		@JsonProperty("ISSN")
		private String issn;
		@JsonProperty("VolumeAndIssue")
		private String volumeAndIssue;
		@JsonProperty("Series")
		private String series;
		@JsonProperty("Edition")
		private String edition;
		@JsonProperty("RequestType")
		private String requestType;
		@JsonProperty("RequestTypeID")
		private Integer requestTypeID;
		@JsonProperty("ItemBarcode")
		private String itemBarcode;
		@JsonProperty("ILLStatusID")
		private Integer illStatusID;
		@JsonProperty("CurrentStatus")
		private String currentStatus;
		@JsonProperty("PreviousStatus")
		private String previousStatus;
		@JsonProperty("StatusTransitionDate")
		private String statusTransitionDate;
		@JsonProperty("StaffNotes1")
		private String staffNotes1;
		@JsonProperty("StaffNotes2")
		private String staffNotes2;
		@JsonProperty("OPACNotes")
		private String opacNotes;
		@JsonProperty("InnReachType")
		private Integer innReachType;
		@JsonProperty("MARCTOMDescription")
		private String marcTomDescription;
		@JsonProperty("CentralItemType")
		private Integer centralItemType;
		@JsonProperty("CentralCode")
		private String centralCode;
		@JsonProperty("ReturnUncirc")
		private Boolean returnUncirc;
		@JsonProperty("InnReachBibCallNumber")
		private String innReachBibCallNumber;
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("HoldTillDate")
		private String holdTillDate;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("InnReachTrackingID")
		private Integer innReachTrackingID;
		@JsonProperty("AssignedBranchID")
		private Integer assignedBranchID;
		@JsonProperty("ILLMessageDetailSentDate")
		private String illMessageDetailSentDate;
		@JsonProperty("InnReachItemBarcode")
		private String innReachItemBarcode;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class ILLRequest {
		@JsonProperty("ILLRequestID")
		private Integer illRequestID;
		@JsonProperty("ILLStatusID")
		private Integer illStatusID;
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("BibRecordID")
		private Integer bibRecordID;
		@JsonProperty("PickupLibID")
		private Integer pickupLibID;
		@JsonProperty("Author")
		private String author;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("BrowseTitleNonFilingCount")
		private Integer browseTitleNonFilingCount;
		@JsonProperty("FormatDescription")
		private String formatDescription;
		@JsonProperty("CreationDate")
		private String creationDate;
		@JsonProperty("ActivationDate")
		private String activationDate;
		@JsonProperty("ILLStatusDescription")
		private String illStatusDescription;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;
		@JsonProperty("ItemStatusDescription")
		private String itemStatusDescription;
		@JsonProperty("NeedByDate")
		private String needByDate;
		@JsonProperty("PickupBranch")
		private String pickupBranch;
		@JsonProperty("FormatID")
		private Integer formatID;
		@JsonProperty("LastStatusTransitionDate")
		private String lastStatusTransitionDate;
		@JsonProperty("INNReachTrackingID")
		private Integer innReachTrackingID;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class SysHoldRequest {
		@JsonProperty("SysHoldRequestID")
		private Integer sysHoldRequestID;
		@JsonProperty("SysHoldStatusID")
		private Integer sysHoldStatusID;
		@JsonProperty("Author")
		private String author;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("BrowseTitleNonFilingCount")
		private Integer browseTitleNonFilingCount;
		@JsonProperty("MARCTOMIDDescription")
		private String marcTomIDDescription;
		@JsonProperty("ActivationDate")
		private String activationDate;
		@JsonProperty("Status")
		private String status;
		@JsonProperty("ExpirationDate")
		private String expirationDate;
		@JsonProperty("PickupBranch")
		private String pickupBranch;
		@JsonProperty("MARCTOMID")
		private Integer marcTomID;
		@JsonProperty("Queue")
		private Integer queue;
		@JsonProperty("HoldUntilDate")
		private String holdUntilDate;
		@JsonProperty("GroupName")
		private String groupName;
		@JsonProperty("QueueTTL")
		private Integer queueTTL;
		@JsonProperty("PickupBranchID")
		private Integer pickupBranchID;
		@JsonProperty("ItemLevelHold")
		private Boolean itemLevelHold;
		@JsonProperty("BorrowByMail")
		private Boolean borrowByMail;
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("DisplayInPAC")
		private Boolean displayInPAC;
		@JsonProperty("LastStatusTransitionDate")
		private String lastStatusTransitionDate;
		@JsonProperty("PACDisplayNotes")
		private String pacDisplayNotes;
		@JsonProperty("TrappingItemRecordID")
		private Integer trappingItemRecordID;
		@JsonProperty("TrappingItemAssignedBranchID")
		private Integer trappingItemAssignedBranchID;
		@JsonProperty("ConstituentBibRecordID")
		private Integer constituentBibRecordID;
		@JsonProperty("HasConstituentBib")
		private Boolean hasConstituentBib;
		@JsonProperty("NewPickupBranchID")
		private Integer newPickupBranchID;
		@JsonProperty("NewPickupBranch")
		private String newPickupBranch;
		@JsonProperty("InnReachType")
		private Integer innReachType;
	}

	// https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/overview
	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class WorkflowReply {
		// Prompt Results
		public static final Integer Continue = 5;
		public static final Integer Retain = 11;
		@JsonProperty("WorkflowPromptID")
		private Integer workflowPromptID;
		@JsonProperty("WorkflowPromptResult")
		private Integer workflowPromptResult;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class WorkflowResponse {
		public static final Integer CompletedSuccessfully = 1;
		public static final Integer InputRequired = -3;
		@JsonProperty("WorkflowRequestGuid")
		private String workflowRequestGuid;
		@JsonProperty("WorkflowStatus")
		private Integer workflowStatus;
		@JsonProperty("Prompt")
		private Prompt prompt;
		@JsonProperty("AnswerExtension")
		private AnswerExtension answerExtension;
		@JsonProperty("InformationMessages")
		private List<InformationMessage> informationMessages;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class Prompt {
		// Prompt Identifiers
		public static final Integer NoDisplayInPAC = 66;
		public static final Integer DuplicateRecords = 72;
		public static final Integer ConfirmItemRecordDelete = 73;
		public static final Integer ConfirmBibRecordDelete = 79;
		public static final Integer LastCopyOrRecordOptions = 82;
		@JsonProperty("WorkflowPromptID")
		private Integer WorkflowPromptID;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("Message")
		private String message;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class BibliographicRecord {
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("BrowseAuthor")
		private String browseAuthor;
		@JsonProperty("BrowseTitle")
		private String browseTitle;
		@JsonProperty("ConstituentFlag")
		private Boolean constituentFlag;
		@JsonProperty("DisplayInPAC")
		private Boolean displayInPAC;
		@JsonProperty("HostFlag")
		private Boolean hostFlag;
		@JsonProperty("ISBN")
		private String iSBN;
		@JsonProperty("ISBNList")
		private String[] ISBNList;
		@JsonProperty("ISSN")
		private String iSSN;
		@JsonProperty("ISSNCancel")
		private String iSSNCancel;
		@JsonProperty("IsSerial")
		private Boolean isSerial;
		@JsonProperty("NormalISBN")
		private String normalISBN;
		@JsonProperty("PubDate")
		private String pubDate;
		@JsonProperty("Publisher")
		private String publisher;
		@JsonProperty("RecordStatusID")
		private Integer recordStatusID;
		@JsonProperty("SeriesNo")
		private String seriesNo;
		@JsonProperty("SeriesTitle")
		private String seriesTitle;
		@JsonProperty("PrimaryMARCTOMID")
		private Integer primaryMARCTOMID;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class MaterialType {
		@JsonProperty("Description")
		private String description;
		@JsonProperty("MaterialTypeID")
		private Integer materialTypeID;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class AnswerExtension {
		@JsonProperty("Data")
		private AnswerData answerData;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class AnswerData {
		@JsonProperty("ItemRecord")
		private ItemRecord itemRecord;
		@JsonProperty("ILLRequestInfo")
		private ILLRequestInfo iLLRequestInfo;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class ItemRecord {
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;
		@JsonProperty("ItemStatusDescription")
		private String itemStatusDescription;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class InformationMessage {
		@JsonProperty("Message")
		private String message;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("Type")
		private Integer type;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class WorkflowRequest {
		@JsonProperty("WorkflowRequestType")
		private Integer workflowRequestType;
		@JsonProperty("TxnBranchID")
		private Integer txnBranchID;
		@JsonProperty("TxnUserID")
		private Integer txnUserID;
		@JsonProperty("TxnWorkstationID")
		private Integer txnWorkstationID;
		@JsonProperty("RequestExtension")
		private RequestExtension requestExtension;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class RequestExtension {
		@JsonProperty("WorkflowRequestExtensionType")
		private Integer workflowRequestExtensionType;
		@JsonProperty("Data")
		private RequestExtensionData data;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class RequestExtensionData {

		@JsonProperty("IsNew")
		private Boolean isNew;
		@JsonProperty("ItemRecordHistoryActionID")
		private Integer itemRecordHistoryActionID;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;

		// When specified caller must also send  HoldableByPickup HoldPickupBranchID HoldableByPrimaryLender 
		// ManualBlockID StatisticalCodeID ShelfLocationID AssignedCollectionID
		@JsonProperty("AssignedBranchID")
		private Integer assignedBranchID;  

		@JsonProperty("AssociatedBibRecordID")
		private Integer associatedBibRecordID;
		@JsonProperty("MaterialTypeID")
		private Integer materialTypeID;
		@JsonProperty("RenewalLimit")
		private Integer renewalLimit;
		@JsonProperty("LoanPeriodCodeID")
		private Integer loanPeriodCodeID;
		@JsonProperty("FineCodeID")
		private Integer fineCodeID;
		// https://qa-polaris.polarislibrary.com/polaris.applicationservices/help/workflow/add_or_update_item_record
		// Says owningBranchId is an Integer not a String
		@JsonProperty("OwningBranchID")
		private Integer owningBranchID;
		@JsonProperty("HomeBranchID")
		private Integer homeBranchID;  // mandatory when creating a new item
		@JsonProperty("ShelvingSchemeID")
		private Integer shelvingSchemeID;
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("OriginalItemStatusID")
		private Integer originalItemStatusID;
		@JsonProperty("DisplayInPAC")
		private Boolean displayInPAC;
		@JsonProperty("Barcode")
		private String barcode;

		@JsonProperty("HoldableByPickup")
		private Boolean holdableByPickup;
		@JsonProperty("HoldPickupBranchID")
		private Integer holdPickupBranchId;
		@JsonProperty("HoldableByPrimaryLender")
		private Boolean holdableByPrimaryLender;
		@JsonProperty("ManualBlockID")
		private Integer manualBlockId;
		@JsonProperty("StatisticalCodeID")
		private Integer statisticalCodeId;
		@JsonProperty("ShelfLocationID")
		private Integer shelfLocationId;
		@JsonProperty("AssignedCollectionID")
		private Integer assignedCollectionId;

		@JsonProperty("IsProvisionalSave")
		private Boolean isProvisionalSave;
		@JsonProperty("NonCirculating")
		private Boolean nonCircluating;
		@JsonProperty("LoneableOutsideSystem")
		private Boolean loneableOutsideSystem;
		@JsonProperty("Holdable")
		private Boolean holdable;

		// item delete fields
		@JsonProperty("IsAutoDelete")
		private Boolean isAutoDelete;
		@JsonProperty("ItemRecordIDs")
		private List<Integer> itemRecordIDs;

		// delete bib fields
		@JsonProperty("BibRecordIDs")
		private List<Integer> bibRecordIDs;


		// fields below are for hold requests
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("PatronBranchID")
		private Integer patronBranchID;
		@JsonProperty("PickupBranchID")
		private Integer pickupBranchID;
		@JsonProperty("Origin")
		private Integer origin;
		@JsonProperty("ActivationDate")
		private String activationDate;
		@JsonProperty("ExpirationDate")
		private String expirationDate;
		@JsonProperty("StaffDisplayNotes")
		private String staffDisplayNotes;
		@JsonProperty("NonPublicNotes")
		private String nonPublicNotes;
		@JsonProperty("PACDisplayNotes")
		private String pACDisplayNotes;
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("ConstituentBibRecordID")
		private Integer constituentBibRecordID;
		@JsonProperty("ItemBarcode")
		private String itemBarcode;
		@JsonProperty("ItemLevelHold")
		private Boolean itemLevelHold;
		@JsonProperty("IgnorePatronBlocksPrompt")
		private Boolean ignorePatronBlocksPrompt;
		@JsonProperty("BulkMode")
		private Boolean bulkMode;
		@JsonProperty("IgnorePromptForMaterialTypeIDs")
		private List<Integer> ignorePromptForMaterialTypeIDs;
		@JsonProperty("PatronBlocksOnlyPrompt")
		private Boolean patronBlocksOnlyPrompt;
		@JsonProperty("IgnoreMaximumHoldsPrompt")
		private Boolean ignoreMaximumHoldsPrompt;
		@JsonProperty("UnlockedRequest")
		private Boolean unlockedRequest;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("BrowseTitle")
		private String browseTitle;


		// convert to ILL request
		@JsonProperty("SysHoldRequestID")
		private String sysHoldRequestID;
		@JsonProperty("SkipTotalILLLimitExceededPrompt")
		private Boolean skipTotalILLLimitExceededPrompt;


		// transfer ILL request
		@JsonProperty("ILLRequestID")
		private Integer iLLRequestID;
		@JsonProperty("circTranType")
		private Integer circTranType;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class DtoBibliographicCreationData {
		@JsonProperty("RecordOwnerID")
		private Integer recordOwnerID;
		@JsonProperty("DisplayInPAC")
		private Boolean displayInPAC;
		@JsonProperty("DoNotOverlay")
		private Boolean doNotOverlay;
		@JsonProperty("Record")
		private DtoMARC21Record record;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class DtoMARC21Record {
		@JsonProperty("leader")
		private String leader;
		@JsonProperty("controlfields")
		private List<DtoMARC21ControlField> controlfields;
		@JsonProperty("datafields")
		private List<DtoMARC21DataField> datafields;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class DtoMARC21ControlField {
		@JsonProperty("tag")
		private String tag;
		@JsonProperty("data")
		private String data;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class DtoMARC21DataField {
		@JsonProperty("tag")
		private String tag;
		@JsonProperty("ind1")
		private String ind1;
		@JsonProperty("ind2")
		private String ind2;
		@JsonProperty("subfields")
		private List<DtoMARC21Subfield> subfields;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class DtoMARC21Subfield {
		@JsonProperty("code")
		private String code;
		@JsonProperty("data")
		private String data;
	}


	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class HoldRequestDefault {
		@JsonProperty("ExpirationDatePeriod")
		private Integer expirationDatePeriod;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronBlockGetRow {
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("PatronFullName")
		private String patronFullName;
		@JsonProperty("BlockType")
		private Integer blockType;
		@JsonProperty("BlockID")
		private Integer blockID;
		@JsonProperty("BlockDescription")
		private String blockDescription;
		@JsonProperty("PatronBranchID")
		private Integer patronBranchID;
		@JsonProperty("CreationDate")
		private String creationDate;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronData {
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("PatronCodeID")
		private Integer patronCodeID;
		@JsonProperty("PatronCode")
		private PatronCode patronCode;
		@JsonProperty("OrganizationID")
		private Integer organizationID;
		@JsonProperty("CreatorID")
		private Integer creatorID;
		@JsonProperty("ModifierID")
		private Integer modifierID;
		@JsonProperty("Barcode")
		private String barcode;
		@JsonProperty("SystemBlocks")
		private Integer systemBlocks;
		@JsonProperty("YTDCircCount")
		private Integer ytdCircCount;
		@JsonProperty("LifetimeCircCount")
		private Integer lifetimeCircCount;
		@JsonProperty("LastActivityDate")
		private LocalDateTime lastActivityDate;
		@JsonProperty("ClaimCount")
		private Integer claimCount;
		@JsonProperty("LostItemCount")
		private Integer lostItemCount;
		@JsonProperty("ChargesAmount")
		private double chargesAmount;
		@JsonProperty("CreditsAmount")
		private double creditsAmount;
		@JsonProperty("RecordStatusID")
		private Integer recordStatusID;
		@JsonProperty("RecordStatusDate")
		private LocalDateTime recordStatusDate;
		@JsonProperty("YTDYouSavedAmount")
		private Double ytdYouSavedAmount;
		@JsonProperty("LifetimeYouSavedAmount")
		private Double lifetimeYouSavedAmount;
		@JsonProperty("Registration")
		private PatronRegistration registration;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronCode {
		@JsonProperty("PatronCodeID")
		private Integer patronCodeID;
		@JsonProperty("Description")
		private String description;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronRegistration {
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("LanguageID")
		private Integer languageID;
		@JsonProperty("NameFirst")
		private String nameFirst;
		@JsonProperty("NameLast")
		private String nameLast;
		@JsonProperty("NameMiddle")
		private String nameMiddle;
		@JsonProperty("NameTitleID")
		private Integer nameTitleID;
		@JsonProperty("NameTitle")
		private NameTitle nameTitle;
		@JsonProperty("NameSuffix")
		private String nameSuffix;
		@JsonProperty("PhoneVoice1")
		private String phoneVoice1;
		@JsonProperty("PhoneVoice2")
		private String phoneVoice2;
		@JsonProperty("PhoneVoice3")
		private String phoneVoice3;
		@JsonProperty("EmailAddress")
		private String emailAddress;
		@JsonProperty("Password")
		private String password;
		@JsonProperty("EntryDate")
		private LocalDateTime entryDate;
		@JsonProperty("ExpirationDate")
		private LocalDateTime expirationDate;
		@JsonProperty("AddrCheckDate")
		private LocalDateTime addrCheckDate;
		@JsonProperty("UpdateDate")
		private LocalDateTime updateDate;
		@JsonProperty("Addresses")
		private List<PatronAddress> addresses;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class NameTitle {
		@JsonProperty("NameTitleID")
		private Integer nameTitleID;
		@JsonProperty("Description")
		private String description;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronAddress {
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("AddressID")
		private Integer addressID;
		@JsonProperty("AddressTypeID")
		private Integer addressTypeID;
		@JsonProperty("AddressTypeDescription")
		private String addressTypeDescription;
		@JsonProperty("AddressLabelID")
		private Integer addressLabelID;
		@JsonProperty("AddressLabelDescription")
		private String addressLabelDescription;
		@JsonProperty("Verified")
		private Boolean verified;
		@JsonProperty("VerificationDate")
		private LocalDateTime verificationDate;
		@JsonProperty("PolarisUserID")
		private Integer polarisUserID;
		@JsonProperty("StreetOne")
		private String streetOne;
		@JsonProperty("StreetTwo")
		private String streetTwo;
		@JsonProperty("StreetThree")
		private String streetThree;
		@JsonProperty("MunicipalityName")
		private String municipalityName;
		@JsonProperty("PostalCode")
		private String postalCode;
		@JsonProperty("ZipPlusFour")
		private String zipPlusFour;
		@JsonProperty("City")
		private String city;
		@JsonProperty("State")
		private String state;
		@JsonProperty("CountryName")
		private String countryName;
		@JsonProperty("CountryID")
		private Integer countryID;
		@JsonProperty("County")
		private String county;
		@JsonProperty("VerificationStatus")
		private Integer verificationStatus;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronCustomData {
		@JsonProperty("PatronDataLabelValue")
		private String patronDataLabelValue;
		@JsonProperty("PatronCustomDataDefinitionID")
		private Integer patronCustomDataDefinitionID;
		@JsonProperty("IsRequired")
		private String isRequired;
		@JsonProperty("CustomDataEntry")
		private String customDataEntry;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class LocalRequest {
		@JsonProperty("ProcedureStep")
		private Integer procedureStep;
		@JsonProperty("Answer")
		private Integer answer;
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("PickupBranchID")
		private Integer pickupBranchID;
		@JsonProperty("Origin")
		private Integer origin;
		@JsonProperty("ActivationDate")
		private String activationDate;
		@JsonProperty("ExpirationDate")
		private String expirationDate;
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("TrackingNumber")
		private String trackingNumber;

		@JsonProperty("UnlockedRequest")
		private Boolean unlockedRequest;
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("MARCTOMID")
		private Integer mARCTOMID;
		@JsonProperty("StaffDisplayNotes")
		private String staffDisplayNotes;
		@JsonProperty("NonPublicNotes")
		private String nonPublicNotes;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class HoldRequestResponse {
		@JsonProperty("Answer")
		private Integer answer;
		@JsonProperty("HoldRequestID")
		private Integer holdRequestID;
		@JsonProperty("MaxHoldMaterialTypes")
		private List<Integer> maxHoldMaterialTypes;
		@JsonProperty("Message")
		private String message;
		@JsonProperty("OldPickUpBranchID")
		private String oldPickUpBranchID;
		@JsonProperty("PAPIAction")
		private Integer papiAction;
		@JsonProperty("PAPIActionProcedure")
		private Integer papiActionProcedure;
		@JsonProperty("PAPIProcedure")
		private Integer papiProcedure;
		@JsonProperty("PAPIProcedureStep")
		private Integer papiProcedureStep;
		@JsonProperty("PAPIPromptType")
		private Integer papiPromptType;
		@JsonProperty("PAPIReturnCode")
		private Integer papiReturnCode;
		@JsonProperty("PAPIStopType")
		private Integer papiStopType;
		@JsonProperty("ProcedureStep")
		private String procedureStep;
		@JsonProperty("ReceiptType")
		private Integer receiptType;
		@JsonProperty("ReceiptUrl")
		private String receiptUrl;
		@JsonProperty("Success")
		private Boolean success;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class LibraryHold {
		@JsonProperty("SysHoldStatusID")
		private Integer sysHoldStatusID;
		@JsonProperty("SysHoldStatus")
		private String sysHoldStatus;
		@JsonProperty("StatusTransitionDate")
		private LocalDateTime statusTransitionDate;
		@JsonProperty("CreationDate")
		private LocalDateTime creationDate;
		@JsonProperty("ActivationDate")
		private LocalDateTime activationDate;
		@JsonProperty("PickupBranchID")
		private Integer pickupBranchID;
		@JsonProperty("ExpirationDate")
		private LocalDateTime expirationDate;
		@JsonProperty("HoldTillDate")
		private LocalDateTime holdTillDate;
		@JsonProperty("Origin")
		private String origin;
		@JsonProperty("ItemLevelHold")
		private Boolean itemLevelHold;
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("PatronBarcode")
		private String patronBarcode;
		@JsonProperty("PatronName")
		private String patronName;
		@JsonProperty("PatronCodeID")
		private Integer patronCodeID;
		@JsonProperty("PatronBranchID")
		private Integer patronBranchID;
		@JsonProperty("PatronDeliveryOptionID")
		private Integer patronDeliveryOptionID;
		@JsonProperty("Author")
		private String author;
		@JsonProperty("Title")
		private String title;
		@JsonProperty("BrowseAuthor")
		private String browseAuthor;
		@JsonProperty("BrowseTitle")
		private String browseTitle;
		@JsonProperty("BrowseTitleNonFilingCount")
		private Integer browseTitleNonFilingCount;
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("ItemBarcode")
		private String itemBarcode;
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;
		@JsonProperty("MARCTOMID")
		private Integer marcTomId;
		@JsonProperty("Publisher")
		private String publisher;
		@JsonProperty("PublicationYear")
		private Integer publicationYear;
		@JsonProperty("Edition")
		private String edition;
		@JsonProperty("ISBNISSN")
		private String isbnIssn;
		@JsonProperty("ISBN")
		private String isbn;
		@JsonProperty("ISSN")
		private String issn;
		@JsonProperty("LCCN")
		private String lccn;
		@JsonProperty("CallNumber")
		private String callNumber;
		@JsonProperty("Pages")
		private String pages;
		@JsonProperty("VolumeNumber")
		private String volumeNumber;
		@JsonProperty("StaffDisplayNotes")
		private String staffDisplayNotes;
		@JsonProperty("NonPublicNotes")
		private String nonPublicNotes;
		@JsonProperty("PatronNotes")
		private String patronNotes;
		@JsonProperty("PACDisplayNotes")
		private String pacDisplayNotes;
		@JsonProperty("UnlockedRequest")
		private Boolean unlockedRequest;
		@JsonProperty("Designation")
		private String designation;
		@JsonProperty("CopyNo")
		private Integer copyNo;
		@JsonProperty("BorrowByMail")
		private Boolean borrowByMail;
		@JsonProperty("TrackingNumber")
		private String trackingNumber;
		@JsonProperty("Series")
		private String series;
		@JsonProperty("ConstituentBibRecordID")
		private Integer constituentBibRecordID;
		@JsonProperty("ConstituentSortAuthor")
		private String constituentSortAuthor;
		@JsonProperty("ConstituentSortTitle")
		private String constituentSortTitle;
		@JsonProperty("ConstituentBrowseAuthor")
		private String constituentBrowseAuthor;
		@JsonProperty("ConstituentBrowseTitle")
		private String constituentBrowseTitle;
		@JsonProperty("NewPickupBranchID")
		private Integer newPickupBranchID;
		@JsonProperty("InnReachType")
		private Integer innReachType;
		@JsonProperty("MARCTOMIDDescription")
		private String marcTomIdDescription;
	}
}
