package org.olf.dcb.core.api;

import org.olf.dcb.core.events.RulesetRelatedDataChangedEvent;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.ObjectRulesetRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Validated
@Controller("/object-rules")
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Object Rulesets")
public class ObjectRulesetController {

	private final ApplicationEventPublisher<RulesetRelatedDataChangedEvent> eventPublisher;
	private final ObjectRulesetRepository objectFilterRulesetRepository;

	public ObjectRulesetController(ObjectRulesetRepository objectFilterRulesetRepository, ApplicationEventPublisher<RulesetRelatedDataChangedEvent> eventPublisher) {
		this.objectFilterRulesetRepository = objectFilterRulesetRepository;
		this.eventPublisher = eventPublisher;
	}

	@Operation(summary = "Browse Object Filter Rulesets", description = "Paginate through the list of known Object Filter Rulesets", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100") })
	@Get("/{?pageable*}")
	public Mono<Page<ObjectRuleset>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		return Mono.from(objectFilterRulesetRepository.queryAll(pageable));
	}

	@Get("/{name}")
	public Mono<ObjectRuleset> show(String name) {
		return Mono.from(objectFilterRulesetRepository.findByName(name));
	}

	@Post("/")
	public Mono<ObjectRuleset> postRuleset(@Valid @Body ObjectRuleset ruleset) {
		return Mono.from(objectFilterRulesetRepository.existsById(ruleset.getName()))
			.flatMap(exists -> Mono.fromDirect(exists ? objectFilterRulesetRepository.update(ruleset) : objectFilterRulesetRepository.save(ruleset)))
			.doOnSuccess(data -> {
				if (data != null) {
					log.debug("Raising event to clear suppression caches");
					eventPublisher.publishEvent( new RulesetRelatedDataChangedEvent(data) );
				}
			});
	}

}
