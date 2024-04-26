package org.olf.dcb.core.interaction.folio;

import static java.util.function.Function.identity;
import static org.olf.dcb.utils.CollectionUtils.nonNullValuesList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

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
				getValue(personalDetails, User.PersonalDetails::getFirstName),
				getValue(personalDetails, User.PersonalDetails::getMiddleName),
				getValue(personalDetails, User.PersonalDetails::getLastName)
			))
			.blocked(getValue(user, User::getBlocked, identity(), false))
			.build());
	}
}
