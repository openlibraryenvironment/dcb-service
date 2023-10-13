package org.olf.dcb.graphql;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.storage.postgres.PostgresBibRepository;
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
public class SourceBibDataFetcher implements DataFetcher<CompletableFuture<Page<BibRecord>>> {

	private static Logger log = LoggerFactory.getLogger(SourceBibDataFetcher.class);

	private final PostgresBibRepository bibRepository;
	private final QueryService qs;

	public SourceBibDataFetcher(PostgresBibRepository bibRepository, QueryService qs) {
		this.bibRepository = bibRepository;
		this.qs = qs;
	}

	public CompletableFuture<Page<BibRecord>> get(DataFetchingEnvironment env) throws Exception {

		Integer pageno = env.getArgument("pageno");
		Integer pagesize = env.getArgument("pagesize");
		String query = env.getArgument("query");

                if ( pageno == null ) pageno = Integer.valueOf(0);
                if ( pagesize == null ) pagesize = Integer.valueOf(10);

		log.debug("SourceBibDataFetcher::get({},{},{})", pageno,pagesize,query);
		Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue());

		if ((query != null) && (query.length() > 0)) {
			log.debug("Returning query version of BibRecord");
                        var spec = qs.evaluate(query, BibRecord.class);
                        return Mono.from(bibRepository.findAll(spec, pageable)).toFuture();
		}

		log.debug("Returning simple bibRecord list");

		return Mono.from(bibRepository.findAll(pageable)).toFuture();
	}

}
