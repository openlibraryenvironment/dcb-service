package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.utils.CollectionUtils.nonNullValuesList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.Patron;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton
@Slf4j
class UserToPatronConverter implements TypeConverter<User, Patron> {
	@Override
	public Optional<Patron> convert(User user, Class<Patron> targetType,
		ConversionContext context) {

		final var personalDetails = user.getPersonal();
		final var mappedExpiryDate = parseExpiryDate(user.getExpirationDate());

		return Optional.of(Patron.builder()
			.localId(nonNullValuesList(user.getId()))
			.localPatronType(user.getPatronGroupName())
			.localBarcodes(nonNullValuesList(user.getBarcode()))
			.localNames(nonNullValuesList(
				getValueOrNull(personalDetails, User.PersonalDetails::getFirstName),
				getValueOrNull(personalDetails, User.PersonalDetails::getMiddleName),
				getValueOrNull(personalDetails, User.PersonalDetails::getLastName)
			))
			.isBlocked(getValue(user, User::getBlocked, false))
			.isActive(getValue(user, User::getActive, true))
			.isDeleted(false)
			.expiryDate(mappedExpiryDate)
			.build());
	}
	private Date parseExpiryDate(String expirationDateStr) {
		if (expirationDateStr != null && !expirationDateStr.trim().isEmpty()) {
			try {
				return Date.from(Instant.parse(expirationDateStr));
			} catch (DateTimeParseException e) {
				try {
					LocalDate localDate = LocalDate.parse(expirationDateStr);
					return Date.from(localDate.atStartOfDay(ZoneId.of("UTC")).toInstant());
				} catch (DateTimeParseException ex) {
					log.warn("Could not parse FOLIO expiration date: {}. Patron will be mapped without an expiry date.", expirationDateStr);
				}
			}
		}
		return null;
	}
}
