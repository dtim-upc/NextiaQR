package edu.upc.essi.dtim.nextiaqr.models.graph_rdfs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jgrapht.graph.DefaultEdge;

@AllArgsConstructor
@Getter
public class Relationship extends DefaultEdge {

    private String label;
    private String uri;
    private String integratedURI;
    private Boolean isIntegrated;
    private String domain;
    private String range;

    @Override
    public String toString()
    {
        return label;
    }

}
