package org.olf.dcb.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.storage.postgres.PostgresClusterRecordRepository;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;

@DcbTest
class SearchTests {

	@Inject
	@Client("/")
	HttpClient client;
	
	@Inject
	QueryService qs;

	@BeforeEach
	void beforeEach() {
	}

	@Inject
	PostgresClusterRecordRepository clusterRecordRepo;

	@Test
	void testSearchUtility() {
		try {
			Mono.justOrEmpty( qs.evaluate("title:title*", ClusterRecord.class) )
				.flatMapMany(clusterRecordRepo::findAll)
				.map(ClusterRecord::toString)
				.doOnNext(System.out::println)
				.blockLast();
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
