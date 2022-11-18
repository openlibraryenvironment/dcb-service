package org.olf.reshare.dcb.bib.record.subclasses;

import java.util.ArrayList;
import java.util.List;

public class Identifier {

    // Instance varibles
    public String namespace;
    public String value;
    List<String> values = new ArrayList<>();

    // Constructors
    public Identifier() {}

    public Identifier(String namespace, String value) {
        this.namespace = namespace;
        this.value = value;
    }

    public Identifier(String namespace, List<String> values) {
        this.namespace = namespace;
        this.values = values;
    }

    // getter and setters
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        sb.append("namespace: \"").append(namespace);
        if(!values.isEmpty()) {sb.append("\", values: \"").append(values);}
        else{sb.append("\", value: \"").append(value);}
        sb.append("\" }");
        return sb.toString();
    }
}