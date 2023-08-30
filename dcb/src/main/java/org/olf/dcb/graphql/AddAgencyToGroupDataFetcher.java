package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.AgencyGroupRepository;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class AddAgencyToGroupDataFetcher implements DataFetcher<CompletableFuture<AgencyGroupMember>> {

  private static Logger log = LoggerFactory.getLogger(AddAgencyToGroupDataFetcher.class);

  private AgencyGroupMemberRepository agencyGroupMemberRepository;
  private AgencyGroupRepository agencyGroupRepository;
  private AgencyRepository agencyRepository;
  private R2dbcOperations r2dbcOperations;

  public AddAgencyToGroupDataFetcher(
                AgencyGroupMemberRepository agencyGroupMemberRepository,
                AgencyRepository agencyRepository,
                AgencyGroupRepository agencyGroupRepository,
                R2dbcOperations r2dbcOperations
        ) {
    this.agencyGroupMemberRepository = agencyGroupMemberRepository;
    this.agencyRepository = agencyRepository;
    this.agencyGroupRepository = agencyGroupRepository;
    this.r2dbcOperations = r2dbcOperations;
  }


  @Override
  public CompletableFuture<AgencyGroupMember> get(DataFetchingEnvironment env) {

    // String name = env.getArgument("name");
    // String code = env.getArgument("code");
    // AgencyGroup input = env.getArgument("input");
    Map input_map = env.getArgument("input");

    log.debug("AddAgencyToGroupDataFetcher::get {}",input_map);

    // input consists of a "group" and "agency" property - each of type ID which maps to a UUID string

        // https://github.com/micronaut-projects/micronaut-graphql/issues/210 says to use
        //  public CompletableFuture<SetResult<S>> get(final DataFetchingEnvironment environment) throws Exception {
        //    var pub = .... get your publisher here
        //    return Mono.from(pub).toFuture();
        //  }

    AgencyGroupMember agm = AgencyGroupMember.builder().build();

    log.debug("get...");

    return Mono.from(
        r2dbcOperations.withTransaction(status -> {

                String agency_uuid_as_string = input_map.get("agency").toString();
                String group_uuid_as_string = input_map.get("group").toString();
                UUID agency_uuid = UUID.fromString(agency_uuid_as_string);
                UUID group_uuid = UUID.fromString(group_uuid_as_string);

                Mono<AgencyGroup> group_mono = Mono.from(agencyGroupRepository.findById(group_uuid));
                Mono<DataAgency> agency_mono = Mono.from(agencyRepository.findById(agency_uuid));

                log.debug("add agency to group agency={} group={}",agency_uuid,group_uuid);

                return Mono.just(agm)
                        .flatMap( agm2 -> group_mono.map ( group -> agm2.setGroup(group) ) )
                        .flatMap( agm2 -> agency_mono.map ( agency -> agm2.setAgency(agency) ) )
                        .map( agm2 -> {
                                UUID record_uuid = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "AGM:"+agency_uuid_as_string+":"+group_uuid_as_string);
                                log.debug("Set uuid for member {} {}",agm2,record_uuid);
                                return agm2.setId(record_uuid);
                        })
                        .flatMap( agm2 -> Mono.from(agencyGroupMemberRepository.saveOrUpdate(agm2)) )
                        ;
          }
        )
    ).toFuture();
  }

}
