package org.olf.reshare.dcb;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
import org.olf.reshare.dcb.bib.record.subclasses.Author;
import org.olf.reshare.dcb.bib.record.subclasses.Description;
import org.olf.reshare.dcb.bib.record.subclasses.Edition;
import org.olf.reshare.dcb.bib.record.subclasses.Identifier;
import org.olf.reshare.dcb.bib.record.subclasses.PublicationInformation;
import org.olf.reshare.dcb.bib.record.subclasses.Title;

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

   @Test
   void testFields() throws Exception {

      List<Identifier> identifiers = new ArrayList<>();
      identifiers.add(new Identifier("Identifier", "test"));

      List<Author> otherAuthors = new ArrayList<>();
      otherAuthors.add(new Author("otherAuthors", "test"));

      List<Title> titleInformation = new ArrayList<>();
      titleInformation.add(new Title("Info", "test"));

      List<PublicationInformation> publicationInformation = new ArrayList<>();
      publicationInformation.add(new PublicationInformation("Info", "test"));

      List<Description> descriptions = new ArrayList<>();
      descriptions.add(new Description("Description", "test"));

      ImportedRecord importedRecord = ImportedRecordBuilder.builder()
      .identifiers(identifiers)
      .mainAuthor(new Author("main author", "test"))
      .otherAuthors(otherAuthors)
      .title(new Title("Title", "test"))
      .titleInformation(titleInformation)
      .edition(new Edition("Edition", "test"))
      .publicationInformation(publicationInformation)
      .descriptions(descriptions)
      .build();


      assertEquals("[{ namespace: \"Identifier\", value: \"test\" }]", importedRecord.identifiers().toString());
      assertEquals("{ namespace: \"main author\", value: \"test\" }", importedRecord.mainAuthor().toString());
      assertEquals("[{ namespace: \"otherAuthors\", value: \"test\" }]", importedRecord.otherAuthors().toString());
      assertEquals("{ namespace: \"Title\", value: \"test\" }", importedRecord.title().toString());
      assertEquals("[{ namespace: \"Info\", value: \"test\" }]", importedRecord.titleInformation().toString());
      assertEquals("{ namespace: \"Edition\", value: \"test\" }", importedRecord.edition().toString());
      assertEquals("[{ namespace: \"Info\", value: \"test\" }]", importedRecord.publicationInformation().toString());
      assertEquals("[{ namespace: \"Description\", value: \"test\" }]", importedRecord.descriptions().toString());
   }

   // TODO: Should except empty strings?
}
