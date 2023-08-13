package org.olf.dcb;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import io.micronaut.serde.annotation.SerdeImport;

import io.micronaut.configuration.graphql.ws.GraphQLWsResponse;
import io.micronaut.configuration.graphql.GraphQLRequestBody;
import io.micronaut.configuration.graphql.GraphQLResponseBody;


// /home/ianibbo/dev/resource_sharing/backend/reshare-dcb-service-olf

@SerdeImport(GraphQLWsResponse.class)
@SerdeImport(GraphQLRequestBody.class)
@SerdeImport(GraphQLResponseBody.class)
@TypeHint(value = { Instant[].class, ZonedDateTime[].class, URI[].class, URL[].class })
@OpenAPIDefinition(info = @Info(title = "DCB", description = "Direct Consortial Borrowing Service", version = "1.0.0"))
public class Application {

	public static void main(String[] args) {
		Micronaut.run(Application.class, args);
	}
}
