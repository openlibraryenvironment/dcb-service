package org.olf.reshare.dcb.bib.record;

import java.util.ArrayList;
import java.util.List;

public class Description {

   String description;
   List<String> descriptions = new ArrayList<>();

   public Description(String description) {
       this.description = description;
   }

   public Description(List<String> descriptions) {
      this.descriptions = descriptions;
   }

   public Description() {
   }

   @Override
   public String toString() {
       StringBuilder sb = new StringBuilder();
       if(description != null) {sb.append("description: ").append(description);}
       if(descriptions != null) {sb.append("descriptions: ").append(descriptions);}
       return sb.toString();
   } 
}
