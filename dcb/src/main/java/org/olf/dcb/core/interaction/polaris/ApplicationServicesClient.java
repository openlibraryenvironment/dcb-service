package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpMethod.DELETE;
import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static io.micronaut.http.HttpMethod.PATCH;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.valueOf;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.BreakableDeletionLinks;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.BriefItemEntry;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.ConfirmBibRecordDelete;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.ConfirmItemBarcodeChanged;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.ConfirmItemRecordDelete;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.DuplicateRecords;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.ExceededTotalRequestLimit;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.FillsRequestTransferPrompt;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.LastCopyOrRecordOptions;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.NoDisplayInPAC;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.PromoteItemRequestToBib;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.Prompt.UpdateItemRecord;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowReply.Continue;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowReply.Retain;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowReply.Yes;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse.CompletedSuccessfully;
import static org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.WorkflowResponse.InputRequired;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.VIRTUAL_BIB_AV_LEADER;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.VIRTUAL_BIB_BOOKS_LEADER;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.PolarisItemStatus;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.getNoteForStaff;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException;
import org.olf.dcb.core.interaction.polaris.exceptions.PolarisWorkflowException;
import org.olf.dcb.core.interaction.polaris.exceptions.SaveVirtualItemException;
import org.zalando.problem.Problem;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

@Slf4j
class ApplicationServicesClient {
	private final PolarisLmsClient client;
	private final ApplicationServicesAuthFilter authFilter;
	private final String URI_PARAMETERS;
	private final PolarisConfig polarisConfig;
	private final Integer TransactingPolarisUserID;
	private final Integer TransactingWorkstationID;
	private final Integer TransactingBranchID;
	// ToDo align these URLs
	public static final URI ERR0210 = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/0210/Polaris/UnableToLoadPatronBlocks");

	ApplicationServicesClient(PolarisLmsClient client, PolarisConfig polarisConfig) {
		this.client = client;
		this.polarisConfig = polarisConfig;
		this.authFilter = new ApplicationServicesAuthFilter(client, polarisConfig);
		this.URI_PARAMETERS = "/polaris.applicationservices/api" + polarisConfig.applicationServicesUriParameters();
		this.TransactingPolarisUserID = polarisConfig.getLogonUserId();
		this.TransactingWorkstationID = polarisConfig.getServicesWorkstationId();
		this.TransactingBranchID = polarisConfig.getLogonBranchId();
	}

	/**
	 * Based upon <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/create_hold_request">post hold request docs</a>
	 */
	Mono<Tuple5<String, Integer, String, String, HoldRequestParameters>> createHoldRequestWorkflow(
		HoldRequestParameters holdRequestParameters) {
		log.debug("createHoldRequestWorkflow with holdRequestParameters {}", holdRequestParameters);

		final var path = createPath("workflow");
		final String activationDateUTC = ZonedDateTime.now(UTC).format(ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
		final var noteWithActivationDateUTC = addActivationDateToNote(holdRequestParameters, activationDateUTC);

		return createRequest(POST, path, uri -> {})
			.zipWith(getLocalRequestBody(holdRequestParameters, activationDateUTC, noteWithActivationDateUTC))
			.map(function(ApplicationServicesClient::addBodyToRequest))
			.flatMap(workflowReq -> client.retrieve(workflowReq, Argument.of(WorkflowResponse.class)))
			.flatMap(resp -> replyIfPromptMatches(resp, ExceededTotalRequestLimit, Continue))
			.flatMap(resp -> replyIfPromptMatches(resp, PromoteItemRequestToBib, Continue))
			.map(this::validateWorkflowResponse)
			.thenReturn(Tuples.of(
				holdRequestParameters.getLocalPatronId(),
				holdRequestParameters.getBibliographicRecordID(),
				activationDateUTC,
				noteWithActivationDateUTC,
				holdRequestParameters));
	}

	private Mono<WorkflowResponse> replyIfPromptMatches(WorkflowResponse response, Integer promptID, Integer promptResult) {

		boolean isInputRequired = InputRequired.equals(response.getWorkflowStatus());
		boolean isPromptMatching = response.getPrompt() != null
			&& response.getPrompt().getWorkflowPromptID() != null
			&& promptID.equals(response.getPrompt().getWorkflowPromptID());

		if (isInputRequired && isPromptMatching) {
			return handlePolarisWorkflow(response, promptID, promptResult);
		}

		return Mono.just(response);
	}

	private static String addActivationDateToNote(HoldRequestParameters holdRequestParameters, String activationDateUTC) {
		final var noteWithActivationDateUTC = (holdRequestParameters.getNote() != null
			? holdRequestParameters.getNote()
			: "Note was null");
		return noteWithActivationDateUTC + "\n Activated: " + activationDateUTC;
	}

	private WorkflowResponse validateWorkflowResponse(WorkflowResponse workflowResponse) {
		log.debug("Validating response to placing a request: {}", workflowResponse);

		validateWorkflowStatus(workflowResponse);
		validateInformationMessages(workflowResponse);

		// logging successful response message
		if (workflowResponse.getWorkflowStatus() > 0 &&
			workflowResponse.getPrompt() != null &&
			workflowResponse.getPrompt().getMessage() != null) {

			log.info(workflowResponse.getPrompt().getMessage());
		}

		return workflowResponse;
	}

	private static void validateWorkflowStatus(WorkflowResponse response) {
		if (response.getWorkflowStatus() < CompletedSuccessfully) {
			log.error("Polaris response: " + response);

			throw Problem.builder()
				.withType(ERR0210)
				.withTitle("Failed to handle Polaris.ApplicationServices API workflow")
				.withDetail(response.getPrompt() != null && response.getPrompt().getTitle() != null ? response.getPrompt().getTitle() : "Response didn't have a title")
				.with("Message", response.getPrompt() != null && response.getPrompt().getMessage() != null ? response.getPrompt().getMessage() : "No message")
				.with("Information messages", response.getInformationMessages() != null ? response.getInformationMessages() : "No messages")
				.with("Workflow prompt ID", response.getPrompt().getWorkflowPromptID() != null ? response.getPrompt().getWorkflowPromptID() : "No prompt id")
				.with("Workflow doc", "https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/overview")
				.with("Workflow status", response.getWorkflowStatus() != null ? response.getWorkflowStatus() : "No status")
				.with("Workflow request GUID", response.getWorkflowRequestGuid() != null ? response.getWorkflowRequestGuid() : "No guid")
				.with("Full response", response)
				.build();
		}
	}

	private static void validateInformationMessages(WorkflowResponse workflowResponse) {
		// It has been found that certain hold request failures do not contain polaris error codes
		// instead there are information messages that contain the error message
		// more information: DCB-1031

		final String successfulRequestGuid = "00000000-0000-0000-0000-000000000000";
		if (Objects.equals(workflowResponse.getWorkflowRequestGuid(), successfulRequestGuid)) {

			Optional.ofNullable(workflowResponse.getInformationMessages())
				.map(List::stream)
				.ifPresent(messages -> {

					final List<String> listOfErrorMessages = List.of(
						"Invalid pickup branch",
						"Placing hold request failed.");

					messages
						.filter(message -> listOfErrorMessages.contains(message.getMessage()))
						.forEach(message -> {
							log.error("Polaris API response: {}", message);
							throw new PolarisWorkflowException(message.getMessage());
						});
				});

		}
	}

	Mono<LibraryHold> getLocalHoldRequest(Integer id) {
		log.info("Getting hold request by id: {}", id);

		final var path = createPath("holds", id);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, LibraryHold.class, FALSE))
			.map(HttpResponse::body)
			.onErrorResume(error -> {
				if ((error instanceof HttpClientResponseException) &&
					(((HttpClientResponseException) error).getStatus() == HttpStatus.NOT_FOUND)) {
					try {
						HttpClientResponseException httpClientError = (HttpClientResponseException) error;
						Optional<String> maybeBody = httpClientError.getResponse().getBody(String.class);
						String errorResponse = maybeBody.orElse("No response body");

						log.error("Hold request {} not found in LMS {}. Error response: \n{}", id, client.getHostLmsCode(), errorResponse);
					} catch (Exception e) {
						log.debug("Hold request {} not found in lms {}", id, client.getHostLmsCode());
					}
					// Note: When a patron checks out the item polaris will delete the hold
					return Mono.just(LibraryHold.builder().sysHoldStatus("Missing").build());

				} else {
					log.error("Failed to get hold request {} from lms {}", id, client.getHostLmsCode());

					return raiseError(error);
				}
			});
	}

	Mono<Boolean> deleteHoldRequest(String holdId) {
		log.debug("Deleting hold request by id: {}", holdId);

		final var path = createPath("holds", holdId);
		return createRequest(DELETE, path, uri -> {})
			.flatMap(request -> client.exchange(request, Boolean.class, FALSE))
			.map(HttpResponse::body)
			.doOnSuccess(bool -> {
				if (Boolean.TRUE.equals(bool)) {
					log.info("Successfully deleted hold request {}", holdId);
				} else {
					log.warn("Deleting hold request {} returned false.", holdId);
				}
			})
			// Deal with the HTTP errors
			.onErrorResume(HttpClientResponseException.class, error -> {
				// A 404 means that this hold is already gone.
				// We will optimistically assume that this means someone has deleted it already.
				if (error.getStatus() == HttpStatus.NOT_FOUND) {
					log.debug("Hold not found: it may already have been deleted.");
					return Mono.just(TRUE);
				}

				// A 400 here usually means that the hold is in a status that does not allow deletion
				// So it needs to be cancelled first
				if (error.getStatus() == HttpStatus.BAD_REQUEST) {
					return Mono.error(error);
				}

				// Any other HTTP error - fail-safely to FALSE
				log.error("Failed to delete hold request {} from LMS {}", holdId, client.getHostLmsCode(), error);
				return Mono.just(FALSE);
			})
			// Deal with the non HTTP errors
			.onErrorResume(error -> {
				if (error instanceof HttpClientResponseException) return Mono.error(error);
				log.error("Failed to delete hold request {} from LMS {}", holdId, client.getHostLmsCode(), error);
				return Mono.just(FALSE);
			});
	}

	Mono<Patron> getPatron(String localPatronId) {
		final var path = createPath("patrons", localPatronId);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, PatronData.class, TRUE))
			.map(HttpResponse::body)
			.map(data -> Patron.builder()
				.localId(singletonList(valueOf(data.getPatronID())))
				.localPatronType(valueOf(data.getPatronCodeID()))
				.localBarcodes(singletonList(data.getBarcode()))
				.localHomeLibraryCode(valueOf(data.getOrganizationID()))
				.localNames(
					Optional.ofNullable(data.getRegistration())
						.map(reg -> Stream.of(reg.getNameFirst(), reg.getNameMiddle(), reg.getNameLast())
							// At the minute if a name is null, we map to a blank string
							.map(name -> name == null ? "" : name)
							.map(String::trim)
							.filter(s -> !s.isEmpty())
							.collect(Collectors.toList())
						)
						// Default to an empty list if registration itself is null
						.orElse(Collections.emptyList())
				)
				.isActive(true)
				.isBlocked(data.getSystemBlocks() != null && data.getSystemBlocks() > 0)
				.isDeleted(false)
				.expiryDate((data.getRegistration() != null && data.getRegistration().getExpirationDate() != null) ? Timestamp.valueOf(data.getRegistration().getExpirationDate()) : null)
				.build());
	}

	public Mono<Integer> handlePatronBlock(Integer localPatronId) {
		return getPatronBlocks(localPatronId)
			.filter(list -> !list.isEmpty())
			.flatMap(list -> deleteUnwantedPatronBlocks(localPatronId, list))
			.defaultIfEmpty( localPatronId );
	}

	// we only expect
	private Mono<Integer> deleteUnwantedPatronBlocks(Integer localPatronId, List<PatronBlockGetRow> patronBlockGetRows) {

		final Integer VERIFY_PATRON_DATA = 3;
		final Integer REGISTRATION_HAS_EXPIRED = 100;
		final var knownBlocksToHandle = List.of(VERIFY_PATRON_DATA, REGISTRATION_HAS_EXPIRED);

		return Flux.fromIterable(patronBlockGetRows)
			.flatMap(row -> {
				if (knownBlocksToHandle.contains(row.getBlockType())) {
					return deletePatronBlock(localPatronId, row.getBlockType(), row.getBlockID())
						.flatMap(id -> Mono.<PatronBlockGetRow>empty())  // Remove this row after successful deletion
						.onErrorResume(e -> {

							log.error("Patron block deletion failed for patron block: {}", row, e);
							return Mono.just(row); // Keep this row if deletion fails
						});
				}
				return Mono.just(row);  // Keep this row if it's not a known block to handle
			})
			.collectList()
			.flatMap(remainingBlocks -> {

				// we still have patron blocks that are unhandled
				if (!remainingBlocks.isEmpty()) {
					final var size = String.valueOf(remainingBlocks.size());
					return raiseError(Problem.builder()
						.withTitle("Patron has unexpected blocks")
						.withDetail(size + " blocks found")
						.with("localPatronId", String.valueOf(localPatronId))
						.with("remainingBlocks", remainingBlocks.toString())
						.build());
				}

				// All patron blocks have been handled
				return Mono.just(localPatronId);
			});
	}

	private Mono<List<PatronBlockGetRow>> getPatronBlocks(Integer localPatronId) {
		final var path = createPath("patrons", localPatronId, "blockssummary");

		return createRequest(GET, path, uri -> uri
				.queryParam("logonBranchID", TransactingBranchID)
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
			.flatMap(request -> client.retrieve(request, Argument.of(Boolean.class)))
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
		log.info("Getting patron barcode from patron id: {}", localId);

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(String.class)))
			// remove quotes
			.map(string -> string.replace("\"", ""))
			.doOnSuccess(barcode -> log.info("Successfully got patron barcode {} from local id {}", barcode, localId));
	}

	public Mono<String> getPatronIdByIdentifier(String identifier, String identifierType) {

		final var path = createPath("ids", "patrons");

		return createRequest(GET, path,
			uri -> uri
				.queryParam("id", identifier)
				.queryParam("type", identifierType))
			.flatMap(request -> client.retrieve(request, Argument.of(String.class)));
	}

	public Mono<PatronDefaults> patrondefaults(Integer illLocation) {

		final var path = createPath("patrondefaults");

		return createRequest(GET, path,
			uri -> uri.queryParam("orgid", illLocation))
			.flatMap(request -> client.exchange(request, PatronDefaults.class, TRUE))
			.map(HttpResponse::body)
			.doOnError(e -> log.debug("Error occurred when getting patron defaults", e));
	}

	public Mono<Integer> getHoldRequestDefaults() {

		final var path = createPath("holdsdefaults");
		final Integer defaultExpirationDatePeriod = 999;

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, HoldRequestDefault.class, TRUE))
			.map(HttpResponse::body)
			.map(HoldRequestDefault::getExpirationDatePeriod)
			.doOnError(e -> log.debug("Error occurred when getting hold request defaults", e))
			.onErrorResume(error -> {
				log.info("Error handled : returning defaultExpirationDatePeriod:" + defaultExpirationDatePeriod);
				return Mono.just(defaultExpirationDatePeriod);
			});
	}


	/**
	 * Used just to test the remote API for the HostLMS Ping operation
	 */
  public Mono<Integer> test() {

    final var path = createPath("holdsdefaults");

    return createRequest(GET, path, uri -> {})
      .flatMap(request -> client.exchange(request, HoldRequestDefault.class, TRUE))
      .map(HttpResponse::body)
      .map(HoldRequestDefault::getExpirationDatePeriod)
      .doOnError(e -> log.debug("Error occurred when getting hold request defaults", e));
  }


//	https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/patrons/get_requests_local
	public Mono<List<SysHoldRequest>> listPatronLocalHolds(String patronId) {
//		Returns list of local hold requests associated with the patron.
//		Server returns the list sorted by SysHoldStatusID and LastStatusTransitionDate in ascending order.
//		Leap client displays the list sorted by Author in ascending order.

		final var path = createPath("patrons", patronId, "requests", "local");
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(SysHoldRequest.class)));
	}

	public Mono<Integer> createBibliographicRecord(Bib bib) {
		final var path = createPath("bibliographicrecords");

		final var canonicalItemType = getValueOrNull(bib, Bib::getCanonicalItemType);
		final String leader = decideLeaderValue(canonicalItemType);
		log.debug("Creating bibliographic record with leader: {}", leader);

		return createRequest(POST, path, uri -> uri.queryParam("type", "create"))
			.map(request -> request.body(DtoBibliographicCreationData.builder()
				.recordOwnerID(1)
				.displayInPAC(FALSE)
				.doNotOverlay(TRUE)
				.record(DtoMARC21Record.builder()
					.leader(leader)
					.controlfields(List.of(DtoMARC21ControlField.builder().tag("008").data(randomAlphanumeric(24)).build()))
					.datafields( List.of( DtoMARC21DataField.builder()
						.tag("245").ind1("0").ind2("0")
						.subfields( List.of(DtoMARC21Subfield.builder().code("a").data(bib.getTitle()).build()) )
						.build())).build()).build()))
			.flatMap(request -> client.retrieve(request, Argument.of(Integer.class)));
	}

	private static String decideLeaderValue(String canonicalItemType) {
		String leader;
		if ("CIRCAV".equals(canonicalItemType)) {
			leader = VIRTUAL_BIB_AV_LEADER;
		} else {
			leader = VIRTUAL_BIB_BOOKS_LEADER;
		}
		return leader;
	}

	public Mono<WorkflowResponse> deleteBibliographicRecord(String id) {

		final var path = createPath("workflow");
		final var DeleteBibRecord = 11;
		final var DeleteBibRecordData = 10;

		return createRequest(POST, path, uri -> {
		})
			.map(request -> request.body(WorkflowRequest.builder()
				.workflowRequestType(DeleteBibRecord)
				.txnUserID(TransactingPolarisUserID)
				.txnBranchID(TransactingBranchID)
				.txnWorkstationID(TransactingWorkstationID)
				.requestExtension(RequestExtension.builder()
					.workflowRequestExtensionType(DeleteBibRecordData)
					.data(RequestExtensionData.builder()
						.bibRecordIDs( singletonList(Integer.valueOf(id)) )
						.build())
					.build())
				.build()))
			.flatMap(req -> client.retrieve(req, Argument.of(WorkflowResponse.class)))
			.flatMap(resp -> handlePolarisWorkflow(resp, ConfirmBibRecordDelete, Continue));
	}

	public Mono<WorkflowResponse> addItemRecord(CreateItemCommand createItemCommand) {

		// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/overview
		// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/add_or_update_item_record
		final var path = createPath("workflow");

		final var barcodePrefix = polarisConfig.getItemBarcodePrefix();
		final var itemBarcode = useBarcodeWithPrefix(createItemCommand, barcodePrefix);
		final var canonicalItemType = createItemCommand.getCanonicalItemType();

		final var itemRecordType = 8;
		final var itemRecordData = 6;
		final var Available = 1; // In

		// The createItemCommand for polaris should use the home location of the patron placing the request
		// So that the request can be routed appropriately in house
		return Mono.zip(createRequest(POST, path, uri -> {}),
				client.getMappedItemType(canonicalItemType))
			.map(tuple -> {
				final var request = tuple.getT1();
				final var itemtype = Integer.parseInt(tuple.getT2());

				// used for branches - note: different combinations important here
				final Integer interLibraryLoanBranch = polarisConfig.getIllLocationId();
				final Integer patronHomeBranch = getPatronHomeBranch(createItemCommand);

				// holds get deleted in polaris after being filled
				// adding a note to help staff know where to send the item back
				final var supplierAgencyCode = getValueOrNull(createItemCommand, CreateItemCommand::getLocationCode);
				final var supplierHostLmsCode = getValueOrNull(createItemCommand, CreateItemCommand::getSupplierHostLmsCode);
				final String noteForStaff = getNoteForStaff(supplierAgencyCode, supplierHostLmsCode);

				final var body = WorkflowRequest.builder()
					.workflowRequestType(itemRecordType)
					.txnUserID(TransactingPolarisUserID)
					.txnBranchID(TransactingBranchID)
					.txnWorkstationID(TransactingWorkstationID)
					.requestExtension( RequestExtension.builder()
						.workflowRequestExtensionType(itemRecordData)
						.data(RequestExtensionData.builder()
							.associatedBibRecordID(Integer.parseInt(createItemCommand.getBibId()))
							.barcode( itemBarcode )
							.isNew(TRUE)
							.displayInPAC(FALSE)
							.assignedBranchID( isInterLibraryLoanBranchIfNotNull(interLibraryLoanBranch, patronHomeBranch) )
							.owningBranchID( isInterLibraryLoanBranchIfNotNull(interLibraryLoanBranch, patronHomeBranch) )
							.homeBranchID( isInterLibraryLoanBranchIfNotNull(interLibraryLoanBranch, patronHomeBranch) )
							.renewalLimit(polarisConfig.getItemRenewalLimit())
							.fineCodeID(polarisConfig.getItemFindCodeId())
							.itemRecordHistoryActionID(polarisConfig.getItemHistoryActionId())
							.loanPeriodCodeID(getItemLoanPeriodCodeId(canonicalItemType))
							.shelvingSchemeID(polarisConfig.getItemShelvingSchemeId())
							.isProvisionalSave(FALSE)
							.nonCircluating(FALSE)
							.loneableOutsideSystem(TRUE)
							.holdable(TRUE)
							.itemStatusID(Available)
							.materialTypeID(itemtype)
							.nonPublicNote(noteForStaff)
							.build())
						.build())
					.build();

				log.info("create item workflow request: {}", body);
				return request.body(body);
			})
			.flatMap(request -> saveItemRequest(request, itemBarcode));
	}

	private Integer getItemLoanPeriodCodeId(String canonicalItemType) {
		log.debug("Determining loan period code ID for canonical item type: {}", canonicalItemType);

		// short term fix for DCB-1860
		// TODO: long term fix - RVM canonical item type -> loan period code id
		final Integer loanPeriodCodeId = "CIRCAV".equals(canonicalItemType)
			? polarisConfig.getItemAvLoanPeriodCodeId()
			: polarisConfig.getItemLoanPeriodCodeId();

		log.debug("Resolved loan period code ID: {}", loanPeriodCodeId);
		return loanPeriodCodeId;
	}

	private static String useBarcodeWithPrefix(CreateItemCommand createItemCommand, String barcodePrefix) {
		return (barcodePrefix != null && !barcodePrefix.equals("")
			? useItemBarcodePrefix(barcodePrefix)
			: useItemBarcodePrefix(""))
			+ createItemCommand.getBarcode();
	}

	private static String useItemBarcodePrefix(String prefix) {
		log.info("Using item barcode prefix: {}", prefix);
		return prefix;
	}

	private static Integer isInterLibraryLoanBranchIfNotNull(Integer interLibraryLoanBranch, Integer patronHomeBranch) {
		return interLibraryLoanBranch != null ? interLibraryLoanBranch : patronHomeBranch;
	}

	private Integer getPatronHomeBranch(CreateItemCommand createItemCommand) {
		final var patronHomeLocation = createItemCommand.getPatronHomeLocation();
		if (patronHomeLocation == null) {
			// note: this will likely imply the locations local id is missing
			throw new IllegalArgumentException(
				"Missing patron home location for polaris user - createItemCommand=" + createItemCommand);
		}

		return Integer.valueOf(patronHomeLocation);
	}

	public Mono<Void> updateItemRecordStatus(String itemId, Integer fromStatus, Integer toStatus) {

		final var path = createPath("workflow");
		final var itemRecordType = 8;
		final var itemRecordData = 6;

		return createRequest(POST, path, uri -> {})
			.map(request -> {
				final var body = WorkflowRequest.builder()
					.workflowRequestType(itemRecordType)
					.txnUserID(TransactingPolarisUserID)
					.txnBranchID(TransactingBranchID)
					.txnWorkstationID(TransactingWorkstationID)
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
			.flatMap(request -> saveItemRequest(request, null))
			.then();
	}

	public Mono<WorkflowResponse> updateItemRecord(String itemId, Integer status,
		String newBarcode, String nonPublicNote, Integer itemType) {

		final var path = createPath("workflow");
		final var itemRecordType = 8;
		final var itemRecordData = 6;

		return createRequest(POST, path, uri -> {})
			.map(request -> {
				final var body = WorkflowRequest.builder()
					.workflowRequestType(itemRecordType)
					.txnUserID(TransactingPolarisUserID)
					.txnBranchID(TransactingBranchID)
					.txnWorkstationID(TransactingWorkstationID)
					.requestExtension( RequestExtension.builder()
						.workflowRequestExtensionType(itemRecordData)
						.data(RequestExtensionData.builder()
							.itemRecordID( Integer.valueOf(itemId) )
							.originalItemStatusID(status)
							.itemStatusID(status)
							.barcode(newBarcode)
							.nonPublicNote(nonPublicNote)
							.materialTypeID(itemType)
							.build())
						.build())
					.build();
				return request.body(body);
			})
			.flatMap(request -> saveItemRequest(request, null));
	}

	public Mono<WorkflowResponse> deleteItemRecord(String id) {

		final var path = createPath("workflow");
		final var DeleteItemRecord = 10;
		final var DeleteItemRecordData = 8;

		return createRequest(POST, path, uri -> {
		})
			.map(request -> request.body(WorkflowRequest.builder()
				.workflowRequestType(DeleteItemRecord)
				.txnUserID(TransactingPolarisUserID)
				.txnBranchID(TransactingBranchID)
				.txnWorkstationID(TransactingWorkstationID)
				.requestExtension(RequestExtension.builder()
					.workflowRequestExtensionType(DeleteItemRecordData)
					.data(RequestExtensionData.builder()
						.isAutoDelete(FALSE)
						.itemRecordIDs( singletonList(Integer.valueOf(id)) )
						.build())
					.build())
				.build()))
			.flatMap(req -> client.retrieve(req, Argument.of(WorkflowResponse.class)))
			.flatMap(response -> handlePolarisWorkflow(response, ConfirmItemRecordDelete, Continue))
			.flatMap(response -> replyIfPromptMatches(response, BreakableDeletionLinks, Continue))
			.flatMap(response -> handlePolarisWorkflow(response, LastCopyOrRecordOptions, Retain));
	}

	// https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/overview
	private Mono<WorkflowResponse> handlePolarisWorkflow(
		WorkflowResponse response, Integer promptID, Integer promptResult) {

		if (!Objects.equals(response.getWorkflowStatus(), InputRequired)) {
			log.info("Input was not required for workflow response: {}", response.getWorkflowRequestGuid());
			return Mono.just(response);
		}

		if (response.getPrompt() != null && response.getPrompt().getTitle() != null) {
			log.info("Trying to handle polaris workflow: {}", response.getPrompt().getTitle());

		} else {
			log.warn("Trying to handle polaris workflow prompt ID: {}, more information found at: {}",
				promptID, "https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/overview");
		}

		return Mono.just(response)
			.filter(workflowResponse -> workflowResponse.getPrompt() != null)
			.filter(workflowResponse -> Objects.equals(workflowResponse.getPrompt().getWorkflowPromptID(), promptID))
			.flatMap(resp -> createItemWorkflowReply(resp.getWorkflowRequestGuid(), promptID, promptResult))
			.flatMap(req -> client.retrieve(req, Argument.of(WorkflowResponse.class)))
			.switchIfEmpty(raiseError(Problem.builder()
				.withType(ERR0210)
				.withTitle("Failed to handle Polaris.ApplicationServices API workflow")
				.withDetail(response.getPrompt() != null && response.getPrompt().getTitle() != null ? response.getPrompt().getTitle() : "Response didn't have a title")
				.with("Message", response.getPrompt() != null && response.getPrompt().getMessage() != null ? response.getPrompt().getMessage() : "No message")
				.with("Information messages", response.getInformationMessages() != null ? response.getInformationMessages() : "No messages")
				.with("Workflow prompt ID", response.getPrompt().getWorkflowPromptID() != null ? response.getPrompt().getWorkflowPromptID() : "No prompt id")
				.with("Workflow doc", "https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/workflow/overview")
				.with("Workflow status", response.getWorkflowStatus() != null ? response.getWorkflowStatus() : "No status")
				.with("Workflow request GUID", response.getWorkflowRequestGuid() != null ? response.getWorkflowRequestGuid() : "No guid")
				.with("Full response", response)
				.with("Expected prompt ID", promptID)
				.with("Expected reply", promptResult)
				.build())
			);
	}

	private Mono<WorkflowResponse> saveItemRequest(MutableHttpRequest<WorkflowRequest> workflowRequest,
		String itemBarcode) {
		// https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/add_or_update_item_record

		return client.retrieve(workflowRequest, Argument.of(WorkflowResponse.class))
			.doOnError(e -> log.info("Error response for save item {}", workflowRequest, e))
			// when we save the virtual item we need to confirm we do not want the item to display in pac
			.flatMap(response -> replyIfPromptMatches(response, NoDisplayInPAC, Continue))
			// confirm the barcode changed on update (only applies to update requests)
			.flatMap(response -> replyIfPromptMatches(response, ConfirmItemBarcodeChanged, Continue))
			// saving an item with a duplicate barcode causes the hold request to fail
			.map(resp -> {
				if (resp.getPrompt() != null &&
					resp.getPrompt().getWorkflowPromptID() != null &&
					resp.getPrompt().getWorkflowPromptID().equals(DuplicateRecords)) {

					validateWorkflowResponse(resp);

					throw new SaveVirtualItemException(
						"Item with barcode: " + itemBarcode + " already exists in host LMS: " + client.getName());
				}

				return resp;
			})
			.switchIfEmpty(Mono.error(new PolarisWorkflowException(
				"Item request failed expecting workflow response to: " + workflowRequest)));
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
			.flatMap(request -> client.retrieve(request, Argument.of(String.class)))
			// remove quotes
			.map(string -> string.replace("\"", ""));
	}

	public Mono<List<MaterialType>> listMaterialTypes() {
		final var path = createPath("materialtypes");

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(MaterialType.class)));
	}

	public Mono<List<PolarisItemStatus>> listItemStatuses() {
		final var path = createPath("itemstatuses");

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(PolarisItemStatus.class)));
	}

	public Mono<BibliographicRecord> getBibliographicRecordByID(String localBibId) {
		final var path = createPath("bibliographicrecords", localBibId);

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, BibliographicRecord.class, TRUE))
			.map(HttpResponse::body);
	}

	private Mono<WorkflowRequest> getLocalRequestBody(HoldRequestParameters holdRequestParameters,
		String activationDate, String noteWithActivationDateUTC) {

		final var placeHoldRequest = 5;
		final var holdRequestData = 9;

		return getHoldRequestDefaults()
			.map(expiration -> WorkflowRequest.builder()
				.workflowRequestType(placeHoldRequest)
				.txnUserID(TransactingPolarisUserID)
				.txnBranchID(TransactingBranchID)
				.txnWorkstationID(TransactingWorkstationID)
				.requestExtension( RequestExtension.builder()
					.workflowRequestExtensionType(holdRequestData)
					.data(RequestExtensionData.builder()
						.patronID(Optional.ofNullable(
							holdRequestParameters.getLocalPatronId()).map(Integer::valueOf).orElse(null))
						.pickupBranchID( checkPickupBranchID(holdRequestParameters.getPickupLocation()))
						.origin(2)
						.activationDate(activationDate)
						.expirationDate(LocalDateTime.now().plusDays(expiration).format( ofPattern("MM/dd/yyyy")))
						.staffDisplayNotes(noteWithActivationDateUTC)
						.nonPublicNotes(noteWithActivationDateUTC)
						.pACDisplayNotes(noteWithActivationDateUTC)
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

	private static Integer checkPickupBranchID(String pickupLocation) {
		log.debug("checking pickup branch id from passed pickup location: '{}'", pickupLocation);

		try {
			return Optional.ofNullable( Integer.valueOf(pickupLocation) )
				.orElseThrow(() -> new NumberFormatException("Invalid number format"));
		} catch (NumberFormatException e) {
			throw new HoldRequestException("Cannot use pickup location '" + pickupLocation + "' for pickupBranchID.");
		}
	}

	private Mono<MutableHttpRequest<?>> createRequest(HttpMethod httpMethod, String path,
		Consumer<UriBuilder> uriBuilderConsumer) {

		final var createdRequest = client.isApplicationServicesBaseUrlPresent()
			? client.createRequestWithOverrideURL(httpMethod,path)
			: client.createRequest(httpMethod,path);

		return createdRequest.map(req -> req.uri(uriBuilderConsumer)).flatMap(authFilter::basicAuth);
	}

	private String createPath(Object... pathSegments) {
		return URI_PARAMETERS + "/" + Arrays.stream(pathSegments).map(Object::toString).collect(Collectors.joining("/"));
	}

	private String createPathWithOrgOverride(Integer overrideOrgId, Object... pathSegments) {
		// There are certain operations (such as posting a blocking note for renewal prevention) where we need to replace the org ID
		// To ensure it matches that of the item itself
		String params = URI_PARAMETERS;

		if (overrideOrgId != null) {
			params = params.replaceFirst("/polaris/\\d+/", "/polaris/" + overrideOrgId + "/");
		}

		return params + "/" + Arrays.stream(pathSegments).map(Object::toString).collect(Collectors.joining("/"));
	}

	private static MutableHttpRequest<WorkflowRequest> addBodyToRequest(
		MutableHttpRequest<?> request, WorkflowRequest body) {

		log.debug("trying addLocalHoldRequest with body {}", body);
		return request.body(body);
	}

	public Mono<ItemRecordFull> itemrecords(String localItemId, Boolean handleItemNotFoundAsMissing) {
		final var path = createPath("itemrecords", localItemId);

		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(ItemRecordFull.class), response -> response
					.onErrorResume(error -> handleItemNotFoundError(handleItemNotFoundAsMissing, localItemId, error))
			));
	}

	private static Mono<ItemRecordFull> handleItemNotFoundError(Boolean handleItemNotFoundAsMissing,
		String localItemId, Throwable error)
	{
		String errorMessage = Objects.toString(error.getMessage(), null);
		log.error("Error attempting to retrieve item record {}: {}", localItemId, errorMessage);

		if (handleItemNotFoundAsMissing && error instanceof HttpClientResponseException httpClientResponseException) {

			final var isNotFound = httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND;

			if (isNotFound) {
				// Pass back an empty Mono to allow further processing
				return Mono.empty();
			}
		}

		return Mono.error(error);
	}

	public Mono<WorkflowResponse> checkIn(String itemId, Integer illLocationId) {

		final var path = createPath("workflow");
		final var CheckInWorkflowRequestType = 1;
		final var CheckInDataWorkflowRequestExtensionType = 2;
		final var CHKIN_NORM = 1;
		final var NumberOfFreeDaysGivenToThePatron = 0;

		return getItemBarcode(itemId)
			.flatMap(barcode -> createRequest(POST, path, uri -> {})
				.map(request -> request.body(WorkflowRequest.builder()
					.workflowRequestType(CheckInWorkflowRequestType)
					.txnUserID(TransactingPolarisUserID)
					.txnBranchID(illLocationId)
					.txnWorkstationID(TransactingWorkstationID)
					.requestExtension(RequestExtension.builder()
						.workflowRequestExtensionType(CheckInDataWorkflowRequestExtensionType)
						.data(RequestExtensionData.builder()
							.checkinTypeID(CHKIN_NORM)
							.itemBarcode(barcode)
							.freeDays(NumberOfFreeDaysGivenToThePatron)
							.ignoreInventoryStatusMessages(FALSE)
							.build())
						.build())
					.build())))
			.flatMap(this::createCheckInRequest);
	}

	private Mono<WorkflowResponse> createCheckInRequest(MutableHttpRequest<WorkflowRequest> workflowRequest) {
			return client.retrieve(workflowRequest, Argument.of(WorkflowResponse.class))
				// Fills another request, transfer?
				.flatMap(response -> replyIfPromptMatches(response, FillsRequestTransferPrompt, Yes))
				.flatMap(response -> replyIfPromptMatches(response, UpdateItemRecord, Yes))
				.map(this::validateWorkflowResponse);
	}

	/**
	 * Based upon <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/workflow/create_hold_request">post hold request docs</a>
	 */
	Mono<Tuple4<String, String, String, String>> createILLHoldRequestWorkflow(HoldRequestParameters holdRequestParameters) {
		log.debug("createILLHoldRequestWorkflow with holdRequestParameters {}", holdRequestParameters);

		final var path = createPath("workflow");
		final String activationDate = LocalDateTime.now().format( ofPattern("yyyy-MM-dd"));

		return createRequest(POST, path, uri -> {})
			.zipWith(Mono.just(WorkflowRequest.builder()
				.workflowRequestType(5)
				.txnUserID(TransactingPolarisUserID)
				.txnBranchID(holdRequestParameters.getLocalItemLocationId())
				.txnWorkstationID(TransactingWorkstationID)
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
				.build()))
			.map(function(ApplicationServicesClient::addBodyToRequest))
			.flatMap(workflowReq -> client.retrieve(workflowReq, Argument.of(WorkflowResponse.class)))
			.map(this::validateWorkflowResponse)
			.thenReturn(Tuples.of(
				holdRequestParameters.getLocalPatronId(),
				holdRequestParameters.getTitle(),
				holdRequestParameters.getNote(),
				activationDate));
	}

	public Mono<List<ILLRequest>> getIllRequest(String patronLocalId) {

		final var path = createPath("patrons", patronLocalId, "requests", "ill");
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(ILLRequest.class)));
	}

	public Mono<WorkflowResponse> convertToIll(Integer illLocationId, String localId) {

		final var path = createPath("workflow");
		final var ConvertHoldRequestToILLRequest = 20;
		final var ConvertToILLRequestData = 16;

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(WorkflowRequest.builder()
				.workflowRequestType(ConvertHoldRequestToILLRequest)
				.txnUserID(TransactingPolarisUserID)
				.txnBranchID(illLocationId)
				.txnWorkstationID(TransactingWorkstationID)
				.requestExtension( RequestExtension.builder()
					.workflowRequestExtensionType(ConvertToILLRequestData)
					.data(RequestExtensionData.builder()
						.sysHoldRequestID(localId)
						.skipTotalILLLimitExceededPrompt(TRUE)
						.build())
					.build())
				.build()))
			.flatMap(this::convertToIllRequest);
	}

	public Mono<ILLRequestInfo> transferRequest(Integer illLocationId, Integer illRequestId) {

		final var path = createPath("workflow");
		final var ReceiveILLRequest = 18;
		final var ReceiveILLData = 14;

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(WorkflowRequest.builder()
				.workflowRequestType(ReceiveILLRequest)
				.txnUserID(TransactingPolarisUserID)
				.txnBranchID(illLocationId)
				.txnWorkstationID(TransactingWorkstationID)
				.requestExtension( RequestExtension.builder()
					.workflowRequestExtensionType(ReceiveILLData)
					.data(RequestExtensionData.builder()
						.iLLRequestID(illRequestId)
						.circTranType(12)
						.build())
					.build())
				.build()))
			.flatMap(this::createTransferRequest);
	}

	private Mono<ILLRequestInfo> createTransferRequest(MutableHttpRequest<WorkflowRequest> workflowReq) {
		return client.retrieve(workflowReq, Argument.of(WorkflowResponse.class))
			.doOnSuccess(r -> log.info("Got transfer request response {}", r))
			.doOnError(e -> log.info("Error response for transferring ILL request {}", workflowReq, e))
			// when we save the virtual item we need to confirm we do not want the item to display in pac
			.flatMap(response -> handlePolarisWorkflow(response, BriefItemEntry, Continue))
			.switchIfEmpty( Mono.error(new PolarisWorkflowException(
				"transferring ILL request failed expecting workflow response to: " + workflowReq)))
			.map(workflowResponse -> workflowResponse.getAnswerExtension().getAnswerData().getILLRequestInfo());
	}

	private Mono<WorkflowResponse> convertToIllRequest(MutableHttpRequest<WorkflowRequest> workflowReq) {
		return client.retrieve(workflowReq, Argument.of(WorkflowResponse.class))
			.doOnSuccess(ApplicationServicesClient::logSuccessfulResponseMessage)
			.doOnError(e -> log.info("Error response for convert ILL request {}", workflowReq, e))
			.switchIfEmpty(Mono.error(new PolarisWorkflowException(
				"convert ILL request failed expecting workflow response to: " + workflowReq)));
	}

	private static void logSuccessfulResponseMessage(WorkflowResponse r) {
		log.info(">>>>>>> {} <<<<<<<", r.getInformationMessages() != null
			? r.getInformationMessages()
			: "No information messages available.");
	}


	public Mono<GetItemHoldsResponse> getReservationsForItem(Integer id, Integer offset, Integer limit) {

		final var path = createPath("itemrecords", id, "holds");
		// Note that this will return bib level holds as well.

		return createRequest(GET, path, uri -> uri
			.queryParam("Limit", limit) // Make sure this is set or you will not get ANY reservation data
			.queryParam("Offset", offset))// Introducing pagination just in case we get some crazy amount of reservations
			.flatMap(request -> client.exchange(request, GetItemHoldsResponse.class, FALSE))
			.map(HttpResponse::body)
			.onErrorResume(error -> {
				// Just in case
				if ((error instanceof HttpClientResponseException) &&
					(((HttpClientResponseException) error).getStatus() == HttpStatus.NOT_FOUND)) {
					return Mono.empty();
				} else {
					log.error("{} {}", client.getHostLmsCode(), path, error);
					return raiseError(error);
				}
			});
	}
	public Mono<Void> placeItemBlock(String itemId, Integer noteId, String text, Integer overrideOrgId) {
		final var path = createPathWithOrgOverride(overrideOrgId, "itemrecords", itemId, "blockingnote");

		final DtoItemRecordBlockingNote blockingNote = DtoItemRecordBlockingNote.builder()
			.noteId(noteId)
			.text(text)
			.build();

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(blockingNote))
			.flatMap(request -> client.retrieve(request, Argument.of(BlockingNoteResponse.class)))
			.flatMap(response -> {
				if (response.isSuccess()) {
					log.debug("Successfully placed blocking note on item {}. Response: {}", itemId, response);
					return Mono.empty();
				} else {
					log.error("Polaris returned failure for blocking note: {}", response.getMessage());
					return Mono.error(new PolarisWorkflowException("Failed to place blocking note on item with ID "+itemId+", error message " + response.getMessage()+" and full response: "+response));
				}
			});
	}


	/**
	 * Fetches a virtual patron, checks the expiry date, updates if necessary
	 * and then maps the final data to the DCB Patron model.
	 * For virtual patrons ONLY.
	 * The intention is to prevent request failures because of virtual patrons expiring and causing blocks.
	 * This will only run for a virtual patron that is actually being used in a request.
	 * @param localPatronId The patron's local ID
	 * @return A Mono emitting the Patron, with expiry date potentially updated.
	 */
	public Mono<Patron> getVirtualPatronAndCheckExpiry(String localPatronId) {
		final var path = createPath("patrons", localPatronId);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, PatronData.class, TRUE))
			.map(HttpResponse::body)
			// Call the expiry check logic
			.flatMap(this::checkAndUpdateExpiryIfNeeded)
			// Now map the (potentially updated) data to the DCB Patron
			.map(data -> Patron.builder()
				.localId(singletonList(valueOf(data.getPatronID())))
				.localPatronType(valueOf(data.getPatronCodeID()))
				.localBarcodes(singletonList(data.getBarcode()))
				.localHomeLibraryCode(valueOf(data.getOrganizationID()))
				.localNames(
					Optional.ofNullable(data.getRegistration())
						.map(reg -> Stream.of(reg.getNameFirst(), reg.getNameMiddle(), reg.getNameLast())
							// At the minute if a name is null, we map to a blank string
							.map(name -> name == null ? "" : name)
							.map(String::trim)
							.filter(s -> !s.isEmpty())
							.collect(Collectors.toList())
						)
						// Default to an empty list if registration itself is null
						.orElse(Collections.emptyList())
				)
				.isActive(true)
				.isBlocked(data.getSystemBlocks() != null && data.getSystemBlocks() > 0)
				.isDeleted(false)
				.expiryDate((data.getRegistration() != null && data.getRegistration().getExpirationDate() != null) ? Timestamp.valueOf(data.getRegistration().getExpirationDate()) : null)
				.build());
	}

	/**
	 * Checks if a patron's registration is expired or expiring within 7 days.
	 * If it is, it attempts to update the expiry date in Polaris to 90 days from now.
	 * Think of 90 days as a starting point: this can be changed if necessary.
	 * @param patronData The raw PatronData from Polaris.
	 * @return A Mono emitting the PatronData, which will be updated if the update was successful.
	 */
	private Mono<PatronData> checkAndUpdateExpiryIfNeeded(PatronData patronData) {
		final var registration = patronData.getRegistration();
		if (registration == null) {
			log.warn("PatronData {} has no registration block. Cannot check expiry.", patronData.getPatronID());
			return Mono.just(patronData);
		}

		final var patronExpiryDateTime = registration.getExpirationDate();
		if (patronExpiryDateTime == null) {
			log.warn("PatronData {} has no expiration date. Cannot check expiry.", patronData.getPatronID());
			return Mono.just(patronData);
		}

		// Making sure it's in UTC for constant time checking
		final var now = LocalDateTime.now(UTC);
		final var expiryThreshold = now.plusDays(30); // 30 days as loans can cover more than 7 days

		// If the patron is expiring within 30 days or has already expired, update
		if (patronExpiryDateTime.isBefore(expiryThreshold)) {
			log.debug("Patron {} expiry date {} is within 30 days. Updating.", patronData.getPatronID(), patronExpiryDateTime);

			final var newExpiryLocalDateTime = now.plusDays(120);
			// Update the registration object, which is used by updatePatronRegistration
			registration.setExpirationDate(newExpiryLocalDateTime);

			return this.updatePatronRegistration(valueOf(patronData.getPatronID()), registration)
				.map(success -> {
					if (Boolean.TRUE.equals(success)) {
						log.debug("Successfully updated patron {} expiry date in Polaris to {}", patronData.getPatronID(), newExpiryLocalDateTime);
						// The patronData object is already mutated, so just pass it on.
						return patronData;
					} else {
						// If we fail, we must revert the mutation before passing it on.
						// Or we'll think we've updated a patron expiry when we haven't.
						log.warn("Failed to update patron {} expiry date in Polaris. Reverting patron change.", patronData.getPatronID());
						registration.setExpirationDate(patronExpiryDateTime);
						return patronData;
					}
				});
		}
		// If our patron hasn't expired, just return the original data
		return Mono.just(patronData);
	}

	/**
	 * Checks if a patron's registration is expired or expiring within 7 days.
	 * If it is, it attempts to update the expiry date in Polaris to 90 days from now.
	 * Think of 90 days as a starting point: this can be changed if necessary.
	 * @param localPatronId The Polaris Patron ID
	 * @param registration The PatronRegistration object to update the Polaris Patron with
	 * @return Boolean Mono depending on whether the update is successful.
	 * Success indicator is a 204 No Content response.
	 */
	public Mono<Boolean> updatePatronRegistration(String localPatronId, PatronRegistration registration) {
		log.debug("Updating registration for patron {}: {}", localPatronId, registration);
		final var path = createPath("patrons", localPatronId) + "?type=profile";
		// Construct the body
		// The endpoint expects a PATCH request
		// With a JSON body with the new expiry date and override policy true
		// https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/patrons/patron_patch
		final var requestBody = DtoPatronProfile.builder()
															.expirationDate(valueOf(registration.expirationDate))
															.overridePolicy(true)
															.build();
		return createRequest(PATCH, path, uri -> {})
			.map(request -> request.body(requestBody))
			// It would be good to audit log this and not just rely on the logs
			.flatMap(request -> client.exchange(request, Void.class, FALSE))
			.flatMap(response -> {
				// Check for 204 No Content
				if (response.getStatus() == HttpStatus.NO_CONTENT) {
					log.info("Successfully updated registration for virtual patron {}", localPatronId);
					return Mono.just(TRUE);
				} else {
					// Any other status code means it didn't work for whatever reason
					log.warn("Unexpected status {} when updating virtual patron {} registration. Body: {}",
						response.getStatus(), localPatronId, response.getBody().orElse(null));
					return Mono.just(FALSE);
				}
			})
			.onErrorResume(e -> {
				log.error("Error updating patron {} registration: {}", localPatronId, e.getMessage(), e);
				return Mono.just(FALSE); // Return false on error to prevent breaking the chain
			});
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class ItemRecordFull {
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("Barcode")
		private String barcode;
		@JsonProperty("AssignedBranchID")
		private Integer assignedBranchID;
		@JsonProperty("ItemStatusID")
		private Integer itemStatusID;
		@JsonProperty("ItemStatusDescription")
		private String itemStatusDescription;
		@JsonProperty("itemStatusName")
		private String itemStatusName;
		@JsonProperty("BibInfo")
		private BibInfo bibInfo;
		@JsonProperty("CirculationData")
		private CirculationData circulationData;

	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class BibInfo {
		@JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
		@JsonProperty("CanItemBeRenewed")
		private Boolean canItemBeRenewed;
		@JsonProperty("Renewals")
		private Integer renewals;
		@JsonProperty("RenewalLimit")
		private Integer renewalLimit;
		@JsonProperty("CallNumber")
		private String callNumber;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class CirculationData {
		@JsonProperty("DueDate")
		private String dueDate;
		@JsonProperty("RenewalCount")
		private Integer renewalCount;
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
	public static class SysHoldRequest {
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
		public static final Integer Yes = 2;
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
		public static final Integer UpdateItemRecord = 8; // found as part of DCB-2024
		public static final Integer FillsRequestTransferPrompt = 30;
		public static final Integer BriefItemEntry = 55;
		public static final Integer ConfirmItemBarcodeChanged = 62; // found as part of DCB-1990
		public static final Integer NoDisplayInPAC = 66;
		public static final Integer ExceededTotalRequestLimit = 68; // found as part of DCB-1353
		public static final Integer DuplicateRecords = 72;
		public static final Integer ConfirmItemRecordDelete = 73;
		public static final Integer BreakableDeletionLinks = 75; // found as part of DCB-1374
		// public static final Integer DuplicateHoldRequests = 77;
		public static final Integer ConfirmBibRecordDelete = 79;
		public static final Integer LastCopyOrRecordOptions = 82;
		public static final Integer PromoteItemRequestToBib = 88;

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
	public static class BibliographicRecord {
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
		@JsonProperty("BlockedRecordIDs")
		private List<String> blockedRecordIds;
		@JsonProperty("DeletedRecordIDs")
		private List<String> deletedRecordIds;

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
		@JsonProperty("Barcode")
		private String barcode;
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
	public static class WorkflowRequest {
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
	public static class RequestExtension {
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

		@JsonProperty("NonPublicNote")
		private String nonPublicNote;
		@JsonProperty("Note")
		private String note;

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

		// check in item
		@JsonProperty("CheckinTypeID")
		private Integer checkinTypeID;
		@JsonProperty("FreeDays")
		private Integer freeDays;
		@JsonProperty("ignoreInventoryStatusMessages")
		private Boolean ignoreInventoryStatusMessages;
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
	static class PatronDefaults {
		@JsonProperty("City")
		private String city;
		@JsonProperty("PostalCode")
		private String postalCode;
		@JsonProperty("State")
		private String state;
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
	public static class PatronData {
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
	public static class LibraryHold {
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

  @Builder
  @Data
  @AllArgsConstructor
  @Serdeable
  static class GetItemHoldsResponse {
    @JsonProperty("PitID")
		private String pitID;
    @JsonProperty("TotalCount")
		private Integer totalCount;
    @JsonProperty("Offset")
		private Integer offset;
    @JsonProperty("Reservations")
		private List<ItemReservationRecord> reservations;
	}

  @Builder
  @Data
  @AllArgsConstructor
  @Serdeable
  static class ItemReservationRecord {
    @JsonProperty("PatronID")
		private Integer patronId;
    @JsonProperty("BibliographicRecordID")
		private Integer bibliographicRecordID;
    @JsonProperty("SysHoldRequestID")
		private Integer sysHoldRequestID;
    @JsonProperty("IssueID")
		private Integer issueId;
    @JsonProperty("ItemBarcode")
		private String itemBarcode;
    @JsonProperty("CreationDate")
		private String creationDate;
    @JsonProperty("ExpirationDate")
		private String expirationDate;
    @JsonProperty("LastStatusTransitionDate")
		private String lastStatusTransitionDate;
    @JsonProperty("HoldTilDate")
		private String holdTilDate;
    @JsonProperty("PickupLocationCode")
		private String pickupLocationCode;
    @JsonProperty("PickupLocationName")
		private String pickupLocationName;
    @JsonProperty("StaffDisplayNotes")
		private String staffDisplayNotes;
    @JsonProperty("ItemStatusID")
		private String itemStatusID;
    @JsonProperty("ShelvingBit")
		private Boolean shelvingBit;
    @JsonProperty("NonCirculating")
		private Boolean nonCirculating;
    @JsonProperty("CheckInDate")
		private String checkInDate;
    @JsonProperty("LastCheckOutRenewDate")
		private String lastCheckOutRenewDate;
    @JsonProperty("InTransitSentDate")
		private String inTransitSentDate;
    @JsonProperty("InTransitRecvdDate")
		private String inTransitRecvdDate;
    @JsonProperty("LastCircTransactionDate")
		private String lastCircTransactionDate;
    @JsonProperty("ItemStatusDate")
		private String itemStatusDate;
    @JsonProperty("AssignedCollectionID")
		private Integer assignedCollectionID;
    @JsonProperty("DueDate")
		private String dueDate;
    @JsonProperty("VolumeName")
		private String volumeName;
		@JsonProperty("SysHoldStatusID")
		private Integer sysHoldStatusID;
	}

  @Builder
  @Data
  @AllArgsConstructor
  @Serdeable
  static class DtoItemRecordBlockingNote {
		@JsonProperty("NoteId")
		private Integer noteId;
		@JsonProperty("Text")
		private String text;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class BlockingNoteResponse {
		@JsonProperty("ItemRecordID")
		private Integer itemRecordID;
		@JsonProperty("Success")
		private boolean success;
		@JsonProperty("Message")
		private String message;
		@JsonProperty("Title")
		private String title;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class DtoPatronProfile {
		@JsonProperty("OverridePolicy")
		private Boolean overridePolicy;
		@JsonProperty("ExpirationDate")
		private String expirationDate;
	}
}
