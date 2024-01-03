package org.olf.dcb.core.interaction.polaris;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException;
import org.olf.dcb.core.interaction.polaris.exceptions.PatronBlockException;
import org.olf.dcb.core.interaction.polaris.exceptions.PolarisWorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpMethod.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.valueOf;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.PolarisClient.APPLICATION_SERVICES;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.extractMapValue;

import java.net.URI;
import org.zalando.problem.Problem;

class ApplicationServicesClient {
	private static final Logger log = LoggerFactory.getLogger(ApplicationServicesClient.class);
	private final PolarisLmsClient client;
	private final ApplicationServicesAuthFilter authFilter;
	private final String URI_PARAMETERS;

	// ToDo align these URLs
	private static final URI ERR0210 = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/0210/Polaris/UnableToLoadPatronBlocks");

	ApplicationServicesClient(
		PolarisLmsClient client)
	{
		this.client = client;
		this.authFilter = new ApplicationServicesAuthFilter(client);
		this.URI_PARAMETERS = "/polaris.applicationservices/api" + client.getGeneralUriParameters(APPLICATION_SERVICES);
	}

	/**
	 * @see https://qa-polaris.polarislibrary.com/Polaris.ApplicationServices/help/holdrequests/post_holdrequest_local
	 * @param holdRequestParameters
	 * @return
	 */
	Mono<HoldRequestResponse> addLocalHoldRequest(HoldRequestParameters holdRequestParameters) {
		final var path = createPath("holds");
		return createRequest(POST, path, uri -> uri.queryParam("bulkmode", true))
			.zipWith( getLocalRequestBody(holdRequestParameters) )
			.map(tuple -> {
				final var request = tuple.getT1();
				final var body = tuple.getT2();
				return request.body(body);
			})
			.flatMap(request -> client.exchange(request, HoldRequestResponse.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()))
			.onErrorResume(Exception.class, error -> {
				if (error instanceof HoldRequestException) {
					return Mono.error(error);
				} else {
					log.debug("An error occurred when creating a hold: {}", error.getMessage());
					return Mono.error(new HoldRequestException("Error occurred when creating a hold"));
				}
			});
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

	public <R> Mono<Integer> handlePatronBlock(Integer localPatronId) {
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

	private <R> Mono<List<PatronBlockGetRow>> getPatronBlocks(Integer localPatronId) {
		final var conf = client.getConfig();
		final var path = createPath("patrons", localPatronId, "blockssummary");
		return createRequest(GET, path, uri -> uri
				.queryParam("logonBranchID", conf.get(LOGON_BRANCH_ID))
				.queryParam("associatedblocks", false))
			  .flatMap(request -> client.retrieve(request, Argument.listOf(PatronBlockGetRow.class)))
        .onErrorResume( error -> {
            log.error("Error attempting to retrieve patron blocks {} : {}", localPatronId, error.getMessage());
            if ( ( error instanceof HttpClientResponseException ) && 
              ( ((HttpClientResponseException)error).getStatus() == HttpStatus.NOT_FOUND ) ) {
              // Not found is not really an error WRT patron blocks
              return Mono.empty();
            }
            else {
              return Mono.error(
                Problem.builder()
                  .withType(ERR0210)
                  .withTitle("Unable to retrieve patron blocks from polaris") // : "+error.getMessage())
                  .withDetail(error.getMessage())
                  .with("localPatronId",localPatronId)
                  .build()
             );
          }
        });

	}

	private <R> Mono<Integer> deletePatronBlock(Integer localPatronId, Integer blocktype, Integer blockid) {
		final var path = createPath("patrons", localPatronId, "blocks", blocktype, blockid);
		return createRequest(DELETE, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(Boolean.class)))
			.doOnSuccess(bool -> {
				if (!bool) {
					log.warn("Deleting patron block returned false.");
				} else {
					log.debug("Successfully deleted patron block.");
				}
			}).thenReturn( localPatronId );
	}

	public Mono<String> getPatronBarcode(String localId) {
		final var path = createPath("barcodes", "patrons", localId);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.of(String.class)))
			// remove quotes
			.map(string -> string.replace("\"", ""));
	}

	private <R> Mono<Integer> getHoldRequestDefaults() {
		final var path = createPath("holdsdefaults");
		final Integer defaultExpirationDatePeriod = 999;
		return createRequest(GET, path, uri -> {})
			// should the org id be pick up org id?
			.flatMap(request -> client.exchange(request, HoldRequestDefault.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()))
			.map(HoldRequestDefault::getExpirationDatePeriod)
			.onErrorReturn(defaultExpirationDatePeriod);
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
			.flatMap(request -> client.retrieve(request, Argument.of(Integer.class)));
	}

  public Mono<Integer> mapCanonicalLocationToLocalPolarisInteger(String code) {
    log.info("mapCanonicalLocationToLocalPolarisInteger({}) - TESTING return 71",code);
    return Mono.just(Integer.valueOf(6));
  }


	public Mono<ItemCreateResponse> addItemRecord(CreateItemCommand createItemCommand) {

		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesMap = (Map<String, Object>) conf.get(SERVICES);
		// final var branchid = extractMapValue(servicesMap, SERVICES_ORG_ID, Integer.class);
		final var workstation = extractMapValue(servicesMap, SERVICES_WORKSTATION_ID, Integer.class);

		final var itemMap = (Map<String, Object>) conf.get(ITEM);
		final var barcodePrefix = extractMapValue(itemMap, BARCODE_PREFIX, String.class);

		final var itemrecordtype = 8;
		final var itemrecorddata = 6;

		final var Available = 1; // In

		return Mono.zip(createRequest(POST, path, uri -> {}),
				client.getMappedItemType(createItemCommand.getCanonicalItemType()),
				mapCanonicalLocationToLocalPolarisInteger(createItemCommand.getLocationCode()))
				.map(tuple -> {

				final var request = tuple.getT1();
				final var itemtype = Integer.parseInt(tuple.getT2());
			  final var itemLocationId = tuple.getT3();
				final var body = WorkflowRequest.builder()
				.workflowRequestType(itemrecordtype)
				.txnUserID(user)
				.txnBranchID(branch)
				.txnWorkstationID(workstation)
				.requestExtension( RequestExtension.builder()
					.workflowRequestExtensionType(itemrecorddata)
					.data(RequestExtensionData.builder()
						.associatedBibRecordID(Integer.parseInt(createItemCommand.getBibId()))
						.barcode(barcodePrefix + createItemCommand.getBarcode())
						.isNew(TRUE)
						.displayInPAC(FALSE)
						.assignedBranchID( itemLocationId ) // needs clarifying
						.owningBranchID( itemLocationId ) // needs clarifying
						.homeBranchID( itemLocationId ) // needs clarifying
						.renewalLimit( extractMapValue(itemMap, RENEW_LIMIT, Integer.class) )
						.fineCodeID( extractMapValue(itemMap, FINE_CODE_ID, Integer.class) )
						.itemRecordHistoryActionID( extractMapValue(itemMap, HISTORY_ACTION_ID, Integer.class) )
						.loanPeriodCodeID( extractMapValue(itemMap, LOAN_PERIOD_CODE_ID, Integer.class) )
						.shelvingSchemeID( extractMapValue(itemMap, SHELVING_SCHEME_ID, Integer.class))
						.isProvisionalSave(FALSE)
						.nonCircluating(FALSE)
						.loneableOutsideSystem(TRUE)
						.holdable(TRUE)
						.itemStatusID(Available)
						.materialTypeID(itemtype)
						.build())
					.build())
				.build();
				log.info("create item workflow request: {}",body);
				return request.body(body);
			})
			.flatMap(this::createItemRequest);
	}

	public Mono<Void> updateItemRecord(String itemId, Integer fromStatus, Integer toStatus) {
		final var path = createPath("workflow");

		final var conf = client.getConfig();
		final var user = extractMapValue(conf, LOGON_USER_ID, Integer.class);
		final var branch = extractMapValue(conf, LOGON_BRANCH_ID, Integer.class);

		final var servicesMap = (Map<String, Object>) conf.get(SERVICES);
		final var workstation = extractMapValue(servicesMap, SERVICES_WORKSTATION_ID, Integer.class);

		final var itemrecordtype = 8;
		final var itemrecorddata = 6;

		return createRequest(POST, path, uri -> {})
			.map(request -> {
				final var body = WorkflowRequest.builder()
					.workflowRequestType(itemrecordtype)
					.txnUserID(user)
					.txnBranchID(branch)
					.txnWorkstationID(workstation)
					.requestExtension( RequestExtension.builder()
						.workflowRequestExtensionType(itemrecorddata)
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

	private Mono<ItemCreateResponse> createItemRequest(MutableHttpRequest<WorkflowRequest> workflowReq) {
		final var InputRequired = -3;
		return client.retrieve(workflowReq, Argument.of(WorkflowResponse.class))
			.doOnSuccess( r -> log.info("Got create item response {}",r) )
			.doOnError( r -> log.info("Error response for create item {} / {}",workflowReq,r) )
			.filter(workflowResponse -> workflowResponse.getWorkflowStatus() == InputRequired)
			.map(WorkflowResponse::getWorkflowRequestGuid)
			.flatMap(this::createItemWorkflowReply)
			.switchIfEmpty( Mono.error(new PolarisWorkflowException("item request failed expecting workflow response to: " + workflowReq)) );
	}

	private Mono<ItemCreateResponse> createItemWorkflowReply(String guid) {
		log.info("Responding to workflow for create item - uuid={}",guid);
		final var NoDisplayInPAC = 66;
		final var Continue = 5;
		return createRequest(PUT, createPath("workflow", guid), uri -> {})
			.map(request -> request.body(WorkflowReply.builder()
				.workflowPromptID(NoDisplayInPAC)
				.workflowPromptResult(Continue).build()))
			.doOnError(e -> log.error("Error response to workflow {}",e) )
			.flatMap(request -> client.retrieve(request, Argument.of(ItemCreateResponse.class)));
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

	public Mono<List<PolarisLmsClient.PolarisItemStatus>> listItemStatuses() {
		final var path = createPath("itemstatuses");
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.retrieve(request, Argument.listOf(PolarisLmsClient.PolarisItemStatus.class)));
	}

	public Mono<BibliographicRecord> getBibliographicRecordByID(String localBibId) {
		final var path = createPath("bibliographicrecords", localBibId);
		return createRequest(GET, path, uri -> {})
			.flatMap(request -> client.exchange(request, BibliographicRecord.class))
			.flatMap(response -> Mono.justOrEmpty(response.getBody()));
	}

	private Mono<LocalRequest> getLocalRequestBody(HoldRequestParameters data) {
		final var conf = client.getConfig();
		final var servicesMap = (Map<String, Object>) conf.get(SERVICES);
		return getHoldRequestDefaults().map( expiration -> LocalRequest.builder()
			.procedureStep(20) // bypass
			.answer(1) // default
			.activationDate( LocalDateTime.now().format( ofPattern("yyyy-MM-dd") ) )
			.expirationDate( LocalDateTime.now().plusDays(expiration).format( ofPattern("MM/dd/yyyy") ))
			.origin(extractMapValue(servicesMap, SERVICES_PRODUCT_ID, Integer.class))
			.patronID(Optional.ofNullable(data.getLocalPatronId()).map(Integer::valueOf).orElse(null))
			.pickupBranchID( checkPickupBranchID(data) )
			.trackingNumber(data.getDcbPatronRequestId())
			.unlockedRequest(true)
			.itemRecordID(Optional.ofNullable(data.getRecordNumber()).map(Integer::valueOf).orElse(null))
			.title(data.getTitle())
			.mARCTOMID(data.getPrimaryMARCTOMID())
			.nonPublicNotes("PickupLoc: "+data.getPickupLocation()+"\r\nTrackingID: "+data.getDcbPatronRequestId())
			.build());
	}

	private static Integer checkPickupBranchID(HoldRequestParameters data) {
		log.debug("checking pickup branch id from passed pickup location: '{}'", data.getLocalItemLocationId());
		try {
			return Optional.ofNullable(data.getLocalItemLocationId())
				.orElseThrow(() -> new NumberFormatException("Invalid number format"));
		} catch (NumberFormatException e) {
			throw new HoldRequestException("Cannot use pickup location '"+data.getLocalItemLocationId()+"' for pickupBranchID.");
		}
	}

	private <T> Mono<MutableHttpRequest<?>> createRequest(HttpMethod httpMethod, String path,
		Consumer<UriBuilder> uriBuilderConsumer) {
		return client.createRequest(httpMethod,path).map(req -> req.uri(uriBuilderConsumer)).flatMap(authFilter::basicAuth);
	}

	private String createPath(Object... pathSegments) {
		return URI_PARAMETERS + "/" + Arrays.stream(pathSegments).map(Object::toString).collect(Collectors.joining("/"));
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class WorkflowReply {
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
		@JsonProperty("WorkflowRequestGuid")
		private String workflowRequestGuid;
		@JsonProperty("WorkflowStatus")
		private Integer workflowStatus;
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
	static class ItemCreateResponse {
		@JsonProperty("AnswerExtension")
		private AnswerExtension answerExtension;
		@JsonProperty("InformationMessages")
		private List<InformationMessage> informationMessages;
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
		@JsonProperty("DesignationsOrVolumes")
		private String designationsOrVolumes;
		@JsonProperty("DuplicateHoldRequests")
		private String duplicateHoldRequests;
		@JsonProperty("HoldRequestID")
		private Integer holdRequestID;
		@JsonProperty("LinkedPatronBlocks")
		private String linkedPatronBlocks;
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
		@JsonProperty("PatronBlocks")
		private String patronBlocks;
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
