package org.olf.dcb.core.interaction.koha.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One line of a patron's account: a debit (fine, charge) or a credit. */
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaAccountLine {
	@JsonProperty("account_line_id")
	private Long accountLineId;

	@JsonProperty("amount")
	private BigDecimal amount;

	@JsonProperty("amount_outstanding")
	private BigDecimal amountOutstanding;

	@JsonProperty("debit_type")
	private String debitType;

	@JsonProperty("credit_type")
	private String creditType;

	@JsonProperty("description")
	private String description;

	@JsonProperty("date")
	private String date;

	@JsonProperty("item_id")
	private Long itemId;

	@JsonProperty("status")
	private String status;
}
