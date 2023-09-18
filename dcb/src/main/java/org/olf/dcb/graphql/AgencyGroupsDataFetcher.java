package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.postgres.PostgresAgencyGroupRepository;
import org.olf.dcb.storage.AgencyGroupMemberRepository;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.AgencyGroup;
import org.olf.dcb.core.model.AgencyGroupMember;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import services.k_int.utils.UUIDUtils;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;

import services.k_int.data.querying.QueryService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class AgencyGroupsDataFetcher implements DataFetcher<Iterable<AgencyGroup>> {

  private static Logger log = LoggerFactory.getLogger(AgencyGroupsDataFetcher.class);

  private AgencyGroupMemberRepository agencyGroupMemberRepository;
  private PostgresAgencyGroupRepository agencyGroupRepository;
  private AgencyRepository agencyRepository;
  private R2dbcOperations r2dbcOperations;

  public AgencyGroupsDataFetcher(
                AgencyGroupMemberRepository agencyGroupMemberRepository,
                AgencyRepository agencyRepository,
                PostgresAgencyGroupRepository agencyGroupRepository,
                R2dbcOperations r2dbcOperations
        ) {
    this.agencyGroupMemberRepository = agencyGroupMemberRepository;
    this.agencyRepository = agencyRepository;
    this.agencyGroupRepository = agencyGroupRepository;
    this.r2dbcOperations = r2dbcOperations;
  }

  @Override
  public Iterable<AgencyGroup> get(DataFetchingEnvironment env) throws Exception {

    log.debug("AgencyGroupsDataFetcher::get({})",env);

    QueryService qs = new QueryService();
    String query = env.getArgument("query");
    if ( ( query != null ) && ( query.length() > 0 ) ) {
      return Mono.justOrEmpty(qs.evaluate(query, AgencyGroup.class))
                          .flatMapMany(spec -> agencyGroupRepository.findAll(spec))
                          .toIterable();
    }

    return Flux.from(agencyGroupRepository.queryAll()).toIterable();
  }

}
