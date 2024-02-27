package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.IMAGE_PNG;
import static io.micronaut.http.MediaType.TEXT_PLAIN;

import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import org.olf.dcb.statemodel.StateModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Controller("/statemodel")
@Secured(SecurityRule.IS_ANONYMOUS)
@Validated
@Tag(name = "State API")
public class StateModelController {
	private static final Logger log = LoggerFactory.getLogger(StateModelController.class);

	private static final String IMAGE_SVG = "image/svg+xml";

	private final StateModelService stateModelService;

	public StateModelController(StateModelService stateModelService) {

		this.stateModelService = stateModelService;
	}

	@SingleResult
	@Operation(
		summary = "Generates a state table diagram in svg format",
	    description = "Generates a state table diagram in svg format"
	)
	@Get(uri = "/diagram/svg", produces = {IMAGE_SVG})
	public Mono<byte[]> getDiagramSVG() {

		log.debug("REST, get state table diagram in SVG format");

	    try {
	        byte[] generatedDiagram = stateModelService.generateGraph(stateModelService.FORMAT_SVG);
	        return Mono.just(generatedDiagram);
	    } catch (Exception e) {
	        log.error("An error occurred generating the SVG state diagram", e);
	    } 
	    return Mono.empty();
	}

	@SingleResult
	@Operation(
		summary = "Generates a state table diagram in png format",
	    description = "Generates a state table diagram in png format"
	)
	@Get(uri = "/diagram/png", produces = {IMAGE_PNG})
	public Mono<byte[]> getDiagramPNG() {

		log.debug("REST, get state table diagram in PNG format");

	    try {
	        byte[] generatedDiagram = stateModelService.generateGraph(stateModelService.FORMAT_PNG);
	        return Mono.just(generatedDiagram);
	    } catch (Exception e) {
	        log.error("An error occurred generating the PNG state diagram", e);
	    } 
	    return Mono.empty();
	}

	@SingleResult
	@Operation(
		summary = "Generates a state table diagram in dot format",
	    description = "Generates a state table diagram in dot format"
	)
	@Get(uri = "/diagram/dot", produces = {TEXT_PLAIN})
	public Mono<byte[]> getDiagram() {

		log.debug("REST, get state table diagram in DOT format");

	    try {
	        byte[] generatedDiagram = stateModelService.generateGraph(stateModelService.FORMAT_DOT);
	        return Mono.just(generatedDiagram);
	    } catch (Exception e) {
	        log.error("An error occurred generating the DOT state diagram", e);
	    } 
	    return Mono.empty();
	}
}
