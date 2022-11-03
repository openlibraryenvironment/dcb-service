package org.olf.reshare.dcb.bib.marc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.Record;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

/*
The MarcImportService is a concrete class that "does the work". 
*/
@Singleton
@Serdeable
public class MarcImportService {

   // https://www.programcreek.com/java-api-examples/?api=org.marc4j.MarcStreamReader
   // List of all records
   public static List<Record> read(String fileName) throws Exception {
      InputStream in = new FileInputStream(new File(fileName));
      MarcReader reader = new MarcStreamReader(in);
    
      List<Record> records = new ArrayList<>();
      while (reader.hasNext()) {
        Record marc4jRecord = reader.next();
        records.add(marc4jRecord);
      }
      return records;
    }
   

   // Simply returns the full marc file
   public static MarcReader readMarcFile(String fileName) throws Exception {
      InputStream  input  = new FileInputStream(new File(fileName));
      MarcReader marcFile = new MarcStreamReader(input);
      return marcFile;
   }


   public static void printNrecords(String fileName, int n) throws Exception {
      int currentCount = 0;
      int maxCount = n;
      MarcReader mymarcfile = readMarcFile(fileName);

      while (mymarcfile.hasNext() && currentCount != maxCount) {
         Record record = mymarcfile.next();
         System.out.println(record);
         currentCount++;
      } 
   }


   // For testing
   public static void main(String args[]) throws Exception {
      
      String filepath = "../SGCLsample1.mrc";

      // Print all marc records as ArrayList
      //System.out.println(MarcImportService.read(filepath));

      // Print the marc file as type MarcReader
      //System.out.println(MarcImportService.readMarcFile(filepath));

      // Print by specifying what number of records you want
      MarcImportService.printNrecords(filepath, 1);
   }
 }
