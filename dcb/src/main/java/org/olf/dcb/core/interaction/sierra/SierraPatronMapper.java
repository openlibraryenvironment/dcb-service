package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static java.util.Collections.singletonList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;
import org.olf.dcb.utils.PropertyAccessUtils;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.patrons.Block;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Slf4j
@Singleton
public class SierraPatronMapper {
	private final NumericPatronTypeMapper numericPatronTypeMapper;

	public SierraPatronMapper(NumericPatronTypeMapper numericPatronTypeMapper) {
		this.numericPatronTypeMapper = numericPatronTypeMapper;
	}

	public Mono<Patron> sierraPatronToHostLmsPatron(
		SierraPatronRecord patronRecord, String hostLmsCode) {

		log.debug("sierraPatronToHostLmsPatron({})", patronRecord);
		final var mappedExpiryDate = parseExpiryDate(patronRecord.getExpirationDate());

		final var result = Patron.builder()
			.localId(singletonList(convertIntegerToString(patronRecord.getId())))
			.localPatronType(convertIntegerToString(patronRecord.getPatronType()))
			.localBarcodes(patronRecord.getBarcodes())
			.localNames(patronRecord.getNames())
			.localHomeLibraryCode(patronRecord.getHomeLibraryCode())
			.isActive(true)
			.isBlocked(isPatronBlocked(patronRecord))
			.isDeleted(patronRecord.getDeleted() != null ? patronRecord.getDeleted() : false)
			.expiryDate(mappedExpiryDate)
			.build();

		if (isEmpty(result.getLocalBarcodes())) {
			log.warn("Returned patron has NO BARCODES : {} -> {}", patronRecord, result);
		}

		return Mono.just(result)
			.flatMap(p -> enrichWithCanonicalPatronType(p, hostLmsCode));
	}

	private static boolean isPatronBlocked(SierraPatronRecord sierraPatronRecord) {
		final var manuallyBlocked = hasCode(sierraPatronRecord.getBlockInfo());
		final var automaticallyBlocked = hasCode(sierraPatronRecord.getAutoBlockInfo());

		return manuallyBlocked || automaticallyBlocked;
	}

	private static boolean hasCode(@Nullable Block blockInfo) {
		final var blockCode = getValueOrNull(blockInfo, Block::getCode);

		return isNotEmpty(blockCode) && isNotHyphen(blockCode);
	}

	private static boolean isNotHyphen(String blockCode) {
		return !blockCode.equals("-");
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron patron, String hostLmsCode) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(hostLmsCode,
				patron.getLocalPatronType(), patron.getFirstLocalId())
			.map(patron::setCanonicalPatronType)
			.defaultIfEmpty(patron);
	}

	private Date parseExpiryDate(String expirationDateStr) {
		if (expirationDateStr != null && !expirationDateStr.trim().isEmpty()) {
			try {
				LocalDate localDate = LocalDate.parse(expirationDateStr);
				return Date.from(localDate.atStartOfDay(ZoneId.of("UTC")).toInstant());
			} catch (DateTimeParseException e) {
				log.warn("Could not parse Sierra expiration date: {}. Patron will be mapped without an expiry date.", expirationDateStr);
			}
		}
		return null;
	}
}
