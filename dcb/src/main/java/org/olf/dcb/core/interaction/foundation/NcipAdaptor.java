package org.olf.dcb.core.interaction.foundation;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.foundation.commands.FoundationCreateItemCommand;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.model.HostLms;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Prototype
public class NcipAdaptor implements ProtocolAdaptor {


	private final HttpClient client;
	private final String endpoint;
	private final String fromAgency;
	private final String toAgency;
	private final String appProfile;
	private final HostLms lms;

	public NcipAdaptor(@Parameter("hostLms") HostLms lms, HttpClient client) {
		this.client = client;
		this.lms = lms;
		Map<String, Object> config = lms.getClientConfig();
		Map<String, Object> ncipConfig = (Map<String, Object>) config.getOrDefault("ncip", Map.of());

		// Endpoint unifies with the declarative side's key (capabilities.imperative.ncip-endpoint-url),
		// falling back to the legacy ncip.endpoint sub-config.
		final var unifiedEndpoint = ImperativeCapabilityConfig.setting(lms, "ncip-endpoint-url");
		this.endpoint = unifiedEndpoint != null
			? unifiedEndpoint.toString()
			: (String) ncipConfig.getOrDefault("endpoint", "");
		// Default agency IDs if not provided in config
		this.fromAgency = (String) ncipConfig.getOrDefault("fromAgency", "DCB-CENTRAL");
		this.toAgency = (String) ncipConfig.getOrDefault("toAgency", lms.getCode());
		this.appProfile = (String) ncipConfig.getOrDefault("appProfileType", "EZBORROW");
	}

	// ========================================================================
	// 1. CREATE ITEM (NCIP: AcceptItem)
	// Used to create temporary "virtual" items in the borrower's LMS
	// ========================================================================
	@Override
	public Mono<HostLmsItem> createItem(FoundationCreateItemCommand command) {
		log.debug("NCIP AcceptItem for requestId: {}", command.getPatronRequestId());

		// Construct optional elements
		String callNumberXml = command.getCallNumber() != null
			? "<CallNumber>" + command.getCallNumber() + "</CallNumber>" : "";
		String isbnXml = command.getIsbn() != null
			? "<BibliographicItemIdentifier><BibliographicItemIdentifierCode Scheme=\"http://www.niso.org/ncip/v1_0/schemes/bibliographicitemidentifiercode/bibliographicitemidentifiercode.scm\">ISBN</BibliographicItemIdentifierCode><BibliographicItemIdentifierValue>" + command.getIsbn() + "</BibliographicItemIdentifierValue></BibliographicItemIdentifier>" : "";

		String content = """
            <RequestId>
                <RequestIdentifierValue>%s</RequestIdentifierValue>
            </RequestId>
            <RequestedActionType Scheme="http://www.niso.org/ncip/v1_0/schemes/requestedactiontype/requestedactiontype.scm">Hold For Pickup</RequestedActionType>
            <UserId>
                <UserIdentifierValue>%s</UserIdentifierValue>
            </UserId>
            <ItemId>
                <ItemIdentifierValue>%s</ItemIdentifierValue>
            </ItemId>
            <ItemOptionalFields>
                <BibliographicDescription>
                    <Author>%s</Author>
                    <Title>%s</Title>
                    %s
                </BibliographicDescription>
                <ItemDescription>
                    %s
                </ItemDescription>
            </ItemOptionalFields>
            <PickupLocation>%s</PickupLocation>
            """.formatted(
			command.getPatronRequestId(),     // RequestId
			command.getPatronId(),            // UserId (Borrower)
			command.getBarcode(),             // ItemId (The temporary barcode)
			escapeXml(command.getAuthor()),   // Author
			escapeXml(command.getTitle()),    // Title
			isbnXml,                          // ISBN (Optional)
			callNumberXml,                    // CallNumber (Optional)
			command.getLocationCode()         // PickupLocation
		);

		return send(NcipProtocol.ACCEPT_ITEM, content).map(response ->
			HostLmsItem.builder()
				.localId(command.getBarcode())
				.status(HostLmsItem.ITEM_REQUESTED)
				.build()
		);
	}

	// ========================================================================
	// 2. LOOKUP USER (NCIP: LookupUser)
	// ========================================================================
	@Override
	public Mono<Patron> findPatron(String localId) {
		// [cite: 192, 194] We request specific elements like Name, Address, Privilege
		String content = """
            <UserId>
                <UserIdentifierValue>%s</UserIdentifierValue>
            </UserId>
            <UserElementType Scheme="http://www.niso.org/ncip/v1_0/schemes/userelementtype/userelementtype.scm">Name Information</UserElementType>
            <UserElementType Scheme="http://www.niso.org/ncip/v1_0/schemes/userelementtype/userelementtype.scm">User Address Information</UserElementType>
            <UserElementType Scheme="http://www.niso.org/ncip/v1_0/schemes/userelementtype/userelementtype.scm">User Privilege</UserElementType>
            """.formatted(localId);

		return send(NcipProtocol.LOOKUP_USER, content).map(response -> {
			// Basic regex parsing (Production should use a real XML parser)
			String firstName = extractTagValue(response, "GivenName");
			String lastName = extractTagValue(response, "Surname");
			String agencyId = extractTagValue(response, "AgencyId"); // Home library

			return Patron.builder()
				.localId(List.of(localId))
				.localNames(List.of(firstName + " " + lastName))
				.localHomeLibraryCode(agencyId)
				.build();
		});
	}

	@Override
	public Mono<Patron> findPatronByBarcode(String barcode) {
		return null;
	}

	// ========================================================================
	// 3. CREATE USER (NCIP: CreateUser)
	// ========================================================================
	@Override
	public Mono<String> createPatron(Patron patron) {
		String content = """
            <UserId>
                <UserIdentifierValue>%s</UserIdentifierValue>
            </UserId>
            <UserOptionalFields>
                <NameInformation>
                    <PersonalNameInformation>
                        <StructuredPersonalName>
                            <GivenName>%s</GivenName>
                            <Surname>%s</Surname>
                        </StructuredPersonalName>
                    </PersonalNameInformation>
                </NameInformation>
                <UserPrivilege>
                     <UserPrivilegeType>Profile</UserPrivilegeType>
                     <UserPrivilegeStatus>
                        <UserPrivilegeStatusType>Active</UserPrivilegeStatusType>
                     </UserPrivilegeStatus>
                     <UserPrivilegeDescription>%s</UserPrivilegeDescription>
                </UserPrivilege>
            </UserOptionalFields>
            """.formatted(
			// Use the first local ID or generate one if strictly creating new
			patron.getLocalId().stream().findFirst().orElse(UUID.randomUUID().toString()),
			escapeXml(patron.getLocalNames().stream().findFirst().orElse("Unknown").split(" ")[0]),
			escapeXml("User"), // Placeholder for surname parsing logic
			patron.getLocalPatronType() // Profile/P-Type
		);

		return send(NcipProtocol.CREATE_USER, content).map(response -> {
			// Return the UserIdentifierValue confirmed by the server
			return extractTagValue(response, "UserIdentifierValue");
		});
	}

	// ========================================================================
	// 4. LOOKUP ITEM (NCIP: LookupItem)
	// ========================================================================
	@Override
	public Mono<HostLmsItem> getItem(String localItemId) {
		String content = """
            <ItemId>
                <ItemIdentifierValue>%s</ItemIdentifierValue>
            </ItemId>
            <ItemElementType>Bibliographic Description</ItemElementType>
            <ItemElementType>Circulation Status</ItemElementType>
            <ItemElementType>Location</ItemElementType>
            """.formatted(localItemId);

		return send(NcipProtocol.LOOKUP_ITEM, content).map(response -> {
			String status = extractTagValue(response, "CirculationStatusValue");
			// Map NCIP status to DCB Canonical Item State
			String dcbStatus = mapNcipStatus(status);

			return HostLmsItem.builder()
				.localId(localItemId)
				.status(dcbStatus)
				.barcode(localItemId)
				.build();
		});
	}

	// ========================================================================
	// 5. CHECK OUT (NCIP: CheckOutItem)
	// ========================================================================
	@Override
	public Mono<String> checkOutItem(CheckoutItemCommand command) {
		// [cite: 200]
		String content = """
            <UserId>
                <UserIdentifierValue>%s</UserIdentifierValue>
            </UserId>
            <ItemId>
                <ItemIdentifierValue>%s</ItemIdentifierValue>
            </ItemId>
            <RequestId>
                <RequestIdentifierValue>%s</RequestIdentifierValue>
            </RequestId>
            """.formatted(
			command.getPatronBarcode(),
			command.getItemBarcode(),
			command.getLocalRequestId()
		);

		return send(NcipProtocol.CHECK_OUT_ITEM, content).map(response -> {
			// Check for success or return the Due Date
			return extractTagValue(response, "DueDate");
		});
	}

	// ========================================================================
	// 6. CHECK IN (NCIP: CheckInItem)
	// ========================================================================
	public Mono<String> checkInItem(String itemBarcode) {
		// [cite: 224]
		String content = """
            <ItemId>
                <ItemIdentifierValue>%s</ItemIdentifierValue>
            </ItemId>
            """.formatted(itemBarcode);

		return send(NcipProtocol.CHECK_IN_ITEM, content).map(response -> "OK");
	}

	// ========================================================================
	// 7. PLACE HOLD (NCIP: RequestItem)
	// ========================================================================
	@Override
	public Mono<LocalRequest> placeHold(PlaceHoldRequestParameters params) {
		// RequestItem can target a Bibliographic Record OR a specific Item [cite: 86]
		String targetIdXml;
		if (params.getLocalItemId() != null) {
			targetIdXml = "<ItemId><ItemIdentifierValue>" + params.getLocalItemId() + "</ItemIdentifierValue></ItemId>";
		} else {
			targetIdXml = "<BibliographicId><BibliographicRecordId><BibliographicRecordIdentifier>" + params.getLocalBibId() + "</BibliographicRecordIdentifier></BibliographicRecordId></BibliographicId>";
		}

		String content = """
            <UserId>
                <UserIdentifierValue>%s</UserIdentifierValue>
            </UserId>
            %s
            <RequestType Scheme="http://www.niso.org/ncip/v1_0/schemes/requesttype/requesttype.scm">Hold</RequestType>
            <RequestScopeType Scheme="http://www.niso.org/ncip/v1_0/schemes/requestscopetype/requestscopetype.scm">Bibliographic Item</RequestScopeType>
            <PickupLocation>%s</PickupLocation>
            """.formatted(
			params.getLocalPatronId(),
			targetIdXml,
			params.getPickupLocationCode()
		);

		return send(NcipProtocol.REQUEST_ITEM, content).map(response -> {
			String reqId = extractTagValue(response, "RequestIdentifierValue");
			return LocalRequest.builder()
				.localId(reqId)
				.localStatus("PLACED")
				.build();
		});
	}

	// ========================================================================
	// Helpers
	// ========================================================================

	private Mono<String> send(String serviceName, String innerContent) {
		String xmlBody = buildNcipPayload(serviceName, innerContent);

		return Mono.from(client.retrieve(
			HttpRequest.POST(endpoint, xmlBody).contentType(MediaType.TEXT_XML)
		)).map(response -> {
			if (response.contains("<Problem>")) {
				String problemDetail = extractTagValue(response, "ProblemDetail");
				throw new RuntimeException("NCIP " + serviceName + " Failed: " + problemDetail);
			}
			return response;
		});
	}

	private String buildNcipPayload(String serviceName, String innerContent) {
		// [cite: 192] NCIP v2.02 envelope structure
		return """
            <?xml version="1.0" encoding="UTF-8"?>
            <NCIPMessage version="http://www.niso.org/schemas/ncip/v2_02/ncip_v2_02.xsd"
                         xmlns="http://www.niso.org/2008/ncip">
                <%s>
                    <InitiationHeader>
                        <FromAgencyId>
                            <AgencyId>%s</AgencyId>
                        </FromAgencyId>
                        <ToAgencyId>
                            <AgencyId>%s</AgencyId>
                        </ToAgencyId>
                        <ApplicationProfileType>%s</ApplicationProfileType>
                    </InitiationHeader>
                    %s
                </%s>
            </NCIPMessage>
            """.formatted(serviceName, fromAgency, toAgency, appProfile, innerContent, serviceName);
	}

	private String escapeXml(String input) {
		if (input == null) return "";
		return input.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}

	// Quick regex helper to avoid heavy XML parsing deps in this snippet
	private String extractTagValue(String xml, String tagName) {
		Pattern pattern = Pattern.compile("<" + tagName + ".*?>(.*?)</" + tagName + ">", Pattern.DOTALL);
		Matcher matcher = pattern.matcher(xml);
		return matcher.find() ? matcher.group(1).trim() : null;
	}

	private String mapNcipStatus(String ncipStatus) {
		if (ncipStatus == null) return HostLmsItem.ITEM_MISSING;
		return switch(ncipStatus) {
			case "On Shelf" -> HostLmsItem.ITEM_AVAILABLE;
			case "On Loan" -> HostLmsItem.ITEM_LOANED;
			case "Lost", "Missing" -> HostLmsItem.ITEM_MISSING;
			case "In Transit" -> HostLmsItem.ITEM_TRANSIT;
			default -> HostLmsItem.ITEM_AVAILABLE;
		};
	}

	/**
	 * Verifies that the system has NCIP enabled and is reachable.
	 * Uses 'LookupVersion' as a safe, read-only "ping".
	 */
	@Override
	public Mono<Boolean> isAvailable() {
		// Send a request asking for the version we are currently using
		String content = """
            <VersionSupported>
                <VersionScheme>http://www.niso.org/schemas/ncip/v2_02/ncip_v2_02.xsd</VersionScheme>
                <VersionValue>2.02</VersionValue>
            </VersionSupported>
            """;

		return sendRaw(NcipProtocol.LOOKUP_VERSION, content)
			.map(response -> {
				// If we get ANY valid XML response (Problem or Success), the service is active.
				// A 404 or Connection Refused would have triggered onErrorResume.
				return response.contains("LookupVersionResponse") || response.contains("Problem");
			})
			.onErrorResume(e -> {
				log.warn("NCIP Ping failed for {}: {}", lms.getCode(), e.getMessage());
				return Mono.just(false);
			});
	}

	/**
	 * Determines which NCIP services are supported.
	 * Tries 'LookupAgency' first. If that returns no data (common in Koha),
	 * returns a default set of expected services.
	 */
	public Mono<List<String>> getSupportedProtocols() {
		String content = "<AgencyId>" + toAgency + "</AgencyId>";

		return sendRaw(NcipProtocol.LOOKUP_AGENCY, content)
			.map(this::parseServicesFromLookupAgency)
			.flatMap(services -> {
				if (services.isEmpty()) {
					log.info("LookupAgency returned no services for {}. Assuming default profile.", lms.getCode());
					// Fallback: If the system is alive (which we know from isAvailable),
					// but LookupAgency is empty, assume standard ILL services are supported.
					return Mono.just(List.of(
						"LookupUser",
						"LookupItem",
						"CheckOutItem",
						"CheckInItem",
						"AcceptItem"
					));
				}
				return Mono.just(services);
			})
			.onErrorResume(e -> {
				log.warn("LookupAgency failed for {}. Returning empty capability list.", lms.getCode());
				return Mono.just(List.of());
			});
	}

	private Mono<String> sendRaw(String serviceName, String content) {
		String xmlBody = buildNcipPayload(serviceName, content);
		return Mono.from(client.retrieve(
			HttpRequest.POST(endpoint, xmlBody).contentType(MediaType.TEXT_XML)
		));
	}

	private List<String> parseServicesFromLookupAgency(String xml) {
		List<String> services = new ArrayList<>();

		// 1. Check for standard "ServiceSupport" tags (The official way)
		// <ServiceSupport>
		//    <ServiceType Scheme="...">Lookup User</ServiceType>
		// </ServiceSupport>
		// Note: Simple regex for robustness. Real impl should use XML parser.
		if (xml.contains("Lookup User") || xml.contains("LookupUser")) services.add("LookupUser");
		if (xml.contains("Check Out Item") || xml.contains("CheckOutItem")) services.add("CheckOutItem");
		if (xml.contains("Check In Item") || xml.contains("CheckInItem")) services.add("CheckInItem");
		if (xml.contains("Accept Item") || xml.contains("AcceptItem")) services.add("AcceptItem");
		if (xml.contains("Lookup Item") || xml.contains("LookupItem")) services.add("LookupItem");

		return services;
	}


	@Override public Mono<HostLmsRenewal> renew(HostLmsRenewal r) { return Mono.empty(); }
	@Override public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron p) { return Mono.empty(); }
}
