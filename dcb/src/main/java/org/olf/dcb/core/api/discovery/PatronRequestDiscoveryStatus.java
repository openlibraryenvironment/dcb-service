package org.olf.dcb.core.api.discovery;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;

/** <p> OpenRS DCB statuses are crucial for librarians, but potentially confusing for patrons
 * This distils our more complex 'behind the scenes' statuses into something more readable
 * Intended for discovery services and patron views only
	* </p>
*/
@Getter
@Serdeable
public enum PatronRequestDiscoveryStatus {
	REQUEST_RECEIVED("Your request has been received and is being processed."),
	REQUEST_PLACED("Your request has now been placed successfully."),
	IN_TRANSIT("The item you requested is now on its way to the pickup library."),
	READY_FOR_PICKUP("Ready for pickup!"),
	CHECKED_OUT("Currently checked out to you."),
	COMPLETED("Returned and completed."),
	CANCELLED("This request was cancelled."),
	ERROR_SCENARIO("This request encountered an error. Please see a librarian"),
	LOCAL("The item you requested is available at your library, and we have forwarded it there. Please check your local discovery service."),
	COULD_NOT_SUPPLY("Nobody in this OpenRS consortium could supply the item you requested. Please see a librarian"),
	SEARCHING_FOR_NEW_SUPPLIER("The first supplying institution could not supply this, so OpenRS is automatically searching for another member of the consortium to fulfil this request");
	private final String friendlyDescription;

	PatronRequestDiscoveryStatus(String friendlyDescription) {
		this.friendlyDescription = friendlyDescription;
	}

}
