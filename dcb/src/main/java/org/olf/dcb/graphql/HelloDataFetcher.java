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


		String name = env.getArgument("name");
		if (name == null || name.trim().length() == 0) {
			name = "World";
		}
		log.debug("HelloDataFetcher::get {}",name);
		return String.format("Hello %s!", name);
	}
}
