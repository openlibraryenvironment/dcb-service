package org.olf.dcb.request.lifecycle;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * PR-8 guardrails for the unified host-interaction model.
 *
 * The declarative NCIP leaf ({@code request.lifecycle.ncip}) must stay removable:
 * deleting it and its config must leave imperative behaviour intact. That only
 * holds if the workflow engine, the placement seam, the tracking policy, and the
 * vendor adapters never depend on it. The shared protocol-mechanics module
 * ({@code core.interaction.ncip}: constants + XSD validator) is deliberately NOT
 * covered by these rules - it is the neutral layer both callers share.
 */
@AnalyzeClasses(
	packages = {
		"org.olf.dcb.request.workflow",
		"org.olf.dcb.request.lifecycle",
		"org.olf.dcb.core.interaction"
	},
	importOptions = ImportOption.DoNotIncludeTests.class)
class LifecycleArchitectureTests {

	private static final String DECLARATIVE_NCIP = "org.olf.dcb.request.lifecycle.ncip..";

	@ArchTest
	static final ArchRule workflow_must_not_depend_on_declarative_ncip = noClasses()
		.that().resideInAPackage("org.olf.dcb.request.workflow..")
		.should().dependOnClassesThat().resideInAPackage(DECLARATIVE_NCIP);

	@ArchTest
	static final ArchRule placement_seam_must_not_depend_on_declarative_ncip = noClasses()
		.that().resideInAPackage("org.olf.dcb.request.lifecycle.placement..")
		.should().dependOnClassesThat().resideInAPackage(DECLARATIVE_NCIP);

	@ArchTest
	static final ArchRule tracking_must_not_depend_on_declarative_ncip = noClasses()
		.that().resideInAPackage("org.olf.dcb.request.lifecycle.tracking..")
		.should().dependOnClassesThat().resideInAPackage(DECLARATIVE_NCIP);

	/**
	 * The wire vocabulary and the XSD validator have exactly one home:
	 * {@code core.interaction.ncip}. A merge once re-introduced private copies here
	 * and the two validators silently drifted on the extension namespace. Declaring
	 * them in the declarative leaf again is a build failure, not a review comment.
	 */
	@ArchTest
	static final ArchRule declarative_ncip_must_not_redeclare_shared_protocol_mechanics =
		noClasses()
			.that().resideInAPackage(DECLARATIVE_NCIP)
			.should().haveSimpleName("NcipProtocol")
			.orShould().haveSimpleName("NcipSchemaValidator")
			.orShould().haveSimpleName("NcipSchemaPath")
			.because("NCIP constants and the XSD validator are shared via "
				+ "org.olf.dcb.core.interaction.ncip - duplicating them lets the "
				+ "imperative and declarative sides drift apart on the wire");

	@ArchTest
	static final ArchRule vendor_adapters_must_not_depend_on_declarative_ncip = noClasses()
		.that().resideInAnyPackage(
			"org.olf.dcb.core.interaction.sierra..",
			"org.olf.dcb.core.interaction.polaris..",
			"org.olf.dcb.core.interaction.folio..",
			"org.olf.dcb.core.interaction.alma..",
			"org.olf.dcb.core.interaction.foundation..")
		.should().dependOnClassesThat().resideInAPackage(DECLARATIVE_NCIP);
}
