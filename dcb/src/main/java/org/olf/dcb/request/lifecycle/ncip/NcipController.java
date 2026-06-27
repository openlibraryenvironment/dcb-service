package org.olf.dcb.request.lifecycle.ncip;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandler;
import reactor.core.publisher.Mono;

@Controller("/ncip/v2_02")
@Secured(IS_ANONYMOUS)
public class NcipController {
	private final InboundLifecycleMessageHandler inboundLifecycleMessageHandler;
	private final NcipInboundXmlMapper inboundXmlMapper;
	private final NcipResponseBuilder responseBuilder;
	private final NcipSchemaValidator schemaValidator;

	public NcipController(
		InboundLifecycleMessageHandler inboundLifecycleMessageHandler,
		NcipInboundXmlMapper inboundXmlMapper,
		NcipResponseBuilder responseBuilder) {

		this(
			inboundLifecycleMessageHandler,
			inboundXmlMapper,
			responseBuilder,
			new NcipSchemaValidator(NcipSchemaPath.schemaPath()));
	}

	NcipController(
		InboundLifecycleMessageHandler inboundLifecycleMessageHandler,
		NcipInboundXmlMapper inboundXmlMapper,
		NcipResponseBuilder responseBuilder,
		NcipSchemaValidator schemaValidator) {

		this.inboundLifecycleMessageHandler = inboundLifecycleMessageHandler;
		this.inboundXmlMapper = inboundXmlMapper;
		this.responseBuilder = responseBuilder;
		this.schemaValidator = schemaValidator;
	}

	@Post(consumes = {
		MediaType.APPLICATION_XML,
		MediaType.TEXT_XML
	}, produces = MediaType.APPLICATION_XML)
	public Mono<MutableHttpResponse<String>> receive(@Body String xml) {
		final NcipInboundMessage ncipMessage;

		try {
			schemaValidator.validate(xml);
			ncipMessage = inboundXmlMapper.map(xml);
		}
		catch (RuntimeException e) {
			return Mono.just(xmlResponse(responseBuilder.problem(messageFrom(e))));
		}

		return inboundLifecycleMessageHandler.handle(
				new NcipInboundMessageMapper().map(ncipMessage))
			.thenReturn(successResponseFor(ncipMessage))
			.onErrorResume(error -> Mono.just(problemResponseFor(ncipMessage, error)));
	}

	private MutableHttpResponse<String> successResponseFor(
		NcipInboundMessage message) {

		return xmlResponse(switch (message.messageKind()) {
			case "ItemShipped" -> responseBuilder.itemShippedResponse();
			default -> responseBuilder.problem(
				"Unsupported NCIP message: " + message.messageKind());
		});
	}

	private MutableHttpResponse<String> problemResponseFor(
		NcipInboundMessage message,
		Throwable error) {

		return xmlResponse(switch (message.messageKind()) {
			case "ItemShipped" -> responseBuilder.itemShippedProblem(
				messageFrom(error));
			default -> responseBuilder.problem(messageFrom(error));
		});
	}

	private static MutableHttpResponse<String> xmlResponse(String body) {
		return HttpResponse.ok(body)
			.contentType(MediaType.APPLICATION_XML_TYPE);
	}

	private static String messageFrom(Throwable error) {
		return error.getMessage() != null
			? error.getMessage()
			: error.getClass().getSimpleName();
	}
}
