package org.olf.dcb.core.interaction.sierra;

import jakarta.inject.Singleton;
import org.olf.dcb.storage.AgencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.SierraItem;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import java.time.ZonedDateTime;

import static io.micronaut.core.util.StringUtils.isNotEmpty;

@Singleton
class ItemResultToItemMapper {
	private static final Logger log = LoggerFactory.getLogger(ItemResultToItemMapper.class);

	private final SierraItemStatusMapper sierraItemStatusMapper;
	private final SierraItemTypeMapper sierraItemTypeMapper;
        private final ReferenceValueMappingRepository referenceValueMappingRepository;
				private final AgencyRepository agencyRepository;
        private static final Integer FIXED_FIELD_61 = Integer.valueOf(61);


	ItemResultToItemMapper(SierraItemStatusMapper sierraItemStatusMapper,
                               SierraItemTypeMapper sierraItemTypeMapper,
                               ReferenceValueMappingRepository referenceValueMappingRepository, AgencyRepository agencyRepository) {
		this.sierraItemStatusMapper = sierraItemStatusMapper;
		this.sierraItemTypeMapper = sierraItemTypeMapper;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.agencyRepository = agencyRepository;
	}

	Mono<org.olf.dcb.core.model.Item> mapResultToItem(SierraItem result, String hostLmsCode, String bibId) {
		// log.debug("mapResultToItem({}, {})", result, hostLmsCode);

			final var dueDate = result.getStatus().getDuedate();

			final var parsedDueDate = isNotEmpty(dueDate)
				? ZonedDateTime.parse(dueDate)
				: null;

                        // We shouldlook into result.fixedFields for 61 here and set itemType according to that code and not
                        // the human readable text
                        String localItemTypeCode = null; 
                        if ( result.getFixedFields() != null ) {
                                if ( result.getFixedFields().get(FIXED_FIELD_61) != null ) {
                                        localItemTypeCode = result.getFixedFields().get(FIXED_FIELD_61).getValue().toString();
                                }
                        }

                        final var flitc = localItemTypeCode;

			return sierraItemStatusMapper.mapStatus(result.getStatus(), hostLmsCode)
				.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
					.id(result.getId())
					.status(itemStatus)
					.dueDate(parsedDueDate)
					.location(org.olf.dcb.core.model.Location.builder()
						.code(result.getLocation().getCode().trim())
						.name(result.getLocation().getName())
						.build())
					.barcode(result.getBarcode())
					.callNumber(result.getCallNumber())
					.hostLmsCode(hostLmsCode)
					.holdCount(result.getHoldCount())
					.bibId(bibId)
                                        .localItemType(result.getItemType())
                                        .localItemTypeCode(flitc)
                                        .deleted(result.getDeleted())
                                        .suppressed(result.getSuppressed())
					.build())
                                .flatMap(item -> enrichItemWithMappedItemType(item, hostLmsCode))
                                .flatMap(item -> enrichItemAgencyFromShelvingLocation(item, hostLmsCode, result.getLocation().getCode().trim()))
                                ;
	}

        Mono<org.olf.dcb.core.model.Item> enrichItemAgencyFromShelvingLocation(org.olf.dcb.core.model.Item item, String hostSystem, String itemShelvingLocation) {
                log.debug("map shelving location to agency  {}:\"{}\"",hostSystem,itemShelvingLocation);
                return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
									"ShelvingLocation", hostSystem, itemShelvingLocation, "AGENCY", "DCB"))
									.flatMap(rvm -> Mono.from(agencyRepository.findOneByCode( rvm.getToValue() )))
									.map(dataAgency -> {
										item.setAgencyCode( dataAgency.getCode() );
										item.setAgencyDescription( dataAgency.getName() );
										return item;
									})
									.defaultIfEmpty(item);
        }

        Mono<org.olf.dcb.core.model.Item> enrichItemWithMappedItemType(org.olf.dcb.core.model.Item item, String hostSystem) {
                // We need to be looking at getLocalItemTypeCode - getLocalItemType is giving us a human readable string at the moment
                // Sierra items should have a fixedField 61 according to https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
                return sierraItemTypeMapper.getCanonicalItemType(hostSystem, item.getLocalItemTypeCode())
                        .defaultIfEmpty("UNKNOWN")
                        .map( mappedType -> item.setCanonicalItemType(mappedType) );
        }
}
