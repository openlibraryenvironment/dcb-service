package org.olf.dcb.indexing.conversion;

import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

@Requires(bean = BulkSharedIndexService.class)
@Factory
public class SharedIndexConverters {
	
	final HostLmsService hostLmsService;
	
	public SharedIndexConverters( HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}
	
	private String idToCodeResolver ( UUID id ) {
		if (id == null) return null;
		
		return hostLmsService.idToCode(id).block();
	}
	
	@Bean
	TypeConverter<ClusterRecord,ClusterRecordIndexDoc> clusterIndexDocConverter = (ClusterRecord object, Class<ClusterRecordIndexDoc> targetType,	ConversionContext context) ->
		 Optional.ofNullable(object)
				.map( cr -> new ClusterRecordIndexDoc(cr, this::idToCodeResolver ));
	
		
}
