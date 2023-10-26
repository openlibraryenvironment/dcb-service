package org.olf.dcb;

import static io.micronaut.http.HttpHeaders.AUTHORIZATION;
import static io.micronaut.http.MediaType.TEXT_PLAIN;

import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;

@Client("/")
public interface AppClient {
	@Consumes(TEXT_PLAIN)
	@Get
	String home(@Header(AUTHORIZATION) String authorization);
}
