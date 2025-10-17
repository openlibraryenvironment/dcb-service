package org.olf.dcb.graphql;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.storage.postgres.PostgresClusterRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.reactivestreams.Publisher;
import java.util.concurrent.CompletableFuture;

@Singleton
public class InstanceClusterDataFetcher implements DataFetcher<CompletableFuture<Page<ClusterRecord>>> {

	private static Logger log = LoggerFactory.getLogger(InstanceClusterDataFetcher.class);

	private final PostgresClusterRecordRepository clusterRecordRepository;
	private final QueryService qs;

	public InstanceClusterDataFetcher(PostgresClusterRecordRepository clusterRecordRepository, QueryService qs) {
		this.clusterRecordRepository = clusterRecordRepository;
		this.qs = qs;
	}

	public CompletableFuture<Page<ClusterRecord>> get(DataFetchingEnvironment env) throws Exception {

                Integer pageno = env.getArgument("pageno");
                Integer pagesize = env.getArgument("pagesize");
                String query = env.getArgument("query");

                if ( pageno == null ) pageno = Integer.valueOf(0);
                if ( pagesize == null ) pagesize = Integer.valueOf(10);

                log.debug("InstanceClusterDataFetcher::get({},{},{})", pageno,pagesize,query);
                Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue());

		if ((query != null) && (query.length() > 0)) {
			log.debug("Returning query version of InstanceCluster");
                        var spec = qs.evaluate(query, ClusterRecord.class);
                        return Mono.from(clusterRecordRepository.findAll(spec, pageable)).toFuture();
		}

		log.debug("Returning simple clusterRecord list");

		return Mono.from(clusterRecordRepository.findAll(pageable)).toFuture();
	}

}
