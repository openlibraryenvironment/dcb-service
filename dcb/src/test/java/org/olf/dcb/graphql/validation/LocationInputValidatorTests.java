package org.olf.dcb.graphql.validation;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.exceptions.EntityCreationException;
import org.olf.dcb.core.interaction.koha.KohaHostLmsClient;
import org.olf.dcb.core.model.DataHostLms;

/**
 * What a pickup location has to carry before DCB will create it.
 * <p>
 * The per-system rules exist because a pickup location is only usable if its localId
 * is something the target system can be handed as a pickup identifier. For Koha that
 * is the branch code it sends as a hold's library_id, and a location created without
 * one is accepted by DCB Admin and then silently sends the hold to the sharing library
 * instead of the branch the patron chose - a wrong shelf, not an error anyone sees.
 */
class LocationInputValidatorTests {
	@Test
	void shouldRejectAKohaPickupLocationWithNoLocalId() {
		final var error = assertThrows(EntityCreationException.class,
			() -> validate(kohaHostLms(), input -> input.remove("localId")));

		assertThat("The message has to say what to put there, not just that it is missing",
			error.getMessage(), containsString("localId is required for Koha systems"));
		assertThat(error.getMessage(), containsString("library_id"));
	}

	@Test
	void shouldRejectAKohaPickupLocationWithABlankLocalId() {
		final var error = assertThrows(EntityCreationException.class,
			() -> validate(kohaHostLms(), input -> input.put("localId", "   ")));

		assertThat(error.getMessage(), containsString("localId is required for Koha systems"));
	}

	@Test
	void shouldRejectALocalIdTooLongToBeAKohaBranchCode() {
		// The case this really guards: a UUID pasted in where a branch code belongs.
		// Koha's library_id is capped at 10 characters, so nothing that long can name a
		// branch, and the hold would be refused by Koha with nothing to point at.
		final var error = assertThrows(EntityCreationException.class,
			() -> validate(kohaHostLms(), input -> input.put("localId", randomUUID().toString())));

		assertThat(error.getMessage(), containsString("at most 10 characters for Koha systems"));
	}

	@Test
	void shouldAcceptAKohaPickupLocationWithABranchCode() {
		assertDoesNotThrow(() -> validate(kohaHostLms(), input -> input.put("localId", "BRANCH-N")));
	}

	@Test
	void shouldStillAcceptASierraPickupLocationWithNoLocalId() {
		// Sierra deliberately tolerates an absent localId - the Koha rule must not have
		// tightened anything but Koha
		assertDoesNotThrow(() -> validate(
			hostLmsFor("org.olf.dcb.core.interaction.sierra.SierraLmsClient"),
			input -> input.remove("localId")));
	}

	private void validate(DataHostLms hostLms, java.util.function.Consumer<Map<String, Object>> customise) {
		final var input = validInput();
		customise.accept(input);

		LocationInputValidator.validateInput(input, hostLms).block();
	}

	private Map<String, Object> validInput() {
		final Map<String, Object> input = new HashMap<>();

		input.put("code", "PICKUP-NORTH");
		input.put("name", "North Branch");
		input.put("type", "Pickup");
		input.put("isPickup", Boolean.TRUE);
		input.put("agencyCode", "agency");
		input.put("hostLmsCode", "koha-host-lms");
		input.put("printLabel", "North Branch");
		input.put("deliveryStops", "agency");
		input.put("latitude", "53.4");
		input.put("longitude", "-2.9");
		input.put("localId", "BRANCH-N");

		return input;
	}

	private DataHostLms kohaHostLms() {
		// The real class name, so the check cannot pass on a string that no Host LMS
		// would ever actually hold
		return hostLmsFor(KohaHostLmsClient.class.getCanonicalName());
	}

	private DataHostLms hostLmsFor(String lmsClientClass) {
		return DataHostLms.builder()
			.id(randomUUID())
			.code("host-lms")
			.lmsClientClass(lmsClientClass)
			.build();
	}
}
