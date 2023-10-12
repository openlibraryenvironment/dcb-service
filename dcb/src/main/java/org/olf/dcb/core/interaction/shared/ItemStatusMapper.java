package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Status;

/**
Status is interpreted based upon
 <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
 this documentation</a>
 */
@Singleton
public class ItemStatusMapper {
	private static final Logger log = LoggerFactory.getLogger(ItemResultToItemMapper.class);
	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	ItemStatusMapper(ReferenceValueMappingRepository referenceValueMappingRepository) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Mono<ItemStatus> mapStatus(Status status, String hostLmsCode) {
		log.debug("mapStatus( status: {}, hostLmsCode: {} )", status, hostLmsCode);

		final var statusCode = getValue(status, Status::getCode);
		final var dueDate = getValue(status, Status::getDuedate);

		return Mono.justOrEmpty(statusCode)
			.flatMap(code -> fetchReferenceValueMap(code, hostLmsCode))
			.map(ReferenceValueMapping::getToValue)
			.map(ItemStatusCode::valueOf)
			.defaultIfEmpty( fallbackStatusMapping(statusCode) )
			.map(itemStatusCode -> checkForDueDate(itemStatusCode, dueDate))
			.map(ItemStatus::new);
	}

	private Mono<ReferenceValueMapping> fetchReferenceValueMap(String statusCode, String hostLmsCode) {
		return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToContext(
				"itemStatus", hostLmsCode, statusCode, "DCB"));
	}

	private String getValue(Status status, Function<Status, String> function) {
		return Optional.ofNullable(status).map(function).orElse(null);
	}

	private ItemStatusCode fallbackStatusMapping(String statusCode) {
		final var AVAILABLE_CODES = Arrays.asList("-", "Available");

		return (statusCode == null || (statusCode.isEmpty()))
				? UNKNOWN
				: AVAILABLE_CODES.contains(statusCode)
					? AVAILABLE
					: UNAVAILABLE;
	}

	private ItemStatusCode checkForDueDate(ItemStatusCode itemStatusCode, String dueDate) {
		return itemStatusCode.equals(AVAILABLE) && isNotEmpty(dueDate) ? CHECKED_OUT : itemStatusCode;
	}
}
