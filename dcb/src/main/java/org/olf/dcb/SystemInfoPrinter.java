package org.olf.dcb;

import java.util.stream.Collectors;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import lombok.extern.slf4j.Slf4j;

@Context
@Slf4j
public class SystemInfoPrinter {
	
	public SystemInfoPrinter( Environment env ) {
		log.info("Runtime reported:\n{}", env.getProperties("jvm").entrySet().stream()
			.map( e -> "\t%s: [%s]".formatted(e.getKey(), e.getValue() ))
			.collect(Collectors.joining("\n")));
	}
}
