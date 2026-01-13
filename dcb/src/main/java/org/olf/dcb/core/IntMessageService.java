package org.olf.dcb.core;

import java.util.Map;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class IntMessageService {
	private static final Map<String,String> mappings = Map.ofEntries(
		Map.entry("UNKNOWN_PICKUP_LOCATION_CODE", "There has been an error in selecting the pickup location. Please try again or select a different pickup location. If this error persists, please notify your library."),
		Map.entry("UNKNOWN_PICKUP_LOCATION_AGENCY", "The selected pickup location is not associated with a valid agency. Please try again or select a different pickup location. If this error persists, please notify your library."),
		Map.entry("DUPLICATE_REQUEST_ATTEMPT", "It appears you already have an existing request for this resource. Please modify your request. If this message is in error, please notify your library."),
		Map.entry("PATRON_NOT_FOUND", "A borrower account could not be found using the information provided."),
		Map.entry("INVALID_PATRON_BARCODE", "The barcode provided is invalid."),
		Map.entry("PATRON_INELIGIBLE", "This borrower account is ineligible for requesting."),
		Map.entry("PATRON_BLOCKED", "This borrower account is unable to place requests at this time. Please contact your library for additional information."),
		Map.entry("PATRON_INACTIVE", "This borrower account is inactive and unable to borrow. Please contact your library for additional information."),
		Map.entry("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING", "Your library does not participate in the borrowing program. Please contact your library for additional information."),
		Map.entry("PATRON_TYPE_NOT_MAPPED", "This borrower account type is not recognized. Please contact your library for assistance."),
		Map.entry("LOCAL_PATRON_TYPE_IS_NON_NUMERIC", "This borrower account has an invalid patron type. Please contact your library for assistance."),
		Map.entry("PATRON_NOT_ASSOCIATED_WITH_AGENCY", "We are unable to determine your home library using the information provided. Please contact your library for assistance."),
		Map.entry("UNKNOWN_BORROWING_HOST_LMS", "There has been an error validating your borrower information. Please wait a few minutes and try again. If this problem persists, please contact your library for assistance."),
		Map.entry("NO_ITEM_SELECTABLE_FOR_REQUEST", "We're sorry, we were unable to locate an available item"),
		Map.entry("CLUSTER_RECORD_NOT_FOUND", "we're sorry, we were unable to find the title being requested. Please re-enter your search. If you believe you have reached this message in error, please reach out to your library for assistance."),
		Map.entry("EXCEEDS_GLOBAL_LIMIT", "Your account has exceeded the maximum global limit for consortial requests"),
		Map.entry("EXCEEDS_AGENCY_LIMIT", "Your account has exceeded the maximum global limit for requests from your institution")
	);

	public String getMessage(String code) {
		return mappings.get(code);
	}
}
