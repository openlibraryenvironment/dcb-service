package org.olf.dcb.core.interaction.folio;

import java.math.BigDecimal;
import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

/**
 * edge-patron's answer to GET /patron/account/{externalSystemId}.
 *
 * <p>Dates stay as strings here and are parsed in {@link PatronAccountMapper}. FOLIO emits
 * an offset rather than a Z, which {@code Instant.parse} refuses, and a whole account is
 * not worth losing to one unparseable timestamp.
 */
@Serdeable
@Builder
@Data
public class PatronAccountResponse {
	@Nullable String id;
	@Nullable Integer totalLoans;
	@Nullable Integer totalHolds;
	@Nullable Integer totalChargesCount;
	@Nullable Money totalCharges;
	@Nullable List<Loan> loans;
	@Nullable List<Charge> charges;

	@Serdeable
	@Builder
	@Data
	public static class Loan {
		@Nullable String id;
		@Nullable Item item;
		@Nullable String loanDate;
		@Nullable String dueDate;
		@Nullable Boolean overdue;
	}

	@Serdeable
	@Builder
	@Data
	public static class Charge {
		@Nullable Item item;
		@Nullable Money chargeAmount;
		@Nullable String accrualDate;
		@Nullable String description;
		@Nullable String state;
		@Nullable String reason;
		@Nullable String feeFineId;
	}

	@Serdeable
	@Builder
	@Data
	public static class Item {
		@Nullable String instanceId;
		@Nullable String itemId;
		@Nullable String title;
		@Nullable String author;
		@Nullable String isbn;
	}

	@Serdeable
	@Builder
	@Data
	public static class Money {
		@Nullable BigDecimal amount;
		@Nullable String isoCurrencyCode;
	}
}
