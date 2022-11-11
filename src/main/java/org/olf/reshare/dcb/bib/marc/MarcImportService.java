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
import org.olf.reshare.dcb.ImportedRecord;
import org.olf.reshare.dcb.bib.BibRecordService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

/*
The MarcImportService is a concrete class that "does the work". 
*/
@Singleton
public class MarcImportService{

   int recordCount = 0;

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

   // TODO: transform to ArrayList more efficiently
   public List<Record> read(String fileName) throws Exception {
      InputStream in = new FileInputStream(fileName);
      MarcReader reader = new MarcStreamReader(in);
    
      List<Record> records = new ArrayList<>();
      while (reader.hasNext()) {
        Record marc4jRecord = reader.next();
        records.add(marc4jRecord);
      }
      return records;
    }

   // Add each ImportedRecord without flux
   public void doImport(String marcFileName) throws Exception {
      MarcReader reader = getMarcFile(marcFileName);
      
      while (reader.hasNext()) {
         Record record = reader.next();
         
         // Get desired fields from marc record
         String controlNumber = record.getVariableField("001").toString();
         String controlNumberIdentifier =  record.getVariableField("003").toString();
         String title = record.getVariableField("245").toString();
         String itemType = record.getVariableField("075").toString();

         // parse into ImportedRecord type
         ImportedRecord importedRecord = new ImportedRecord(UUID.randomUUID(), controlNumber, controlNumberIdentifier, title, itemType);
         bibRecordService.addBibRecord(importedRecord);

         // How many records?
         recordCount++;
      }
      System.out.print("Processed " + recordCount + " records.");
      recordCount=0;
    }

    public Iterable<Record> convertToIterable() throws Exception {
      final MarcReader reader = getMarcFile("../test-data/100K_Truman_records.D221005.mrc");
      final Iterable<Record> iterableMarcStream = new Iterable<>() {
            @Override
            public Iterator<Record> iterator() {
               return new Iterator<Record>() {

               // Delegate to the reader. We made it final so we can reference from nested
               // scopes.
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
   
   // Attempt at using flux
   public void recordsFlux() throws Exception {
      Flux<Record> fluxOfListOfRecord = Flux.fromIterable(convertToIterable());
      fluxOfListOfRecord.subscribe(record -> {

         ControlField field001 = (ControlField) record.getVariableField("001");
         ControlField field003 = (ControlField) record.getVariableField("003");
         DataField field245 = (DataField) record.getVariableField("245");
         DataField field075 = (DataField) record.getVariableField("075");
         
      	ImportedRecord importedRecord = new ImportedRecord(UUID.randomUUID(), Objects.toString(field001, null), Objects.toString(field003, null), Objects.toString(field245, null), Objects.toString(field075, null));
         bibRecordService.addBibRecord(importedRecord);
      });
   }
}
