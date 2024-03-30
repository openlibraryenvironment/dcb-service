package org.olf.dcb.tracking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Duration;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;

public class TrackingHelpers {


	// See: https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/pages/2870575137/Tracking+v3+matrix
  //
	public static Optional<Duration> getDurationFor(Status pr_status) {
		Duration result = switch ( pr_status ) {
      case SUBMITTED_TO_DCB -> null;
      case PATRON_VERIFIED -> null;
      case RESOLVED -> null;
      case NOT_SUPPLIED_CURRENT_SUPPLIER -> null;
      case NO_ITEMS_AVAILABLE_AT_ANY_AGENCY -> null;
			// We want to poll in real time for confirmed - in part this is due to tests 1m would be OK
      case REQUEST_PLACED_AT_SUPPLYING_AGENCY -> Duration.ofSeconds(0);
      case CONFIRMED -> null;
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
