package org.olf.reshare.dcb.test;

import static java.time.Instant.now;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.ClusterRecord;
import org.olf.reshare.dcb.storage.BibRepository;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class BibRecordFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final BibRepository bibRepository;

	public BibRecordFixture(BibRepository bibRepository) {
		this.bibRepository = bibRepository;
	}

	public void createBibRecord(UUID bibRecordId, UUID sourceSystemId,
		String sourceRecordId, ClusterRecord clusterRecord) {

		Mono.from(bibRepository.save(new BibRecord(bibRecordId, now(), now(),
				sourceSystemId, sourceRecordId, "Brain of the Firm", clusterRecord,
				"", "")))
			.block();
	}

	public void deleteAllBibRecords() {
		dataAccess.deleteAll(bibRepository.findAll(),
			bibRecord -> bibRepository.delete(bibRecord.getId()));
	}
}
