package org.olf.dcb.core.interaction.sierra;

import jakarta.inject.Singleton;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Status;

import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.storage.NumericRangeMappingRepository;

import java.util.Optional;
import java.util.function.Function;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.model.ItemStatusCode.*;

/**
 *
 */
@Singleton
class SierraItemTypeMapper {
        private static final Logger log = LoggerFactory.getLogger(SierraItemTypeMapper.class);

        private NumericRangeMappingRepository numericRangeMappingRepository;

        public SierraItemTypeMapper(NumericRangeMappingRepository numericRangeMappingRepository) {
                this.numericRangeMappingRepository = numericRangeMappingRepository;
        }

        // Sierra item type comes from fixed field 61 - see https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
        public Mono<String> getCanonicalItemType(String system, String localItemTypeCode) {

                log.debug("map({},{})", system, localItemTypeCode);

                // Sierra item types are integers and they are usually mapped by a range
                // I have a feelig that creating a static cache of system->localItemType mappings will have solid performance
                // benefits
                if ( localItemTypeCode != null ) {
                        try {
                                Long l = Long.valueOf(localItemTypeCode);
                                log.debug("Look up item type {}",l);
                                return Mono.from(numericRangeMappingRepository.findMappedValueFor(system, "ItemType", "DCB", l))
                                        .doOnNext(nrm -> log.debug("nrm: {}",nrm))
                                        .defaultIfEmpty( "UNKNOWN");
                        }
                        catch ( Exception e ) {
                                log.warn("Problem trying to convert {} into  long value",localItemTypeCode);
                        }
                }
                log.warn("No localItemType provided - returning UNKNOWN");
                return Mono.just("UNKNOWN");
        }
}

