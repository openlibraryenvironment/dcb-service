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
			AgencyGroupsDataFetcher agencyGroupsDataFetcher,
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
						.dataFetcher("hello", dataFetchers.getHelloDataFetcher())
						.dataFetcher("agencies", dataFetchers.getAgenciesDataFetcher())
						.dataFetcher("agencyGroups", agencyGroupsDataFetcher)
						.dataFetcher("patronRequests", dataFetchers.getPatronRequestsDataFetcher())
						.dataFetcher("supplierRequests", dataFetchers.getSupplierRequestsDataFetcher()))
				.type("Mutation",
						typeWiring -> typeWiring.dataFetcher("createAgencyGroup", createAgencyGroupDataFetcher)
								.dataFetcher("addAgencyToGroup", addAgencyToGroupDataFetcher))
				.type("AgencyGroup",
						typeWiring -> typeWiring.dataFetcher("members", dataFetchers.getAgencyGroupMembersDataFetcher()))
				.type("AgencyGroupMember",
						typeWiring -> typeWiring.dataFetcher("agency", dataFetchers.getAgencyDataFetcherForGroupMember()))
				.build();

		// Create the executable schema.
		GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);

		log.debug("returning {}", graphQLSchema.toString());

		// Return the GraphQL bean.
		return GraphQL.newGraphQL(graphQLSchema).build();
	}
}
