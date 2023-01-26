package edu.upc.essi.dtim.nextiaqr.models.graph_rdfs;

import lombok.Data;

import java.util.List;

@Data
public class NodeClass {

    private String uri;
    private Boolean isIntegrated;
    private String integratedURI;
    private List<AttDatatype> datatypes;

}
