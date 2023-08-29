package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.AgencyGroupRepository;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.AgencyGroup;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import services.k_int.utils.UUIDUtils;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;

@Singleton
public class CreateAgencyGroupDataFetcher implements DataFetcher<AgencyGroup> {

  private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

  private AgencyGroupRepository agencyGroupRepository;
  private AgencyRepository agencyRepository;
  private R2dbcOperations r2dbcOperations;

  public CreateAgencyGroupDataFetcher(AgencyRepository agencyRepository,
                      AgencyGroupRepository agencyGroupRepository,
                      R2dbcOperations r2dbcOperations
        ) {
    this.agencyRepository = agencyRepository;
    this.agencyGroupRepository = agencyGroupRepository;
    this.r2dbcOperations = r2dbcOperations;
  }


  @Override
  public AgencyGroup get(DataFetchingEnvironment env) {
    String name = env.getArgument("name");
    String code = env.getArgument("code");
  
    log.debug("getCreateAgencyGroupDataFetcher {}/{}",code,name);

    UUID id = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "AgencyGroup:"+code);

    log.debug("getCreateAgencyGroupDataFetcher");
    AgencyGroup ag = AgencyGroup.builder()
                            .id(id)
                            .name(name)
                            .code(code)
                            .build();

    log.debug("save or update agency {}",ag);

    return Mono.from(
        r2dbcOperations.withTransaction(status -> 
                Mono.from(agencyGroupRepository.saveOrUpdate(ag))
        )
    ).block();
  }
}
