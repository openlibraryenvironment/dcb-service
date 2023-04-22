package org.olf.reshare.dcb.test;

import static java.time.Instant.now;
import static org.olf.reshare.dcb.utils.DCBStringUtilities.generateBlockingString;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.storage.BibRepository;
import org.olf.reshare.dcb.storage.BibIdentifierRepository;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class BibRecordFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final BibRepository bibRepository;
	private final BibIdentifierRepository bibIdentifierRepository;

	public BibRecordFixture(BibRepository bibRepository, BibIdentifierRepository bibIdentifierRepository) {
		this.bibRepository = bibRepository;
		this.bibIdentifierRepository = bibIdentifierRepository;
	}

	public void createBibRecord(UUID bibRecordId, UUID sourceSystemId,
		String sourceRecordId, ClusterRecord clusterRecord) {

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
					.build()
			))
			.block();
	}

	public void deleteAllBibRecords() {
		dataAccess.deleteAll(bibIdentifierRepository.findAll(),
			bibIdentifierRecord -> bibIdentifierRepository.delete(bibIdentifierRecord.getId()));
		dataAccess.deleteAll(bibRepository.findAll(),
			bibRecord -> bibRepository.delete(bibRecord.getId()));
	}
}
