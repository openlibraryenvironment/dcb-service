package org.olf.dcb.graphql;

import org.olf.dcb.core.model.AgencyGroup;
import org.olf.dcb.storage.postgres.PostgresAgencyGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;

@Singleton
public class AgencyGroupsDataFetcher implements DataFetcher<Iterable<AgencyGroup>> {

	private static Logger log = LoggerFactory.getLogger(AgencyGroupsDataFetcher.class);

	private final PostgresAgencyGroupRepository agencyGroupRepository;
	private final	QueryService qs;
	

	public AgencyGroupsDataFetcher(PostgresAgencyGroupRepository agencyGroupRepository, QueryService qs) {
		this.agencyGroupRepository = agencyGroupRepository;
		this.qs = qs;
	}

	@Override
	public Iterable<AgencyGroup> get(DataFetchingEnvironment env) throws Exception {

		log.debug("AgencyGroupsDataFetcher::get({})", env);
		String query = env.getArgument("query");
		if ((query != null) && (query.length() > 0)) {
			log.debug("Returning query version of agencies");
			return Mono.justOrEmpty(qs.evaluate(query, AgencyGroup.class))
					.flatMapMany(spec -> agencyGroupRepository.findAll(spec)).toIterable();
		}

		log.debug("Returning simple agency list");
		return Flux.from(agencyGroupRepository.queryAll()).toIterable();
	}

}
