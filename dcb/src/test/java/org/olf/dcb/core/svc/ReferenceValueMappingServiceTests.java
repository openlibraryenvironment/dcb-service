package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyName;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasNoAgencyName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.core.model.ReferenceValueMapping;
import reactor.core.publisher.Mono;

import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Test the TVM service in a context free way
 * Our test vocabulary is metadata semantics. We want to know how to map the concept "title" into different contexts.
 * We have a root context called DC - Dublin core - this is our default. In Dublin core the concept "title" is mapped to "DC.title".
 * In the context of MARC21, The concept of "Title" is mapped to "245". Another context "EAD" uses DC.title to represent title.
 *
 * Run these alone with: ./gradlew clean build test --tests org.olf.dcb.core.svc.ReferenceValueMappingServiceTests
 * 
 */
@Slf4j
@DcbTest
class ReferenceValueMappingServiceTests {

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@Inject
	private ReferenceValueMappingService referenceValueMappingService;

	@BeforeEach
	void beforeEach() {
		log.info("Configure reference value mappings...");
		log.info("Deleting...");
		referenceValueMappingFixture.deleteAll();
		log.info("Inserting...");
		referenceValueMappingFixture.defineMapping("GLOBAL", "CONCEPT", "title", "DC", "CONCEPT", "DC.title", false);
		referenceValueMappingFixture.defineMapping("GLOBAL", "CONCEPT", "title", "MARC21", "CONCEPT", "245", false);
		log.info("Done...");
	}

	@Test
	void TestDirectMapping() {
		log.debug("test direct mapping");
		// Ask the service to find out what the mapping from the Global concept title is to a concept in marc21

		String mappedValue = referenceValueMappingService.findMapping("CONCEPT", "GLOBAL", "title", "CONCEPT", "MARC21")
			.doOnNext(rvm -> log.info("Got rvm {}",rvm))
      .map(ReferenceValueMapping::getToValue)
			.block();
		assert mappedValue.equals("245");
	}

	@Test
	void testDefaultMapping() {
		List <String> contextHierarchy = List.of( "EAD", "DC" );
		String mappedValue = referenceValueMappingService.findMappingUsingHierarchy("CONCEPT", "GLOBAL", "title", "CONCEPT", contextHierarchy)
			.doOnNext(rvm -> log.info("Got rvm {}",rvm))
      .map(ReferenceValueMapping::getToValue)
			.block();
		assert mappedValue.equals("DC.title");
	}

  @Test
  void testDirectMappingWithHierarchy() {
    List <String> contextHierarchy = List.of( "MARC21", "DC" );
    String mappedValue = referenceValueMappingService.findMappingUsingHierarchy("CONCEPT", "GLOBAL", "title", "CONCEPT", contextHierarchy)
			.doOnNext(rvm -> log.info("Got rvm {}",rvm))
      .map(ReferenceValueMapping::getToValue)
      .block();
    assert mappedValue.equals("245");
  }

  @Test
  void testNoMapping() {
    List <String> contextHierarchy = List.of( "MARC21", "DC" );
    Mono<ReferenceValueMapping> rvm = referenceValueMappingService.findMappingUsingHierarchy("CONCEPT", "GLOBAL", "wibble", "CONCEPT", contextHierarchy);
    assert rvm.hasElement().block() == Boolean.FALSE;
  }



}
