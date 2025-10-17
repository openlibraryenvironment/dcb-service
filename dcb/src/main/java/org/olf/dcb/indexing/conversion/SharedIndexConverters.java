package org.olf.dcb.indexing.conversion;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;
import org.olf.dcb.storage.BibAvailabilityCountRepository;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Factory
@Blocking
@Requires(bean = BulkSharedIndexService.class)
public class SharedIndexConverters {
	
	final HostLmsService hostLmsService;
	final BibAvailabilityCountRepository bibAvailabilityCountRepository;
	
	public SharedIndexConverters( HostLmsService hostLmsService, BibAvailabilityCountRepository bibAvailabilityCountRepository ) {
		this.hostLmsService = hostLmsService;
		this.bibAvailabilityCountRepository = bibAvailabilityCountRepository;
	}
	
	private String idToCodeResolver ( UUID id ) {
		if (id == null) return null;
		
		return hostLmsService.idToCode(id).block();
	}
	

	private Map<String, Collection<BibAvailabilityCount>> readAvailabilityCache ( UUID clusterId ) {
		
		
		if (clusterId == null) return null;
		
		var availability = Flux.from( bibAvailabilityCountRepository.findAllKnownForCluster(clusterId) )
				.collectMultimap(count -> count.getBibId().toString())
				.block();
		
		// log.trace("returning availability [{}]", availability);
		
		return availability;
	}
	
	@Bean
	TypeConverter<ClusterRecord,ClusterRecordIndexDoc> clusterIndexDocConverter = (ClusterRecord object, Class<ClusterRecordIndexDoc> targetType,	ConversionContext context) ->
		 Optional.ofNullable(object)
				.map( cr -> new ClusterRecordIndexDoc(cr, this::idToCodeResolver, readAvailabilityCache( cr.getId() )));
	
		
}
