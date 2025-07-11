package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

import org.olf.dcb.core.model.HostLms;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.olf.dcb.core.interaction.MultipleVirtualPatronsFound;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
import org.olf.dcb.core.interaction.polaris.PolarisLmsClient.BibsPagedResult;
import org.olf.dcb.core.interaction.polaris.exceptions.FindVirtualPatronException;
import org.olf.dcb.core.interaction.polaris.exceptions.ItemCheckoutException;
import org.reactivestreams.Publisher;
import org.zalando.problem.Problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
public class PAPIClient {
	private final PolarisLmsClient client;
	private final PAPIAuthFilter authFilter;
	private final String PUBLIC_PARAMETERS;
	private final String PROTECTED_PARAMETERS;
	private final PolarisConfig polarisConfig;
	private final ConversionService conversionService;
	private final HostLms lms;

	public PAPIClient(PolarisLmsClient client, PolarisConfig polarisConfig, ConversionService conversionService, HostLms lms) {
		this.client = client;
		this.polarisConfig = polarisConfig;
		this.authFilter = new PAPIAuthFilter(client, polarisConfig);
		this.lms = lms;

		// Build PAPI base parameters
		String PAPI_PARAMETERS = polarisConfig.pAPIServiceUriParameters();
		String BASE_PARAMETERS = "/PAPIService/REST";
		this.PUBLIC_PARAMETERS = BASE_PARAMETERS + "/public" + PAPI_PARAMETERS;
		this.PROTECTED_PARAMETERS = BASE_PARAMETERS + "/protected" + PAPI_PARAMETERS;
		this.conversionService = conversionService;
	}

	/*
	Public endpoints
	*/
	@SingleResult
	public Mono<Patron> patronValidate(String barcode, String password) {
		final var path = createPath(PUBLIC_PARAMETERS, "patron", barcode);

		if (barcode == null || password == null) {
			throw new IllegalArgumentException(
				"Cannot validate a patron with barcode: "+barcode+" and password: "+password);
		}

		final var patronCredentials = PatronCredentials.builder()
			.barcode(barcode)
			.password(password)
			.build();

		return createRequest(GET, path, uri -> {})
			.flatMap( req -> authFilter.ensurePatronAuth(req, patronCredentials, FALSE) )
			.flatMap(request -> client.retrieve(request, Argument.of(PatronValidateResult.class)))
			.filter(PatronValidateResult::getValidPatron)
			.map(patronValidateResult -> Patron.builder()
				.localId(singletonList(valueOf(patronValidateResult.getPatronID())))
				.localPatronType(valueOf(patronValidateResult.getPatronCodeID()))
				.localBarcodes(singletonList(patronValidateResult.getBarcode()))
				.localHomeLibraryCode(valueOf(patronValidateResult.getAssignedBranchID()))
				.hostLmsCode(lms.getCode())
				.build())
			.flatMap(this::resolveHomeLibraryCode);
	}

	/**
 	 * If this Patron carries a valid .localHomeLibraryCode see if we can resolve it into an agency
	 */
	private Mono<Patron> resolveHomeLibraryCode(Patron patron) {
		log.debug("resolveHomeLibraryCode({})",patron);
		return Mono.just(patron);
	}

	public Mono<PatronRegistrationCreateResult> patronRegistrationCreate(Patron patron) {
		log.info("patronRegistrationCreate {}", patron);

		final var path = createPath(PUBLIC_PARAMETERS, "patron");
		final PatronRegistration body = getPatronRegistration(patron);

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(body))
			.doOnSuccess(req -> log.debug("patronRegistrationCreate body: {}", req.getBody()))
			// passing empty patron credentials will allow public requests without patron auth
			.flatMap(req -> authFilter.ensurePatronAuth(req, emptyCredentials(), FALSE))
			.flatMap(request -> client.retrieve(request, Argument.of(PatronRegistrationCreateResult.class)));
	}

	private PatronRegistration getPatronRegistration(Patron patron) {
		final var patronBarcodePrefix = polarisConfig.getPatronBarcodePrefix("DCB-");
		return PatronRegistration.builder()
			.logonBranchID(polarisConfig.getLogonBranchId())
			.logonUserID(polarisConfig.getLogonUserId())
			.logonWorkstationID(polarisConfig.getServicesWorkstationId())
			.patronBranchID(patron.getLocalItemLocationId())
			.nameFirst(patron.getLocalBarcodes().get(0))
			.nameLast(patron.getUniqueIds().get(0))
			.patronCode(parseInt(patron.getLocalPatronType()))
			.barcode(patronBarcodePrefix + patron.getLocalBarcodes().get(0))
			.birthdate("1999-11-01")

			// Polaris requires these fields,
			// we call the API to extract defaults
			// or fallback to empty strings if not
			.postalCode(patron.getPostalCode())
			.city(patron.getCity())
			.state(patron.getState())
			.build();
	}

	public Mono<String> patronRegistrationUpdate(String barcode, String patronType) {
		log.info("patronRegistrationUpdate {} {}", barcode, patronType);

		final var path = createPath(PUBLIC_PARAMETERS, "patron", barcode);

		final var body = PatronRegistration.builder()
			.logonBranchID(polarisConfig.getLogonBranchId())
			.logonUserID(polarisConfig.getLogonUserId())
			.logonWorkstationID(polarisConfig.getServicesWorkstationId())
			.patronCode(Integer.valueOf(patronType))
			.build();

		return client.createRequest(PUT, path)
			// passing empty patron credentials will allow public requests without patron auth
			.flatMap(req -> authFilter.ensurePatronAuth(req, emptyCredentials(), TRUE))
			.map(request -> request.body(body))
			.doOnSuccess(req -> log.debug("patronRegistrationUpdate body: {}", req.getBody()))
			.flatMap(request -> client.retrieve(request, Argument.of(PatronUpdateResult.class)))
			.doOnSuccess(patronUpdateResult -> log.debug("PatronUpdateResult: {}", patronUpdateResult))
			.map(patronUpdateResult -> barcode);
	}

	public Mono<PatronCirculationBlocksResult> getPatronCirculationBlocks(String barcode) {
		log.info("getPatronCirculationBlocks(), barcode: {}", barcode);

		final var path = createPath(PUBLIC_PARAMETERS, "patron", barcode, "circulationblocks");

		return client.createRequest(GET, path)
			// passing empty patron credentials will allow public requests without patron auth
			.flatMap(req -> authFilter.ensurePatronAuth(req, emptyCredentials(), TRUE))
			.flatMap(request -> client.retrieve(request, Argument.of(PatronCirculationBlocksResult.class)))
			.flatMap(result -> checkForPAPIErrorCode(result, CannotGetPatronBlocksProblem::new));
	}

	public Mono<ItemCheckoutResult> itemCheckoutPost(String itemBarcode, String patronBarcode) {

		final var path = createPath(PUBLIC_PARAMETERS, "patron", patronBarcode, "itemsout");

		log.info("itemCheckoutPost PatronBarcode {} itemBarcode {} path {}", patronBarcode, itemBarcode, path);

		final var body = ItemCheckoutData.builder()
			.logonBranchID(polarisConfig.getIllLocationId())
			.logonWorkstationID(polarisConfig.getServicesWorkstationId())
			.itemBarcode(itemBarcode)
			.build();

		log.debug("POLARIS itemCheckoutPost {}",body);

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(body))
			// passing empty patron credentials will allow public requests without patron auth
			.flatMap(req -> authFilter.ensurePatronAuth(req, emptyCredentials(), TRUE))
			.flatMap(request -> client.retrieve(request, Argument.of(ItemCheckoutResult.class)))
			.doOnSuccess( r -> log.debug("Result of client.retrieve itemCheckoutPost {}",r) )
			.map(this::checkForItemCheckOutError);
	}

	private ItemCheckoutResult checkForItemCheckOutError(ItemCheckoutResult itemCheckoutResult) {

		log.debug("checkForItemCheckOutError {}",itemCheckoutResult);

		// PAPI Error Codes: https://documentation.iii.com/polaris/PAPI/current/PAPIService/PAPIServiceOverview.htm#papiserviceoverview_3170935956_1221124
		if (itemCheckoutResult.getPapiErrorCode() == 0) {
			return itemCheckoutResult;
		}

		throw Problem.builder()
			.withTitle("Polaris ItemCheckoutPost failed")
			.withDetail(itemCheckoutResult.getErrorMessage() != null ? itemCheckoutResult.getErrorMessage() : "Error message was null")
			.with("itemCheckoutResult", itemCheckoutResult)
			.build();
	}

	// https://documentation.iii.com/polaris/PAPI/current/PAPIService/PAPIServiceHoldRequestCancel.htm#papiserviceholdrequestcancel_3571891891_1215927
	public Mono<String> holdRequestCancel(String patronBarcode, String requestID, Integer wsid, Integer userid) {

		final var path = createPath(PUBLIC_PARAMETERS, "patron", patronBarcode, "holdrequests", requestID, "cancelled");

		return createRequest(PUT, path, uri -> uri
			.queryParam("wsid", wsid).queryParam("userid", userid))
			.map(request -> request.body(""))
			// passing empty patron credentials will allow public requests without patron auth
			.flatMap(req -> authFilter.ensurePatronAuth(req, emptyCredentials(), TRUE))
			.flatMap(request -> client.retrieve(request, Argument.of(HoldRequestCancelResult.class)))
			.map(result -> {

				if (result.getPapiErrorCode() >= 0) {
					return requestID;
				}

				throw Problem.builder()
					.withTitle("Failed to cancel hold request")
					.withDetail(result.getErrorMessage() != null ? result.getErrorMessage()
						: "HoldRequestCancelResult error message was null")
					.with("PatronBarcode", patronBarcode)
					.with("RequestID", requestID)
					.with("userid", userid)
					.with("wsid", wsid)
					.build();
			});
	}

	/*
	Protected endpoints
	*/
	@SingleResult
	public Publisher<BibsPagedResult> synch_BibsPagedGet(String startdatemodified, Integer lastId, Integer nrecs) {
		
		return Mono.from( synch_BibsPagedGetRaw (startdatemodified, lastId, nrecs) )
      .doOnNext( jsonNode -> log.info("SYNC BIB PAGED GET RESPONSE: {}",jsonNode.toString()) )
			.map( node -> conversionService.convertRequired(node, BibsPagedResult.class))
      .doOnNext( bpr -> log.info("BPR: {}",bpr ))
      .doOnError( e -> log.error("Problem in  synch_BibsPagedGet {}",e) );
	}

	@SingleResult
	public Publisher<JsonNode> synch_BibsPagedGetRaw( BibsPagedGetParams params ) {

		String dateStr = Optional.ofNullable(params.getStartdatemodified())
					.map( inst -> inst.truncatedTo(ChronoUnit.MILLIS).toString() )
					// .map( inst -> {
          //    // Convert the instant to a string without the timezone
          //    // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
          //    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
          //    LocalDateTime localDateTime = LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
          //    return(formatter.format(localDateTime));
          // })
					.orElse(null);

    log.info("get page : {} {} {}",lms.getCode(),params, dateStr);

		// we are relying on last id here
		return synch_BibsPagedGetRaw( dateStr, params.getLastId(), params.getNrecs() );
	}

	@SingleResult
	public Publisher<JsonNode> synch_BibsPagedGetRaw(String startdatemodified, Integer lastId, Integer nrecs) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "bibs", "MARCXML", "paged");
		return createRequest(GET, path, uri -> uri
				.queryParam("startdatemodified", startdatemodified)
				.queryParam("lastid", lastId)
				.queryParam("nrecs", nrecs))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(JsonNode.class))));
	}

	// https://documentation.iii.com/polaris/PAPI/7.4/PAPIService/Synch_BibsPagedGet.htm
	@SingleResult
	public Publisher<GetBibsPagedResult> synch_GetUpdatedBibsPaged(String startdatemodified, Integer nrecs) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "bibs", "updated", "paged");
		return createRequest(GET, path, uri -> uri
			.queryParam("updatedate", startdatemodified)
			.queryParam("nrecs", nrecs))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(GetBibsPagedResult.class))))
			.doOnNext(result -> log.info("Result of synch_GetUpdatedBibsPaged {}",result));
	}

	// https://documentation.iii.com/polaris/PAPI/7.4/PAPIService/Synch_BibsByIDGet.htm#papiservicesynchdiscovery_454418000_1271378
	@SingleResult
	public Publisher<JsonNode> synch_BibsByIDGetRaw(String bibids) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "bibs", "MARCXML");
		return createRequest(GET, path, uri -> uri
			.queryParam("bibids", bibids))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(JsonNode.class))));
	}

	public Mono<List<ItemGetRow>> synch_ItemGetByBibID(String localBibId) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "items", "bibid", localBibId);

		return createRequest(GET, path, uri -> uri.queryParam("excludeecontent", false))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(ItemGetResponse.class))))
			.map(ItemGetResponse::getItemGetRows);
	}

	/**
	 * Search for a patron in Polaris
	 * @param barcode barcode to search for in patron's first, middle, last name (PATNF) field
	 * Refer to <a href="https://documentation.iii.com/polaris/PAPI/current/PAPIService/PAPIServicePatronSearch.htm#papiservicepatronsearch_3172958390_1270206">patron search docs</a>
	 * for more information
	 */
	public Mono<PatronSearchRow> patronSearch(String barcode) {

		final var path = createPath(PROTECTED_PARAMETERS, "search", "patrons", "boolean");
		final var ccl = "PATNF=" + barcode;

		log.debug("Using ccl: {} to search for virtual patron.", ccl);

		AtomicInteger retryCount = new AtomicInteger(0);
		final var findDelay = polarisConfig.getHoldFetchingDelay(5);
		final var maxRetry = polarisConfig.getMaxHoldFetchingRetry(10);

		return makePatronSearchRequest(path, ccl, findDelay)
			.retryWhen(Retry.max(maxRetry + 1)
				.filter(throwable -> throwable instanceof FindVirtualPatronException && retryCount.get() < maxRetry)
				.doBeforeRetry(retrySignal -> log.debug("Fetch virtual patron retry: {}", retryCount.incrementAndGet())));
	}

	private Mono<PatronSearchRow> makePatronSearchRequest(String path, String ccl, Integer findDelay) {
		return createRequest(GET, path, uri -> uri.queryParam("q", ccl))
			.flatMap(authFilter::ensureStaffAuth)
			.delayElement(Duration.ofSeconds(findDelay))
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(PatronSearchResult.class))))
			.flatMap(result -> checkForPAPIErrorCode(result, PAPIClient::toFindVirtualPatronException))
			.map(checkForUniquePatronResult(path, ccl));
	}

	private Function<PatronSearchResult, PatronSearchRow> checkForUniquePatronResult(String path, String ccl) {

		return patronSearchResult -> {

			final var patronSearchRows = getValueOrNull(patronSearchResult, PatronSearchResult::getPatronSearchRows);
			final var rowSize = (patronSearchRows != null) ? patronSearchRows.size() : 0;

			if (rowSize < 1) {
				log.warn("No virtual Patron found.");

				throw VirtualPatronNotFound.builder()
					.withDetail(rowSize + " records found")
					.with("path", path)
					.with("ccl", ccl)
					.with("Response", patronSearchResult)
					.build();
			}

			if (rowSize > 1) {
				log.error("More than one virtual patron found: {}", patronSearchResult);

				throw MultipleVirtualPatronsFound.builder()
					.withDetail(rowSize + " records found")
					.with("path", path)
					.with("ccl", ccl)
					.with("Response", patronSearchResult)
					.build();
			}

			// Return the PatronSearchRow
			log.info("checkForUniquePatronResult passed: {}", patronSearchResult);
			return patronSearchResult.getPatronSearchRows().get(0);
		};
	}

	private static FindVirtualPatronException toFindVirtualPatronException(
		Integer code, String message) {

		return new FindVirtualPatronException(
			"PAPIService returned [%d], with message: %s".formatted(code, message));
	}

	private Mono<MutableHttpRequest<?>> createRequest(HttpMethod httpMethod, String path,
		Consumer<UriBuilder> uriBuilderConsumer) {

		return client.createRequest(httpMethod,path)
			.map(req -> req.uri(uriBuilderConsumer));
	}

	private String createPath(Object... pathSegments) {
		return Arrays.stream(pathSegments).map(Object::toString).collect(Collectors.joining("/"));
	}

	private <T extends PapiResult> Mono<T> checkForPAPIErrorCode(T result,
		BiFunction<Integer, String, Throwable> toThrowableMapper) {

		final var errorCode = getValue(result, PapiResult::getPapiErrorCode, 0);
		final var errorMessage = getValueOrNull(result, PapiResult::getErrorMessage);

		// Any positive number: Represents either the count of rows returned or the number of rows affected by the procedure.
		// See individual procedure definitions for details.
		// ref: https://documentation.iii.com/polaris/PAPI/current/PAPIService/PAPIServiceOverview.htm#papiserviceoverview_3170935956_1210888
		// 0 = Success
		if (errorCode < 0) {
			// we assume (-1, general failure) translates to no results found
			final var generalFailureCode = -1;

			if (errorCode == generalFailureCode) {
				log.info("PAPIService returned 'General Failure' for the virtual patron search: {}, {}",
					errorCode, errorMessage);

				return Mono.just(result);
			}

			log.error("PAPIService returned error code: {}, with message: '{}'", errorCode, errorMessage);

			return Mono.error(toThrowableMapper.apply(errorCode, errorMessage));
		}

		return Mono.just(result);
	}

	private static PatronCredentials emptyCredentials() {
		return PatronCredentials.builder().build();
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class GetBibsPagedResult {
		@JsonProperty("BibIDListRows")
		private List<BibIDListRow> BibIDListRows;
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class BibIDListRow {
		@JsonProperty("BibliographicRecordID")
		private Integer BibliographicRecordID;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class HoldRequestCancelResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class ItemCheckoutData {
		@JsonProperty("ItemBarcode")
		private String itemBarcode;
		@JsonProperty("LogonBranchID")
		private Integer logonBranchID;
		@JsonProperty("LogonUserID")
		private Integer logonUserID;
		@JsonProperty("LogonWorkstationID")
		private Integer logonWorkstationID;
	}

	interface PapiResult {
		Integer getPapiErrorCode();
		String getErrorMessage();
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class ItemCheckoutResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
		@JsonProperty("ItemRecordID")
		private String itemRecordID;
		@JsonProperty("IsRenewal")
		private Boolean isRenewal;
		@JsonProperty("DueDate")
		private String dueDate;
		@JsonProperty("PatronBlockFlags")
		private Integer patronBlockFlags;
		@JsonProperty("ItemBlockFlags")
		private Integer itemBlockFlags;
		@JsonProperty("RenewalBlockFlags")
		private Integer renewalBlockFlags;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronUpdateResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class PatronRegistrationCreateResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
		@JsonProperty("Barcode")
		private String barcode;
		@JsonProperty("PatronID")
		private Integer patronID;
		@JsonProperty("StatisticalClassID")
		private Integer statisticalClassID;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class PatronCirculationBlocksResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
		@JsonProperty("CanPatronCirculate")
		private Boolean canPatronCirculate;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class PatronRegistration {
		@JsonProperty("LogonBranchID")
		private Integer logonBranchID;
		@JsonProperty("LogonUserID")
		private Integer logonUserID;
		@JsonProperty("LogonWorkstationID")
		private Integer logonWorkstationID;
		@JsonProperty("PatronBranchID")
		private Integer patronBranchID;
		@JsonProperty("PostalCode")
		private String postalCode;
		@JsonProperty("ZipPlusFour")
		private String zipPlusFour;
		@JsonProperty("City")
		private String city;
		@JsonProperty("State")
		private String state;
		@JsonProperty("County")
		private String county;
		@JsonProperty("CountryID")
		private Integer countryID;
		@JsonProperty("StreetOne")
		private String streetOne;
		@JsonProperty("StreetTwo")
		private String streetTwo;
		@JsonProperty("StreetThree")
		private String streetThree;
		@JsonProperty("NameFirst")
		private String nameFirst;
		@JsonProperty("NameLast")
		private String nameLast;
		@JsonProperty("NameMiddle")
		private String nameMiddle;
		@JsonProperty("User1")
		private String user1;
		@JsonProperty("User2")
		private String user2;
		@JsonProperty("User3")
		private String user3;
		@JsonProperty("User4")
		private String user4;
		@JsonProperty("User5")
		private String user5;
		@JsonProperty("Gender")
		private String gender;
		@JsonProperty("Birthdate")
		private String birthdate;
		@JsonProperty("PhoneVoice1")
		private String phoneVoice1;
		@JsonProperty("PhoneVoice2")
		private String phoneVoice2;
		@JsonProperty("PhoneVoice3")
		private String phoneVoice3;
		@JsonProperty("Phone1CarrierID")
		private Integer phone1CarrierID;
		@JsonProperty("Phone2CarrierID")
		private Integer phone2CarrierID;
		@JsonProperty("Phone3CarrierID")
		private Integer phone3CarrierID;
		@JsonProperty("EmailAddress")
		private String emailAddress;
		@JsonProperty("AltEmailAddress")
		private String altEmailAddress;
		@JsonProperty("LanguageID")
		private Integer languageID;
		@JsonProperty("UserName")
		private String userName;
		@JsonProperty("Password")
		private String password;
		@JsonProperty("Password2")
		private String password2;
		@JsonProperty("DeliveryOptionID")
		private Integer deliveryOptionID;
		@JsonProperty("EnableSMS")
		private Boolean enableSMS;
		@JsonProperty("TxtPhoneNumber")
		private Integer txtPhoneNumber;
		@JsonProperty("Barcode")
		private String barcode;
		@JsonProperty("EReceiptOptionID")
		private Integer eReceiptOptionID;
		@JsonProperty("PatronCode")
		private Integer patronCode;
		@JsonProperty("ExpirationDate")
		private String expirationDate;
		@JsonProperty("AddrCheckDate")
		private String addrCheckDate;
		@JsonProperty("GenderID")
		private Integer genderID;
		@JsonProperty("LegalNameFirst")
		private String legalNameFirst;
		@JsonProperty("LegalNameLast")
		private String legalNameLast;
		@JsonProperty("LegalNameMiddle")
		private String legalNameMiddle;
		@JsonProperty("UseLegalNameOnNotices")
		private Boolean useLegalNameOnNotices;
		@JsonProperty("RequestPickupBranchID")
		private Integer requestPickupBranchID;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronSearchResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String ErrorMessage;
		@JsonProperty("WordList")
		private String WordList;
		@JsonProperty("TotalRecordsFound")
		private Integer TotalRecordsFound;
		@JsonProperty("PatronSearchRows")
		private List<PatronSearchRow> PatronSearchRows;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	public static class PatronSearchRow {
		@JsonProperty("PatronID")
		private Integer PatronID;
		@JsonProperty("Barcode")
		private String Barcode;
		@JsonProperty("OrganizationID")
		private Integer OrganizationID;
		@JsonProperty("PatronFirstLastName")
		private String PatronFirstLastName;
	}

		@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronCredentials {
		@JsonProperty("Barcode")
		private String barcode;
		@JsonProperty("Password")
		private String password;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class ItemGetResponse implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String ErrorMessage;
		@JsonProperty("ItemGetRows")
		private List<ItemGetRow> ItemGetRows;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	// public for ItemResultToItemMapper
	public static class ItemGetRow {
		@JsonProperty("LocationID")
		private Integer LocationID;
		@JsonProperty("LocationName")
		private String LocationName;
		@JsonProperty("CollectionID")
		private Integer CollectionID;
		@JsonProperty("CollectionName")
		private String CollectionName;
		@JsonProperty("Barcode")
		private String Barcode;
		@JsonProperty("PublicNote")
		private String PublicNote;
		@JsonProperty("CallNumber")
		private String CallNumber;
		@JsonProperty("Designation")
		private String Designation;
		@JsonProperty("VolumeNumber")
		private String VolumeNumber;
		@JsonProperty("ShelfLocation")
		private String ShelfLocation;

		// believed to be the item status 'description'
		@JsonProperty("CircStatus")
		private String CircStatus;
		@JsonProperty("CircStatusID")
		private Integer circStatusID;
		@JsonProperty("CircStatusName")
		private String circStatusName;
		@JsonProperty("CircStatusBanner")
		private String circStatusBanner;

		@JsonProperty("LastCircDate")
		private String LastCircDate;
		@JsonProperty("MaterialTypeID")
		private String MaterialTypeID;
		@JsonProperty("MaterialType")
		private String MaterialType;
		@JsonProperty("TextualHoldingsNote")
		private String TextualHoldingsNote;
		@JsonProperty("RetentionStatement")
		private String RetentionStatement;
		@JsonProperty("HoldingsStatement")
		private String HoldingsStatement;
		@JsonProperty("HoldingsNote")
		private String HoldingsNote;
		@JsonProperty("Holdable")
		private Boolean Holdable;
		@JsonProperty("DueDate")
		private String DueDate;
		@JsonProperty("ItemRecordID")
		private Integer ItemRecordID;
		@JsonProperty("BibliographicRecordID")
		private Integer BibliographicRecordID;
		@JsonProperty("IsDisplayInPAC")
		private Boolean IsDisplayInPAC;
		@JsonProperty("CreationDate")
		private String CreationDate;
		@JsonProperty("FirstAvailableDate")
		private String FirstAvailableDate;
		@JsonProperty("ModificationDate")
		@JsonInclude(JsonInclude.Include.NON_NULL)
		private String ModificationDate;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	private static class PatronValidateResult implements PapiResult {
		@JsonProperty("PAPIErrorCode")
		private Integer papiErrorCode;
		@JsonProperty("ErrorMessage")
		private String ErrorMessage;
		@JsonProperty("Barcode")
		private String Barcode;
		@JsonProperty("ValidPatron")
		private Boolean ValidPatron;
		@JsonProperty("PatronID")
		private Integer PatronID;
		@JsonProperty("PatronCodeID")
		private Integer PatronCodeID;
		@JsonProperty("AssignedBranchID")
		private Integer AssignedBranchID;
		@JsonProperty("PatronBarcode")
		private String PatronBarcode;
		@JsonProperty("AssignedBranchName")
		private String AssignedBranchName;
		@JsonProperty("ExpirationDate")
		private String ExpirationDate;
		@JsonProperty("OverridePasswordUsed")
		private Boolean OverridePasswordUsed;
	}
}
