package org.olf.dcb.graphql;

import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.AgencyGroupMember;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.AgencyGroupMemberRepository;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;
import org.olf.dcb.storage.postgres.PostgresSupplierRequestRepository;
import org.olf.dcb.storage.postgres.PostgresBibRepository;
import org.olf.dcb.storage.postgres.PostgresRawSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reactivestreams.Publisher;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import graphql.schema.DataFetcher;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.annotation.TypeHint.AccessType;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;
import java.util.List;
import java.util.UUID;

@Singleton
@TypeHint(typeNames = { "org.apache.lucene.search.Query", "org.apache.lucene.search.MultiTermQuery" }, accessType = {
		AccessType.ALL_DECLARED_CONSTRUCTORS, AccessType.ALL_DECLARED_FIELDS, AccessType.ALL_DECLARED_METHODS })
public class DataFetchers {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private final PostgresAgencyRepository postgresAgencyRepository;
	private final PostgresPatronRequestRepository postgresPatronRequestRepository;
	private final PostgresSupplierRequestRepository postgresSupplierRequestRepository;
	private final AgencyGroupMemberRepository agencyGroupMemberRepository;
	private final PostgresBibRepository postgresBibRepository;
        private final PostgresRawSourceRepository postgresRawSourceRepository;
	private final QueryService qs;

	public DataFetchers(PostgresAgencyRepository postgresAgencyRepository,
			AgencyGroupMemberRepository agencyGroupMemberRepository,
			PostgresPatronRequestRepository postgresPatronRequestRepository,
			PostgresSupplierRequestRepository postgresSupplierRequestRepository, 
			PostgresBibRepository postgresBibRepository, 
                        PostgresRawSourceRepository postgresRawSourceRepository,
                        QueryService qs) {
		this.qs = qs;
		this.postgresAgencyRepository = postgresAgencyRepository;
		this.agencyGroupMemberRepository = agencyGroupMemberRepository;
		this.postgresPatronRequestRepository = postgresPatronRequestRepository;
		this.postgresSupplierRequestRepository = postgresSupplierRequestRepository;
		this.postgresBibRepository = postgresBibRepository;
                this.postgresRawSourceRepository = postgresRawSourceRepository;
	}

	/**
	 * Retrieve agencies. NO security constraints placed on this call (Except
	 * perhaps rate limiting later on)
	 *
	 * RequestResponse customizers - see here: See here
	 * https://github.com/micronaut-projects/micronaut-graphql/blob/master/examples/jwt-security/src/main/java/example/graphql/RequestResponseCustomizer.java
	 * 
	 */
	public DataFetcher<Iterable<DataAgency>> getAgenciesDataFetcher() {
		return env -> {
			String query = env.getArgument("query");
			log.debug("AgenciesDataFetcher::get({})", query);
			// securityService... boolean isAuthenticated(), boolean hasRole(String),
			// Optional<Authentication> getAuthentication Optional<String> username
			// log.debug("Current user : {}",securityService.username().orElse(null));
			// return Flux.from(agencyRepository.queryAll()).toIterable();

			return Mono.justOrEmpty(qs.evaluate(query, DataAgency.class))
					.flatMapMany(spec -> postgresAgencyRepository.findAll(spec)).toIterable();
		};
	}

	public DataFetcher<CompletableFuture<DataAgency>> getAgencyDataFetcherForGroupMember() {
		return env -> {
			AgencyGroupMember agm = (AgencyGroupMember) env.getSource();
			return Mono.from(agencyGroupMemberRepository.findAgencyById(agm.getId())).toFuture();
		};
	}

	public DataFetcher<Iterable<AgencyGroupMember>> getAgencyGroupMembersDataFetcher() {
		return env -> {
			log.debug("getAgencyGroupMembersDataFetcher args={}/ctx={}/root={}/src={}", env.getArguments(),
					env.getGraphQlContext(), env.getRoot(), env.getSource());
			// securityService... boolean isAuthenticated(), boolean hasRole(String),
			// Optional<Authentication> getAuthentication Optional<String> username
			// log.debug("Current user : {}",securityService.username().orElse(null));

			// List<AgencyGroupMember> l = new java.util.ArrayList();
			// return l;
			return Flux.from(agencyGroupMemberRepository.findByGroup(env.getSource())).toIterable();
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

	public DataFetcher<Iterable<PatronRequest>> getPatronRequestsDataFetcher() {
		return env -> {
			String query = env.getArgument("query");
			log.debug("PatronRequestsDataFetcher::get({})", query);
			return Mono.justOrEmpty(qs.evaluate(query, PatronRequest.class))
					.flatMapMany(spec -> postgresPatronRequestRepository.findAll(spec)).toIterable();
		};
	}

	public DataFetcher<Iterable<SupplierRequest>> getSupplierRequestsDataFetcher() {
		return env -> {
			String query = env.getArgument("query");
			log.debug("SupplierRequestsDataFetcher::get({})", query);
			return Mono.justOrEmpty(qs.evaluate(query, SupplierRequest.class))
					.flatMapMany(spec -> postgresSupplierRequestRepository.findAll(spec)).toIterable();
		};
	}

        public DataFetcher<CompletableFuture<List<BibRecord>>> getClusterMembersDataFetcher() {
                return env -> {
                        log.debug("getClusterMembersDataFetcher args={}/ctx={}/root={}/src={}", env.getArguments(), env.getGraphQlContext(), env.getRoot(), env.getSource());
                        return Flux.from(postgresBibRepository.findAllByContributesTo(env.getSource())).collectList().toFuture();
                };
        }

        public DataFetcher<CompletableFuture<RawSource>> getSourceRecordForBibDataFetcher() {
                return env -> {
                        BibRecord br = (BibRecord) env.getSource();
                        String sourceRecordId=br.getSourceRecordId();
                        UUID sourceSystemUUID=br.getSourceSystemId();
                        log.debug("Find raw source with ID {} from {}",sourceRecordId,sourceSystemUUID);
                        return Mono.from(postgresRawSourceRepository.findOneByHostLmsIdAndRemoteId(sourceSystemUUID,sourceRecordId)).toFuture();
                };
        }

}
