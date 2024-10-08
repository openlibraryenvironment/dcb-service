package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ExportService {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ExportService.class);
	
	private final ExportAgencyService agencyService;
	private final ExportHostLmsService hostLmsService;
	private final ExportLibraryContactService libraryContactService;
	private final ExportLibraryGroupService libraryGroupService;
	private final ExportLibraryGroupMemberService libraryGroupMemberService;
	private final ExportLibraryService libraryService;
	private final ExportLocationService locationService;
	private final ExportNumericRangeMappingService numericRangeMappingService;
	private final ExportObjectRulesetService objectRulesetService;
	private final ExportPersonService personService;
	private final ExportReferenceValueMappingService referenceValueMappingService;
	
	public ExportService(
		ExportAgencyService agencyService,
		ExportHostLmsService hostLmsService,
		ExportLibraryContactService libraryContactService,
		ExportLibraryGroupService libraryGroupService,
		ExportLibraryGroupMemberService libraryGroupMemberService,
		ExportLibraryService libraryService,
		ExportLocationService locationService,
		ExportNumericRangeMappingService numericRangeMappingService,
		ExportObjectRulesetService objectRulesetService,
		ExportPersonService personService,
		ExportReferenceValueMappingService referenceValueMappingService
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

	public Map<String, Object> export(
		Collection<UUID> hostLmsIds,
		Map<String, Object> result,
		List<String> errors
	) {
		// The values we need to lookup the reference mappings with
		List<String> contextValues = new ArrayList<String>();
		List<String> rulesetNames = new ArrayList<String>();
		List<String> agencyCodes = new ArrayList<String>();
		List<UUID> libraryIds = new ArrayList<UUID>();
		List<UUID> personIds = new ArrayList<UUID>();

		// The lists contextValues and rulesetNames are populated by hostLmsService.export so this needs to be executed before these lists are used 
		hostLmsService.export(hostLmsIds, contextValues, rulesetNames, result, errors);
		
		// The list agencyCodes is populated by agencyService.export so this needs to be executed before this list is used 
		agencyService.export(hostLmsIds, agencyCodes, result, errors);
		
		// Now for the rest of the configuration
		locationService.export(hostLmsIds, result, errors);
		numericRangeMappingService.export(contextValues, result, errors);
		objectRulesetService.export(rulesetNames, result, errors);
		referenceValueMappingService.export(contextValues, result, errors);

		// Finally the library related config
		libraryService.export(agencyCodes, libraryIds, result, errors);
		libraryGroupService.export(libraryIds, result, errors);
		libraryGroupMemberService.export(libraryIds, result, errors);
		libraryContactService.export(libraryIds, personIds, result, errors);
		personService.export(personIds, result, errors);

		return(result);
	}
}
