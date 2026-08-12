package services.k_int.interaction.alma.types;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.micronaut.serde.ObjectMapper;

/**
 * Alma names this field "expiry_date" on the wire. Binding it to the Java name
 * expirationDate silently produced a null expiry on every patron, so nothing was
 * ever detected as expired and the virtual patron expiry extension never applied.
 */
@TestInstance(PER_CLASS)
class AlmaUserSerdeTests {
	private final ObjectMapper objectMapper = ObjectMapper.getDefault();

	@Test
	void shouldDeserialiseAlmaExpiryDate() throws IOException {
		final var json = """
			{
				"primary_id": "patron-id",
				"first_name": "Test",
				"last_name": "Patron",
				"user_group": { "value": "UNDRGRD", "desc": "Undergraduate" },
				"status": { "value": "ACTIVE", "desc": "Active" },
				"expiry_date": "2026-04-29Z"
			}
			""";

		final var almaUser = objectMapper.readValue(json, AlmaUser.class);

		assertThat(almaUser.getExpirationDate(), is("2026-04-29Z"));
	}

	@Test
	void shouldSerialiseAlmaExpiryDateBackUnderTheNameAlmaExpects() throws IOException {
		final var almaUser = AlmaUser.builder()
			.primary_id("patron-id")
			.expirationDate("2026-12-31Z")
			.build();

		final var json = objectMapper.writeValueAsString(almaUser);

		assertThat(json, containsString("\"expiry_date\":\"2026-12-31Z\""));
	}
}
