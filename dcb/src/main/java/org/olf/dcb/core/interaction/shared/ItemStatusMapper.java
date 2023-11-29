package org.olf.dcb.core.interaction.shared;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.fallbackBasedUponAvailableStatuses;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.Status;

/**
Status is interpreted based upon
 <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
 this documentation</a>
 */
@Slf4j
@Singleton
public class ItemStatusMapper {
	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	ItemStatusMapper(ReferenceValueMappingRepository referenceValueMappingRepository) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Mono<ItemStatus> mapStatus(Status status, String hostLmsCode,
		FallbackMapper fallbackStatusMapping) {

		log.debug("mapStatus( status: {}, hostLmsCode: {} )", status, hostLmsCode);

		final var statusCode = getValue(status, Status::getCode);
		final var dueDate = getValue(status, Status::getDuedate);

		return Mono.justOrEmpty(statusCode)
			.flatMap(code -> fetchReferenceValueMap(code, hostLmsCode))
			.map(ReferenceValueMapping::getToValue)
			.map(ItemStatusCode::valueOf)
			.defaultIfEmpty(fallbackStatusMapping.map(statusCode))
			.map(itemStatusCode -> checkForDueDate(itemStatusCode, dueDate))
			.map(ItemStatus::new);
	}

	public Mono<ItemStatus> mapStatus(String statusCode, String hostLmsCode,
		FallbackMapper fallbackMapper) {

		return Mono.justOrEmpty(statusCode)
			.flatMap(code -> fetchReferenceValueMap(code, hostLmsCode))
			.map(ReferenceValueMapping::getToValue)
			.map(ItemStatusCode::valueOf)
			.defaultIfEmpty(fallbackMapper.map(statusCode))
			.map(ItemStatus::new);
	}

	private Mono<ReferenceValueMapping> fetchReferenceValueMap(String statusCode, String hostLmsCode) {
		return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToContext(
				"itemStatus", hostLmsCode, statusCode, "DCB"))
			.doOnSuccess(mapping -> log.debug("Found mapping: {} for status code: {} host LMS: {}", mapping, statusCode, hostLmsCode));
	}

	private String getValue(Status status, Function<Status, String> function) {
		return Optional.ofNullable(status).map(function).orElse(null);
	}

	private ItemStatusCode checkForDueDate(ItemStatusCode itemStatusCode, String dueDate) {
		return itemStatusCode.equals(AVAILABLE) && isNotEmpty(dueDate) ? CHECKED_OUT : itemStatusCode;
	}

	@FunctionalInterface
	public interface FallbackMapper {
		static FallbackMapper sierraFallback() {
			return fallbackBasedUponAvailableStatuses("-");
		}

		static FallbackMapper polarisFallback() {
			return fallbackBasedUponAvailableStatuses("In");
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
