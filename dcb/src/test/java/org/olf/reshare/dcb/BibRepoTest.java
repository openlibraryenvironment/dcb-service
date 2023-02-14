package org.olf.reshare.dcb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.postgres.PostgresBibRepository;
import org.olf.reshare.dcb.test.DcbTest;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import org.junit.jupiter.api.Order;

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
	void testBibCreation() {
		log.debug("1. testBibCreation");

		Mono<BibRecord> bib_record_mono = Mono.from(bibRepo.save(new BibRecord(UUID.randomUUID(), "Brain of the Firm")));
		log.debug("Created test bib: {}", bib_record_mono.block().toString());

		// Make sure that we have 1 task
		List<org.olf.reshare.dcb.core.model.BibRecord> bibs = Flux.from(bibRepo.findAll()).collectList().block();
		assert bibs != null;

		log.debug("Got {} bibs: {}", bibs.size(), bibs.toString());
		assert bibs.size() == 1;

	}

}
