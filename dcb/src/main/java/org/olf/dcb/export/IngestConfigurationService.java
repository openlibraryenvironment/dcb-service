package org.olf.dcb.export;

import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class IngestConfigurationService {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(IngestConfigurationService.class);
	
	private final IngestAgencyService agencyService;
	private final IngestHostLmsService hostLmsService;
	private final IngestLibraryContactService libraryContactService;
	private final IngestLibraryGroupService libraryGroupService;
	private final IngestLibraryGroupMemberService libraryGroupMemberService;
	private final IngestLibraryService libraryService;
	private final IngestLocationService locationService;
	private final IngestNumericRangeMappingService numericRangeMappingService;
	private final IngestObjectRulesetService objectRulesetService;
	private final IngestPersonService personService;
	private final IngestReferenceValueMappingService referenceValueMappingService;
	
	public IngestConfigurationService(
		IngestAgencyService agencyService,
		IngestHostLmsService hostLmsService,
		IngestLibraryContactService libraryContactService,
		IngestLibraryGroupService libraryGroupService,
		IngestLibraryGroupMemberService libraryGroupMemberService,
		IngestLibraryService libraryService,
		IngestLocationService locationService,
		IngestNumericRangeMappingService numericRangeMappingService,
		IngestObjectRulesetService objectRulesetService,
		IngestPersonService personService,
		IngestReferenceValueMappingService referenceValueMappingService
	) {
		this.agencyService = agencyService;
		this.hostLmsService = hostLmsService;
		this.libraryContactService = libraryContactService;
		this.libraryService = libraryService;
		this.libraryGroupService = libraryGroupService;
		this.libraryGroupMemberService = libraryGroupMemberService;
		this.locationService = locationService;
		this.numericRangeMappingService = numericRangeMappingService;
		this.objectRulesetService = objectRulesetService;
		this.personService = personService;
		this.referenceValueMappingService = referenceValueMappingService;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// The site configuration, note these are in order of dependencies, so be wary about changeing the order
		objectRulesetService.ingest(siteConfiguration, ingestResult);
		hostLmsService.ingest(siteConfiguration, ingestResult);
		agencyService.ingest(siteConfiguration, ingestResult);
		locationService.ingest(siteConfiguration, ingestResult);
		numericRangeMappingService.ingest(siteConfiguration, ingestResult);
		referenceValueMappingService.ingest(siteConfiguration, ingestResult);

		// Finally the library related config
		libraryService.ingest(siteConfiguration, ingestResult);
		libraryGroupService.ingest(siteConfiguration, ingestResult);
		libraryGroupMemberService.ingest(siteConfiguration, ingestResult);
		personService.ingest(siteConfiguration, ingestResult);
		libraryContactService.ingest(siteConfiguration, ingestResult);
	}
}

