package org.olf.reshare.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

@Secured(SecurityRule.IS_ANONYMOUS)
@Produces(APPLICATION_JSON)
@Controller
@Tag(name = "Audit API")
public class AuditController {

}
