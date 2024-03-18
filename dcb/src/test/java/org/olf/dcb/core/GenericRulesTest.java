package org.olf.dcb.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.test.DcbTest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.FixedField.FixedFieldBuilder;

@DcbTest
@Slf4j
@MicronautTest(propertySources = "classpath:generic-filter-config.yml")
public class GenericRulesTest {
		
	@Inject
	@Named("slu-bib-suppression")
	ObjectRuleset injectedRuleset;
	
	@Inject
	ObjectMapper mapper;
	
	private static final String json = "{"
			+ "      \"name\": \"slu-bib-suppression\","
			+ "      \"type\": \"DISJUNCTIVE\","
			+ "      \"conditions\": [{"
			+ "          \"operation\" : \"propertyPresent\","
			+ "          \"property\": \"fixedFields.3.value\","
			+ "          \"negated\": true"
			+ "      },{"
			+ "          \"operation\" : \"propertyValueAnyOf\","
			+ "          \"property\": \"fixedFields.3.value\","
			+ "          \"values\": [\"z\", \"s\", \"f\", \"n\"],"
			+ "          \"negated\": true"
			+ "      }]"
			+ "}";
	
	
	@Test
	void checkFiltersOnStream() throws IOException {
		
		ObjectRuleset parsedRuleset = mapper.readValue(json, ObjectRuleset.class);
		
		log.info("Parsed (JSON) filterSet: {}", parsedRuleset);
		
		log.atInfo()
			.log("Injected filterSet: {}", injectedRuleset);
		
		// Generate a stream and then apply the injected filterSet
		List<FixedFieldObject> results = generateFixedFieldObjectsWithField3Values(
				"z", "f",
				"b", "a", "s",
				"b", "c", "d", null, "", "n")
			.filter(injectedRuleset)
			.toList();
		
		assertEquals(7, results.size());

		// Generate a stream and then apply the parsed filterSet
		results = generateFixedFieldObjectsWithField3Values(
				"z", "f",
				"b", "a", "s",
				"b", "c", "d", null, "", "n")
			.filter(parsedRuleset)
			.toList();
		
		assertEquals(7, results.size());
	}
	
	private Stream<FixedFieldObject> generateFixedFieldObjectsWithField3Values(String... vals) {
		return Stream.of(vals)
			.map(FixedField.builder()::value)
			.map(FixedFieldBuilder::build)
			.map(field -> Map.of(3, field))
			.map(FixedFieldObject::new);
	}
	
	@Introspected
	@AllArgsConstructor
	@Getter
	private static class FixedFieldObject {
		@Nullable Map<Integer, FixedField> fixedFields;
	}
}
