package org.olf.dcb.graphql;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.core.model.*;
import org.olf.dcb.core.model.clustering.*;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.storage.AgencyGroupMemberRepository;
import org.olf.dcb.storage.LibraryGroupMemberRepository;
import org.olf.dcb.storage.postgres.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

import graphql.schema.DataFetcher;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.annotation.TypeHint.AccessType;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.data.querying.QueryService;


@Singleton
@TypeHint(typeNames = { "org.apache.lucene.search.Query", "org.apache.lucene.search.MultiTermQuery" }, accessType = {
		AccessType.ALL_DECLARED_CONSTRUCTORS, AccessType.ALL_DECLARED_FIELDS, AccessType.ALL_DECLARED_METHODS })
public class DataFetchers {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private final PostgresAgencyRepository postgresAgencyRepository;
	private final PostgresPatronRequestRepository postgresPatronRequestRepository;
	private final PostgresSupplierRequestRepository postgresSupplierRequestRepository;
	private final PostgresInactiveSupplierRequestRepository postgresInactiveSupplierRequestRepository;
	private final AgencyGroupMemberRepository agencyGroupMemberRepository;
	private final PostgresBibRepository postgresBibRepository;
	private final PostgresHostLmsRepository postgresHostLmsRepository;
	private final PostgresLocationRepository postgresLocationRepository;
	private final PostgresAgencyGroupRepository postgresAgencyGroupRepository;
	private final PostgresProcessStateRepository postgresProcessStateRepository;
	private final PostgresPatronRequestAuditRepository postgresPatronRequestAuditRepository;
	private final PostgresPatronIdentityRepository postgresPatronIdentityRepository;
	private final PostgresClusterRecordRepository postgresClusterRecordRepository;
	private final PostgresReferenceValueMappingRepository postgresReferenceValueMappingRepository;
	private final PostgresNumericRangeMappingRepository postgresNumericRangeMappingRepository;
	private final PostgresMatchPointRepository postgresMatchPointRepository;

	private final PostgresLibraryRepository postgresLibraryRepository;

	private final PostgresConsortiumRepository postgresConsortiumRepository;

	private final PostgresLibraryGroupRepository postgresLibraryGroupRepository;

	private final LibraryGroupMemberRepository libraryGroupMemberRepository;

	private final PostgresLibraryGroupMemberRepository postgresLibraryGroupMemberRepository;
	private final PostgresPersonRepository postgresPersonRepository;

	private final PostgresLibraryContactRepository postgresLibraryContactRepository;

	private final PostgresDataChangeLogRepository postgresDataChangeLogRepository;

	private final PostgresConsortiumContactRepository postgresConsortiumContactRepository;

	private final PostgresFunctionalSettingRepository postgresFunctionalSettingRepository;
	private final PostgresConsortiumFunctionalSettingRepository postgresConsortiumFunctionalSettingRepository;
	private final PostgresRoleRepository postgresRoleRepository;

	private final PostgresSourceRecordRepository postgresSourceRecordRepository;
	private final PostgresAlarmRepository postgresAlarmRepository;
	private final QueryService qs;

	public DataFetchers(PostgresAgencyRepository postgresAgencyRepository,
											AgencyGroupMemberRepository agencyGroupMemberRepository,
											PostgresPatronRequestRepository postgresPatronRequestRepository,
											PostgresSupplierRequestRepository postgresSupplierRequestRepository,
											PostgresInactiveSupplierRequestRepository postgresInactiveSupplierRequestRepository,
											PostgresBibRepository postgresBibRepository,
											PostgresHostLmsRepository postgresHostLmsRepository,
											PostgresLocationRepository postgresLocationRepository,
											PostgresAgencyGroupRepository postgresAgencyGroupRepository,
											PostgresProcessStateRepository postgresProcessStateRepository,
											PostgresPatronRequestAuditRepository postgresPatronRequestAuditRepository,
											PostgresPatronIdentityRepository postgresPatronIdentityRepository,
											PostgresClusterRecordRepository postgresClusterRecordRepository,
											PostgresNumericRangeMappingRepository postgresNumericRangeMappingRepository,
											PostgresReferenceValueMappingRepository postgresReferenceValueMappingRepository,
											PostgresLibraryRepository postgresLibraryRepository,
											PostgresConsortiumRepository postgresConsortiumRepository,
											PostgresPersonRepository postgresPersonRepository,
											PostgresLibraryGroupRepository postgresLibraryGroupRepository, LibraryGroupMemberRepository libraryGroupMemberRepository,
											PostgresLibraryGroupMemberRepository postgresLibraryGroupMemberRepository,
											PostgresLibraryContactRepository postgresLibraryContactRepository, PostgresDataChangeLogRepository postgresDataChangeLogRepository,
											PostgresMatchPointRepository postgresMatchPointRepository, PostgresConsortiumContactRepository postgresConsortiumContactRepository,
											PostgresFunctionalSettingRepository postgresFunctionalSettingRepository,
											PostgresConsortiumFunctionalSettingRepository postgresConsortiumFunctionalSettingRepository,
											PostgresRoleRepository postgresRoleRepository,
											PostgresSourceRecordRepository postgresSourceRecordRepository,
											PostgresAlarmRepository postgresAlarmRepository,
											QueryService qs) {
		this.qs = qs;
		this.postgresAgencyRepository = postgresAgencyRepository;
		this.agencyGroupMemberRepository = agencyGroupMemberRepository;
		this.postgresPatronRequestRepository = postgresPatronRequestRepository;
		this.postgresSupplierRequestRepository = postgresSupplierRequestRepository;
		this.postgresInactiveSupplierRequestRepository = postgresInactiveSupplierRequestRepository;
		this.postgresBibRepository = postgresBibRepository;
		this.postgresHostLmsRepository = postgresHostLmsRepository;
		this.postgresLocationRepository = postgresLocationRepository;
		this.postgresAgencyGroupRepository = postgresAgencyGroupRepository;
		this.postgresProcessStateRepository = postgresProcessStateRepository;
		this.postgresPatronRequestAuditRepository = postgresPatronRequestAuditRepository;
		this.postgresPatronIdentityRepository = postgresPatronIdentityRepository;
		this.postgresClusterRecordRepository = postgresClusterRecordRepository;
		this.postgresReferenceValueMappingRepository = postgresReferenceValueMappingRepository;
		this.postgresNumericRangeMappingRepository = postgresNumericRangeMappingRepository;
		this.postgresLibraryRepository = postgresLibraryRepository;
		this.postgresConsortiumRepository = postgresConsortiumRepository;
		this.postgresPersonRepository = postgresPersonRepository;
		this.postgresLibraryGroupRepository = postgresLibraryGroupRepository;
		this.libraryGroupMemberRepository = libraryGroupMemberRepository;
		this.postgresLibraryGroupMemberRepository = postgresLibraryGroupMemberRepository;
		this.postgresLibraryContactRepository = postgresLibraryContactRepository;
		this.postgresDataChangeLogRepository = postgresDataChangeLogRepository;
		this.postgresMatchPointRepository = postgresMatchPointRepository;
		this.postgresConsortiumContactRepository = postgresConsortiumContactRepository;
		this.postgresFunctionalSettingRepository = postgresFunctionalSettingRepository;
		this.postgresConsortiumFunctionalSettingRepository = postgresConsortiumFunctionalSettingRepository;
		this.postgresRoleRepository = postgresRoleRepository;
		this.postgresSourceRecordRepository = postgresSourceRecordRepository;
		this.postgresAlarmRepository = postgresAlarmRepository;
	}


        public DataFetcher<CompletableFuture<Page<DataAgency>>> getAgenciesDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        String direction = env.getArgument("orderBy");

                        
                        if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "name";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);
                
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

	public DataFetcher<CompletableFuture<List<MatchPoint>>> getMatchPointsForBibRecordDataFetcher() {
		return env -> {
			BibRecord br = (BibRecord) env.getSource();
			return Flux.from(postgresMatchPointRepository.findAllByBibId(br.getId())).collectList().toFuture();
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
			String direction = env.getArgument("orderBy");

			if ( pageno == null ) pageno = Integer.valueOf(0);
			if ( pagesize == null ) pagesize = Integer.valueOf(10);
			if ( order == null ) order = "dateCreated";
			if ( direction == null ) direction = "ASC";

			Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

			Pageable pageable = Pageable
				.from(pageno.intValue(), pagesize.intValue())
				.order(order, orderBy);
                
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
			String direction = env.getArgument("orderBy");

			if ( pageno == null ) pageno = Integer.valueOf(0);
			if ( pagesize == null ) pagesize = Integer.valueOf(10);
			if ( order == null ) order = "auditDate";
			if ( direction == null ) direction = "ASC";

			Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

			Pageable pageable = Pageable
				.from(pageno.intValue(), pagesize.intValue())
				.order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, PatronRequestAudit.class);
				return Mono.from(postgresPatronRequestAuditRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresPatronRequestAuditRepository.findAll(pageable)).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Page<DataChangeLog>>> getDataChangeLogDataFetcher() {
		return env -> {
			log.debug("getDataChangeLogDataFetcher {}",env);
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if ( pageno == null ) pageno = Integer.valueOf(0);
			if ( pagesize == null ) pagesize = Integer.valueOf(10);
			if ( order == null ) order = "timestampLogged";
			if ( direction == null ) direction = "ASC";

			Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

			Pageable pageable = Pageable
				.from(pageno.intValue(), pagesize.intValue())
				.order(order, orderBy);

			String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
				.map(Object::toString)
				.orElse("User not detected");

			Collection<String> roles = env.getGraphQlContext().get("roles");

			// Check if the user has the required role
			if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
				log.warn("getDataChangeLog: Access denied for user {} with roles {}: user does not have the required role to access the data change log.", userString, roles);
				throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
			}

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, DataChangeLog.class);
				return Mono.from(postgresDataChangeLogRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresDataChangeLogRepository.findAll(pageable)).toFuture();
		};
	}


        public DataFetcher<CompletableFuture<Page<PatronIdentity>>> getPatronIdentitiesDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        String direction = env.getArgument("orderBy");


												if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

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
                        String direction = env.getArgument("orderBy");

												if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "dateCreated";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

                        Pageable pageable = Pageable
                                .from(pageno.intValue(), pagesize.intValue())
                                .order(order, orderBy);
                
                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, SupplierRequest.class);
                                return Mono.from(postgresSupplierRequestRepository.findAll(spec, pageable)).toFuture();
                        }
                        
                        return Mono.from(postgresSupplierRequestRepository.findAll(pageable)).toFuture();
                };
        }

				public DataFetcher<CompletableFuture<Page<InactiveSupplierRequest>>> getInactiveSupplierRequestsDataFetcher() {
					return env -> {
						Integer pageno = env.getArgument("pageno");
						Integer pagesize = env.getArgument("pagesize");
						String query = env.getArgument("query");
						String order = env.getArgument("order");
						String direction = env.getArgument("orderBy");

						if ( pageno == null ) pageno = Integer.valueOf(0);
						if ( pagesize == null ) pagesize = Integer.valueOf(10);
						if ( order == null ) order = "dateCreated";
						if ( direction == null ) direction = "ASC";

						Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

						Pageable pageable = Pageable
							.from(pageno.intValue(), pagesize.intValue())
							.order(order, orderBy);

						if ((query != null) && (query.length() > 0)) {
							var spec = qs.evaluate(query, InactiveSupplierRequest.class);
							return Mono.from(postgresInactiveSupplierRequestRepository.findAll(spec, pageable)).toFuture();
						}

						return Mono.from(postgresInactiveSupplierRequestRepository.findAll(pageable)).toFuture();
					};
				}

        public DataFetcher<CompletableFuture<Page<AgencyGroup>>> getPaginatedAgencyGroupsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
												String order = env.getArgument("order");
												String direction = env.getArgument("orderBy");

												if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "name";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

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

				public DataFetcher<CompletableFuture<SourceRecord>> getSourceRecordForBibDataFetcher() {
					return env -> {
						BibRecord br = (BibRecord) env.getSource();
						//ToDo: This isn't ideal.. need to find a better way
						String sourceRecordId="%"+br.getSourceRecordId();
						UUID sourceSystemUUID=br.getSourceSystemId();
						log.debug("Find raw source with ID {} from {}",sourceRecordId,sourceSystemUUID);
						return Flux.from(postgresSourceRecordRepository.findByHostLmsIdAndRemoteIdLike(sourceSystemUUID,sourceRecordId))
								.singleOrEmpty()
								.toFuture();
					};
				}


        public DataFetcher<CompletableFuture<Page<Location>>> getLocationsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        String direction = env.getArgument("orderBy");

												if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "name";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

                        log.debug("getLocationsDataFetcher({},{},{})", pageno,pagesize,query);
                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

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
                        String direction = env.getArgument("orderBy");

												if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "code";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

                        log.debug("InstanceClusterDataFetcher::get({},{},{})", pageno,pagesize,query);
                        Pageable pageable = Pageable
                                .from(pageno.intValue(), pagesize.intValue())
                                .order(order, orderBy)
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
                        return Flux.from(postgresPatronRequestAuditRepository.findAllByPatronRequestOrderByAuditDate(parent))
				.collectList()
				.toFuture();
                };
        }


	public DataFetcher<CompletableFuture<ClusterRecord>> getClusterRecordForPR(){
		return env -> {
			PatronRequest parent = (PatronRequest) env.getSource();
			log.debug("Get the Bib Cluster Record for {}", parent);
			return Mono.from(postgresClusterRecordRepository.findById(parent.getBibClusterId())).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<PatronRequest>> getPatronRequestForSupplierRequestDataFetcher() {
                return env -> {
                        SupplierRequest sr = (SupplierRequest) env.getSource();
                        return Mono.from(postgresPatronRequestRepository.getPRForSRID(sr.getId())).toFuture();
                };
	}

	public DataFetcher<CompletableFuture<PatronRequest>> getPatronRequestForInactiveSupplierRequestDataFetcher() {
		return env -> {
			InactiveSupplierRequest sr = (InactiveSupplierRequest) env.getSource();
			return Mono.from(postgresPatronRequestRepository.getPatronRequestByInactiveSupplierRequestId(sr.getId())).toFuture();
		};
	}

  public DataFetcher<CompletableFuture<PatronIdentity>> getPatronIdentityForPatronRequestRequest() {
    return env -> {
      PatronRequest pr = (PatronRequest) env.getSource();
      if ( pr.getRequestingIdentity() != null ) {
        UUID pid = pr.getRequestingIdentity().getId();
        return Mono.from(postgresPatronIdentityRepository.findById(pid)).toFuture();
      }                 
      else {            
        Mono<PatronIdentity> r = Mono.empty();
        return r.toFuture();
      }                 
    };          
  }                     

	public DataFetcher<CompletableFuture<PatronIdentity>> getVPatronForSupplierRequest() {
		return env -> {
			SupplierRequest sr = (SupplierRequest) env.getSource();
			if ( sr.getVirtualIdentity() != null ) {
				UUID vpatronid = sr.getVirtualIdentity().getId();
				return Mono.from(postgresPatronIdentityRepository.findById(vpatronid)).toFuture();
			}
			else {
				Mono<PatronIdentity> r = Mono.empty();
				return r.toFuture();
			}
		};
	}

	public DataFetcher<CompletableFuture<DataAgency>> getAgencyForLocation() {
                // ToDo: FillOut
                return env -> {
                        Location l = (Location) env.getSource();
			if ( l.getAgency() != null ) {
                        	UUID agencyUUID = l.getAgency().getId();
                        	return Mono.from(postgresAgencyRepository.findById(agencyUUID)).toFuture();
			}
			else {
				Mono<DataAgency> r = Mono.empty();
				return r.toFuture();
			}
                };
	}

        public DataFetcher<CompletableFuture<DataHostLms>> getHostSystemForLocation() {
                // ToDo: FillOut
                return env -> {
                        Location l = (Location) env.getSource();
			if ( l.getHostSystem() != null ) {
                        	UUID hostSystemUUID = l.getHostSystem().getId();
                        	return Mono.from(postgresHostLmsRepository.findById(hostSystemUUID)).toFuture();
			}
			else {
				Mono<DataHostLms> r = Mono.empty();
				return r.toFuture();
			}
                };
        }

        
        public DataFetcher<CompletableFuture<Location>> getParentForLocation() {
                // ToDo: FillOut
                return env -> {
                        Location l = (Location) env.getSource();
			if ( l.getParentLocation() != null ) {
	                        UUID parentLocationUUID = l.getParentLocation().getId();
        	                return Mono.from(postgresLocationRepository.findById(parentLocationUUID)).toFuture();
			}
			else {
				Mono<Location> r = Mono.empty();
				return r.toFuture();
			}
                };
        }

        public DataFetcher<CompletableFuture<Page<NumericRangeMapping>>> getNumericRangeMappingsDataFetcher() {
                return env -> {
                        Integer pageno = env.getArgument("pageno");
                        Integer pagesize = env.getArgument("pagesize");
                        String query = env.getArgument("query");
                        String order = env.getArgument("order");
                        String direction = env.getArgument("orderBy");

												if ( pageno == null ) pageno = Integer.valueOf(0);
                        if ( pagesize == null ) pagesize = Integer.valueOf(10);
                        if ( order == null ) order = "id";
                        if ( direction == null ) direction = "ASC";

												Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

                        Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue())
                                .order(order, orderBy);

                        if ((query != null) && (query.length() > 0)) {
                                var spec = qs.evaluate(query, NumericRangeMapping.class);
                                return Mono.from(postgresNumericRangeMappingRepository.findAll(spec, pageable)).toFuture();
                        }

                        return Mono.from(postgresNumericRangeMappingRepository.findAll(pageable)).toFuture();
                };
        }
	public DataFetcher<CompletableFuture<Page<ReferenceValueMapping>>> getReferenceValueMappingsDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if (pageno == null) pageno = 0;
			if (pagesize == null) pagesize = 10;
			if (order == null) order = "id";
			if (direction == null) direction = "ASC";

			// Creates the sorting direction
			Sort.Order.Direction orderBy = Sort.Order.Direction.valueOf(direction);

			// This is special handling for lastImported sort to ensure that it is sorted NULLS LAST in DESC order
			// Implemented in data fetcher as I was unable to implement at repository level
			// Postgres seems to do NULLS FIRST by default for descending order
			if (order.equals("lastImported")) {
				if ((query != null) && (query.length() > 0)) {
					var spec = qs.evaluate(query, ReferenceValueMapping.class);

					// Fetch all records based on the specification, preserving the query
					Integer finalPageno = pageno;
					Integer finalPagesize = pagesize;
					return Mono.from(postgresReferenceValueMappingRepository.findAll(spec, Pageable.unpaged()))
						.flatMap(page -> {
							// Sort the records with lastImported values NULLS LAST and respect the sort direction
							List<ReferenceValueMapping> sortedList = page.getContent().stream()
								.sorted(orderBy == Sort.Order.Direction.DESC
									? Comparator.comparing(ReferenceValueMapping::getLastImported,
									Comparator.nullsLast(Comparator.reverseOrder()))
									: Comparator.comparing(ReferenceValueMapping::getLastImported,
									Comparator.nullsLast(Comparator.naturalOrder())))
								.collect(Collectors.toList());

							// Then applies pagination
							int fromIndex = finalPageno * finalPagesize;
							int toIndex = Math.min(fromIndex + finalPagesize, sortedList.size());
							List<ReferenceValueMapping> paginatedList = sortedList.subList(fromIndex, toIndex);
							Page<ReferenceValueMapping> resultPage = Page.of(paginatedList, Pageable.from(finalPageno, finalPagesize), sortedList.size());
							return Mono.just(resultPage);
						}).toFuture();
				}

				// If no query is provided, fetch all records
				Integer finalPageno1 = pageno;
				Integer finalPagesize1 = pagesize;
				return Mono.from(postgresReferenceValueMappingRepository.findAll(Pageable.unpaged()))
					.flatMap(page -> {
						// Sort the records with lastImported values NULLS LAST and respect the sort direction
						List<ReferenceValueMapping> sortedList = page.getContent().stream()
							.sorted(orderBy == Sort.Order.Direction.DESC
								? Comparator.comparing(ReferenceValueMapping::getLastImported,
								Comparator.nullsLast(Comparator.reverseOrder()))
								: Comparator.comparing(ReferenceValueMapping::getLastImported,
								Comparator.nullsLast(Comparator.naturalOrder())))
							.collect(Collectors.toList());

						// Then apply pagination
						int fromIndex = finalPageno1 * finalPagesize1;
						int toIndex = Math.min(fromIndex + finalPagesize1, sortedList.size());
						List<ReferenceValueMapping> paginatedList = sortedList.subList(fromIndex, toIndex);
						Page<ReferenceValueMapping> resultPage = Page.of(paginatedList, Pageable.from(finalPageno1, finalPagesize1), sortedList.size());
						return Mono.just(resultPage);
					}).toFuture();
			} else {
				// If it's not a last imported sort, we don't want to do the above as it's not great for performance.
				// So we should just handle it as usual.
				Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue())
					.order(order, orderBy);
				if ((query != null) && (query.length() > 0)) {
					var spec = qs.evaluate(query, ReferenceValueMapping.class);
					return Mono.from(postgresReferenceValueMappingRepository.findAll(spec, pageable)).toFuture();
				}
				return Mono.from(postgresReferenceValueMappingRepository.findAll(pageable)).toFuture();
			}
		};
	}


	public DataFetcher<CompletableFuture<List<Location>>> getAgencyLocationsDataFetcher() {
		return env -> {
			log.debug("getAgencyLocationsDataFetcher args={}/ctx={}/root={}/src={}", env.getArguments(), env.getGraphQlContext(), env.getRoot(), env.getSource());
			return Flux.from(postgresLocationRepository.queryAllByAgency(env.getSource())).collectList().toFuture();
		};
	}

	/**
	 * This isn't nice, but it's done like this so locate can avoid having to put logic in the front end.
   * if Pick up anywhere is enabled, return all locations, otherwise only return the patrons agency locations
	 */
	public DataFetcher<CompletableFuture<List<Location>>> getPickupLocationsDataFetcher() {
		return env -> {                 
			String agency = env.getArgument("forAgency");
    
			return Mono.from(postgresFunctionalSettingRepository.findByName(FunctionalSettingType.PICKUP_ANYWHERE))
      .flatMap(pua -> {
        if (FunctionalSetting.isEnabled(pua)) {
          return Flux.from(postgresLocationRepository.getSortedPickupLocations(agency)).collectList();
        } else {
          return Flux.from(postgresLocationRepository.getPickupLocationsByAgencyCode(agency)).collectList();
        }
      })
      .switchIfEmpty(Mono.defer(() ->
        Flux.from(postgresLocationRepository.getSortedPickupLocations(agency)).collectList()
      ))
      .toFuture();  // Convert the Mono<List<Location>> to CompletableFuture<List<Location>>
		};
	}

	public DataFetcher<CompletableFuture<Page<Library>>> getLibrariesDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if (pageno == null) pageno = Integer.valueOf(0);
			if (pagesize == null) pagesize = Integer.valueOf(10);
			if (order == null) order = "fullName";
			if (direction == null) direction = "ASC";

			Sort.Order.Direction orderBy = Sort.Order.Direction.valueOf(direction);

			log.debug("LibrariesDataFetcher::get({},{},{})", pageno, pagesize, query);
			Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, Library.class);
				return Mono.from(postgresLibraryRepository.findAll(spec, pageable)).toFuture();
			}
			return Mono.from(postgresLibraryRepository.findAll(pageable)).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<DataHostLms>> getHostLmsForAgencyDataFetcher() {
		return env -> {
			Agency a = (Agency) env.getSource();
			if (a.getHostLms() != null) {
				UUID hostSystemUUID = a.getHostLms().getId();
				return Mono.from(postgresHostLmsRepository.findById(hostSystemUUID)).toFuture();
			} else {
				Mono<DataHostLms> r = Mono.empty();
				return r.toFuture();
			}
		};
	}


	public DataFetcher<CompletableFuture<DataAgency>> getAgencyForLibraryDataFetcher() {
		return env -> {
			Library l = (Library) env.getSource();
			if (l.getAgencyCode() != null) {
				String agencyCode = l.getAgencyCode();
				return Mono.from(postgresAgencyRepository.findOneByCode(agencyCode)).toFuture();
			} else {
				log.debug("Agency code not available.");
				Mono<DataAgency> r = Mono.empty();
				return r.toFuture();
			}
		};
	}

// Fetcher to get a second Host LMS for Libraries that have one.
	// This is done by navigating the context hierarchy of the first Host LMS (on the agency) to get the code
	// for the second Host LMS, which we then return, if present. If not this just returns empty.
	public DataFetcher<CompletableFuture<DataHostLms>> getSecondHostLmsForLibraryDataFetcher() {
		return env -> {
			Library l = (Library) env.getSource();
			if (l.getAgencyCode() != null) {
				String agencyCode = l.getAgencyCode();
				return Mono.from(postgresAgencyRepository.findOneByCode(agencyCode))
					.flatMap(agency -> {
						if (agency != null) {
							DataHostLms h = agency.getHostLms();
							if (h != null) {
								return Mono.from(postgresHostLmsRepository.findById(h.getId()))
									.flatMap(hostLms -> {
										ArrayList roles = (ArrayList) hostLms.getClientConfig().get("roles");
										ArrayList context = (ArrayList) hostLms.getClientConfig().get("contextHierarchy");
										// we shouldn't be trying to look up if roles is not there or isn't bigger than 2 - this indicates no second Host LMS present
										if (context !=null && !context.isEmpty())
										{
											String hostLmsCode = (String) context.get(1);
											if (roles != null && roles.size() < 2) {
												return Mono.from(postgresHostLmsRepository.findByCode(hostLmsCode));
											}
										}
										return Mono.empty();
									});
							}
						}
						return Mono.empty(); // Return empty Mono here if no second Host LMS is found.
					}).toFuture();
			} else {
				return CompletableFuture.completedFuture(null);
			}
		};
	}

	public DataFetcher<CompletableFuture<Page<LibraryGroup>>> getLibraryGroupsDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if ( pageno == null ) pageno = Integer.valueOf(0);
			if ( pagesize == null ) pagesize = Integer.valueOf(10);
			if ( order == null ) order = "name";
			if ( direction == null ) direction = "ASC";

			Sort.Order.Direction orderBy =  Sort.Order.Direction.valueOf(direction);

			Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, LibraryGroup.class);
				return Mono.from(postgresLibraryGroupRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresLibraryGroupRepository.findAll(pageable)).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<LibraryGroupMember>>> getLibraryGroupMembersDataFetcher() {
		return env -> {
			log.debug("getLibraryGroupMembersDataFetcher args={}/ctx={}/root={}/src={}", env.getArguments(), env.getGraphQlContext(), env.getRoot(), env.getSource());
			return Flux.from(libraryGroupMemberRepository.findByLibraryGroup(env.getSource())).collectList().toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<LibraryGroupMember>>> getLibraryGroupMembersByLibraryDataFetcher() {
		return env -> {
			log.debug("getLibraryGroupMembersDataFetcher args={}/ctx={}/root={}/src={}", env.getArguments(), env.getGraphQlContext(), env.getRoot(), env.getSource());
			return Flux.from(libraryGroupMemberRepository.findByLibrary(env.getSource())).collectList().toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Library>> getLibraryForGroupMemberDataFetcher() {
		return env -> {
			LibraryGroupMember lgm = (LibraryGroupMember) env.getSource();
			return Mono.from(libraryGroupMemberRepository.findLibraryById(lgm.getId())).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<LibraryGroup>> getGroupForGroupMemberDataFetcher() {
		return env -> {
			LibraryGroupMember lgm = (LibraryGroupMember) env.getSource();
			return Mono.from(libraryGroupMemberRepository.findLibraryGroupById(lgm.getId())).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<LibraryGroupMember>> getAllLibraryGroupMembers() {
			return env -> {
				log.debug("Fetching the group members for a given library.");
				return Mono.from(postgresLibraryGroupMemberRepository.findAll()).toFuture();
			};
		}

	public DataFetcher<CompletableFuture<Page<Consortium>>> getConsortiaDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if (pageno == null) pageno = Integer.valueOf(0);
			if (pagesize == null) pagesize = Integer.valueOf(10);
			if (order == null) order = "name";
			if (direction == null) direction = "ASC";

			Sort.Order.Direction orderBy = Sort.Order.Direction.valueOf(direction);

			log.debug("ConsortiaDataFetcher::get({},{},{})", pageno, pagesize, query);
			Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, Consortium.class);
				return Mono.from(postgresConsortiumRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresConsortiumRepository.findAll(pageable)).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Consortium>> getConsortiumForLibraryGroupDataFetcher() {
		return env -> {
			log.debug("Fetching the consortium for a given library group.");
			return Mono.from(postgresConsortiumRepository.findOneByLibraryGroup(env.getSource())).toFuture();
		};


	}

		public DataFetcher<CompletableFuture<List<Person>>> getContactsForLibraryDataFetcher() {
		return env -> {
			log.debug("Fetching the contacts for a given library."+env.getSource());

			return Flux.from(postgresLibraryContactRepository.findByLibrary(env.getSource()))
				.doOnNext(libraryContact -> {
					log.debug("LibraryContact: {}", libraryContact);
				})
				.flatMap(libraryContact -> {
					return Mono.from(postgresPersonRepository.findById(libraryContact.getPerson().getId()))
						.doOnNext(person -> {
							log.debug("Person: {}", person);
						});
				})
				.distinct() // Only return unique Person objects
				.collectList()
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<Person>>> getContactsForConsortiumDataFetcher() {
		return env -> {
			log.debug("Fetching the contacts for a given consortium."+env.getSource());

			return Flux.from(postgresConsortiumContactRepository.findByConsortium(env.getSource()))
				.doOnNext(consortiumContact -> {
					log.debug("ConsortiumContact: {}", consortiumContact);
				})
				.flatMap(consortiumContact -> {
					return Mono.from(postgresPersonRepository.findById(consortiumContact.getPerson().getId()))
						.doOnNext(person -> {
							log.debug("Person: {}", person);
						});
				})
				.distinct() // Only return unique Person objects
				.collectList()
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Role>> getRoleForPersonDataFetcher() {
		return env -> {
			log.debug("Fetching the role for a given person."+env.getSource());
			Person envPerson = (Person) env.getSource();
			UUID personId = envPerson.getId();

			return Mono.from(postgresPersonRepository.findById(personId))
				.doOnNext(person -> {
					log.debug("Person: {}", person);
				})
				.flatMap(person -> {
					return Mono.from(postgresRoleRepository.findById(person.getRole().getId()))
						.doOnNext(role -> {
							log.debug("Role: {} with name {}", role, role.getName());
						});
				})
				.toFuture();
		};
	}

	public DataFetcher<CompletableFuture<Page<Role>>> getRolesDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if (pageno == null) pageno = Integer.valueOf(0);
			if (pagesize == null) pagesize = Integer.valueOf(10);
			if (order == null) order = "name";
			if (direction == null) direction = "ASC";

			Sort.Order.Direction orderBy = Sort.Order.Direction.valueOf(direction);

			log.debug("RoleDataFetcher::get({},{},{})", pageno, pagesize, query);
			Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, Role.class);
				return Mono.from(postgresRoleRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresRoleRepository.findAll(pageable)).toFuture();
		};
	}

	public DataFetcher<CompletableFuture<List<FunctionalSetting>>> getFunctionalSettingsForConsortiumDataFetcher() {
		return env -> {
			log.debug("Fetching the contacts for a given consortium."+env.getSource());

			return Flux.from(postgresConsortiumFunctionalSettingRepository.findByConsortium(env.getSource()))
				.doOnNext(consortiumFunctionalSetting -> {
					log.debug("FunctionalSetting: {}", consortiumFunctionalSetting);
				})
				.flatMap(consortiumFunctionalSetting -> {
					return Mono.from(postgresFunctionalSettingRepository.findById(consortiumFunctionalSetting.getFunctionalSetting().getId()))
						.doOnNext(functionalSetting -> {
							log.debug("Functional Setting: {}", functionalSetting);
						});
				})
				.distinct() // Only return unique Functional Setting objects
				.collectList()
				.toFuture();
		};
	}
	public DataFetcher<CompletableFuture<Page<FunctionalSetting>>> getFunctionalSettingsDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if (pageno == null) pageno = Integer.valueOf(0);
			if (pagesize == null) pagesize = Integer.valueOf(10);
			if (order == null) order = "name";
			if (direction == null) direction = "ASC";

			Sort.Order.Direction orderBy = Sort.Order.Direction.valueOf(direction);

			log.debug("FunctionalSettingDataFetcher::get({},{},{})", pageno, pagesize, query);
			Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, FunctionalSetting.class);
				return Mono.from(postgresFunctionalSettingRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresFunctionalSettingRepository.findAll(pageable)).toFuture();
		};
	}
	public DataFetcher<CompletableFuture<Page<Alarm>>> getAlarmsDataFetcher() {
		return env -> {
			Integer pageno = env.getArgument("pageno");
			Integer pagesize = env.getArgument("pagesize");
			String query = env.getArgument("query");
			String order = env.getArgument("order");
			String direction = env.getArgument("orderBy");

			if (pageno == null) pageno = Integer.valueOf(0);
			if (pagesize == null) pagesize = Integer.valueOf(10);
			if (order == null) order = "name";
			if (direction == null) direction = "ASC";

			Sort.Order.Direction orderBy = Sort.Order.Direction.valueOf(direction);

			log.debug("AlarmDataFetcher::get({},{},{})", pageno, pagesize, query);
			Pageable pageable = Pageable.from(pageno.intValue(), pagesize.intValue()).order(order, orderBy);

			if ((query != null) && (query.length() > 0)) {
				var spec = qs.evaluate(query, Alarm.class);
				return Mono.from(postgresAlarmRepository.findAll(spec, pageable)).toFuture();
			}

			return Mono.from(postgresAlarmRepository.findAll(pageable)).toFuture();
		};
	}
}
