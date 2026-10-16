package org.olf.dcb.core.interaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * What a patron holds at their OWN library, which DCB never saw.
 *
 * <p>See docs/discovery-service-approach.md §6.4. A Host LMS that cannot answer returns an
 * empty Mono rather than an empty account: "we could not ask" and "you have nothing out"
 * are different sentences and a patron must not be shown the second for the first.
 */
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class LocalPatronAccount {
	/** Capped by the caller. {@link #totalLoans} is what the library says it has. */
	@Nullable List<LocalLoan> loans;
	@Nullable List<LocalCharge> charges;

	@Nullable Integer totalLoans;
	@Nullable Integer totalCharges;
	@Nullable BigDecimal amountOwed;
	@Nullable String currency;

	@Builder
	@Data
	@NoArgsConstructor(onConstructor_ = @Creator())
	@AllArgsConstructor
	@Serdeable
	@ExcludeFromGeneratedCoverageReport
	public static class LocalLoan {
		@Nullable String title;
		@Nullable String author;
		@Nullable String isbn;

		/**
		 * The bib record in the local system. Carried because it is what a discovery
		 * service resolves to a cluster, which is how a local loan links to a catalogue
		 * record instead of sitting as dead text.
		 */
		@Nullable String localBibId;

		/** Needed by a future renewal, which is addressed by item. */
		@Nullable String localItemId;

		@Nullable Instant loanDate;
		@Nullable Instant dueDate;
		boolean overdue;
	}

	@Builder
	@Data
	@NoArgsConstructor(onConstructor_ = @Creator())
	@AllArgsConstructor
	@Serdeable
	@ExcludeFromGeneratedCoverageReport
	public static class LocalCharge {
		@Nullable String description;
		@Nullable String reason;
		@Nullable String state;
		@Nullable BigDecimal amount;
		@Nullable String currency;
		@Nullable Instant accrualDate;
		/** The item the charge is against, where the library says. */
		@Nullable String title;
	}
}
