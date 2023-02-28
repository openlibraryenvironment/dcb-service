package org.olf.reshare.dcb.sierra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
public class SierraApiTests {

	@Inject
	SierraApiClient client;

	@Inject
	ResourceLoader loader;

	private final String MOCK_ROOT = "classpath:mock-responses/sierra";

	@Test
	public void testLoginTokenType(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-login.json")
						.orElseThrow()
						.readAllBytes()))));


		var response = Mono.from(client.login("key", "secret")).block();
		System.out.println(response);
		assertNotNull(response);
		assertEquals(response.type().toLowerCase(), "bearer");
	}

	@Test
	public void testLoginTokenExpiration(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-login.json")
						.orElseThrow()
						.readAllBytes()))));

		var response = Mono.from(client.login("key", "secret")).block();
		assertNotNull(response);
		assertEquals(response.getClass(), AuthToken.class);
		assertFalse(response.isExpired());
		assertTrue(response.expires().isAfter(Instant.MIN));
	}

	@Test
	public void testLoginTokenUnique(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/token")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-login.json")
						.orElseThrow()
						.readAllBytes()))));

		var token1 = Mono.from(client.login("key", "secret")).block();
		var token2 = Mono.from(client.login("key", "secret")).block();

		assertNotNull(token1);
		assertNotNull(token2);
		assertEquals(token1.getClass(), AuthToken.class);
		assertEquals(token2.getClass(), AuthToken.class);
		assertNotSame(token1, token2);
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=1
	 */
	@Test
	public void testQueryLimit(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "1")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-limit.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.bibs(BibParams.builder().limit(1).build())).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.total(), 1);
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=1&offset=1
	 */
	@Test
	public void testQueryOffset(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "1")
				.withQueryStringParameter("offset", "1")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-offset.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.bibs(BibParams.builder().limit(1).offset(1).build())).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.start(), 1);
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=3&id=1000001%2C1000003%2C1000005
	*/
	@Test
	public void testQueryId(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "3")
			//.withQueryStringParameter("id", "1000001,1000003,1000005")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader.getResourceAsStream(MOCK_ROOT + "/sierra-api-id.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		// are we seperating the id from sierra? (one and many ids)
		var response = Mono.from(client.bibs(BibParams.builder().limit(3).build())).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.entries().get(0).id(), "1000001");
		assertEquals(response.entries().get(1).id(), "1000003");
		assertEquals(response.entries().get(2).id(), "1000005");
	}


	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=1&fields=author%2Ctitle%2CpublishYear%2Cdeleted&deleted=false
	 */
	@Test
	public void testQueryFields(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "1")
				.withQueryStringParameter("deleted", "false") // required?
				.withQueryStringParameter("fields", "author,title,publishYear,deleted")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-fields.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.bibs(params ->
			{
				params
					.limit(1)
					.deleted(false) // required?
					.fields(List.of("author", "title", "publishYear", "deleted"));
			}
		)).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);

		BibResult bibResult = new BibResult("1000001", LocalDateTime.of(2022, 2, 15, 14, 18, 5), LocalDateTime.of(2003, 5, 8, 15, 55), null, false, null);
		BibResultSet bibResultSet = new BibResultSet(1, 0, List.of(bibResult));
		assertEquals(response, bibResultSet);
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=1&createdDate=2003-05-08T15%3A55&deleted=false
 	*/
	@Test
	public void testQueryCreatedDate(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "1")
				.withQueryStringParameter("deleted", "false") // required?
				.withQueryStringParameter("createdDate", "2003-05-08T15:55") // 2003-05-08T15:55:00Z ??
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-limit.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		LocalDateTime localDateTime = LocalDateTime.of(2003, Month.MAY, 8, 15, 55, 0);

		var response = Mono.from(client.bibs(params -> {
				params
					.limit(1)
					.deleted(false) // required?
					.createdDate(dtr -> dtr.fromDate(localDateTime));
			}
		)).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertNotNull(response.entries().get(0).createdDate());

		String str = response.entries().get(0).createdDate().toString();
		assertEquals(str, "2003-05-08T15:55");
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=1&updatedDate=2022-02-15T14%3A18&deleted=false
 	*/
	@Test
	public void testQueryUpdatedDate(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "1")
				.withQueryStringParameter("deleted", "false") // required?
				.withQueryStringParameter("updatedDate", "2022-02-15T14:18:05") // 2022-02-15T14:18:05Z ??
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-limit.json")
						.orElseThrow()
						.readAllBytes()))));

		LocalDateTime localDateTime = LocalDateTime.of(2022, Month.FEBRUARY, 15, 14, 18, 5);
		// Fetch from sierra and block
		var response = Mono.from(client.bibs(params -> {
				params
					.limit(1)
					.deleted(false) // required?
					.updatedDate(dtr -> dtr.fromDate(localDateTime));
			}
		)).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertNotNull(response.entries().get(0).updatedDate());

		String str = response.entries().get(0).updatedDate().toString();
		assertNotNull(str);
		assertEquals(str, "2022-02-15T14:18:05");
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=5&deletedDate=%5B2011-08-25%2C2012-07-23%5D&deleted=true
 	*/
	@Test
	public void testQueryDeletedDate(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "5")
				.withQueryStringParameter("deleted", "true") // required?
				.withQueryStringParameter("deletedDate", "[2011-08-25T00:00,2012-07-23T00:00]") // [2011-08-25,2012-07-23]?
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-deleted.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(
				client.bibs(BibParams.builder()
					.limit(5)
					.deleted(true)
					.deletedDate(dtr -> { // long winded
						dtr.fromDate(LocalDateTime.from(LocalDateTime.of(2011, Month.AUGUST, 25, 0, 0, 0)))
							.to(LocalDateTime.from(LocalDateTime.of(2012, Month.JULY, 23, 0, 0, 0)));
					})
					.build()))
			.block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);

		// Check response contains a deleted date
		LocalDate localDate = LocalDate.of(2012, 1, 13);

		/* LocalDateTime != LocalDate :(
		var ref = new Object() { List<LocalDate> updatedDates = new ArrayList<>(); };
		response.entries().forEach( (n) -> ref.updatedDates.add( n.updatedDate() ) ); // for each add to an array
		assertTrue(ref.updatedDates.contains(localDate));
		*/

		// work around
		assertEquals(response.entries().get(0).deletedDate(), localDate);
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=1&suppressed=false
 	*/
	@Test
	public void testQuerySuppressed(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "1")
				.withQueryStringParameter("suppressed", "false")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-limit.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.bibs(BibParams.builder().limit(1).suppressed(false).build())).block();

		// BibResult not supporting suppressed
		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.total(), 1);
		assertEquals(response.entries().get(0).id(), "1000001");
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=3&fields=locations%2Cdeleted&deleted=false&suppressed=false&locations=a
 	*/
	@Test
	public void testQueryLocation(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "3")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("fields", "locations,deleted")
				.withQueryStringParameter("locations", "a")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-locations.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.bibs(params ->
			{
				params
					.limit(3)
					.deleted(false)
					.fields(List.of("locations", "deleted"))
					.location("a");
			}
		)).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.total(), 3);
		// ??
		assertEquals(response.entries().get(0).id(), "1000001");
		assertEquals(response.entries().get(1).id(), "1000003"); // 1000002?
		assertEquals(response.entries().get(2).id(), "1000005"); // 1000003?
	}

	/*
	https://sandbox.iii.com:443/iii/sierra-api/v6/bibs/?limit=3&offset=1&fields=author%2Ctitle%2CpublishYear%2Cdeleted%2Clocations&deleted=false&suppressed=false&locations=a
 	*/
	@Test
	public void testConstructor(MockServerClient mock) throws IOException {

		// Mock the response from Sierra
		mock.when(
			request()
				.withHeader("Accept", "application/json")
				.withMethod("GET")
				.withPath("/iii/sierra-api/v6/bibs/")
				.withQueryStringParameter("limit", "3")
				.withQueryStringParameter("suppressed", "false")
				.withQueryStringParameter("offset", "1")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("fields", "author,title,publishYear,deleted,locations")
				.withQueryStringParameter("locations", "a")
		).respond(
			response()
				.withStatusCode(200)
				.withContentType(MediaType.APPLICATION_JSON)
				.withBody(
					json(new String(loader
						.getResourceAsStream(MOCK_ROOT + "/sierra-api-constructor.json")
						.orElseThrow()
						.readAllBytes()))));

		// Fetch from sierra and block
		var response = Mono.from(client.bibs(
			3,
			1,
			"null",
			"null",
			List.of("author", "title", "publishYear", "deleted", "locations"),
			false,
			null,
			false,
			List.of("a"))).block();

		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.total(), 3);
		// ?????
		assertEquals(response.entries().get(0).id(), "1000001"); // 1000002?
		assertEquals(response.entries().get(1).id(), "1000003"); // 1000003?
		assertEquals(response.entries().get(2).id(), "1000005"); // 1000004?
	}

}
