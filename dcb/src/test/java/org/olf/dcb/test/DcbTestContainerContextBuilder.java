package org.olf.dcb.test;

import java.util.Map;

import org.testcontainers.postgresql.PostgreSQLContainer;

import io.micronaut.context.DefaultApplicationContextBuilder;

public class DcbTestContainerContextBuilder extends DefaultApplicationContextBuilder {
	private static final PostgreSQLContainer POSTGRES =
		new PostgreSQLContainer("postgres:18")
			.withDatabaseName("dcb")
			.withUsername("postgres")
			.withPassword("postgres")
			.withCommand("postgres", "-N", "200");

	@Override
	public io.micronaut.context.ApplicationContext build() {
		ensurePostgresStarted();
		properties(postgresProperties());
		return super.build();
	}

	private static synchronized void ensurePostgresStarted() {
		if (!POSTGRES.isRunning()) {
			POSTGRES.start();
		}
	}

	private static Map<String, Object> postgresProperties() {
		String host = POSTGRES.getHost();
		Integer port = POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
		String databaseName = POSTGRES.getDatabaseName();
		String username = POSTGRES.getUsername();
		String password = POSTGRES.getPassword();

		return Map.ofEntries(
			Map.entry("datasources.default.url", POSTGRES.getJdbcUrl()),
			Map.entry("datasources.default.username", username),
			Map.entry("datasources.default.password", password),
			Map.entry("datasources.default.driverClassName", "org.postgresql.Driver"),
			Map.entry("r2dbc.datasources.default.url",
				"r2dbc:pool:postgresql://" + host + ":" + port + "/" + databaseName),
			Map.entry("r2dbc.datasources.default.username", username),
			Map.entry("r2dbc.datasources.default.password", password),
			Map.entry("r2dbc.datasources.default.options.driver", "pool"),
			Map.entry("r2dbc.datasources.default.options.protocol", "postgresql"),
			Map.entry("db.host", host),
			Map.entry("db.port", port.toString()),
			Map.entry("db.database", databaseName),
			Map.entry("db.username", username),
			Map.entry("db.password", password)
		);
	}
}
