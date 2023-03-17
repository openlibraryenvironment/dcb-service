package org.olf.reshare.dcb.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.item.availability.AvailabilityResponseView;
import org.olf.reshare.dcb.test.DcbTest;

import java.net.URI;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@DcbTest
public class LiveAvailabilityApiTests {

	@Inject
	@Client("/")
	HttpClient client;

	@Test
	void canProvideAListOfAvailableItemsViaLiveAvailabilityApi() {

		URI uri = UriBuilder.of("/items/availability")
			.queryParam("bibRecordId", "1000001")
			.queryParam("systemCode", "SANDBOX")
			.build();

		AvailabilityResponseView availabilityResponseView = client.toBlocking()
			.retrieve(HttpRequest.GET(uri), AvailabilityResponseView.class);

		assertThat(availabilityResponseView, is(notNullValue()));
		assertThat(availabilityResponseView.getItemList().size(), is(3));
	}
}
