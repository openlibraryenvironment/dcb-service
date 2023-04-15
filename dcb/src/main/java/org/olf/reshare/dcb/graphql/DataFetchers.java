package org.olf.reshare.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.olf.reshare.dcb.storage.AgencyRepository;
import org.olf.reshare.dcb.core.model.DataAgency;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Singleton
public class DataFetchers {

  private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

  private AgencyRepository agencyRepository;

  public DataFetchers(AgencyRepository agencyRepository) {
    this.agencyRepository = agencyRepository;
  }

  /**
   * Retrieve agencies.
   * NO security constraints placed on this call (Except perhaps rate limiting later on)
   *
   * RequestResponse customizers - see here:
   * See here https://github.com/micronaut-projects/micronaut-graphql/blob/master/examples/jwt-security/src/main/java/example/graphql/RequestResponseCustomizer.java
   * 
  public DataFetcher<Iterable<DataAgency>> getAgenciesDataFetcher() {
    return dataFetchingEnvironment -> {
      log.debug("AgenciesDataFetcher::get");
      // securityService...  boolean isAuthenticated(), boolean hasRole(String), Optional<Authentication> getAuthentication Optional<String> username
      // log.debug("Current user : {}",securityService.username().orElse(null));
      return Flux.from(agencyRepository.findAll()).toIterable();
    };
  }
   */

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
