package org.olf.reshare.dcb;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.bib.marc.MarcImportService;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest
public class MarcImportServiceTest {
   
   private static final String marcFileName = "../SGCLsample1.mrc";
   
   @Inject
   MarcImportService marcImportService;

   @Test
   void testDoImport() {
       try {
         marcImportService.doImport(marcFileName);
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

}
