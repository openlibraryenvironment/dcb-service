package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.storage.AgencyGroupRepository;
import org.olf.dcb.storage.AgencyGroupMemberRepository;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.AgencyGroup;
import org.olf.dcb.core.model.AgencyGroupMember;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;
import services.k_int.utils.UUIDUtils;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import java.util.concurrent.CompletableFuture;
import services.k_int.data.querying.QueryService;

@Singleton
public class DataFetchers {

  private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

  private AgencyGroupRepository agencyGroupRepository;
  private PostgresAgencyRepository postgresAgencyRepository;
  private AgencyGroupMemberRepository agencyGroupMemberRepository;

  public DataFetchers(PostgresAgencyRepository postgresAgencyRepository,
                      AgencyGroupRepository agencyGroupRepository,
                      AgencyGroupMemberRepository agencyGroupMemberRepository
        ) {
        this.postgresAgencyRepository = postgresAgencyRepository;
        this.agencyGroupRepository = agencyGroupRepository;
        this.agencyGroupMemberRepository = agencyGroupMemberRepository;
  }

  /**
   * Retrieve agencies.
   * NO security constraints placed on this call (Except perhaps rate limiting later on)
   *
   * RequestResponse customizers - see here:
   * See here https://github.com/micronaut-projects/micronaut-graphql/blob/master/examples/jwt-security/src/main/java/example/graphql/RequestResponseCustomizer.java
   * 
   */
  public DataFetcher<Iterable<DataAgency>> getAgenciesDataFetcher() {
    return env -> {
        String query = env.getArgument("query");
        log.debug("AgenciesDataFetcher::get({})",query);
        // securityService...  boolean isAuthenticated(), boolean hasRole(String), Optional<Authentication> getAuthentication Optional<String> username
        // log.debug("Current user : {}",securityService.username().orElse(null));
        // return Flux.from(agencyRepository.queryAll()).toIterable();
        QueryService qs = new QueryService();

        return Mono.justOrEmpty(qs.evaluate(query, DataAgency.class))
                        .flatMapMany(spec -> postgresAgencyRepository.findAll(spec))
                        .toIterable();
    };
  }

  public DataFetcher<CompletableFuture<DataAgency>> getAgencyDataFetcherForGroupMember() {
    return env -> {
      AgencyGroupMember agm = (AgencyGroupMember)env.getSource();
      return Mono.from(agencyGroupMemberRepository.findAgencyById(agm.getId())).toFuture();
    };
  }

  public DataFetcher<Iterable<AgencyGroupMember>> getAgencyGroupMembersDataFetcher() {
    return env -> {
      log.debug("getAgencyGroupMembersDataFetcher args={}/ctx={}/root={}/src={}",env.getArguments(),env.getContext(),env.getRoot(),env.getSource());
      // securityService...  boolean isAuthenticated(), boolean hasRole(String), Optional<Authentication> getAuthentication Optional<String> username
      // log.debug("Current user : {}",securityService.username().orElse(null));

      // List<AgencyGroupMember> l = new java.util.ArrayList();
      // return l;
      return Flux.from(agencyGroupMemberRepository.findByGroup(env.getSource()))
        .toIterable();
    };
  }

  public DataFetcher<String> getHelloDataFetcher() {
    return dataFetchingEnvironment -> {
      String name = dataFetchingEnvironment.getArgument("name");
      if (name == null || name.trim().length() == 0) {
        name = "World";
      }
      return String.format("Hello %s!", name);
    };
  }

}
