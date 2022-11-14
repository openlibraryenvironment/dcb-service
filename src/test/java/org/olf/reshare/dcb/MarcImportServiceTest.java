package org.olf.reshare.dcb;

import java.io.FileNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.bib.marc.MarcImportService;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest
@SuppressWarnings("all")
public class MarcImportServiceTest {
   
   // /https://www.loc.gov/standards/marcxml/
   private static final String testMarcFile = "src/main/resources/sandburg.mrc";
   private static final String marcFileOutsideProject = "../test-data/SGCLsample1.mrc";
   
   @Inject
   MarcImportService marcImportService;

   @Test
   void testFluxOfRecords() {
       try {
         marcImportService.fluxOfRecords(testMarcFile);
      } catch (Exception e) {e.printStackTrace();}
   }

   @Test
   void testGetMarcFile() throws Exception {
      FileNotFoundException thrown = Assertions.assertThrows(FileNotFoundException.class, () -> {
         marcImportService.fluxOfRecords("Unrecognized file");
      }, "Unrecognized file (No such file or directory)");
      Assertions.assertEquals("Unrecognized file (No such file or directory)", thrown.getMessage());
   }

}
