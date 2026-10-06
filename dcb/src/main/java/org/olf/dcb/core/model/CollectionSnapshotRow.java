package org.olf.dcb.core.model;

import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

/**
 * One row of BibRepository.getCollectionSnapshot. Which columns are set depends on kind:
 * PROFILE (source, item = works, unique = works no other source holds), FORMAT (source,
 * derivedType, item = works), HOLDERS (holderCount, item = works with that many holders) and
 * HOLDINGS (item = holdings, unique = contributing sources).
 */
@Introspected
public record CollectionSnapshotRow(
	String kind,
	@Nullable UUID sourceSystemId,
	@Nullable String sourceSystemCode,
	@Nullable String derivedType,
	@Nullable Long holderCount,
	Long itemCount,
	@Nullable Long uniqueCount) {
}
