package services.k_int.interaction.gokb;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.HttpHeaders.USER_AGENT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.time.Instant;
import java.util.Objects;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;

@Client("${" + GokbApiClient.CONFIG_ROOT + ".api.url:`https://gokb.org/gokb/api`}")
@Header(name = USER_AGENT, value = "ReShare DCB")
@Header(name = ACCEPT, value = APPLICATION_JSON)
public interface GokbApiClient {
	public static final String COMPONENT_TYPE_TIPP = "TitleInstancePackagePlatform";
	public static final String QUERY_PARAM_COMPONENT_TYPE = "component_type";

	public final static String CONFIG_ROOT = "gokb.client";
	static final Logger log = LoggerFactory.getLogger(GokbApiClient.class);

	@SingleResult
	default Publisher<GokbScrollResponse> scrollTipps(@Nullable String scrollId, @Nullable Instant changedSince) {
		return scroll(COMPONENT_TYPE_TIPP, scrollId, Objects.toString(changedSince));
	}
	
	@Get("/scroll")
	@SingleResult
	@Retryable
	<T> Publisher<GokbScrollResponse> scroll(
			@QueryValue(GokbApiClient.QUERY_PARAM_COMPONENT_TYPE) String type,
			@Nullable @QueryValue("scrollId") String scrollId,
			@Nullable @QueryValue("changedSince") String changedSince);
}
