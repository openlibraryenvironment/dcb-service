package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.utils.CollectionUtils.nonNullValuesList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import org.olf.dcb.core.interaction.Patron;

public class UserToPatronConverter {
	public Patron convert(User user) {
		final var personalDetails = user.getPersonal();

		return Patron.builder()
			.localId(nonNullValuesList(user.getId()))
			.localPatronType(user.getPatronGroupName())
			.localBarcodes(nonNullValuesList(user.getBarcode()))
			.localNames(nonNullValuesList(
				getValue(personalDetails, User.PersonalDetails::getFirstName),
				getValue(personalDetails, User.PersonalDetails::getMiddleName),
				getValue(personalDetails, User.PersonalDetails::getLastName)
			))
			.blocked(false)
			.build();
	}
}
