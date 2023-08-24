package services.k_int.interaction.gokb;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.NotBlank;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;

@Client("${" + GokbApiClient.CONFIG_ROOT + ".api.url:`https://gokb.org/gokb/api`}")
@Header(name = ACCEPT, value = APPLICATION_JSON)
public interface GokbApiClient {
	public static final String COMPONENT_TYPE_TIPP = "TitleInstancePackagePlatform";
	public static final String QUERY_PARAM_COMPONENT_TYPE = "component_type";

	public final static String CONFIG_ROOT = "gokb.client";
	static final Logger log = LoggerFactory.getLogger(GokbApiClient.class);

	@SingleResult
	public default Publisher<GokbScrollResponse> scrollTipps(@Nullable String scrollId, @Nullable Instant changedSince) {
		return scroll(COMPONENT_TYPE_TIPP, scrollId, changedSince != null ? changedSince.truncatedTo(ChronoUnit.SECONDS).toString() : null);
	}

	@Get("/scroll")
	@SingleResult
	@Retryable
	abstract <T> Publisher<GokbScrollResponse> scroll(
		@NonNull @NotBlank @QueryValue(GokbApiClient.QUERY_PARAM_COMPONENT_TYPE) String type,
		@Nullable @QueryValue("scrollId") String scrollId,
		@Nullable @QueryValue("changedSince") String changedSince);
}
