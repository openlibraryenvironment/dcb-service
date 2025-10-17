package org.olf.dcb.test;

import io.micronaut.context.annotation.Prototype;
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

		Map<String, Object> bookInfo = new HashMap<>();
		bookInfo.put("author", Author.builder().name("Stafford Beer").build());
		bookInfo.put("title", "Brain of the Firm");

		Mono.from(bibRepository.save(
				BibRecord
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
					.derivedType("Book")
					.canonicalMetadata(bookInfo)
					.build()
			))
			.block();
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
