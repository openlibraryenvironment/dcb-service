package org.olf.dcb.rules;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.oaipmh.OaiRecord;
import services.k_int.interaction.sierra.bibs.BibResult;

@Slf4j
@MicronautTest(propertySources = "rulesetTests.yml")
public class RulesetTests {
	
	private static final String RESOURCE_SUB_PATH = Stream.ofNullable(RulesetTests.class.getPackageName())
			.map( pkg -> pkg.split("\\.") )
			.flatMap( Stream::of )
			.collect( Collectors.joining(File.separator) )
		+ File.separator;
	
	private @NonNull Optional<InputStream> getRelativeResource ( String resource ) {

		final String path = "classpath:" + RESOURCE_SUB_PATH + StringUtils.trimLeadingCharacter(resource, File.separatorChar);
		
		log.debug("Looking for resource [{}]", path);
		
		return resourceResolver.getResourceAsStream(path);
	}

	@Inject
	ObjectRulesService ruleService;
	
	@Inject
	ConversionService conversionService;
	
	@Inject
	ResourceResolver resourceResolver;
	
	@Inject
	ObjectMapper mapper;

	private JsonNode folioSources = null;
	private JsonNode sierraSources = null;
	
	private JsonNode parseJsonFile ( String fileName ) throws IOException {
		return mapper.readValue(getRelativeResource(fileName).get(), JsonNode.class);
	}

	@BeforeEach
	void setupFileSources() throws IOException {
		if (folioSources == null) {
			folioSources = parseJsonFile("folio-source-data.json");
		}
		
		if (sierraSources == null) {
			sierraSources = parseJsonFile("sierra-records.json");
		}
	}

	@ParameterizedTest
	@CsvSource({"excluded-explicitly,false", "excluded-explicitly-true,false", "included-explicitly,true",
		"included-missing-field,true", "included-missing-field-2,true", "test-998t-variant,true"})
	void testFolio999tSuppressionFromJSON( String propertyName, boolean expected ) throws IOException {
		
		ObjectRuleset ruleset = ruleService.findByName("folio999t").block();
		assertNotNull(ruleset);
		
		JsonNode json = folioSources.get(propertyName);

		assertNotNull(json);
		
		ArrayList<String> details = new ArrayList<>();
		boolean result = ruleset.test(new AnnotatedObject(json, details));
		assertEquals(expected, result);
	}
	
	@ParameterizedTest
	@CsvSource({"excluded-explicitly,false", "excluded-explicitly-true,false", "included-explicitly,true",
		"included-missing-field,true", "included-missing-field-2,true", "test-998t-variant,true"})
	void testFolio999tSuppressionFromObjects( String propertyName, boolean expected ) throws IOException {
		
		ObjectRuleset ruleset = ruleService.findByName("folio999t").block();
		assertNotNull(ruleset);
		
		JsonNode json = folioSources.get(propertyName);
		OaiRecord target = conversionService.convertRequired(json, OaiRecord.class);

		assertNotNull(target);
		ArrayList<String> details = new ArrayList<>();
		
		boolean result = ruleset.test(new AnnotatedObject(target, details));
		assertEquals(expected, result);
	}
	
	@ParameterizedTest
	@CsvSource({"excluded-explicitly,true", "excluded-explicitly-true,true", "included-explicitly,true",
		"included-missing-field,true", "included-missing-field-2,true", "test-998t-variant,false"})
	void testFolio998tSuppressionFromJSON( String propertyName, boolean expected ) throws IOException {
		
		ObjectRuleset ruleset = ruleService.findByName("folio998t").block();
		assertNotNull(ruleset);
		
		JsonNode json = folioSources.get(propertyName);

		assertNotNull(json);
		
		ArrayList<String> details = new ArrayList<>();
		boolean result = ruleset.test(new AnnotatedObject(json, details));
		assertEquals(expected, result);
	}
	
	@ParameterizedTest
	@CsvSource({"excluded-explicitly,true", "excluded-explicitly-true,true", "included-explicitly,true",
		"included-missing-field,true", "included-missing-field-2,true", "test-998t-variant,false"})
	void testFolio998tSuppressionFromObjects( String propertyName, boolean expected ) throws IOException {
		
		ObjectRuleset ruleset = ruleService.findByName("folio998t").block();
		assertNotNull(ruleset);
		
		JsonNode json = folioSources.get(propertyName);
		OaiRecord target = conversionService.convertRequired(json, OaiRecord.class);

		assertNotNull(target);
		ArrayList<String> details = new ArrayList<>();
		
		boolean result = ruleset.test(new AnnotatedObject(target, details));
		assertEquals(expected, result);
	}
	
	@ParameterizedTest
	@CsvSource({"include-present,true", "include-missing,true", "exclude-z,false", "exclude-s,false", "exclude-f,false", "exclude-n,false"})
	void testSierraTypeRecord( String propertyName, boolean expected ) {
		
		ObjectRuleset ruleset = ruleService.findByName("sierraff31").block();
		assertNotNull(ruleset);
		
		JsonNode target = sierraSources.get(propertyName);
		assertNotNull(target);
		
		BibResult br = conversionService.convertRequired(target, BibResult.class);
		
		ArrayList<String> details = new ArrayList<>();
		boolean result = ruleset.test(new AnnotatedObject(br, details));
		assertEquals(expected, result);
	}
}
