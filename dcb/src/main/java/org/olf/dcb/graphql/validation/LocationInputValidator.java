package org.olf.dcb.graphql.validation;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.model.DataHostLms;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
/** <p> This is a class for validating location input.
 * </p><br>
 * <p> This class applies the validation rules to the proposed new location.
 * It shifts the validation out of the data fetcher class to improve readability and separate concerns
 * This class could be extended and made more generic to facilitate validation for other entities also.
 * */

@Slf4j
public class LocationInputValidator {
	private static final double MIN_LATITUDE = -90.0;
	private static final double MAX_LATITUDE = 90.0;
	private static final double MIN_LONGITUDE = -180.0;
	private static final double MAX_LONGITUDE = 180.0;

	// branches.branchcode in Koha, and maxLength 10 in its own API schema for library_id
	private static final int KOHA_MAX_LIBRARY_ID_LENGTH = 10;


	public static Mono<Void> validateInput(Map<String, Object> input, DataHostLms hostLms) {
		return Mono.defer(() -> {
			// Check for required fields
			String[] requiredFields = {
				"code", "name", "type", "isPickup",
				"agencyCode", "hostLmsCode", "printLabel", "deliveryStops",
				"latitude", "longitude"
			};
			log.debug("Location for validation {}", input);


			for (String field : requiredFields) {
				if (input.get(field) == null) {
					return Mono.error(new EntityCreationException(
						String.format("Location creation failed: %s is required", field)));
				}
			}

			if (!input.get("type").toString().equalsIgnoreCase("Pickup"))
			{
				return Mono.error(new EntityCreationException("Location creation failed: the creation of non-pickup locations is not supported."));
			}
			// Validate latitude
			double latitude = Double.parseDouble(input.get("latitude").toString());
			if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
				return Mono.error(new EntityCreationException(
					"Location creation failed: latitude must be between -90 and 90"));
			}

			// Validate longitude
			double longitude = Double.parseDouble(input.get("longitude").toString());
			if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
				return Mono.error(new EntityCreationException(
					"Location creation failed: longitude must be between -180 and 180"));
			}

			// Validate localId based on lmsClientClass
			String localId = input.get("localId") != null ? input.get("localId").toString() : "";
			String lmsClientClass = hostLms.getLmsClientClass().toLowerCase();
			log.debug(lmsClientClass, localId);

			if (lmsClientClass.contains("folio")) {
				if (localId == null || localId.isBlank()) {
					return Mono.error(new EntityCreationException(
						"Location creation failed: localId is required for FOLIO systems"));
				}
				try {
					UUID parsedUuid = UUID.fromString(localId);
					log.debug("Location creation: Folio service point UUID is {}", parsedUuid);
				} catch (IllegalArgumentException e) {
					return Mono.error(new EntityCreationException(
						"Location creation failed: localId must be a valid UUID for FOLIO systems"));
				}
			} else if (lmsClientClass.contains("polaris")) {
				log.debug("Location creation: Polaris system detected with client class {} and localId {}", lmsClientClass, localId);
				if (localId == null || localId.isBlank()) {
					return Mono.error(new EntityCreationException(
						"Location creation failed: localId is required for Polaris systems"));
				}
				try {
					int id = Integer.parseInt(localId);
					if (id < 0) {
						return Mono.error(new EntityCreationException(
							"Location creation failed: localId must be a non-negative integer for Polaris systems"));
					}
				} catch (NumberFormatException e) {
					return Mono.error(new EntityCreationException(
						"Location creation failed: localId must be a valid integer for Polaris systems"));
				}
			} else if (lmsClientClass.contains("koha")) {
				// The Koha branch code, which is the only thing KohaHostLmsClient can send as
				// a hold's library_id. Without it the hold silently goes to the sharing
				// library instead of the branch the patron chose.
				log.debug("Location creation: Koha system detected with client class {} and localId {}", lmsClientClass, localId);
				if (localId.isBlank()) {
					return Mono.error(new EntityCreationException(
						"Location creation failed: localId is required for Koha systems, and must be the Koha library_id (branch code)"));
				}
				// Koha's library_id is branches.branchcode, a string of at most 10
				// characters. A longer value cannot name any branch, so reject it here
				// rather than at hold placement.
				if (localId.length() > KOHA_MAX_LIBRARY_ID_LENGTH) {
					return Mono.error(new EntityCreationException(
						"Location creation failed: localId must be at most %d characters for Koha systems, as Koha's library_id is a branch code"
							.formatted(KOHA_MAX_LIBRARY_ID_LENGTH)));
				}
			} else if (lmsClientClass.contains("sierra")) {
				log.debug("Location creation: Sierra system detected with client class {} and localId {}", lmsClientClass, localId);
				if (localId != null && !localId.isBlank()) {
					try {
						int id = Integer.parseInt(localId);
						if (id < 0) {
							return Mono.error(new EntityCreationException(
								"Location creation failed: localId must be a non-negative integer for Sierra systems"));
						}
					} catch (NumberFormatException e) {
						return Mono.error(new EntityCreationException(
							"Location creation failed: localId must be a valid integer for Sierra systems"));
					}
				}
			}
			return Mono.empty();
		});
	}
}
