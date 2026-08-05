package org.olf.dcb.core.interaction.ncip;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * PR-4: the shared NCIP schema validator. Constructing it here proves the
 * packaged NCIP v2.02 XSD actually loads; the assertions prove it enforces the
 * schema. This is the single validator both the imperative Foundation adaptor
 * and (in PR-6) the declarative transport are held to.
 *
 * Positive conformance of built payloads is exercised where a payload builder
 * exists (declarative side, PR-6).
 */
class NcipSchemaValidatorTests {
	private final NcipSchemaValidator validator = NcipSchemaValidator.usingDefaultSchema();

	@Test
	void packagesTheCompleteNcipSchemaSet() {
		final var classLoader = getClass().getClassLoader();

		assertNotNull(classLoader.getResource("schemas/ncip_v2_02.xsd"));
		assertNotNull(classLoader.getResource("schemas/openrs-ncip-fallback-host.xsd"));
		assertNotNull(classLoader.getResource("schemas/openrs_ncip_extension.xsd"));
	}

	@Test
	void rejectsXmlThatIsNotAnNcipMessage() {
		assertThrows(IllegalArgumentException.class,
			() -> validator.validate("<not-ncip/>"));
	}

	@Test
	void rejectsMalformedXml() {
		assertThrows(IllegalArgumentException.class,
			() -> validator.validate("<NCIPMessage><unclosed>"));
	}
}
