package org.olf.reshare.dcb;

import java.util.List;
import java.util.UUID;

import javax.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.test.DcbTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DcbTest
@TestMethodOrder(OrderAnnotation.class)
class BibRepoTest {

	public static final Logger log = LoggerFactory.getLogger(BibRepoTest.class);

	@Inject
	EmbeddedApplication<?> application;

	@Inject
	BibRepository bibRepo;

	@Test
	@Order(1)
	@Transactional
	void testBibCreation() {
		log.debug("1. testBibCreation");

		Mono<BibRecord> bib_record_mono = Mono.from(bibRepo.save(
				BibRecord.builder()
					.id(UUID.randomUUID())
					.title("Brain of the Firm")
					.build()));
		log.debug("Created test bib: {}", bib_record_mono.block().toString());

		// Make sure that we have 1 task
		List<org.olf.reshare.dcb.core.model.BibRecord> bibs = Flux.from(bibRepo.findAll()).collectList().block();
		assert bibs != null;

		log.debug("Got {} bibs: {}", bibs.size(), bibs.toString());
		assert bibs.size() == 1; 

	}

}
