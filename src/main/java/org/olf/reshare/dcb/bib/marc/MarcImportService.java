package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;
import org.olf.reshare.dcb.bib.BibRecordService;
import org.olf.reshare.dcb.bib.record.ImportedRecord;
import org.olf.reshare.dcb.bib.record.ImportedRecordBuilder;
import org.olf.reshare.dcb.bib.record.subclasses.*;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

/*
The MarcImportService is a concrete class that "does the work". 
*/
@Singleton
public class MarcImportService{

   private final BibRecordService bibRecordService;

   public MarcImportService(BibRecordService bibRecordService) {
      this.bibRecordService = bibRecordService;
   }

   // Simply returns the full marc file as type MarcReader
   private MarcReader getMarcFile(String fileName) throws Exception {
      InputStream  input  = new FileInputStream(new File(fileName));
      MarcReader marcFile = new MarcStreamReader(input);
      return marcFile;
   }

   private Iterable<Record> convertToIterable(String fileName) throws Exception {
      final MarcReader reader = getMarcFile(fileName);
      final Iterable<Record> iterableMarcStream = new Iterable<>() {
            @Override
            public Iterator<Record> iterator() {   
               return new Iterator<Record>() {

               // Delegate to the reader. We made it final so we can reference from nested scopes.
               @Override
               public boolean hasNext() {
                  return reader.hasNext();
               }

               @Override
               public Record next() {
                  return reader.next();
               }
            };
         }
      };
      return iterableMarcStream;
   }

   // 0(n)
   private List<String> getDataFromAllOccurerncesOfField(List<VariableField> allFields, String subfield){
      List<String> allData = new ArrayList<>();

      for (int i = 0; i < allFields.size(); i++) {
         DataField field = (DataField) allFields.get(i);
         if(field != null && field.getSubfieldsAsString(subfield) != null) {
            allData.add(field.getSubfieldsAsString(subfield));
         }
      }
      return allData;
   }
   
   // Using flux to transform records into "ImportedRecord" type
   public void fluxOfRecords(String fileName) throws Exception {
      Flux<Record> fluxOfListOfRecord = Flux.fromIterable(convertToIterable(fileName));
      fluxOfListOfRecord.subscribe(record -> {

         // Identiders
         List<Identifier> identifiers = new ArrayList<>();

         String controlNumberValue = Objects.toString(record.getControlNumber(), null);
         Identifier controlNumber = new Identifier("Control Number", controlNumberValue);
         identifiers.add(controlNumber);

         ControlField field003 = (ControlField) record.getVariableField("003");
         String value003 = null;
         if(field003 != null) {value003 = field003.getData();}
         Identifier ControlNumberIdentifier = new Identifier("Control Number Identifer", value003);
         identifiers.add(ControlNumberIdentifier);

         DataField field010 = (DataField) record.getVariableField("010");
         String value010a = null;
         if(field010 != null) {value010a = Objects.toString(field010.getSubfieldsAsString("a"), null);}
         Identifier ICCN = new Identifier("Library of Congress Control Number", value010a);
         identifiers.add(ICCN);

         List<VariableField> all020 = record.getVariableFields("020");
         List<String> values020a = getDataFromAllOccurerncesOfField(all020, "a");
         Identifier ISBN = new Identifier("International Standard Book Number", values020a);
         identifiers.add(ISBN);

         DataField field022 = (DataField)  record.getVariableField("022");
         String value022a = null;
         if(field022 != null) {value022a = Objects.toString(field022.getSubfieldsAsString("a"));}
         Identifier ISSN = new Identifier("International Standard Serial Number", value022a);
         identifiers.add(ISSN);

         // Authors
         DataField field100 = (DataField) record.getVariableField("100");
         String value100a = null;
         if(field100 != null) {value100a = Objects.toString(field100.getSubfieldsAsString("a"));}
         Author mainAuthor = (Author) new Author( "Personal Name", value100a );

         List<Author> otherAuthors = new ArrayList<>();

         List<VariableField> all110 = record.getVariableFields("110");
         List<String> values110a = getDataFromAllOccurerncesOfField(all110, "a");
         Author field110 = new Author( "corporateName", values110a );
         otherAuthors.add(field110);

         List<VariableField> all111 = record.getVariableFields("111");
         List<String> values111a = getDataFromAllOccurerncesOfField(all111, "a");
         Author field111 = new Author( "meetingOrJurisdictionName", values111a );
         otherAuthors.add(field111);

         List<VariableField> all700 = record.getVariableFields("700");
         List<String> values700a = getDataFromAllOccurerncesOfField(all700, "a");
         Author field700 = new Author( "personalNameNR", values700a );
         otherAuthors.add(field700);

         List<VariableField> all710 = record.getVariableFields("710");
         List<String> values710a = getDataFromAllOccurerncesOfField(all710, "a");
         Author field710 = new Author( "corporateOrJurisdictionName", values710a );
         otherAuthors.add(field710);

         List<VariableField> all711 = record.getVariableFields("711");
         List<String> values711a = getDataFromAllOccurerncesOfField(all711, "a");
         Author field711 = new Author( "meetingName", values711a );
         otherAuthors.add(field711);

         List<VariableField> all720 = record.getVariableFields("720");
         List<String> values720a = getDataFromAllOccurerncesOfField(all720, "a");
         Author field720 = new Author( "nameNR", values720a );
         otherAuthors.add(field720);
   
         // Title Information
         List<VariableField> all245a = record.getVariableFields("245");
         List<String> values245a = getDataFromAllOccurerncesOfField(all245a, "a");
         Title title = new Title("Title (NR)", values245a);

         List<Title> titleInformation = new ArrayList<>();

         List<VariableField> all245b = record.getVariableFields("245");
         List<String> values245b = getDataFromAllOccurerncesOfField(all245b,"b");
         Title field245b = new Title("Remainder of title (NR)", values245b);
         titleInformation.add(field245b);

         List<VariableField> all245c = record.getVariableFields("245");
         List<String> values245c = getDataFromAllOccurerncesOfField(all245c, "c");
         Title field245c = new Title("Statement of responsibility, etc. (NR)", values245c);
         titleInformation.add(field245c);

         // publication information
         List<PublicationInformation> publicationInformation = new ArrayList<>();

         List<VariableField> all260a = record.getVariableFields("260");
         List<String> values260a = getDataFromAllOccurerncesOfField(all260a, "a");
         PublicationInformation field260a = new PublicationInformation("Place of publication, distribution, etc. (R)", values260a );
         publicationInformation.add(field260a);

         List<VariableField> all260b = record.getVariableFields("260");
         List<String> values260b = getDataFromAllOccurerncesOfField(all260b, "b");
         PublicationInformation field260b = new PublicationInformation("Name of publisher, distributor, etc. (R)", values260b );
         publicationInformation.add(field260b);

         List<VariableField> all260c = record.getVariableFields("260");
         List<String> values260c = getDataFromAllOccurerncesOfField(all260c, "c");
         PublicationInformation field260c = new PublicationInformation("Date of publication, distribution, etc. (R)", values260c );
         publicationInformation.add(field260c);

         List<VariableField> all260e = record.getVariableFields("260");
         List<String> values260e = getDataFromAllOccurerncesOfField(all260e, "e");
         PublicationInformation field260e = new PublicationInformation("Place of manufacture (R)", values260e );
         publicationInformation.add(field260e);

         List<VariableField> all260f = record.getVariableFields("260");
         List<String> values260f = getDataFromAllOccurerncesOfField(all260f, "f");
         PublicationInformation field260f = new PublicationInformation("Manufacturer", values260f );
         publicationInformation.add(field260f);

         // Edition
         List<VariableField> all250 = record.getVariableFields("250");
         List<String> values250a = getDataFromAllOccurerncesOfField(all250, "a");
         Edition edition = new Edition( "Edition Statement", values250a );

         // Descriptions
         List<Description> descriptions = new ArrayList<>();

         List<VariableField> all300a = record.getVariableFields("300");
         List<String> values300a = getDataFromAllOccurerncesOfField(all300a, "a");
         Description field300a = new Description("Extent", values300a );
         descriptions.add(field300a);

         List<VariableField> all300b = record.getVariableFields("300");
         List<String> values300b = getDataFromAllOccurerncesOfField(all300b, "b");
         Description field300b = new Description("Other physical details", values300b );
         descriptions.add(field300b);

         List<VariableField> all300c = record.getVariableFields("300");
         List<String> values300c = getDataFromAllOccurerncesOfField(all300c, "c");
         Description field300c = new Description("Dimensions", values300c );
         descriptions.add(field300c);

         List<VariableField> all300e = record.getVariableFields("300");
         List<String> values300e = getDataFromAllOccurerncesOfField(all300e, "e");
         Description field300e = new Description("Accompanying material", values300e );
         descriptions.add(field300e);

         List<VariableField> all300f = record.getVariableFields("300");
         List<String> values300f = getDataFromAllOccurerncesOfField(all300f, "f");
         Description field300f = new Description("Type of unit", values300f );
         descriptions.add(field300f);

         List<VariableField> all520a = record.getVariableFields("520");
         List<String> values520a = getDataFromAllOccurerncesOfField(all520a, "a");
         Description field520a = new Description("Summary, etc.", values520a );
         descriptions.add(field520a);

         List<VariableField> all520b = record.getVariableFields("520");
         List<String> values520b = getDataFromAllOccurerncesOfField(all520b, "b");
         Description field520b = new Description("Expansion of summary note", values520b );
         descriptions.add(field520b);

         List<VariableField> all520c = record.getVariableFields("520");
         List<String> values520c = getDataFromAllOccurerncesOfField(all520c, "c");
         Description field520c = new Description("Assigning source", values520c );
         descriptions.add(field520c);

         List<VariableField> all520u = record.getVariableFields("520");
         List<String> values520u = getDataFromAllOccurerncesOfField(all520u, "u");
         Description field520u = new Description("Uniform Resource Identifier", values520u );
         descriptions.add(field520u);

         List<VariableField> all5202 = record.getVariableFields("520");
         List<String> values5202 = getDataFromAllOccurerncesOfField(all5202, "2");
         Description field5202 = new Description("Source", values5202 );
         descriptions.add(field5202);

         List<VariableField> all650a = record.getVariableFields("650");
         List<String> values650a = getDataFromAllOccurerncesOfField(all650a, "a");
         Description field650a = new Description("Topical term or geographic name entry element", values650a );
         descriptions.add(field650a);

         List<VariableField> all650b = record.getVariableFields("650");
         List<String> values650b = getDataFromAllOccurerncesOfField(all650b, "b");
         Description field650b = new Description("Topical term following geographic name entry element", values650b );
         descriptions.add(field650b);

         List<VariableField> all650c = record.getVariableFields("650");
         List<String> values650c = getDataFromAllOccurerncesOfField(all650c, "c");
         Description field650c = new Description("Location of event", values650c );
         descriptions.add(field650c);

         List<VariableField> all650d = record.getVariableFields("650");
         List<String> values650d = getDataFromAllOccurerncesOfField(all650d, "d");
         Description field650d = new Description("Active dates", values650d );
         descriptions.add(field650d);
      
         ImportedRecord importedRecord = ImportedRecordBuilder.builder()
                           .id(UUID.randomUUID())
                           .identifiers(identifiers)
                           .mainAuthor(mainAuthor)
                           .otherAuthors(otherAuthors)
                           .title(title)
                           .titleInformation(titleInformation)
                           .edition(edition)
                           .publicationInformation(publicationInformation)
                           .descriptions(descriptions)
                           .build();

         bibRecordService.addBibRecord(importedRecord);
      });
   }
}
