package org.olf.dcb.test;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.ingest.model.Author;
import org.olf.dcb.storage.BibIdentifierRepository;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.MatchPointRepository;

import static java.time.Instant.now;
import static org.olf.dcb.utils.DCBStringUtilities.generateBlockingString;

@Singleton
public class BibRecordFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final BibRepository bibRepository;
	private final BibIdentifierRepository bibIdentifierRepository;
	private final MatchPointRepository matchPointRepository;

	public BibRecordFixture(BibRepository bibRepository,
		BibIdentifierRepository bibIdentifierRepository,
		MatchPointRepository matchPointRepository) {

		this.bibRepository = bibRepository;
		this.bibIdentifierRepository = bibIdentifierRepository;
		this.matchPointRepository = matchPointRepository;
	}

	public void createBibRecord(UUID bibRecordId, UUID sourceSystemId,
		String sourceRecordId, ClusterRecord clusterRecord) {

		createBibRecord(bibRecordId, sourceSystemId, sourceRecordId, clusterRecord, "Book");
	}

	/**
	 * @param derivedType null leaves the column unset, which is what an ingest that could not
	 *   derive a type produces - derived_type is varchar(32) with no NOT NULL.
	 */
	public void createBibRecord(UUID bibRecordId, UUID sourceSystemId,
		String sourceRecordId, ClusterRecord clusterRecord, @Nullable String derivedType) {

		Map<String, Object> bookInfo = new HashMap<>();
		bookInfo.put("author", Author.builder().name("Stafford Beer").build());
		bookInfo.put("title", "Brain of the Firm");

		final var bibRecord = BibRecord
			.builder()
			.id(bibRecordId)
			.dateCreated(now())
			.dateUpdated(now())
			.sourceRecordId(sourceRecordId)
			.sourceSystemId(sourceSystemId)
			.title("Brain of the Firm")
			.contributesTo(clusterRecord)
			.blockingTitle(generateBlockingString("Brain of the Firm"))
			.recordStatus("a")
			.typeOfRecord("b")
			.canonicalMetadata(bookInfo);

		if (derivedType != null) {
			bibRecord.derivedType(derivedType);
		}

		Mono.from(bibRepository.save(bibRecord.build())).block();
	}

	public void deleteAll() {

		dataAccess.deleteAll(matchPointRepository.queryAll(),
			bibIdentifierRecord -> matchPointRepository.delete(bibIdentifierRecord.getId()));
		
		dataAccess.deleteAll(bibIdentifierRepository.queryAll(),
			bibIdentifierRecord -> bibIdentifierRepository.delete(bibIdentifierRecord.getId()));
		dataAccess.deleteAll(bibRepository.queryAll(),
			bibRecord -> bibRepository.delete(bibRecord.getId()));
	}
}
