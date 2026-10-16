package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.olf.dcb.core.interaction.LocalPatronAccount;

import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * edge-patron's account onto DCB's — see docs/discovery-service-approach.md section 6.4.
 */
@Slf4j
final class PatronAccountMapper {

	private PatronAccountMapper() {
	}

	static LocalPatronAccount toLocalAccount(PatronAccountResponse response) {
		return LocalPatronAccount.builder()
			.loans(mapEach(response.getLoans(), PatronAccountMapper::toLoan))
			.charges(mapEach(response.getCharges(), PatronAccountMapper::toCharge))
			.totalLoans(response.getTotalLoans())
			.totalCharges(response.getTotalChargesCount())
			.amountOwed(getValueOrNull(response.getTotalCharges(), PatronAccountResponse.Money::getAmount))
			.currency(getValueOrNull(response.getTotalCharges(), PatronAccountResponse.Money::getIsoCurrencyCode))
			.build();
	}

	private static LocalPatronAccount.LocalLoan toLoan(PatronAccountResponse.Loan loan) {
		final var item = loan.getItem();

		return LocalPatronAccount.LocalLoan.builder()
			.title(getValueOrNull(item, PatronAccountResponse.Item::getTitle))
			.author(getValueOrNull(item, PatronAccountResponse.Item::getAuthor))
			.isbn(getValueOrNull(item, PatronAccountResponse.Item::getIsbn))
			.localBibId(getValueOrNull(item, PatronAccountResponse.Item::getInstanceId))
			.localItemId(getValueOrNull(item, PatronAccountResponse.Item::getItemId))
			.loanDate(parseTimestamp(loan.getLoanDate()))
			.dueDate(parseTimestamp(loan.getDueDate()))
			.overdue(Boolean.TRUE.equals(loan.getOverdue()))
			.build();
	}

	private static LocalPatronAccount.LocalCharge toCharge(PatronAccountResponse.Charge charge) {
		final var amount = charge.getChargeAmount();

		return LocalPatronAccount.LocalCharge.builder()
			.description(charge.getDescription())
			.reason(charge.getReason())
			.state(charge.getState())
			.amount(getValueOrNull(amount, PatronAccountResponse.Money::getAmount))
			.currency(getValueOrNull(amount, PatronAccountResponse.Money::getIsoCurrencyCode))
			.accrualDate(parseTimestamp(charge.getAccrualDate()))
			.title(getValueOrNull(charge.getItem(), PatronAccountResponse.Item::getTitle))
			.build();
	}

	private static <T, R> List<R> mapEach(@Nullable List<T> values,
		java.util.function.Function<T, R> mapper) {

		return values == null ? List.of() : values.stream().map(mapper).toList();
	}

	/**
	 * FOLIO emits an offset rather than a Z, which {@code Instant.parse} refuses. A whole
	 * account is not worth losing to one unparseable timestamp, so this degrades the field.
	 */
	@Nullable
	private static Instant parseTimestamp(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException offsetFailure) {
			try {
				return Instant.parse(value);
			} catch (DateTimeParseException instantFailure) {
				log.warn("Could not parse a FOLIO account timestamp: {}", value);
				return null;
			}
		}
	}
}
