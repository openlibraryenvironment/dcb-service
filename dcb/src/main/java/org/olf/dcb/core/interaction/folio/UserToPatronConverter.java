package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.utils.CollectionUtils.nonNullValuesList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Optional;

import org.olf.dcb.core.interaction.Patron;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

@Singleton
class UserToPatronConverter implements TypeConverter<User, Patron> {
	@Override
	public Optional<Patron> convert(User user, Class<Patron> targetType,
		ConversionContext context) {

		final var personalDetails = user.getPersonal();

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
			.build());
	}
}
