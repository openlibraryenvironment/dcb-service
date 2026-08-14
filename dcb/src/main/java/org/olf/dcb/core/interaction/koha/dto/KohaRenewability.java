package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Koha's own answer to "can this loan be renewed, and if not why not", from
 * GET /api/v1/checkouts/{checkout_id}/allows_renewal.
 * <p>
 * This is the only reliable way to know whether renewal prevention worked: renewal eligibility
 * depends on circulation rules and system preferences that DCB cannot see, so asking is the
 * difference between preventing a renewal and hoping we did.
 */
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaRenewability {

	@JsonProperty("allows_renewal")
	private Boolean allowsRenewal;

	@JsonProperty("max_renewals")
	private Integer maxRenewals;

	@JsonProperty("current_renewals")
	private Integer currentRenewals;

	@JsonProperty("unseen_renewals")
	private Integer unseenRenewals;

	/** Koha's reason for refusing renewal, such as "on_reserve" or "item_denied_renewal" */
	@JsonProperty("error")
	private String error;

	@Override
	public String toString() {
		return "KohaRenewability{" +
			"allowsRenewal=" + allowsRenewal +
			", currentRenewals=" + currentRenewals +
			", maxRenewals=" + maxRenewals +
			", error='" + error + '\'' +
			'}';
	}
}
