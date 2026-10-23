package org.olf.dcb.core.interaction.koha.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** GET /api/v1/patrons/{patron_id}/account: the balance with its outstanding lines. */
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaPatronBalance {
	@JsonProperty("balance")
	private BigDecimal balance;

	@JsonProperty("outstanding_debits")
	private Outstanding outstandingDebits;

	@JsonProperty("outstanding_credits")
	private Outstanding outstandingCredits;

	@Serdeable
	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Outstanding {
		@JsonProperty("total")
		private BigDecimal total;

		@JsonProperty("lines")
		private List<KohaAccountLine> lines;
	}
}
