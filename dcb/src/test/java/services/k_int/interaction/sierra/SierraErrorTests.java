package services.k_int.interaction.sierra;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SierraErrorTests {

	@Test
	public void testSierraError () {

		SierraError exception = new SierraError();

		exception.setDescription("description");
		exception.setName("name");
		exception.setSpecificCode(400);
		exception.setCode(400);

		assertNotNull(exception);
		Assertions.assertEquals(exception.getMessage(), "name: description - [400 / 400]");
		Assertions.assertEquals(exception.toString(), "name: description - [400 / 400]");
	}
}
