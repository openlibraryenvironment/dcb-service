package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.Map;

import org.olf.dcb.security.RoleNames;
import org.olf.dcb.sql.GenericSelectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller("/sql")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Sql API")
public class SqlController {
	private static final Logger log = LoggerFactory.getLogger(SqlController.class);

	private final GenericSelectService genericSelectService;

	public SqlController(GenericSelectService genericSelectService) {

		this.genericSelectService = genericSelectService;
	}

	@Get(uri = "/", produces = APPLICATION_JSON)
	public Map<String, Object> executeNamedSql(@Parameter String name) {
		return genericSelectService.selectNamed(name);
	}
	
	@Post(uri = "/", produces = APPLICATION_JSON)
	public Map<String, Object> execute(@Body String sql) {
		return genericSelectService.select(sql);
	}

}
