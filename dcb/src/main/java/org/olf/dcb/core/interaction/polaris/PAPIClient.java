package org.olf.dcb.core.interaction.polaris;

import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.LOGON_BRANCH_ID;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.LOGON_USER_ID;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.PATRON_BARCODE_PREFIX;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.SERVICES_WORKSTATION_ID;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.PolarisClient.PAPIService;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.extractMapValueWithDefault;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.extractRequiredMapValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.polaris.PolarisLmsClient.BibsPagedResult;
import org.olf.dcb.core.interaction.polaris.exceptions.FindVirtualPatronException;
import org.olf.dcb.core.interaction.polaris.exceptions.ItemCheckoutException;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class PAPIClient {
	private final PolarisLmsClient client;
	private final PAPIAuthFilter authFilter;
	private final String PUBLIC_PARAMETERS;
	private final String PROTECTED_PARAMETERS;

	public PAPIClient(PolarisLmsClient client) {
		this.client = client;
		this.authFilter = new PAPIAuthFilter(client);

		// Build PAPI base parameters
		String PAPI_PARAMETERS = client.getGeneralUriParameters(PAPIService);
		String BASE_PARAMETERS = "/PAPIService/REST";
		this.PUBLIC_PARAMETERS = BASE_PARAMETERS + "/public" + PAPI_PARAMETERS;
		this.PROTECTED_PARAMETERS = BASE_PARAMETERS + "/protected" + PAPI_PARAMETERS;
	}

	/*
	Public endpoints
	*/
	@SingleResult
	public Mono<Patron> patronValidate(String barcode, String password) {
		final var path = createPath(PUBLIC_PARAMETERS, "patron", barcode);

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
				.build());
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
		final var conf = client.getConfig();
		final var servicesConfig = client.getServicesConfig();
		final var patronBarcodePrefix = extractMapValueWithDefault(
			servicesConfig, PATRON_BARCODE_PREFIX, String.class, "DCB-");

		return PatronRegistration.builder()
			.logonBranchID(extractRequiredMapValue(conf, LOGON_BRANCH_ID, Integer.class))
			.logonUserID(extractRequiredMapValue(conf, LOGON_USER_ID, Integer.class))
			.logonWorkstationID(extractRequiredMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class))
			.patronBranchID(patron.getLocalItemLocationId())
			.nameFirst(patron.getLocalBarcodes().get(0))
			.nameLast(patron.getUniqueIds().get(0))
			.userName(patron.getUniqueIds().get(0))
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
		final var conf = client.getConfig();
		final var servicesConfig = client.getServicesConfig();

		final var body = PatronRegistration.builder()
			.logonBranchID( extractRequiredMapValue(conf, LOGON_BRANCH_ID, Integer.class) )
			.logonUserID( extractRequiredMapValue(conf, LOGON_USER_ID, Integer.class) )
			.logonWorkstationID( extractRequiredMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class) )
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
		return Mono.just(PatronCirculationBlocksResult.builder().build());
	}

	public Mono<ItemCheckoutResult> itemCheckoutPost(String itemBarcode, String patronBarcode) {

		final var path = createPath(PUBLIC_PARAMETERS, "patron", patronBarcode, "itemsout");
		final var servicesConfig = client.getServicesConfig();

		log.info("itemCheckoutPost PatronBarcode {} itemBarcode {} path {}", patronBarcode, itemBarcode, path);

		final var body = ItemCheckoutData.builder()
			.logonBranchID(client.illLocationId())
			.logonWorkstationID(extractRequiredMapValue(servicesConfig, SERVICES_WORKSTATION_ID, Integer.class))
			.itemBarcode(itemBarcode)
			.build();

		log.debug("POLARIS itemCheckoutPost {}",body);

		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(body))
			// passing empty patron credentials will allow public requests without patron auth
			.flatMap(req -> authFilter.ensurePatronAuth(req, emptyCredentials(), TRUE))
			.flatMap(request -> client.retrieve(request, Argument.of(ItemCheckoutResult.class)))
			.doOnSuccess( r -> log.debug("Result of client.retrieve itemCheckoutPost {}",r) )
			.flatMap(this::checkForItemCheckOutError);
	}

	private Mono<ItemCheckoutResult> checkForItemCheckOutError(ItemCheckoutResult itemCheckoutResult) {

		log.debug("checkForItemCheckOutError {}",itemCheckoutResult);

		return Mono.just(itemCheckoutResult)
			// PAPI Error Codes: https://documentation.iii.com/polaris/PAPI/current/PAPIService/PAPIServiceOverview.htm#papiserviceoverview_3170935956_1221124
			.filter(result -> result.getPapiErrorCode() == 0)
			.switchIfEmpty(Mono.error(() -> new ItemCheckoutException(
				"Checkout of local item id: " + itemCheckoutResult.getItemRecordID()
				+ ", failed with error message: '" + itemCheckoutResult.getErrorMessage() + "'")));
	}

	/*
	Protected endpoints
	*/
	@SingleResult
	public Publisher<BibsPagedResult> synch_BibsPagedGet(String startdatemodified, Integer lastId, Integer nrecs) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "bibs", "MARCXML", "paged");
		return createRequest(GET, path, uri -> uri
				.queryParam("startdatemodified", startdatemodified)
				.queryParam("lastid", lastId)
				.queryParam("nrecs", nrecs))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(BibsPagedResult.class))));
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
		return makePatronSearchRequest(path, ccl);
	}

	private Mono<PatronSearchRow> makePatronSearchRequest(String path, String ccl) {
		return createRequest(GET, path, uri -> uri.queryParam("q", ccl))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(PatronSearchResult.class))))
			.flatMap(result -> checkForPAPIErrorCode(result, PAPIClient::toFindVirtualPatronException))
			.flatMap(this::checkForUniquePatronResult);
	}

	private static FindVirtualPatronException toFindVirtualPatronException(
		Integer code, String message) {

		return new FindVirtualPatronException(
			"PAPIService returned [%d], with message: %s".formatted(code, message));
	}

	private Mono<PatronSearchRow> checkForUniquePatronResult(PatronSearchResult patronSearchResult) {
		final var recordsFound = patronSearchResult.getTotalRecordsFound();

		if (recordsFound < 1) {
			log.info("No Patron found, returning an empty mono to create a new patron.");

			return Mono.empty();
		}

		if (recordsFound > 1) {
			log.error("More than one virtual patron found: {}", patronSearchResult);
			throw new FindVirtualPatronException(recordsFound + " records found for virtual patron.");
		}

		// Return the PatronSearchRow
		return Mono.just(patronSearchResult.getPatronSearchRows().get(0));
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

		final var errorCode = getValueOrDefault(result, PapiResult::getPapiErrorCode, 0);
		final var errorMessage = getValue(result, PapiResult::getErrorMessage);

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
