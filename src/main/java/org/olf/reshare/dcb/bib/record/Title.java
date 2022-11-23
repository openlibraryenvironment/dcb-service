package org.olf.reshare.dcb.bib.record;

import java.util.ArrayList;
import java.util.List;

public class Title {

    String title;
    List<String> otherTitleInformation = new ArrayList<>();

    public Title(List<String> otherTitleInformation) {
       this.otherTitleInformation = otherTitleInformation;
    }

    public Title(String title) {
        this.title = title;
    }

    public Title() {
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(title != null) {sb.append("title: ").append(title);}
        if(otherTitleInformation != null) {sb.append("otherTitleInformation: ").append(otherTitleInformation);}
        return sb.toString();
    } 
}
