package org.olf.dcb.graphql;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.postgres.PostgresClusterRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.reactivestreams.Publisher;

@Singleton
public class InstanceClusterDataFetcher implements DataFetcher<Publisher<Page<ClusterRecord>>> {

	private static Logger log = LoggerFactory.getLogger(InstanceClusterDataFetcher.class);

	private final PostgresClusterRecordRepository clusterRecordRepository;
	private final QueryService qs;
	

	public InstanceClusterDataFetcher(PostgresClusterRecordRepository clusterRecordRepository, QueryService qs) {
		this.clusterRecordRepository = clusterRecordRepository;
		this.qs = qs;
	}

	@Override
	public Publisher<Page<ClusterRecord>> get(DataFetchingEnvironment env) throws Exception {

		log.debug("InstanceClusterDataFetcher::get({})", env);
		String query = env.getArgument("query");
		Pageable pageable = Pageable.from(0);
		if ((query != null) && (query.length() > 0)) {
			log.debug("Returning query version of agencies");
			return Mono.justOrEmpty(qs.evaluate(query, ClusterRecord.class))
					.flatMap(spec -> Mono.from(clusterRecordRepository.findAll(spec, pageable)));
		}

		log.debug("Returning simple clusterRecord list");
		return Mono.from(clusterRecordRepository.queryAll(pageable));
	}

}
