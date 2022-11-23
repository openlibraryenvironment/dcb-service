package org.olf.reshare.dcb.bib.record;

import java.util.ArrayList;
import java.util.List;

public class PublicationInformation {

    String publicationInformation;
    List<String> allPublicationInformation = new ArrayList<>();

    public PublicationInformation(List<String> allPublicationInformation) {
       this.allPublicationInformation = allPublicationInformation;
    }

    public PublicationInformation(String PublicationInformation) {
        this.publicationInformation = publicationInformation;
    }
 
    public PublicationInformation() {
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(publicationInformation != null) {sb.append("publicationInformation: ").append(publicationInformation);}
        if(allPublicationInformation != null) {sb.append("allPublicationInformation: ").append(allPublicationInformation);}
        return sb.toString();
    } 
}
