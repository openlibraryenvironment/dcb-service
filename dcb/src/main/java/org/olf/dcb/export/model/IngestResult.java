package org.olf.dcb.export.model;

import java.util.ArrayList;
import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Accessors(chain = true)
@Data
@ExcludeFromGeneratedCoverageReport
@Serdeable
@ToString
public class IngestResult {
	public ProcessingResult agencies = new ProcessingResult();
	public ProcessingResult lmsHosts = new ProcessingResult();
	public ProcessingResult libraries = new ProcessingResult();
	public ProcessingResult libraryContacts = new ProcessingResult();
	public ProcessingResult libraryGroupMembers = new ProcessingResult();
	public ProcessingResult libraryGroups = new ProcessingResult();
	public ProcessingResult locations = new ProcessingResult();
	public ProcessingResult numericRangeMappings = new ProcessingResult();
	public ProcessingResult objectRulesets = new ProcessingResult();
	public ProcessingResult persons = new ProcessingResult();
	public ProcessingResult referenceValueMappings = new ProcessingResult();
	public List<String> messages = new ArrayList<String>();
	
	public int getTotalSuccessful() {
		return (
			agencies.getSuccessful() +
			lmsHosts.getSuccessful() +
			libraries.getSuccessful() +
			libraryContacts.getSuccessful() +
			libraryGroupMembers.getSuccessful() +
			libraryGroups.getSuccessful() +
			locations.getSuccessful() +
			numericRangeMappings.getSuccessful() +
			objectRulesets.getSuccessful() +
			persons.getSuccessful() +
			referenceValueMappings.getSuccessful()
		);
	}
	
	public int getTotalFailed() {
		return (
			agencies.getFailed() +
			lmsHosts.getFailed() +
			libraries.getFailed() +
			libraryContacts.getFailed() +
			libraryGroupMembers.getFailed() +
			libraryGroups.getFailed() +
			locations.getFailed() +
			numericRangeMappings.getFailed() +
			objectRulesets.getFailed() +
			persons.getFailed() +
			referenceValueMappings.getFailed()
		);
	}
}
