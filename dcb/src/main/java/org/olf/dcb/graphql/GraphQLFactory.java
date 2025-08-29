package org.olf.dcb.graphql;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.SchemaMissingError;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://github.com/graphql-java/graphql-java-extended-scalars
import graphql.scalars.ExtendedScalars;


// See : https://lifeinide.com/post/2019-04-15-micronaut-graphql-with-transaction-and-security-support/

@Factory
public class GraphQLFactory {

	private static Logger log = LoggerFactory.getLogger(GraphQLFactory.class);

	@Bean
	@Singleton
	public GraphQL graphQL(
			CreateAgencyGroupDataFetcher createAgencyGroupDataFetcher,
			AddAgencyToGroupDataFetcher addAgencyToGroupDataFetcher,
			CreateLibraryDataFetcher createLibraryDataFetcher,
			ResourceResolver resourceResolver,
			InstanceClusterDataFetcher instanceClusterDataFetcher,
			SourceBibDataFetcher sourceBibDataFetcher, AddLibraryToGroupDataFetcher addLibraryToGroupDataFetcher,
			CreateLibraryGroupDataFetcher createLibraryGroupDataFetcher,
			CreateConsortiumDataFetcher createConsortiumDataFetcher,
			UpdateAgencyParticipationStatusDataFetcher updateAgencyParticipationStatusDataFetcher,
			DeleteLibraryDataFetcher deleteLibraryDataFetcher,
			DeleteLocationDataFetcher deleteLocationDataFetcher, UpdateLocationDataFetcher updateLocationDataFetcher,
			UpdateLibraryDataFetcher updateLibraryDataFetcher, UpdateContactDataFetcher updateContactDataFetcher,
			UpdateReferenceValueMappingDataFetcher updateReferenceValueMappingDataFetcher,
			UpdateNumericRangeMappingDataFetcher updateNumericRangeMappingDataFetcher,
			DeleteReferenceValueMappingDataFetcher deleteReferenceValueMappingDataFetcher,
			DeleteNumericRangeMappingDataFetcher deleteNumericRangeMappingDataFetcher,
			UpdateConsortiumDataFetcher updateConsortiumDataFetcher,
			UpdateFunctionalSettingDataFetcher updateFunctionalSettingDataFetcher,
			CreateContactDataFetcher createContactDataFetcher, DeleteConsortiumDataFetcher deleteConsortiumDataFetcher,
			CreateRoleDataFetcher createRoleDataFetcher, UpdateRoleDataFetcher updateRoleDataFetcher,
			CreateFunctionalSettingDataFetcher createFunctionalSettingDataFetcher,
			CreateReferenceValueMappingDataFetcher createReferenceValueMappingDataFetcher,
			CreateLocationDataFetcher createLocationDataFetcher, CreateLibraryContactDataFetcher createLibraryContactDataFetcher,
			DeleteLibraryContactDataFetcher deleteLibraryContactDataFetcher,
			DeleteConsortiumContactDataFetcher deleteConsortiumContactDataFetcher,
			UpdateConsortialMaxLoansDataFetcher updateConsortialMaxLoansDataFetcher,
			DataFetchers dataFetchers) {

		log.debug("GraphQLFactory::graphQL");

		SchemaParser schemaParser = new SchemaParser();
		SchemaGenerator schemaGenerator = new SchemaGenerator();

		// Load the schema
		InputStream schemaDefinition = resourceResolver.getResourceAsStream("classpath:schema.graphqls")
				.orElseThrow(SchemaMissingError::new);

		// Parse the schema and merge it into a type registry
		TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
		typeRegistry.merge(schemaParser.parse(new BufferedReader(new InputStreamReader(schemaDefinition))));

		log.debug("Add runtime wiring");

		RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
				.type(TypeRuntimeWiring.newTypeWiring("Query")
						.dataFetcher("agencies", dataFetchers.getAgenciesDataFetcher())
						.dataFetcher("hostLms", dataFetchers.getHostLMSDataFetcher())
						.dataFetcher("locations", dataFetchers.getLocationsDataFetcher())
						.dataFetcher("agencyGroups", dataFetchers.getPaginatedAgencyGroupsDataFetcher())
						.dataFetcher("patronRequests", dataFetchers.getPatronRequestsDataFetcher())
						.dataFetcher("supplierRequests", dataFetchers.getSupplierRequestsDataFetcher())
						.dataFetcher("inactiveSupplierRequests", dataFetchers.getInactiveSupplierRequestsDataFetcher())
						.dataFetcher("instanceClusters", instanceClusterDataFetcher)
						.dataFetcher("sourceBibs", sourceBibDataFetcher)
						.dataFetcher("processStates", dataFetchers.getProcessStateDataFetcher())
						.dataFetcher("audits", dataFetchers.getAuditsDataFetcher())
						.dataFetcher("patronIdentities", dataFetchers.getPatronIdentitiesDataFetcher())
						.dataFetcher("numericRangeMappings", dataFetchers.getNumericRangeMappingsDataFetcher())
						.dataFetcher("referenceValueMappings", dataFetchers.getReferenceValueMappingsDataFetcher())
						.dataFetcher("pickupLocations", dataFetchers.getPickupLocationsDataFetcher())
						.dataFetcher("libraries", dataFetchers.getLibrariesDataFetcher())
						.dataFetcher("consortia", dataFetchers.getConsortiaDataFetcher())
						.dataFetcher("libraryGroups", dataFetchers.getLibraryGroupsDataFetcher())
						.dataFetcher("libraryGroupMembers", dataFetchers.getAllLibraryGroupMembers())
						.dataFetcher("dataChangeLog", dataFetchers.getDataChangeLogDataFetcher())
						.dataFetcher("roles", dataFetchers.getRolesDataFetcher())
						.dataFetcher("functionalSettings", dataFetchers.getFunctionalSettingsDataFetcher())
						.dataFetcher("alarms", dataFetchers.getAlarmsDataFetcher())
				)
				.type("Mutation",
					typeWiring -> typeWiring
						.dataFetcher("createAgencyGroup", createAgencyGroupDataFetcher)
						.dataFetcher("addAgencyToGroup", addAgencyToGroupDataFetcher)
						.dataFetcher("createLibrary", createLibraryDataFetcher)
						.dataFetcher("createLibraryGroup", createLibraryGroupDataFetcher)
						.dataFetcher("createConsortium", createConsortiumDataFetcher)
						.dataFetcher("addLibraryToGroup", addLibraryToGroupDataFetcher)
						.dataFetcher("updateAgencyParticipationStatus", updateAgencyParticipationStatusDataFetcher)
						.dataFetcher("deleteLibrary", deleteLibraryDataFetcher)
						.dataFetcher("deleteLocation", deleteLocationDataFetcher)
						.dataFetcher("updateLocation", updateLocationDataFetcher)
						.dataFetcher("updateLibrary", updateLibraryDataFetcher)
						.dataFetcher("updatePerson", updateContactDataFetcher)
						.dataFetcher("updateReferenceValueMapping", updateReferenceValueMappingDataFetcher)
						.dataFetcher("updateNumericRangeMapping", updateNumericRangeMappingDataFetcher)
						.dataFetcher("deleteReferenceValueMapping", deleteReferenceValueMappingDataFetcher)
						.dataFetcher("deleteNumericRangeMapping", deleteNumericRangeMappingDataFetcher)
						.dataFetcher("updateConsortium", updateConsortiumDataFetcher)
						.dataFetcher("updateFunctionalSetting", updateFunctionalSettingDataFetcher)
						.dataFetcher("createContact", createContactDataFetcher)
						.dataFetcher("deleteContact", deleteConsortiumContactDataFetcher)
						.dataFetcher("deleteConsortium", deleteConsortiumDataFetcher)
						.dataFetcher("createRole", createRoleDataFetcher)
						.dataFetcher("updateRole", updateRoleDataFetcher)
						.dataFetcher("createFunctionalSetting", createFunctionalSettingDataFetcher)
						.dataFetcher("createReferenceValueMapping", createReferenceValueMappingDataFetcher)
						.dataFetcher("createLocation", createLocationDataFetcher)
						.dataFetcher("createLibraryContact", createLibraryContactDataFetcher)
						.dataFetcher("deleteLibraryContact", deleteLibraryContactDataFetcher)
						.dataFetcher("updateConsortialMaxLoans", updateConsortialMaxLoansDataFetcher))
			.type("Agency",
					typeWiring -> typeWiring
						.dataFetcher("locations", dataFetchers.getAgencyLocationsDataFetcher())
						.dataFetcher("hostLms", dataFetchers.getHostLmsForAgencyDataFetcher()))
			.type("AgencyGroup",
				typeWiring -> typeWiring
					.dataFetcher("members", dataFetchers.getAgencyGroupMembersDataFetcher()))
			.type("AgencyGroupMember",
				typeWiring -> typeWiring
					.dataFetcher("agency", dataFetchers.getAgencyDataFetcherForGroupMember()))
			.type("Consortium",
				typeWiring -> typeWiring
					.dataFetcher("contacts", dataFetchers.getContactsForConsortiumDataFetcher())
					.dataFetcher("functionalSettings", dataFetchers.getFunctionalSettingsForConsortiumDataFetcher()))
			.type("ClusterRecord",
				typeWiring -> typeWiring
					.dataFetcher("members", dataFetchers.getClusterMembersDataFetcher()))
			.type("BibRecord",
				typeWiring -> typeWiring
					.dataFetcher("sourceRecord", dataFetchers.getSourceRecordForBibDataFetcher())
					.dataFetcher("matchPoints", dataFetchers.getMatchPointsForBibRecordDataFetcher()))
			.type("SupplierRequest",
				typeWiring -> typeWiring
					.dataFetcher("patronRequest", dataFetchers.getPatronRequestForSupplierRequestDataFetcher())
					.dataFetcher("virtualPatron", dataFetchers.getVPatronForSupplierRequest()))
			.type("InactiveSupplierRequest",
				typeWiring -> typeWiring
					.dataFetcher("patronRequest", dataFetchers.getPatronRequestForInactiveSupplierRequestDataFetcher()))
			.type("PatronRequest",
				typeWiring -> typeWiring
					.dataFetcher("suppliers", dataFetchers.getSupplierRequestsForPR())
					.dataFetcher("audit", dataFetchers.getAuditMessagesForPR())
					.dataFetcher("clusterRecord", dataFetchers.getClusterRecordForPR())
					.dataFetcher("requestingIdentity", dataFetchers.getPatronIdentityForPatronRequestRequest()))
			.type("Location",
				typeWiring -> typeWiring
					.dataFetcher("agency", dataFetchers.getAgencyForLocation())
					.dataFetcher("hostSystem", dataFetchers.getHostSystemForLocation())
					.dataFetcher("parentLocation", dataFetchers.getParentForLocation()))
			.type("Library",
				typeWiring -> typeWiring
					.dataFetcher("agency", dataFetchers.getAgencyForLibraryDataFetcher())
					.dataFetcher("secondHostLms", dataFetchers.getSecondHostLmsForLibraryDataFetcher())
					.dataFetcher("contacts", dataFetchers.getContactsForLibraryDataFetcher())
					.dataFetcher("membership", dataFetchers.getLibraryGroupMembersByLibraryDataFetcher()))
			.type("LibraryGroup",
				typeWiring -> typeWiring
					.dataFetcher("members", dataFetchers.getLibraryGroupMembersDataFetcher())
					.dataFetcher("consortium", dataFetchers.getConsortiumForLibraryGroupDataFetcher())
			)
			.type("LibraryGroupMember",
				typeWiring -> typeWiring
					.dataFetcher("library", dataFetchers.getLibraryForGroupMemberDataFetcher())
					.dataFetcher("libraryGroup", dataFetchers.getGroupForGroupMemberDataFetcher())
			)
			.type("Person",
				typeWiring -> typeWiring
					.dataFetcher("role", dataFetchers.getRoleForPersonDataFetcher()))
			.scalar(ExtendedScalars.Json)
			.build();

		// Create the executable schema.
		GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

		log.debug("returning {}", graphQLSchema.toString());

		// Return the GraphQL bean.
		return GraphQL.newGraphQL(graphQLSchema).build();
	}
}
