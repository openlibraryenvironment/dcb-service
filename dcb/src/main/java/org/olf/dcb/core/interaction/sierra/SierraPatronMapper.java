package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static java.util.Collections.singletonList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.shared.NumericPatronTypeMapper;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.patrons.Block;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;

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

		final var result = Patron.builder()
			.localId(singletonList(convertIntegerToString(patronRecord.getId())))
			.localPatronType(convertIntegerToString(patronRecord.getPatronType()))
			.localBarcodes(patronRecord.getBarcodes())
			.localNames(patronRecord.getNames())
			.localHomeLibraryCode(patronRecord.getHomeLibraryCode())
			.blocked(isPatronBlocked(patronRecord))
			.isDeleted(patronRecord.getDeleted() != null ? patronRecord.getDeleted() : false)
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
		return isNotEmpty(getValue(blockInfo, Block::getCode));
	}

	private Mono<Patron> enrichWithCanonicalPatronType(Patron p, String hostLmsCode) {
		return numericPatronTypeMapper.mapLocalPatronTypeToCanonical(hostLmsCode,
				p.getLocalPatronType(), p.getLocalId().stream().findFirst().orElse(null))
			.map(p::setCanonicalPatronType)
			.defaultIfEmpty(p);
	}
}
