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
import graphql.schema.GraphQLScalarType;
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
			ResourceResolver resourceResolver,
			InstanceClusterDataFetcher instanceClusterDataFetcher,
			SourceBibDataFetcher sourceBibDataFetcher,
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
						.dataFetcher("instanceClusters", instanceClusterDataFetcher)
						.dataFetcher("sourceBibs", sourceBibDataFetcher)
						.dataFetcher("processStates", dataFetchers.getProcessStateDataFetcher())
						.dataFetcher("audits", dataFetchers.getAuditsDataFetcher())
						.dataFetcher("patronIdentities", dataFetchers.getPatronIdentitiesDataFetcher())
						.dataFetcher("numericRangeMappings", dataFetchers.getNumericRangeMappingsDataFetcher())
						.dataFetcher("referenceValueMappings", dataFetchers.getReferenceValueMappingsDataFetcher())
				)
				.type("Mutation",
					typeWiring -> typeWiring
						.dataFetcher("createAgencyGroup", createAgencyGroupDataFetcher)
						.dataFetcher("addAgencyToGroup", addAgencyToGroupDataFetcher))
				.type("AgencyGroup",
					typeWiring -> typeWiring
						.dataFetcher("members", dataFetchers.getAgencyGroupMembersDataFetcher()))
				.type("AgencyGroupMember",
					typeWiring -> typeWiring
						.dataFetcher("agency", dataFetchers.getAgencyDataFetcherForGroupMember()))
				.type("ClusterRecord",
					typeWiring -> typeWiring
						.dataFetcher("members", dataFetchers.getClusterMembersDataFetcher()))
				.type("BibRecord",
					typeWiring -> typeWiring
						.dataFetcher("sourceRecord", dataFetchers.getSourceRecordForBibDataFetcher()))
				.type("SupplierRequest",
					typeWiring -> typeWiring
						.dataFetcher("patronRequest", dataFetchers.getPatronRequestForSupplierRequestDataFetcher())
						.dataFetcher("virtualPatron", dataFetchers.getVPatronForSupplierRequest()))
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
				.scalar(ExtendedScalars.Json)
				.build();

		// Create the executable schema.
		GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

		log.debug("returning {}", graphQLSchema.toString());

		// Return the GraphQL bean.
		return GraphQL.newGraphQL(graphQLSchema).build();
	}
}
