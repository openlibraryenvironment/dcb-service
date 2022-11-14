package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;

import java.util.Objects;
import java.util.UUID;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.ImportedRecordBuilder;

import org.olf.reshare.dcb.bib.BibRecordService;

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

   // Simply returns the full marc file
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
   
   // Using flux to transform records into 'ImportedRecord' type
   public void fluxOfRecords(String fileName) throws Exception {
      Flux<Record> fluxOfListOfRecord = Flux.fromIterable(convertToIterable(fileName));
      fluxOfListOfRecord.subscribe(record -> {

         // 0XX
         ControlField field001 = (ControlField) record.getVariableField("001"); // control number assigned by the organization creating, using, or distributing the record.
         ControlField field003 = (ControlField) record.getVariableField("003"); // MARC Organization Code identifying whose system control number is present in field 001.
         DataField field010 = (DataField) record.getVariableField("010"); // Library of Congress Control Number (LCCN)
         DataField field020 = (DataField) record.getVariableField("020"); // International Standard Book Number (ISBN)
         DataField field022 = (DataField) record.getVariableField("022");// International Standard Serial Number (ISSN)
         DataField field075 = (DataField) record.getVariableField("075"); // Type of entity that is described by the authority record as a whole.
         DataField field082 = (DataField) record.getVariableField("082"); // Dewey Decimal Classification Number (DDCN)
         DataField field092 = (DataField) record.getVariableField("092"); // Locally Assigned Dewey Call Number (LADCN)
         // 1XX
         DataField field100 = (DataField) record.getVariableField("100"); // personal name main entry (author)
         // 2XX
         DataField field245 = (DataField) record.getVariableField("245"); // title information (which includes the title, other title information, and the statement of responsibility)
         DataField field250 = (DataField) record.getVariableField("250"); // edition
         DataField field260 = (DataField) record.getVariableField("260"); // publication information
         // 3XX - 8XX
         DataField field300 = (DataField) record.getVariableField("300"); // physical description (often referred to as the "collation" when describing books)
         DataField field490 = (DataField) record.getVariableField("490"); // series statement
         DataField field520 = (DataField) record.getVariableField("520"); // annotation or summary note
         DataField field650 = (DataField) record.getVariableField("650"); // topical subject heading
         DataField field700 = (DataField) record.getVariableField("700"); // personal name added entry (joint author, editor, or illustrator)

         ImportedRecord importedRecord = ImportedRecordBuilder.builder()
                           .identifier(UUID.randomUUID())
                           .controlNumber(Objects.toString(field001, null))
                           .controlNumberIdentifier(Objects.toString(field003, null))
                           .LCCN(Objects.toString(field010, null))
                           .ISBN(Objects.toString(field020, null))
                           .ISSN(Objects.toString(field022, null))
                           .itemType(Objects.toString(field075, null))
                           .DDCN(Objects.toString(field082, null))
                           .LADCN(Objects.toString(field092, null))
                           .author(Objects.toString(field100, null))
                           .title(Objects.toString(field245, null))
                           .edition(Objects.toString(field250, null))
                           .publicationInformation(Objects.toString(field260, null))
                           .physicalDescription(Objects.toString(field300, null))
                           .seriesStatement(Objects.toString(field490, null))
                           .annotationOrSummaryNote(Objects.toString(field520, null))
                           .topicalSubjectHeading(Objects.toString(field650, null))
                           .personalNameAddedEntry(Objects.toString(field700, null))
                           .build();

         bibRecordService.addBibRecord(importedRecord);
      });
   }
}
