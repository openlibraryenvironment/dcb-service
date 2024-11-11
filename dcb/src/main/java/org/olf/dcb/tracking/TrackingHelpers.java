package org.olf.dcb.tracking;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronRequest.Status;

import static java.util.Arrays.asList;

@Slf4j
public class TrackingHelpers {

	private static final String DEV_PROFILE = "DEVELOPMENT";
	private static final String PROD_PROFILE = "PRODUCTION";
	private static final List<String> RECOGNISED_PROFILES = asList(DEV_PROFILE, PROD_PROFILE);
	private static final String DEFAULT_PROFILE = PROD_PROFILE;

	/**
	 * Retrieves the appropriate duration for the given patron request status and circulation tracking profile.
	 *
	 * @param prStatus                   the status of the patron request
	 * @param circulationTrackingProfile the circulation tracking profile, defaults to PRODUCTION if not set
	 * @return the duration for the given status and profile
	 */
	public static Optional<Duration> getDurationFor(Status prStatus, String circulationTrackingProfile) {
		final String profile = getNormalisedProfile(circulationTrackingProfile);

		return switch (profile) {
			case DEV_PROFILE -> getDevDurationFor(prStatus);
			case PROD_PROFILE -> getLiveDurationFor(prStatus);
			default -> getLiveDurationFor(prStatus);
		};
	}

	private static String getNormalisedProfile(String circulationTrackingProfile) {
		if (circulationTrackingProfile == null) {
			log.warn("No circulation tracking profile is defined. " +
				"Set the circulation tracking profile to 'PRODUCTION' or 'DEVELOPMENT'.");
			return DEFAULT_PROFILE;
		}

		final String normalisedProfile = circulationTrackingProfile.toUpperCase();
		if (!RECOGNISED_PROFILES.contains(normalisedProfile)) {
			log.warn("Circulation tracking profile '{}' is not recognised. " +
				"Set the circulation tracking profile to 'PRODUCTION' or 'DEVELOPMENT'.", normalisedProfile);
			return DEFAULT_PROFILE;
		}
		return normalisedProfile;
	}

	public static Optional<Duration> getDevDurationFor(Status pr_status) {
		Duration result = switch ( pr_status ) {
			case SUBMITTED_TO_DCB -> null;
			case PATRON_VERIFIED -> null;
			case RESOLVED -> null;
			case NOT_SUPPLIED_CURRENT_SUPPLIER -> null;
			case NO_ITEMS_SELECTABLE_AT_ANY_AGENCY -> null;
			// We want to poll in real time for confirmed - in part this is due to tests 1m would be OK
			case REQUEST_PLACED_AT_SUPPLYING_AGENCY -> Duration.ofMillis(10);
			case CONFIRMED -> Duration.ofMinutes(10);
			case REQUEST_PLACED_AT_BORROWING_AGENCY -> Duration.ofMinutes(10);
			case RECEIVED_AT_PICKUP -> Duration.ofMinutes(10);
			case READY_FOR_PICKUP -> Duration.ofMinutes(10);
			case LOANED -> Duration.ofMinutes(10);
			case PICKUP_TRANSIT -> Duration.ofMinutes(10);
			case RETURN_TRANSIT -> Duration.ofMinutes(10);
			case CANCELLED -> null;
			case COMPLETED -> null;
			case FINALISED -> null;
			case ERROR -> null;
			default -> null;
		};
		return Optional.ofNullable(result);
	}

	public static Optional<Duration> getLiveDurationFor(Status pr_status) {
		Duration result = switch ( pr_status ) {
			case SUBMITTED_TO_DCB -> null;
			case PATRON_VERIFIED -> null;
			case RESOLVED -> null;
			case NOT_SUPPLIED_CURRENT_SUPPLIER -> null;
			case NO_ITEMS_SELECTABLE_AT_ANY_AGENCY -> null;
			// We want to poll in real time for confirmed - in part this is due to tests 1m would be OK
			case REQUEST_PLACED_AT_SUPPLYING_AGENCY -> Duration.ofSeconds(0);
			case CONFIRMED -> Duration.ofMinutes(10);
			case REQUEST_PLACED_AT_BORROWING_AGENCY -> Duration.ofHours(1);
			case RECEIVED_AT_PICKUP -> Duration.ofHours(1);
			case READY_FOR_PICKUP -> Duration.ofHours(1);
			case LOANED -> Duration.ofHours(6);
			case PICKUP_TRANSIT -> Duration.ofHours(1);
			case RETURN_TRANSIT -> Duration.ofHours(1);
			case CANCELLED -> null;
			case COMPLETED -> null;
			case FINALISED -> null;
			case ERROR -> null;
			default -> null;
		};
		return Optional.ofNullable(result);
	}
}
