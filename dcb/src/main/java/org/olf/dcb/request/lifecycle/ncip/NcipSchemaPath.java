package org.olf.dcb.request.lifecycle.ncip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class NcipSchemaPath {
	private NcipSchemaPath() {
	}

	public static Path schemaPath() {
		final var workingDirectory = Paths.get("").toAbsolutePath();
		final var packagedSchema = workingDirectory.resolve(
			"resources/schemas/ncip_v2_02.xsd");

		if (Files.exists(packagedSchema)) {
			return packagedSchema;
		}

		final var repositorySchema = workingDirectory.resolve(
			"src/xsd/ncip_v2_02.xsd");

		if (Files.exists(repositorySchema)) {
			return repositorySchema;
		}

		final var moduleSchema = workingDirectory.resolve(
			"../src/xsd/ncip_v2_02.xsd").normalize();

		if (Files.exists(moduleSchema)) {
			return moduleSchema;
		}

		throw new IllegalStateException(
			"Could not find NCIP schema from " + workingDirectory);
	}
}
