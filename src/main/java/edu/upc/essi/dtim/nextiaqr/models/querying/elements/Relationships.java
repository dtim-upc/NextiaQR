package edu.upc.essi.dtim.nextiaqr.models.querying.elements;

import edu.upc.essi.dtim.nextiaqr.models.graph_rdfs.NodeClass;
import lombok.Data;

@Data
public class Relationships {

    private NodeClass subject;
    private String relationship;
    private NodeClass object;
    private Boolean isIntegrated;
    private String integratedURI;



}
