package org.olf.dcb;
//package org.olf.dcb;
//
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.TestMethodOrder;
//import io.micronaut.http.client.HttpClient;
//import io.micronaut.http.client.annotation.Client;
//import io.micronaut.runtime.EmbeddedApplication;
//import jakarta.inject.Inject;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import org.olf.dcb.core.model.Agency;
//import org.olf.dcb.storage.AgencyRepository;
//import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
//import org.olf.dcb.test.DcbTest;
//
//import java.util.UUID;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.List;
//
//import org.junit.jupiter.api.Order;
//
//@DcbTest
//@TestMethodOrder(OrderAnnotation.class)
//class AgencyRepoTest {
//
//	public static final Logger log = LoggerFactory.getLogger(AgencyRepoTest.class);
//
//	@Inject
//	EmbeddedApplication<?> application;
//
//	@Inject
//	AgencyRepository agencyRepo;
//
//	@Test
//	@Order(1)
//	void testAgencyCreation() {
//		log.debug("1. testAgencyCreation");
//
//		Mono<Agency> agency_mono = Mono.from(agencyRepo.save(new Agency(UUID.randomUUID(), "DCB Test Institution A")));
//		log.debug("Created test agency: {}", agency_mono.block().toString());
//
//		// Make sure that we have 1 task
//		List<org.olf.dcb.core.model.Agency> agencies = Flux.from(agencyRepo.findAll()).collectList().block();
//		assert agencies != null;
//
//		log.debug("Got {} agencies: {}", agencies.size(), agencies.toString());
//		assert agencies.size() == 1;
//
//	}
//
//}
//
