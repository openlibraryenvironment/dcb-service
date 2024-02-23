package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

import org.olf.dcb.core.api.exceptions.FileUploadValidationException;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.utils.DCBConfigurationService;
import org.olf.dcb.utils.DCBConfigurationService.UploadedConfigImport;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Controller("/uploadedMappings")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Uploaded Mappings API")
@Consumes(MULTIPART_FORM_DATA)
// @Introspected SO: Think this is here by mistake. Commenting just in case
public class UploadedMappingsController {
	private final DCBConfigurationService configurationService;
	private ReferenceValueMappingRepository referenceValueMappingRepository;

	public UploadedMappingsController(DCBConfigurationService configurationService, ReferenceValueMappingRepository referenceValueMappingRepository) {
		this.configurationService = configurationService;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	// This handles the return of an appropriate error + message when a file fails validation.
	@Error(FileUploadValidationException.class)
	public HttpResponse<String> handleValidationException(FileUploadValidationException ex) {
		// Return a 400 Bad Request response with the validation error message
		return HttpResponse.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	// This method returns all mappings of a given category that have not been deleted.
	// As @Where("@.deleted = false") is specified in the ReferenceValueMapping class,
	// all methods will return only mappings that haven't been flagged as deleted by default.
	@Get(value = "/{category}", produces = APPLICATION_JSON)
	public Flux<ReferenceValueMapping> get(@Parameter String category) {
		return Flux.from(referenceValueMappingRepository.findAllByFromCategory(category));
	}

	// Method to return all mappings of a given category and context.
	@Get(value = "/{category}/{context}", produces = APPLICATION_JSON)
	public Flux<ReferenceValueMapping> get(@Parameter String category, @Parameter String context) {
		return Flux.from(referenceValueMappingRepository.findAllByFromCategoryAndFromContext(category, context));
	}

	// Returns all deleted items, in case we need to retrieve mappings that have been deleted by the user.
	@Get(value = "/deleted", produces = APPLICATION_JSON)
	public Flux<ReferenceValueMapping> get() {
		return Flux.from(referenceValueMappingRepository.findDeleted());
	}

	// This method posts a file of uploaded mappings of a given mapping category.
	@Post(value = "/upload", consumes = MULTIPART_FORM_DATA, produces = APPLICATION_JSON)
	public Mono<UploadedConfigImport> post(CompletedFileUpload file, String code, String mappingCategory) {
		return configurationService.importConfiguration(mappingCategory, code, file);
	}
}
