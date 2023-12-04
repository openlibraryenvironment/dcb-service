package org.olf.dcb.graphql;

import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.model.AgencyGroupMember;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.AgencyGroup;
import org.olf.dcb.core.model.ProcessState;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.storage.AgencyGroupMemberRepository;
import org.olf.dcb.storage.postgres.PostgresAgencyRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestRepository;
import org.olf.dcb.storage.postgres.PostgresSupplierRequestRepository;
import org.olf.dcb.storage.postgres.PostgresBibRepository;
import org.olf.dcb.storage.postgres.PostgresRawSourceRepository;
import org.olf.dcb.storage.postgres.PostgresHostLmsRepository;
import org.olf.dcb.storage.postgres.PostgresLocationRepository;
import org.olf.dcb.storage.postgres.PostgresAgencyGroupRepository;
import org.olf.dcb.storage.postgres.PostgresProcessStateRepository;
import org.olf.dcb.storage.postgres.PostgresSupplierRequestRepository;
import org.olf.dcb.storage.postgres.PostgresPatronRequestAuditRepository;
import org.olf.dcb.storage.postgres.PostgresPatronIdentityRepository;
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
        private final PostgresHostLmsRepository postgresHostLmsRepository;
        private final PostgresLocationRepository postgresLocationRepository;
        private final PostgresAgencyGroupRepository postgresAgencyGroupRepository;
        private final PostgresProcessStateRepository postgresProcessStateRepository;
        private final PostgresPatronRequestAuditRepository postgresPatronRequestAuditRepository;
        private final PostgresPatronIdentityRepository postgresPatronIdentityRepository;
	private final QueryService qs;

	public DataFetchers(PostgresAgencyRepository postgresAgencyRepository,
			AgencyGroupMemberRepository agencyGroupMemberRepository,
			PostgresPatronRequestRepository postgresPatronRequestRepository,
			PostgresSupplierRequestRepository postgresSupplierRequestRepository, 
			PostgresBibRepository postgresBibRepository, 
                        PostgresRawSourceRepository postgresRawSourceRepository,
                        PostgresHostLmsRepository postgresHostLmsRepository,
                        PostgresLocationRepository postgresLocationRepository,
                        PostgresAgencyGroupRepository postgresAgencyGroupRepository,
			PostgresProcessStateRepository postgresProcessStateRepository,
                        PostgresPatronRequestAuditRepository postgresPatronRequestAuditRepository,
                        PostgresPatronIdentityRepository postgresPatronIdentityRepository,
                        QueryService qs) {
		this.qs = qs;
		this.postgresAgencyRepository = postgresAgencyRepository;
		this.agencyGroupMemberRepository = agencyGroupMemberRepository;
		this.postgresPatronRequestRepository = postgresPatronRequestRepository;
		this.postgresSupplierRequestRepository = postgresSupplierRequestRepository;
		this.postgresBibRepository = postgresBibRepository;
                this.postgresRawSourceRepository = postgresRawSourceRepository;
                this.postgresHostLmsRepository = postgresHostLmsRepository;
                this.postgresLocationRepository = postgresLocationRepository;
                this.postgresAgencyGroupRepository = postgresAgencyGroupRepository;
                this.postgresProcessStateRepository = postgresProcessStateRepository;
                this.postgresPatronRequestAuditRepository = postgresPatronRequestAuditRepository;
                this.postgresPatronIdentityRepository = postgresPatronIdentityRepository;
	}


        public DataFetcher<CompletableFuture<Page<DataAgency>>> getAgenciesDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        
                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "name";

                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue())
				.order(order);
                
                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, DataAgency.class);
                                return Mono.from(postgresAgencyRepository.findAll(spec, pageable)).toFuture();
                        }
                        
                        return Mono.from(postgresAgencyRepository.findAll(pageable)).toFuture();
                };
        }

	public DataFetcher<CompletableFuture<DataAgency>> getAgencyDataFetcherForGroupMember() {
		return env -> {
			AgencyGroupMember agm = (AgencyGroupMember) env.getSource();
			return Mono.from(agencyGroupMemberRepository.findAgencyById(agm.getId())).toFuture();
		};
	}

        public DataFetcher<CompletableFuture<List<AgencyGroupMember>>> getAgencyGroupMembersDataFetcher() {
                return env -> {
                        log.debug("getAgencyGroupMembersDataFetcher args={}/ctx={}/root={}/src={}", env.getArguments(), env.getGraphQlContext(), env.getRoot(), env.getSource());
                        return Flux.from(agencyGroupMemberRepository.findByGroup(env.getSource())).collectList().toFuture();
                };
        }

        public DataFetcher<CompletableFuture<Page<PatronRequest>>> getPatronRequestsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        
                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "dateCreated";

                        Pageable pageable = Pageable
                                .from(pageno.intValue(), pagesize.intValue())
                                .order(order);
                
                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, PatronRequest.class);
                                return Mono.from(postgresPatronRequestRepository.findAll(spec, pageable)).toFuture();
                        }
                        
                        return Mono.from(postgresPatronRequestRepository.findAll(pageable)).toFuture();
                };
        }

        public DataFetcher<CompletableFuture<Page<PatronRequestAudit>>> getAuditsDataFetcher() {
                return env -> {
                        log.debug("getAuditsDataFetcher {}",env);
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");

                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "auditDate";

                        Pageable pageable = Pageable
				.from(pageno.intValue(), pagesize.intValue())
                                .order(order);

                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, PatronRequestAudit.class);
                                return Mono.from(postgresPatronRequestAuditRepository.findAll(spec, pageable)).toFuture();
                        }

                        return Mono.from(postgresPatronRequestAuditRepository.findAll(pageable)).toFuture();
                };
        }


        public DataFetcher<CompletableFuture<Page<PatronIdentity>>> getPatronIdentitiesDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        
                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);

                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue());
                
                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, PatronIdentity.class);
                                return Mono.from(postgresPatronIdentityRepository.findAll(spec, pageable)).toFuture();
                        }
                        
                        return Mono.from(postgresPatronIdentityRepository.findAll(pageable)).toFuture();
                };
        }

        public DataFetcher<CompletableFuture<Page<SupplierRequest>>> getSupplierRequestsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        
                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "dateCreated";

                        Pageable pageable = Pageable
                                .from(pageno.intValue(), pagesize.intValue())
                                .order(order);
                
                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, SupplierRequest.class);
                                return Mono.from(postgresSupplierRequestRepository.findAll(spec, pageable)).toFuture();
                        }
                        
                        return Mono.from(postgresSupplierRequestRepository.findAll(pageable)).toFuture();
                };
        }

        public DataFetcher<CompletableFuture<Page<AgencyGroup>>> getPaginatedAgencyGroupsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        
                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);

                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue());

                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, AgencyGroup.class);
                                return Mono.from(postgresAgencyGroupRepository.findAll(spec, pageable)).toFuture();
                        }

                        return Mono.from(postgresAgencyGroupRepository.findAll(pageable)).toFuture();
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

        public DataFetcher<CompletableFuture<Page<Location>>> getLocationsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");

                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "name";

                        log.debug("InstanceClusterDataFetcher::get({},{},{})", pageno,pagesize,query);
                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue())
				.order(order);

                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, Location.class);
                                return Mono.from(postgresLocationRepository.findAll(spec, pageable)).toFuture();
                        }

                        return Mono.from(postgresLocationRepository.findAll(pageable)).toFuture();
                };
        }


        public DataFetcher<CompletableFuture<Page<DataHostLms>>> getHostLMSDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");

                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "code";

                        log.debug("InstanceClusterDataFetcher::get({},{},{})", pageno,pagesize,query);
                        Pageable pageable = Pageable
                                .from(pageno.intValue(), pagesize.intValue())
                                .order(order)
                                ;

                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, DataHostLms.class);
                                return Mono.from(postgresHostLmsRepository.findAll(spec, pageable)).toFuture();
                        }

                        log.debug("Returning simple clusterRecord list");

                        return Mono.from(postgresHostLmsRepository.findAll(pageable)).toFuture();
                };
        }

	public DataFetcher<CompletableFuture<Page<ProcessState>>> getProcessStateDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");

                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);

                        log.debug("ProcessStateDataFetcher::get({},{},{})", pageno,pagesize,query);
                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue());

                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, ProcessState.class);
                                return Mono.from(postgresProcessStateRepository.findAll(spec, pageable)).toFuture();
                        }

                        log.debug("Returning simple processState list");

                        return Mono.from(postgresProcessStateRepository.findAll(pageable)).toFuture();
                };
	}

        public DataFetcher<CompletableFuture<List<SupplierRequest>>> getSupplierRequestsForPR() {
                return env -> {
			PatronRequest parent = env.getSource();
                        log.debug("getSupplierRequestsForPR {}",parent);
                        return Flux.from(postgresSupplierRequestRepository.findAllByPatronRequest(parent))
				.collectList()
				.toFuture();
                };
        }

        public DataFetcher<CompletableFuture<List<PatronRequestAudit>>> getAuditMessagesForPR() {
                return env -> {
			PatronRequest parent = env.getSource();
                        log.debug("getAuditMessagesForPR {}",parent);
                        return Flux.from(postgresPatronRequestAuditRepository.findAllByPatronRequest(parent))
				.collectList()
				.toFuture();
                };
        }

	public DataFetcher<CompletableFuture<PatronRequest>> getPatronRequestForSupplierRequestDataFetcher() {
                return env -> {
                        SupplierRequest sr = (SupplierRequest) env.getSource();
                        return Mono.from(postgresPatronRequestRepository.getPRForSRID(sr.getId())).toFuture();
                };
	}

	public DataFetcher<CompletableFuture<DataAgency>> getAgencyForLocation() {
                // ToDo: FillOut
                return env -> {
                        Location l = (Location) env.getSource();
                        UUID agencyUUID = l.getAgency().getId();
                        return Mono.from(postgresAgencyRepository.findById(agencyUUID)).toFuture();
                };
	}

        public DataFetcher<CompletableFuture<DataHostLms>> getHostSystemForLocation() {
                // ToDo: FillOut
                return env -> {
                        Location l = (Location) env.getSource();
                        UUID hostSystemUUID = l.getHostSystem().getId();
                        return Mono.from(postgresHostLmsRepository.findById(hostSystemUUID)).toFuture();
                };
        }
        
        public DataFetcher<CompletableFuture<Location>> getParentForLocation() {
                // ToDo: FillOut
                return env -> {
                        Location l = (Location) env.getSource();
                        @io.micronaut.core.annotation.NonNull
                        UUID parentLocationUUID = l.getParentLocation().getId();
                        return Mono.from(postgresLocationRepository.findById(parentLocationUUID)).toFuture();
                };
        }
}
