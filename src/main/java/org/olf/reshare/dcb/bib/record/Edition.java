package org.olf.reshare.dcb.bib.record;

public class Edition {
   String edition;

   public Edition(String edition) {
       this.edition = edition;
   }

   public Edition() {
   }

   @Override
   public String toString() {
       StringBuilder sb = new StringBuilder();
       if(edition != null) {sb.append("edition: ").append(edition);}
       return sb.toString();
   } 
}
