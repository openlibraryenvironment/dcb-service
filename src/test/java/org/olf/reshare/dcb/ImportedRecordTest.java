package org.olf.reshare.dcb;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import static org.olf.reshare.dcb.bib.record.AuthorBuilder.Author;
import static org.olf.reshare.dcb.bib.record.IdentifierBuilder.Identifier;
import org.olf.reshare.dcb.bib.record.Author;
import org.olf.reshare.dcb.bib.record.AuthorBuilder;
import org.olf.reshare.dcb.bib.record.Description;
import org.olf.reshare.dcb.bib.record.DescriptionBuilder;
import org.olf.reshare.dcb.bib.record.Edition;
import org.olf.reshare.dcb.bib.record.EditionBuilder;
import org.olf.reshare.dcb.bib.record.Identifier;
import org.olf.reshare.dcb.bib.record.IdentifierBuilder;
import org.olf.reshare.dcb.bib.record.ImportedRecord;
import org.olf.reshare.dcb.bib.record.ImportedRecordBuilder;
import org.olf.reshare.dcb.bib.record.PublicationInformation;
import org.olf.reshare.dcb.bib.record.PublicationInformationBuilder;
import org.olf.reshare.dcb.bib.record.Title;
import org.olf.reshare.dcb.bib.record.TitleBuilder;


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
      identifiers.add( IdentifierBuilder.builder().namespace("test").value("testValue").build() );

      List<Author> authors = new ArrayList<>();
      authors.add( AuthorBuilder.builder().name("name").build() );

      List<Title> titleInformation = new ArrayList<>();
      titleInformation.add( TitleBuilder.builder().title("title").build() );

      List<PublicationInformation> publicationInformation = new ArrayList<>();
      publicationInformation.add( PublicationInformationBuilder.builder().publicationInformation("info").build() );

      List<Description> descriptions = new ArrayList<>();
      descriptions.add( DescriptionBuilder.builder().description("description").build() );

      ImportedRecord importedRecord = ImportedRecordBuilder.builder()
         .identifiers( identifiers )
         .mainAuthor( AuthorBuilder.builder().name("name").build() )
         .otherAuthors( authors )
         .title( TitleBuilder.builder().title("title").build() )
         .titleInformation(titleInformation)
         .edition( EditionBuilder.builder().edition("edition").build() )
         .publicationInformation( publicationInformation )
         .descriptions( descriptions )
         .build();

      assertEquals("[Identifier[id=null, namespace=test, value=testValue, values=null]]", importedRecord.identifiers().toString());
      assertEquals("Author[id=null, name=name, identifier=null]", importedRecord.mainAuthor().toString());
      assertEquals("Title[id=null, title=title, otherTitleInformation=null]", importedRecord.title().toString());
      assertEquals("[Title[id=null, title=title, otherTitleInformation=null]]", importedRecord.titleInformation().toString());
      assertEquals("Edition[id=null, edition=edition]", importedRecord.edition().toString());
      assertEquals("[PublicationInformation[id=null, publicationInformation=info, allPublicationInformation=null]]", importedRecord.publicationInformation().toString());
      assertEquals("[Description[id=null, description=description, descriptions=null]]", importedRecord.descriptions().toString());
   }

   // TODO: Should except empty strings?
}
