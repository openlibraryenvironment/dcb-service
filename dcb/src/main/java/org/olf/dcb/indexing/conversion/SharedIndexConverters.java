package org.olf.dcb.indexing.conversion;

import java.util.Optional;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.indexing.bulk.BulkSharedIndexService;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.TypeConverter;


@Requires(bean = BulkSharedIndexService.class)
@Factory
public class SharedIndexConverters {

	@Bean
	TypeConverter<ClusterRecord,ClusterRecordIndexDoc> osIndexDocConverter = (object, targetType, context) ->
		Optional.ofNullable(object)
			.map(ClusterRecordIndexDoc::new);
		
}
