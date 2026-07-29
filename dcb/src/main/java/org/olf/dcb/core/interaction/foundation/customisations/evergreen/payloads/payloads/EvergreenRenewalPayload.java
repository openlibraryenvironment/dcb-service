package org.olf.dcb.core.interaction.foundation.customisations.evergreen.payloads.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class EvergreenRenewalPayload {

	/**
	 * The barcode of the item being renewed.
	 * Mapped to "copy_barcode" or "barcode" depending on specific API version,
	 * standard open-ils.circ.renew usually accepts "barcode".
	 */
	@JsonProperty("barcode")
	private String itemBarcode;

	/**
	 * The barcode of the patron who has the item checked out.
	 * Required if the auth token belongs to a staff member performing the action.
	 */
	@JsonProperty("patron_barcode")
	private String patronBarcode;

	/**
	 * Optional: If true, overrides maximum renewal limits if the user has permission.
	 * Useful for the DCB "force renewal" scenarios.
	 */
	@JsonProperty("override")
	@Builder.Default
	private boolean override = false;
}
