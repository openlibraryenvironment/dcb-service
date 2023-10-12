package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.fallbackBasedUponAvailableStatuses;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.util.List;

import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ItemStatusMapper {
	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	ItemStatusMapper(ReferenceValueMappingRepository referenceValueMappingRepository) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Mono<ItemStatus> mapStatus(String statusCode, String dueDate,
		String hostLmsCode, boolean checkForDueDate, FallbackMapper fallbackStatusMapping) {

		log.debug("mapStatus(statusCode: {}, dueDate: {} hostLmsCode: {}, checkForDueDate: {}, fallbackStatusMapping: {})",
			statusCode, dueDate, hostLmsCode, checkForDueDate, fallbackStatusMapping);

		return Mono.justOrEmpty(statusCode)
			.flatMap(code -> fetchReferenceValueMap(code, hostLmsCode))
			.map(ReferenceValueMapping::getToValue)
			.map(ItemStatusCode::valueOf)
			.defaultIfEmpty(fallbackStatusMapping.map(statusCode))
			.map(itemStatusCode -> checkForDueDate(itemStatusCode, dueDate, checkForDueDate))
			.map(ItemStatus::new);
	}

	private Mono<ReferenceValueMapping> fetchReferenceValueMap(String statusCode, String hostLmsCode) {
		return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToContext(
				"itemStatus", hostLmsCode, statusCode, "DCB"))
			.doOnSuccess(mapping -> log.debug("Found mapping: {} for status code: {} host LMS: {}", mapping, statusCode, hostLmsCode));
	}

	private ItemStatusCode checkForDueDate(ItemStatusCode itemStatusCode,
		String dueDate, boolean checkForDueDate) {

		if (!checkForDueDate) {
			return itemStatusCode;
		}

		return itemStatusCode.equals(AVAILABLE) && isNotEmpty(dueDate)
			? CHECKED_OUT
			: itemStatusCode;
	}

	@FunctionalInterface
	public interface FallbackMapper {
		static FallbackMapper unknownStatusFallback() {
			return statusCode -> UNKNOWN;
		}

		static FallbackMapper fallbackBasedUponAvailableStatuses(String... availableStatusCodes) {
			return fallbackBasedUponAvailableStatuses(List.of(availableStatusCodes));
		}

		static FallbackMapper fallbackBasedUponAvailableStatuses(
			List<String> availableStatusCodes) {

			return statusCode -> (statusCode == null || (statusCode.isEmpty()))
				? handleUnknown(statusCode)
				: availableStatusCodes.contains(statusCode)
					? AVAILABLE
					: UNAVAILABLE;
		}

		static org.olf.dcb.core.model.ItemStatusCode handleUnknown(String statusCode) {
			log.warn("Fallback Mapper :: Unhandled status code {}",statusCode);
			return UNKNOWN;
		}

		ItemStatusCode map(String statusCode);
	}
}
