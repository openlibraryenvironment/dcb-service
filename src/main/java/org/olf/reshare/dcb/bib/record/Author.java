package org.olf.reshare.dcb.bib.record;

public class Author {

    String name;
    Identifier identifier;

    public Author() {}

    public Author(String name, Identifier identifier) {
        this.name = name;
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Identifier identifier) {
        this.identifier = identifier;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(name != null) {sb.append("name: ").append(name);}
        if(identifier != null) {sb.append("ientifier: ").append(identifier);}
        return sb.toString();
    } 
}
