package org.olf.reshare.dcb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.bib.marc.MarcImportService;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
;

@MicronautTest
public class MarcImportServiceTest {

   String filepath = "../SGCLsample1.mrc";

   @Test
	void checkMethodsDontReturnNull () throws Exception {
         assertNotNull(MarcImportService.readMarcFile(filepath));
         assertNotNull(MarcImportService.read(filepath));
	}
   
}
