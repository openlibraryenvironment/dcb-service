package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.clustering.ClusterRecord;
import org.olf.reshare.dcb.ingest.model.Author;
import org.olf.reshare.dcb.storage.BibIdentifierRepository;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.MatchPointRepository;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.time.Instant.now;
import static org.olf.reshare.dcb.utils.DCBStringUtilities.generateBlockingString;

@Prototype
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

	public void deleteAllBibRecords() {

		dataAccess.deleteAll(matchPointRepository.findAll(),
			bibIdentifierRecord -> matchPointRepository.delete(bibIdentifierRecord.getId()));
		
		dataAccess.deleteAll(bibIdentifierRepository.findAll(),
			bibIdentifierRecord -> bibIdentifierRepository.delete(bibIdentifierRecord.getId()));
		dataAccess.deleteAll(bibRepository.findAll(),
			bibRecord -> bibRepository.delete(bibRecord.getId()));
	}
}
