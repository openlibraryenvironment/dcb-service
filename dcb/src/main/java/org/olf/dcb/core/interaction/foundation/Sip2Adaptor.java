package org.olf.dcb.core.interaction.foundation;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.foundation.ProtocolAdaptor;
import org.olf.dcb.core.interaction.foundation.commands.FoundationCreateItemCommand;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Prototype
public class Sip2Adaptor implements ProtocolAdaptor {


	private final String host;
	private final int port;
	private final String loginUser;
	private final String loginPass;
	private final String locationCode;
	private final boolean errorDetectionEnabled;

	// SIP2 Date Format: YYYYMMDDZZZZHHMMSS (ZZZZ = usually blank/spaces or timezone)
	private static final DateTimeFormatter SIP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd    HHmmss");

	public Sip2Adaptor(@Parameter("hostLms") HostLms lms) {
		Map<String, Object> config = lms.getClientConfig();
		Map<String, Object> sipConfig = (Map<String, Object>) config.getOrDefault("sip2", Map.of());

		this.host = (String) sipConfig.getOrDefault("host", "localhost");
		this.port = (Integer) sipConfig.getOrDefault("port", 6001);
		this.loginUser = (String) sipConfig.getOrDefault("login", "");
		this.loginPass = (String) sipConfig.getOrDefault("pass", "");
		this.locationCode = (String) sipConfig.getOrDefault("locationCode", "SC"); // SC = Self Check
		this.errorDetectionEnabled = (Boolean) sipConfig.getOrDefault("errorDetection", true);
	}

	// ========================================================================
	// 1. CORE CONNECTIVITY (The "Wire" Layer)
	// ========================================================================

	@Override
	public Mono<Boolean> isAvailable() {
		// Send '99' (SC Status) -> Expect '98' (ACS Status)
		return executeCommand("990001.00")
			.map(response -> response.startsWith("98"))
			.onErrorResume(e -> {
				log.warn("SIP2 Availability Check failed: {}", e.getMessage());
				return Mono.just(false);
			});
	}

	/**
	 * The SIP2 TCP transport is not yet wired on this branch. The message
	 * vocabulary in this class is retained as groundwork; the actual transport
	 * (connect -> login 93/94 -> command -> response) will be delivered as its
	 * own protocol slice. Until then every SIP2 operation fails fast rather than
	 * pretending to work.
	 */
	private Mono<String> executeCommand(String commandMsg) {
		return Mono.error(new UnsupportedOperationException(
			"SIP2 transport is not yet implemented for FoundationClient"));
	}

	// ========================================================================
	// 2. CIRCULATION ACTIONS
	// ========================================================================

	@Override
	public Mono<String> checkOutItem(CheckoutItemCommand command) {
		// Msg 11: Checkout
		// 11<SC><NoBlock><Date><...fields...>
		// AA: Patron Barcode, AB: Item Barcode
		String msg = new StringBuilder("11")
			.append("Y") // SC Renewal Policy (Y=Yes)
			.append("N") // No Block
			.append(now())
			.append("AO").append(locationCode).append("|")
			.append("AA").append(command.getPatronBarcode()).append("|")
			.append("AB").append(command.getItemBarcode()).append("|")
			.append("AC").append(loginPass).append("|") // Optional terminal password
			.toString();

		return executeCommand(msg).map(response -> {
			// Response 12: Checkout Response
			// 12<1/0 OK/Fail><NoBlock><Date>...
			if (response.startsWith("121")) {
				return "OK"; // Success
			}

			// Extract Screen Message (AF) for error detail
			String screenMsg = extractField(response, "AF");
			throw new RuntimeException("SIP2 Checkout Failed: " + (screenMsg != null ? screenMsg : "Unknown Error"));
		});
	}

	public Mono<String> checkInItem(String itemBarcode) {
		// Msg 09: Checkin
		// 09<NoBlock><Date>...
		String msg = new StringBuilder("09")
			.append("N")
			.append(now())
			.append("AP").append(locationCode).append("|")
			.append("AB").append(itemBarcode).append("|")
			.append("AC").append(loginPass).append("|")
			.toString();

		return executeCommand(msg).map(response -> {
			// Response 10: Checkin Response
			if (response.startsWith("101")) {
				return "OK";
			}
			String screenMsg = extractField(response, "AF");
			throw new RuntimeException("SIP2 Checkin Failed: " + screenMsg);
		});
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal renewal) {
		// Msg 29: Renew
		String msg = new StringBuilder("29")
			.append("Y") // Third party allowed
			.append("N") // No block
			.append(now())
			.append("AO").append(locationCode).append("|")
			.append("AA").append(renewal.getLocalPatronBarcode()).append("|")
			.append("AB").append(renewal.getLocalItemBarcode()).append("|")
			.toString();

		return executeCommand(msg).map(response -> {
			// Response 30: Renew Response
			if (response.startsWith("301")) {
				return renewal;
			}
			throw new RuntimeException("SIP2 Renewal Failed: " + extractField(response, "AF"));
		});
	}

	// ========================================================================
	// 3. PATRON ACTIONS
	// ========================================================================

	@Override
	public Mono<Patron> findPatron(String localId) {
		// Msg 63: Patron Information
		String msg = new StringBuilder("63")
			.append("000") // Language (000=Unknown)
			.append(now())
			.append("      ") // Summary fields (blank requests all)
			.append("AO").append(locationCode).append("|")
			.append("AA").append(localId).append("|")
			.toString();

		return executeCommand(msg).map(response -> {
			// Response 64: Patron Information Response
			// 64<PatronStatus><Language><TransactionDate>...

			String name = extractField(response, "AE"); // AE = Personal Name
			String homeAddress = extractField(response, "BD"); // BD = Home Address

			// Note: SIP2 often requires additional parsing for home library,
			// usually embedded in the patron record or screen message.

			return Patron.builder()
				.localId(List.of(localId))
				.localNames(List.of(name != null ? name : "Unknown"))
				.build();
		});
	}

	@Override
	public Mono<Patron> findPatronByBarcode(String barcode) {
		return null;
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		// ** NOT SUPPORTED **
		// SIP2 is a circulation protocol, not an administrative one.
		// Standard SIP2 cannot create users.
		return Mono.error(new UnsupportedOperationException(
			"SIP2 does not support Create Patron. Please configure a custom override strategy (e.g. REST API) for this Host LMS."));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		// Fallback to barcode lookup
		return findPatron(patron.determineHomeIdentityBarcode());
	}

	// ========================================================================
	// 4. ITEM ACTIONS
	// ========================================================================

	@Override
	public Mono<HostLmsItem> getItem(String localItemId) {
		// Msg 17: Item Information
		String msg = new StringBuilder("17")
			.append(now())
			.append("AO").append(locationCode).append("|")
			.append("AB").append(localItemId).append("|")
			.toString();

		return executeCommand(msg).map(response -> {
			// Response 18: Item Information Response
			// Format: 18<CircStatus><Security><Fee><Date>...

			if (!response.startsWith("18")) {
				log.error("SIP2 Unexpected response to Item Info: {}", response);
				return HostLmsItem.builder()
					.localId(localItemId)
					.status(HostLmsItem.ITEM_MISSING)
					.build();
			}

			// 1. Parse Status using the fixed-position bytes
			String status = parseSip2Status(response);

			// 2. Parse Title (Variable field 'AJ')
			String title = extractField(response, "AJ");

			return HostLmsItem.builder()
				.localId(localItemId)
				.barcode(localItemId)
				.status(status)
				.build();
		});
	}

	@Override
	public Mono<HostLmsItem> createItem(FoundationCreateItemCommand command) {
		// ** NOT SUPPORTED **
		// SIP2 cannot create temporary/virtual items.
		return Mono.error(new UnsupportedOperationException(
			"SIP2 does not support Create Item. Please configure a custom override strategy for this Host LMS."));
	}

	// ========================================================================
	// 5. REQUEST ACTIONS (Place Hold)
	// ========================================================================

	@Override
	public Mono<LocalRequest> placeHold(PlaceHoldRequestParameters params) {
		// Msg 15: Hold
		String msg = new StringBuilder("15")
			.append(now())
			.append("BW").append(now()) // Expiration Date (Default to today/immediate)
			.append("AO").append(locationCode).append("|")
			.append("AA").append(params.getLocalPatronId()).append("|")
			.append("AB").append(params.getLocalItemId()).append("|") // Item Barcode (Target)
			// Note: If placing bib hold, use "AJ" (Title) or "MA" (Bib Id) if supported by vendor extension
			.toString();

		return executeCommand(msg).map(response -> {
			// Response 16: Hold Response
			if (response.startsWith("161")) {
				return LocalRequest.builder()
					.localStatus("PLACED")
					.build();
			}
			throw new RuntimeException("SIP2 Place Hold Failed: " + extractField(response, "AF"));
		});
	}


	// ========================================================================
	// HELPERS
	// ========================================================================

	private String now() {
		return LocalDateTime.now().format(SIP_DATE_FMT);
	}

	private String applyChecksum(String msg) {
		msg += "AY"; // Sequence number placeholder (usually ignored in stateless/short connections)
		msg += "0";  // Sequence value

		int sum = 0;
		for (char c : msg.toCharArray()) {
			sum += c;
		}

		// Checksum algorithm: -(sum) & 0xFFFF
		int checksum = (-sum) & 0xFFFF;
		String hex = Integer.toHexString(checksum).toUpperCase();

		// Pad to 4 chars
		while (hex.length() < 4) hex = "0" + hex;

		return msg + "AZ" + hex + "\r";
	}

	private String cleanResponse(String raw) {
		// Remove trailing CR/LF and Checksum (AZxxxx)
		String cleaned = raw.trim();
		if (cleaned.contains("AZ")) {
			return cleaned.substring(0, cleaned.indexOf("AZ"));
		}
		return cleaned;
	}

	/**
	 * Extracts a variable-length field like "AB12345|" -> returns "12345"
	 */
	private String extractField(String response, String tag) {
		String search = tag;
		int start = response.indexOf(search);
		if (start == -1) return null;

		// Move past tag
		start += tag.length();

		int end = response.indexOf('|', start);
		if (end == -1) return response.substring(start);

		return response.substring(start, end);
	}

	/**
	 * Parses the 2-character fixed-field Circulation Status from a SIP2 Message 18 response.
	 * Position 2 and 3 (0-indexed) contain the status.
	 */
	private String parseSip2Status(String response) {
		if (response == null || response.length() < 4) {
			return HostLmsItem.ITEM_MISSING;
		}

		// Extract the 2-char status immediately following "18"
		String code = response.substring(2, 4);

		return switch (code) {
			case "02" -> HostLmsItem.ITEM_REQUESTED;    // On Order
			case "03" -> HostLmsItem.ITEM_AVAILABLE;    // Available
			case "04" -> HostLmsItem.ITEM_LOANED;       // Charged
			case "05" -> HostLmsItem.ITEM_LOANED;       // Charged; not to be recalled
			case "06" -> HostLmsItem.ITEM_MISSING;      // In Process
			case "07" -> HostLmsItem.ITEM_LOANED;       // Recalled
			case "08" -> HostLmsItem.ITEM_ON_HOLDSHELF; // Waiting on Hold Shelf
			case "09" -> HostLmsItem.ITEM_RETURNED;     // Waiting to be re-shelved
			case "10" -> HostLmsItem.ITEM_TRANSIT;      // In Transit
			case "11" -> HostLmsItem.ITEM_MISSING;      // Claimed Returned
			case "12" -> HostLmsItem.ITEM_MISSING;      // Lost
			case "13" -> HostLmsItem.ITEM_MISSING;      // Missing
			default   -> HostLmsItem.ITEM_MISSING;      // 01=Other or unknown
		};
	}
}
