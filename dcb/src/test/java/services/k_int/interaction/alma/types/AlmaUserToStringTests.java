package services.k_int.interaction.alma.types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.util.List;

import org.junit.jupiter.api.Test;

class AlmaUserToStringTests {
	@Test
	void shouldLeavePersonalDataOutOfToString() {
		final var user = AlmaUser.builder()
			.primary_id("PRIMARY-7731")
			.first_name("Ada")
			.last_name("Lovelace")
			.password("secret-password-5520")
			.external_id("EXTERNAL-9912")
			.identifiers(List.of(UserIdentifier.builder().value("BARCODE-4410").build()))
			.build();

		final var rendered = user.toString();

		for (String personal : List.of("PRIMARY-7731", "Ada", "Lovelace", "secret-password-5520",
			"EXTERNAL-9912", "BARCODE-4410")) {

			assertThat(rendered, not(containsString(personal)));
		}
	}
}
