package org.olf.dcb.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.dataimport.job.SourceRecordDataSource;
import org.olf.dcb.dataimport.job.SourceRecordImportJob;
import org.olf.dcb.dataimport.job.SourceRecordService;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.JobCheckpointRepository;
import org.olf.dcb.storage.SourceRecordRepository;

import io.micronaut.json.tree.JsonNode;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

/**
 *  Have created this second host lms service class to avoid a circular dependancy with sourceRecordService as that 
 *  makes calls into host lms service, it could probably be named better or the methods could be elsewhere but I took the easy 
 *  route.
 */
@Slf4j
@Singleton
public class HostLmsService2 {

	private final DataHostLms NULL_DATA_HOST_LMS = new DataHostLms() ;
	private final Mono<DataHostLms> NULL_MONO_DATA_HOST_LMS = Mono.just(NULL_DATA_HOST_LMS);
	private final JsonNode EMPTY_JSON_NODE = JsonNode.createObjectNode(new HashMap<String, JsonNode>());
	
	private final BibRepository bibRepository;
	private final HostLmsRepository hostLmsRepository;
	private final HostLmsService hostLmsService;
	private final JobCheckpointRepository jobCheckpointRepository;
	private final SourceRecordRepository sourceRecordRepository;
	private final SourceRecordService sourceRecordService;

	HostLmsService2(
		BibRepository bibRepository,
		HostLmsRepository hostLmsRepository,
		HostLmsService hostLmsService,
		JobCheckpointRepository jobCheckpointRepository,
		SourceRecordRepository sourceRecordRepository,
		SourceRecordService sourceRecordService
	) {
		this.bibRepository = bibRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.hostLmsService = hostLmsService;
		this.jobCheckpointRepository = jobCheckpointRepository;
		this.sourceRecordRepository = sourceRecordRepository;
		this.sourceRecordService = sourceRecordService;

		// Set a null id for the null data host
		NULL_DATA_HOST_LMS.id = UUIDUtils.ZERO_UUID;
	}

	/**
	 * Retrieves useful details about the host lms in relation to ingest and import 
	 * @param id the identifier for the host lms you want the information about
	 * @return A map containing the relevant information
	 */
	@Transactional
	public Mono<Map<String, Object>> getImportIngestDetails(UUID id) {
		return(Mono.from(hostLmsRepository.findById(id))
			.defaultIfEmpty(NULL_DATA_HOST_LMS)
			.flatMap(hostLms -> getImportIngestDetailsForDataHost(hostLms))
		);
	}
	
	/**
	 * Retrieves useful details about the all the host lms in relation to ingest and import 
	 * @return A list containing the relevant information
	 */
	@Transactional
	public Mono<List<Map<String, Object>>> getAllImportIngestDetails() {
		return(hostLmsService.getAllHostLms()
			.map(hostLms -> getImportIngestDetailsForDataHost(hostLms))
			// This next flatMap seems a bit odd, but it gives up the raw Map which we want and not a Mono<Map>
			// Be wary about changing this as it took me a while to get here
			.flatMap(hostImportIngestDetails -> hostImportIngestDetails)
			.collectList()
		);
	}

	/**
	 * Obtains details about the ingest / import process for a host
	 * @param hostLms The host
	 * @return The import / ingest details for a host
	 */
	protected Mono<Map<String, Object>> getImportIngestDetailsForDataHost(DataHostLms hostLms) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<String> errors = new ArrayList<String>();
		result.put("errors", errors);
		result.put("id", hostLms.id);
		result.put("name", hostLms.name);
		return(hostLmsService.getIngestSourceFor(hostLms)
			.doOnError((Throwable t) -> {
				errors.add("Unable to obtain ingest source for id " + hostLms.id + ", error: " + t.getMessage());
			})
			.flatMap((IngestSource ingestSource) -> {
				Boolean enabled = sourceRecordService.isIngestEnabled((SourceRecordDataSource)ingestSource);
				result.put("ingestEnabled", enabled);
				return(sourceRecordService.createJobInstanceForSource(ingestSource, true)
					.doOnError((Throwable t) -> {
						errors.add("Unable to obtain job instance for id " + hostLms.id + ", error: " + t.getMessage());
					})
					.flatMap((SourceRecordImportJob sourceRecordImportJob) -> {
						UUID checkPointId = sourceRecordImportJob.getId();
						return(Mono.from(jobCheckpointRepository.findCheckpointByJobId(checkPointId))
							.doOnError((Throwable t) -> {
								errors.add("Unable to obtain check point instance for id " + hostLms.id + ", error: " + t.getMessage());
							})
							.defaultIfEmpty(EMPTY_JSON_NODE)
							.flatMap((JsonNode jsonNode) -> {
								result.put("checkPointId", checkPointId);
								result.put("checkPoint", jsonNode);
								return(NULL_MONO_DATA_HOST_LMS);
							})
						);
					})
				);
			})
			.onErrorResume(t -> NULL_MONO_DATA_HOST_LMS)
			.flatMap((a) -> {
				return(Mono.from(sourceRecordRepository.getCountForHostLms(hostLms.id))
					.flatMap((Long recordCount) -> {
						result.put("sourceRecordCount", recordCount);
						return(NULL_MONO_DATA_HOST_LMS);
					})
				);
			})
			.flatMap((a) -> {
				return(Mono.from(bibRepository.getCountForHostLms(hostLms.id))
					.flatMap((Long recordCount) -> {
						result.put("bibRecordCount", recordCount);
						return(NULL_MONO_DATA_HOST_LMS);
					})
				);
			})
			.flatMap((a) -> {
				return(Flux.from(sourceRecordRepository.getProcessStatusForHostLms(hostLms.id))
					.collectList()
					.flatMap((List<RecordCount> processStateCounts) -> {
						result.put("processStates", processStateCounts);
						return(NULL_MONO_DATA_HOST_LMS);
					})
				);
			})
			.flatMap(a -> Mono.just(result))
		);
	}
}
