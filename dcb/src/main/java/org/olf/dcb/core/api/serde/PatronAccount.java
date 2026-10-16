package org.olf.dcb.core.api.serde;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * What the asserted patron holds at their OWN library, which DCB never saw.
 *
 * <p>{@code available} is the field that stops this lying. A library DCB cannot ask and a
 * patron with nothing out both produce empty lists, and a client must say "we could not
 * ask" for the first — see docs/discovery-service-approach.md section 6.4.
 */
@Serdeable
@Introspected
public record PatronAccount(
	boolean available,
	@JsonInclude(JsonInclude.Include.ALWAYS) List<Loan> loans,
	@JsonInclude(JsonInclude.Include.ALWAYS) List<Charge> charges,
	/** What the library says it holds, which may exceed the rows returned. */
	@Nullable Integer totalLoans,
	@Nullable Integer totalCharges,
	@Nullable BigDecimal amountOwed,
	@Nullable String currency
) {

	/** The honest answer when the patron's own library will not say. */
	public static PatronAccount unavailable() {
		return new PatronAccount(false, List.of(), List.of(), null, null, null, null);
	}

	@Serdeable
	@Introspected
	public record Loan(
		@Nullable String title,
		@Nullable String author,
		@Nullable String isbn,
		/**
		 * The local bib record, so a discovery service can resolve this loan to a cluster
		 * and link it to a catalogue record. Not an identifier a patron can act on.
		 */
		@Nullable String localBibId,
		@Nullable Instant loanDate,
		@Nullable Instant dueDate,
		boolean overdue
	) {}

	@Serdeable
	@Introspected
	public record Charge(
		@Nullable String title,
		@Nullable String description,
		@Nullable String reason,
		@Nullable String state,
		@Nullable BigDecimal amount,
		@Nullable String currency,
		@Nullable Instant accrualDate
	) {}
}
