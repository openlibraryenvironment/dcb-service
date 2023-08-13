package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HelloDataFetcher implements DataFetcher<String> {

	private static Logger log = LoggerFactory.getLogger(HelloDataFetcher.class);

	@Override
	public String get(DataFetchingEnvironment env) {

		log.debug("HelloDataFetcher::get");

		String name = env.getArgument("name");
		if (name == null || name.trim().length() == 0) {
			name = "World";
		}
		return String.format("Hello %s!", name);
	}
}
