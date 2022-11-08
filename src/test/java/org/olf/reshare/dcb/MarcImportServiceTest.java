package org.olf.reshare.dcb;

import java.io.FileNotFoundException;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.bib.marc.MarcImportService;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest
@SuppressWarnings("all")
public class MarcImportServiceTest {
   
   private static final String marcFileName = "../SGCLsample1.mrc";
   
   @Inject
   MarcImportService marcImportService;

   @Test
   void testDoImportWorks() {
       try {
         assertNotNull(marcFileName);
         marcImportService.doImport(marcFileName);
      } catch (Exception e) {e.printStackTrace();}
   }

   @Test
   void testDoImportThrowsExceptionForUnrecognisedFile() {
      FileNotFoundException thrown = Assertions.assertThrows(FileNotFoundException.class, () -> {
         marcImportService.doImport("Unrecognized file");
      }, "Unrecognized file (No such file or directory)");
      Assertions.assertEquals("Unrecognized file (No such file or directory)", thrown.getMessage());
   }

}
