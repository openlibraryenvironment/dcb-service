package org.olf.dcb.core.interaction.polaris;

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
import org.olf.dcb.core.interaction.Patron;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpMethod.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.*;
import static org.olf.dcb.core.interaction.polaris.PolarisLmsClient.PolarisClient.PAPIService;

public class PAPIClient {
	static final Logger log = LoggerFactory.getLogger(PAPIClient.class);
	private final PolarisLmsClient client;
	private final PAPIAuthFilter authFilter;
	private final String PUBLIC_PARAMETERS;
	private final String PROTECTED_PARAMETERS;
	public PAPIClient(
		PolarisLmsClient client)
	{
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
		final var patronCredentials = PatronCredentials.builder().barcode(barcode).password(password).build();
		return createRequest(GET, path, uri -> {})
			.flatMap( req -> authFilter.ensurePatronAuth(req, patronCredentials, FALSE) )
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(PatronValidateResult.class))))
			.filter(PatronValidateResult::getValidPatron)
			.map(patronValidateResult -> Patron.builder()
				.localId(singletonList(valueOf(patronValidateResult.getPatronID())))
				.localPatronType(valueOf(patronValidateResult.getPatronCodeID()))
				.localBarcodes(singletonList(patronValidateResult.getBarcode()))
				.localHomeLibraryCode(valueOf(patronValidateResult.getAssignedBranchID()))
				.build());
	}

	public Mono<PatronRegistrationCreateResult> patronRegistrationCreate(Patron patron) {
		final var path = createPath(PUBLIC_PARAMETERS, "patron");
		// passing empty patron credentials will allow public requests without patron auth
		final var empty = PatronCredentials.builder().build();
		final PatronRegistration body = getPatronRegistration(patron);
		return createRequest(POST, path, uri -> {})
			.map(request -> request.body(body))
			.flatMap( req -> authFilter.ensurePatronAuth(req, empty, FALSE) )
			.flatMap(request -> client.retrieve(request, Argument.of(PatronRegistrationCreateResult.class)));
	}

	public Mono<String> patronRegistrationUpdate(String barcode, String patronType) {
		final var path = createPath(PUBLIC_PARAMETERS, "patron", barcode);
		// passing empty patron credentials will allow public requests without patron auth
		final var empty = PatronCredentials.builder().build();
		final var conf = client.getHostLms().getClientConfig();
		final var servicesMap = (Map<String, Object>) conf.get(SERVICES);
		final var body = PatronRegistration.builder()
			.logonBranchID(Integer.valueOf((String) conf.get(LOGON_BRANCH_ID)))
			.logonUserID(Integer.valueOf((String) conf.get(LOGON_USER_ID)))
			.logonWorkstationID(Integer.valueOf((String) servicesMap.get(SERVICES_WORKSTATION_ID)))
			.patronCode(Integer.valueOf(patronType))
			.build();
		return client.createRequest(PUT, path)
			.flatMap(req -> authFilter.ensurePatronAuth(req, empty, TRUE))
			.map(request -> request.body(body))
			.flatMap(request -> client.retrieve(request, Argument.of(PatronUpdateResult.class)))
			.filter(patronUpdateResult -> patronUpdateResult.getPAPIErrorCode() == 0)
			.map(patronUpdateResult -> barcode);
	}

	/*
	Protected endpoints
	*/
	@SingleResult
	public Publisher<PolarisLmsClient.BibsPagedResult> synch_BibsPagedGet(String updatedate, Integer lastId, Integer nrecs) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "bibs", "MARCXML", "paged");
		return createRequest(GET, path, uri -> uri
				.queryParam("updatedate", updatedate)
				.queryParam("lastid", lastId)
				.queryParam("nrecs", nrecs))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(PolarisLmsClient.BibsPagedResult.class))));
	}

	public Mono<List<ItemGetRow>> synch_ItemGetByBibID(String localBibId) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "items", "bibid", localBibId);

		return createRequest(GET, path, uri -> uri.queryParam("excludeecontent", false))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(ItemGetResponse.class))))
			.map(ItemGetResponse::getItemGetRows);
	}

	public Mono<PatronSearchRow> patronSearch(String barcode, String uniqueID) {
		final var path = createPath(PROTECTED_PARAMETERS, "search", "patrons", "boolean");
		final var ccl = "PATNF=" + barcode + " AND PATNL=" + uniqueID;
		return createRequest(GET, path, uri -> uri.queryParam("q", ccl))
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> Mono.from(client.retrieve(request, Argument.of(PatronSearchResult.class))))
			.filter(patronSearchResult -> patronSearchResult.getTotalRecordsFound() == 1)
			.map(PatronSearchResult::getPatronSearchRows)
			.map(patronSearchRows -> patronSearchRows.get(0));
	}

	public Mono<ItemGetRow> synch_ItemGet(String recordNumber) {
		final var path = createPath(PROTECTED_PARAMETERS, "synch", "item", recordNumber);
		return createRequest(GET, path, uri -> {})
			.flatMap(authFilter::ensureStaffAuth)
			.flatMap(request -> client.retrieve(request, Argument.of(ItemGetResponse.class)))
			.map(ItemGetResponse::getItemGetRows)
			.map(itemGetRows -> itemGetRows.get(0));
	}

	private <T> Mono<MutableHttpRequest<?>> createRequest(HttpMethod httpMethod, String path,
		Consumer<UriBuilder> uriBuilderConsumer) {
		return client.createRequest(httpMethod,path).map(req -> req.uri(uriBuilderConsumer));
	}

	private String createPath(Object... pathSegments) {
		return Arrays.stream(pathSegments).map(Object::toString).collect(Collectors.joining("/"));
	}

	private PatronRegistration getPatronRegistration(Patron patron) {
		final var conf = client.getHostLms().getClientConfig();
		final var servicesMap = (Map<String, Object>) conf.get(SERVICES);
		return PatronRegistration.builder()
			.logonBranchID(Integer.valueOf((String) conf.get(LOGON_BRANCH_ID)))
			.logonUserID(Integer.valueOf((String) conf.get(LOGON_USER_ID)))
			.logonWorkstationID(Integer.valueOf((String) servicesMap.get(SERVICES_WORKSTATION_ID)))
			.patronBranchID(Integer.valueOf(patron.getLocalHomeLibraryCode()))
			.nameFirst(patron.getLocalBarcodes().get(0))
			.nameLast(patron.getUniqueIds().get(0))
			.userName(patron.getUniqueIds().get(0))
			.birthdate("1999-11-01")
			.postalCode("63131")
			.streetOne("1412 S Spoede")
			.city("SAINT LOUIS")
			.state("MO")
			.patronCode( parseInt(patron.getLocalPatronType()) )
			.build();
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronUpdateResult {
		@JsonProperty("PAPIErrorCode")
		private Integer pAPIErrorCode;
		@JsonProperty("ErrorMessage")
		private String errorMessage;
	}

	@Builder
	@Data
	@AllArgsConstructor
	@Serdeable
	static class PatronRegistrationCreateResult {
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
	static class PatronRegistration {
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
	static class PatronSearchResult {
		@JsonProperty("PAPIErrorCode")
		private Integer PAPIErrorCode;
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
	static class PatronSearchRow {
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
	private static class ItemGetResponse {
		@JsonProperty("PAPIErrorCode")
		private Integer PAPIErrorCode;
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
		@JsonProperty("CircStatus")
		private String CircStatus;
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
	private static class PatronValidateResult {
		@JsonProperty("PAPIErrorCode")
		private Integer PAPIErrorCode;
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