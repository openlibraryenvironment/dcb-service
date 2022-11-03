package org.olf.reshare.dcb.bib.gokb;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.HttpHeaders.USER_AGENT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

@Client("${" + GokbApiClient.CONFIG_ROOT + ".api.url:`https://gokb.org/gokb/api`}")
@Requires(property = GokbApiClient.CONFIG_ROOT)
@Header(name = USER_AGENT, value = "ReShare DCB")
@Header(name = ACCEPT, value = APPLICATION_JSON)
public interface GokbApiClient {

	public final static String CONFIG_ROOT = "gokb";

	@SingleResult
	default Publisher<GokbScrollResponse> scrollTipps ( @Nullable String scrollId ) {
		return scroll( "TitleInstancePackagePlatform", scrollId );
	}
	
	
	@SingleResult
	default	Publisher<GokbScrollResponse> scrollTipps() { return scrollTipps(null); }
	
	@Get("/scroll")
	@SingleResult
	<T> Publisher<GokbScrollResponse> scroll(
		@QueryValue("component_type") String type,
		@Nullable @QueryValue("scrollId") String scrollId
		);
}
