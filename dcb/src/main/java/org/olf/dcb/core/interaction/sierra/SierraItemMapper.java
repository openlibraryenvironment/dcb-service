package org.olf.dcb.core.interaction.sierra;

import static java.lang.Boolean.FALSE;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.fallbackBasedUponAvailableStatuses;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.rules.ObjectRuleset;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.VarField;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.items.Status;

@Slf4j
@Singleton
public class SierraItemMapper {
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final String VOLUME_FIELD_TAG="v";

	/**
	 Status is interpreted based upon
	 <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
	 this documentation</a>
	 */
	public static ItemStatusMapper.FallbackMapper sierraItemStatusFallback() {
		return fallbackBasedUponAvailableStatuses("-");
	}

	SierraItemMapper(ItemStatusMapper itemStatusMapper, NumericItemTypeMapper itemTypeMapper,
		LocationToAgencyMappingService locationToAgencyMappingService) {

		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
	}
	
	private boolean deriveItemSuppressedFlag( @NonNull SierraItem item, @NonNull Optional<ObjectRuleset> itemSuppressionRules) {
		
		if ( Boolean.TRUE.equals(item.getSuppressed()) ) return true;
		
		// Grab the suppression rules set against the Host Lms
		// False is the default value for suppression if we can't find the named ruleset
		// or if there isn't one.
		return itemSuppressionRules
		  .map( rules -> rules.negate().test(item) ) // Negate as the rules evaluate "true" for inclusion
		  .orElse(FALSE);
	}

	public Mono<Item> mapResultToItem( SierraItem itemResult, String hostLmsCode, String localBibId, @NonNull Optional<ObjectRuleset> itemSuppressionRules ) {
		log.debug("mapResultToItem({}, {}, {})", itemResult, hostLmsCode, localBibId);

		final var statusCode = getValueOrNull(itemResult.getStatus(), Status::getCode);
		final var dueDate = getValueOrNull(itemResult.getStatus(), Status::getDuedate);

		final String rawVolumeStatement = extractRawVolumeStatement(itemResult.getVarFields());
		final String parsedVolumeStatement = parseVolumeStatement(rawVolumeStatement);

		// Sierra item type comes from fixed field 61 - see https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
		// We need to be looking at getLocalItemTypeCode - getLocalItemType is giving us a human-readable string at the moment
		return itemStatusMapper.mapStatus(statusCode, dueDate, hostLmsCode, sierraItemStatusFallback())
			.map(itemStatus -> Item.builder()
				.localId(itemResult.getId())
				.status(itemStatus)
				.dueDate(parsedDueDate(itemResult))
				.location(org.olf.dcb.core.model.Location.builder()
					.code(itemResult.getLocation().getCode().trim())
					.name(itemResult.getLocation().getName())
					.build())
				.barcode(itemResult.getBarcode())
				.callNumber(itemResult.getCallNumber())
				.holdCount(itemResult.getHoldCount())
				.localBibId(localBibId)
				.localItemType(itemResult.getItemType())
				.localItemTypeCode(determineLocalItemTypeCode(itemResult.getFixedFields()))
				.deleted(itemResult.getDeleted())
				.suppressed(deriveItemSuppressedFlag(itemResult, itemSuppressionRules))
				.rawVolumeStatement(rawVolumeStatement)
				.parsedVolumeStatement(parsedVolumeStatement)
				.build())
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, hostLmsCode))
			.flatMap(itemTypeMapper::enrichItemWithMappedItemType);

	}

	@Nullable
	private Instant parsedDueDate(SierraItem result) {
		return Optional.ofNullable(result.getStatus())
			.map(Status::getDuedate)
			.filter(StringUtils::isNotEmpty)
			.map(Instant::parse)
			.orElse(null);
	}

	private String determineLocalItemTypeCode(Map<Integer, FixedField> fixedFields) {
		// We should look into result.fixedFields for 61 here and set itemType according to that code
		// and not the human-readable text
		final var FIXED_FIELD_61 = 61;

		String localItemTypeCode = null;

		if (fixedFields != null) {
			final var fixedField61 = fixedFields.get(FIXED_FIELD_61);

			if (fixedField61 != null) {
				final var value = fixedField61.getValue();

				if (value != null) {
					localItemTypeCode = value.toString();
				}
			}
		}

		return localItemTypeCode;
	}

	private String extractRawVolumeStatement(List<VarField> varFields) {

		String result = null;

		if ( varFields != null ) {
			// Volume information can be found in a varField with fieldTag="v" and the value is in the "content" field
			Optional<VarField> volumeVarField = varFields.stream()
				.filter(varField -> varField.getFieldTag().equals(VOLUME_FIELD_TAG) )
				.findFirst();

			if (volumeVarField.isPresent()) {
				VarField foundObject = volumeVarField.get();
				// log.debug("Extracting {}",foundObject);
				result = foundObject.getContent();
			}
			else {
			}
		}

		// log.debug("Result of volume extraction {}",result);
		return result;
	}

	private String parseVolumeStatement(String volumeStatement) {

		// \b(?:Vol\.? ?|v)(\d+)(?:,? ?Item \d+)?\b
		// Explanation:
    //   \b: Word boundary to ensure that the pattern is matched as a whole word.
    //   (?:Vol\.? ?|v): Non-capturing group to match either "Vol", "Vol.", "v" or "v" followed by an optional period and space.
    //   (\d+): Capturing group to match one or more digits representing the volume.
    //   (?:,? ?Item \d+)?: Optional non-capturing group to match an optional comma, optional space, "Item", a space, and one or more digits. This is to handle cases like "Vol 1, Item 1".
    //   \b: Word boundary to complete the pattern matching.

		String result = null;
		if ( volumeStatement != null ) {

			// v.1, p:1 -> v1p1
			result = volumeStatement
				.toLowerCase()
				.replaceAll("\\p{Punct}", " ")	// remove punctuation
				.replaceAll(" ", "")	// remove spaces
				.replaceAll("pt", "p") // pt->p
				.replaceAll("no", "n") // no->n
				;
			// result = volumeStatement;
		}
		return result;
	}
}
