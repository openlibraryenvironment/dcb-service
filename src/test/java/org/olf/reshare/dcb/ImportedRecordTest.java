package org.olf.reshare.dcb;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.olf.reshare.dcb.bib.record.ImportedRecord;
import org.olf.reshare.dcb.bib.record.ImportedRecordBuilder;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;


@MicronautTest
public class ImportedRecordTest {

   private final String testMarcFile = "src/main/resources/5recordsSGCLsample1.mrc";

   @Test
   private MarcReader testImportOfFile() throws Exception {
      String marcFileName = testMarcFile;
      InputStream input = new FileInputStream(new File(marcFileName));
      MarcReader marcFile = new MarcStreamReader(input);
      assertNotNull(marcFileName);

      return marcFile;
   }

   @Test
   void testConstructor() throws Exception {
      ImportedRecord importedRecord = ImportedRecordBuilder.builder().build();
      assertEquals(ImportedRecord.class, importedRecord.getClass());
   }

   // TODO: Is this how we want an UUID to be generated?
   @Test
   void testIdentifiersAreDifferent() throws Exception {

      ImportedRecord id1 = ImportedRecordBuilder.builder().id(UUID.randomUUID()).build();
      ImportedRecord id2 = ImportedRecordBuilder.builder().id(UUID.randomUUID()).build();

      assertNotNull(id1.id());
      assertNotNull(id2.id());
      assertNotEquals(id2.id(), id1.id());
   }

   // TODO: Identifer null even though specifier @NonNull
   @Test
   void testNoIdentifer() throws Exception {

      ImportedRecord id1 = ImportedRecordBuilder.builder().build();
      assertNull(id1.id());
   }

   // TODO: Should except empty strings?
   /** 
   @Test
   void testEmptyStrings() throws Exception {

      ImportedRecord id1 = ImportedRecordBuilder.builder()
                              .identifier("")
                              .controlNumber("")
                              .controlNumberIdentifier("")
                              .LCCN("")
                              .ISBN("")
                              .ISSN("")
                              .itemType("")
                              .DDCN("")
                              .LADCN("")
                              .author("")
                              .title("")
                              .edition("")
                              .publicationInformation("")
                              .physicalDescription("")
                              .seriesStatement("")
                              .annotationOrSummaryNote("")
                              .topicalSubjectHeading("")
                              .personalNameAddedEntry("")
                              .build();
      assertNotEquals("", id1.identifier());
      assertNotEquals("", id1.controlNumber());
      assertNotEquals("", id1.LCCN());
      assertNotEquals("", id1.ISBN());
      assertNotEquals("", id1.ISSN());
      assertNotEquals("", id1.itemType());
      assertNotEquals("", id1.DDCN());
      assertNotEquals("", id1.LADCN());
      assertNotEquals("", id1.author());
      assertNotEquals("", id1.title());
      assertNotEquals("", id1.edition());
      assertNotEquals("", id1.publicationInformation());
      assertNotEquals("", id1.physicalDescription());
      assertNotEquals("", id1.seriesStatement());
      assertNotEquals("", id1.annotationOrSummaryNote());
      assertNotEquals("", id1.topicalSubjectHeading());
      assertNotEquals("", id1.personalNameAddedEntry());
   }
   */

   
}
