package org.olf.dcb.graphql;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.storage.postgres.PostgresBibRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;

@Singleton
public class SourceBibDataFetcher implements DataFetcher<Iterable<BibRecord>> {

	private static Logger log = LoggerFactory.getLogger(SourceBibDataFetcher.class);

	private final PostgresBibRepository bibRepository;
	private final QueryService qs;
	

	public SourceBibDataFetcher(PostgresBibRepository bibRepository, QueryService qs) {
		this.bibRepository = bibRepository;
		this.qs = qs;
	}

	@Override
	public Iterable<BibRecord> get(DataFetchingEnvironment env) throws Exception {

		log.debug("SourceBibDataFetcher::get({})", env);
		String query = env.getArgument("query");
		if ((query != null) && (query.length() > 0)) {
			log.debug("Returning query version of agencies");
			return Mono.justOrEmpty(qs.evaluate(query, BibRecord.class))
					.flatMapMany(spec -> bibRepository.findAll(spec)).toIterable();
		}

		log.debug("Returning simple bibRecord list");
		return Flux.from(bibRepository.queryAll()).toIterable();
	}

}
