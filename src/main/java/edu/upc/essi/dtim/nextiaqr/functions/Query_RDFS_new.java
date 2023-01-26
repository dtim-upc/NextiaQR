package edu.upc.essi.dtim.nextiaqr.functions;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import edu.upc.essi.dtim.nextiaqr.jena.RDFUtil;
import edu.upc.essi.dtim.nextiaqr.models.graph.CQVertex;
import edu.upc.essi.dtim.nextiaqr.models.graph.IntegrationEdge;
import edu.upc.essi.dtim.nextiaqr.models.graph.IntegrationGraph;
import edu.upc.essi.dtim.nextiaqr.models.graph.RelationshipEdge;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.Namespaces;
import edu.upc.essi.dtim.nextiaqr.models.querying.ConjunctiveQuery;
import edu.upc.essi.dtim.nextiaqr.models.querying.RewritingResult;
import edu.upc.essi.dtim.nextiaqr.models.querying.Wrapper;
import edu.upc.essi.dtim.nextiaqr.utils.Tuple2;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.PathBlock;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class Query_RDFS_new {



    public RewritingResult rewriteToUnionOfConjunctiveQueries(String query, Dataset T) {

        RewritingResult res = new RewritingResult();

        Tuple2<Map<String,Tuple2<Integer, String>>, List<TriplePath> > queryStructure = parseSPARQL(query);

        List<TriplePath> PHI_p = queryStructure._2;
//        populateOptimizedStructures(T,PHI_p);
//        InfModel PHI_o = queryStructure._3;


        // Identify query classes
        Graph<String, RelationshipEdge> conceptsGraph = new SimpleDirectedGraph<>(RelationshipEdge.class);
        PHI_p.forEach(t -> {
            // Add only classes so its easier to populate later the list of classes
            // Add only classes connected by object properties. If object is var, then property is datatype.
            if( !t.getObject().isVariable() ) {
                conceptsGraph.addVertex(t.getSubject().getURI());
                conceptsGraph.addVertex(t.getObject().getURI());
                conceptsGraph.addEdge(t.getSubject().getURI(), t.getObject().getURI(),
                        new RelationshipEdge(t.getPredicate().getURI()) );
            }
        });
        // This is required when only one class is queried, where all edges are datatype
        if (conceptsGraph.vertexSet().isEmpty()) {
            conceptsGraph.addVertex(PHI_p.get(0).getSubject().getURI());
        }

        IntegrationGraph G = new IntegrationGraph();
        conceptsGraph.vertexSet().forEach(c -> {
            Set<ConjunctiveQuery> CQs = getConceptCoveringCQs(c,T);
            G.addVertex(new CQVertex(c,CQs));
        });
        conceptsGraph.edgeSet().forEach(e -> {

            //This should only be performed over object properties that represents a join. Objectproperty - JoinProperty - integratedDatatypeProperty
            CQVertex source = G.vertexSet().stream().filter(v -> v.getLabel().equals(conceptsGraph.getEdgeSource(e))).findFirst().get();
            CQVertex target = G.vertexSet().stream().filter(v -> v.getLabel().equals(conceptsGraph.getEdgeTarget(e))).findFirst().get();

            Set<Wrapper> wrappers = getEdgeCoveringWrappers(source.getLabel(),target.getLabel(),e.getLabel(),T);
            G.addEdge(source,target,new IntegrationEdge(e.getLabel(),wrappers));
        });

        //Define a data structure D: CQVertex --> BGP
        //  tracks the subgraph that a CQVertex subsumes (used when merging vertices)
        //  we only need to monitor concepts, the previous phase already guaranteed that queries cover all features
        Map<CQVertex, BasicPattern> D = Maps.newHashMap();
        PHI_p.forEach(t -> {
            //if (t.getPredicate().getURI().equals(GlobalGraph.HAS_FEATURE.val())) {
            if (conceptsGraph.vertexSet().contains(t.getSubject().getURI())) {
                D.putIfAbsent(new CQVertex(t.getSubject().getURI()),new BasicPattern());
                D.get(new CQVertex(t.getSubject().getURI())).add(t.asTriple());
            }
        });

        while (!G.edgeSet().isEmpty()) {

            IntegrationEdge e = G.edgeSet().iterator().next();
            CQVertex source = G.getEdgeSource(e);
            CQVertex target = G.getEdgeTarget(e);

            Set<Wrapper> edgeCoveringWrappers = e.getWrappers();
            Set<ConjunctiveQuery> Qs = source.getCQs();
            Set<ConjunctiveQuery> Qt = target.getCQs();

            BasicPattern both = new BasicPattern();
            both.addAll(D.get(source)); both.addAll(D.get(target));
            //addTriple(both,source.getLabel(),e.getLabel(),target.getLabel());
            //Go back to the original graph to check the labels of the source and target vertex that e connects
//            addTriple(both,conceptsGraph.getEdgeSource(new RelationshipEdge(e.getLabel())),
//                    e.getLabel(),conceptsGraph.getEdgeTarget(new RelationshipEdge(e.getLabel())));

            // union and join
            Set<ConjunctiveQuery> Q = combineSetsOfCQs(Qs, Qt, edgeCoveringWrappers);

            String newLabel = source.getLabel()+"-"+target.getLabel();
            CQVertex joinedVertex = new CQVertex(newLabel,Q);

            //Update D with the new label
            D.put(joinedVertex,both);

            //Remove the processed edge
            G.removeEdge(e);
            //Add the new vertex to the graph
            G.addVertex(joinedVertex);
            //Create edges to the new vertex from those neighbors of source and target
            Graphs.neighborSetOf(G, source).forEach(neighbor -> {
                if (!source.equals(neighbor)) {
                    if (G.containsEdge(source,neighbor)) {
                        IntegrationEdge connectingEdge = G.getEdge(source, neighbor);
                        G.removeEdge(connectingEdge);
                        G.addEdge(joinedVertex, neighbor, connectingEdge);
                    }
                    else if (G.containsEdge(neighbor,source)) {
                        IntegrationEdge connectingEdge = G.getEdge(neighbor,source);
                        G.removeEdge(connectingEdge);
                        G.addEdge(neighbor,joinedVertex,connectingEdge);
                    }
                }
            });
            Graphs.neighborListOf(G, target).forEach(neighbor -> {
                if (!target.equals(neighbor)) {
                    if (G.containsEdge(target,neighbor)) {
                        IntegrationEdge connectingEdge = G.getEdge(target, neighbor);
                        G.removeEdge(connectingEdge);
                        G.addEdge(joinedVertex, neighbor, connectingEdge);
                    }
                    else if (G.containsEdge(neighbor,target)) {
                        IntegrationEdge connectingEdge = G.getEdge(neighbor,target);
                        G.removeEdge(connectingEdge);
                        G.addEdge(neighbor,joinedVertex,connectingEdge);
                    }
                }
            });
            G.removeVertex(source);
            G.removeVertex(target);

        }

        Set<ConjunctiveQuery> ucqs = G.vertexSet().iterator().next().getCQs();
//        Set<ConjunctiveQuery> out = ucqs.stream().filter(cq -> minimal(cq.getWrappers(),PHI_p))
//                .filter(cq -> !(cq.getWrappers().size()>1 && cq.getJoinConditions().size()==0))
//                .filter(cq -> cq.getJoinConditions().size()>=(cq.getWrappers().size()-1))
//                .filter(cq -> minimal(cq.getWrappers(),PHI_p))
//                //.filter(cq -> covering(cq.getWrappers(),PHI_p))
//                .filter(cq -> cq.getProjections().size() >= projectionOrder.size())
//                .collect(Collectors.toSet());
//
//        res.setCQs(out);
//        res.setFeaturesPerAttribute(Maps.newHashMap(featuresPerAttribute));
//        res.setProjectionOrder(queryStructure._1);

        return res;
    }

    // merge conjuntive queries over the same wrapper but different classes
    private ConjunctiveQuery mergeCQs(ConjunctiveQuery CQ_A, ConjunctiveQuery CQ_B){
        ConjunctiveQuery mergedCQ = new ConjunctiveQuery();
        mergedCQ.getProjections().addAll(Sets.union(CQ_A.getProjections(),CQ_B.getProjections()));
        mergedCQ.getJoinConditions().addAll(Sets.union(CQ_A.getJoinConditions(),CQ_B.getJoinConditions()));
        mergedCQ.getWrappers().addAll(Sets.union(CQ_A.getWrappers(),CQ_B.getWrappers()));
        return mergedCQ;
    }


//    public Map<String,Integer> projectionOrder = Maps.newHashMap();
    public Tuple2<Map<String,Tuple2<Integer, String>>, List<TriplePath> > parseSPARQL(String SPARQL) {
        // Compile the SPARQL using ARQ and generate its <pi,phi> representation
        // pi -> projected phi-> triples where clause
        Query q = QueryFactory.create(SPARQL);
        Op ARQ = Algebra.compile(q);

//        PatternVars.vars(q.getQueryPattern()) ;

        Element el = ((ElementGroup)q.getQueryPattern()).get(0);
        ElementPathBlock epb = (ElementPathBlock)el;
        List<TriplePath> tp = epb.getPattern().getList();

        List<Var> selection = q.getProjectVars();

        Map<String,Tuple2<Integer, String>> selectionPi = Maps.newHashMap();
        int i = 0;
        for(TriplePath t : tp) {
//            Triple t = p.asTriple();
            if(selection.contains(t.getObject())) {
                // not sure if we should keep "?" in the projection
                // not sure if we should save the projection order. I guess selection variable has it
                selectionPi.put(t.getPredicate().getURI(), new Tuple2(i, t.getObject().getName()));
                i++;
//                projectionOrder.put()
            }
        }
        return new Tuple2<>(selectionPi, tp);
    }

    private void addTriple(Model model, String s, String p, String o) {
        model.add(new ResourceImpl(s), new PropertyImpl(p), new ResourceImpl(o));
    }
    private void addTriple(BasicPattern pattern, String s, String p, String o) {
        pattern.add(new Triple(new ResourceImpl(s).asNode(), new PropertyImpl(p).asNode(), new ResourceImpl(o).asNode()));
    }


    private Set<ConjunctiveQuery> getConceptCoveringCQs(String c, Dataset T ) {


        Map<Wrapper,Set<String>> attsPerWrapper = Maps.newHashMap();
        Set<String> F = Sets.newHashSet();

        // maybe should be done in minimal graph and not in all graphs
        String query = " SELECT ?g (?property AS ?datatype) WHERE { " +
                " GRAPH <http://minimal> { " +
                " ?property <"+ RDFS.domain.getURI() +"> <"+ c +">. " +
//                " ?property <"+ RDF.type.getURI() +"> <"+ RDF.Property.getURI() +">. " +
                " ?property <"+ RDFS.range.getURI() +"> ?resource. " +
                "    FILTER NOT EXISTS {" +
                "    ?resource <"+ RDF.type.getURI() +"> ?type. " +
                "    } " +
                " } " +
                " } ";
        //datatype properties over the global graph
        RDFUtil.runAQuery(query, T)
                .forEachRemaining(f -> F.add(f.get("datatype").asResource().getURI()) );

        //Case when the class has no datatype properties, we need to identify the wrapper using the class instead of the property
        if (F.isEmpty()) {
            query = " SELECT ?g WHERE { GRAPH ?g { " +
                    " <"+ c +"> <"+ RDF.type.getURI() +"> <"+RDFS.Class.getURI()+"> " +
                    " } } ";
            RDFUtil.runAQuery(query, T)
                    .forEachRemaining(wrapper -> {
                        String w = wrapper.get("g").toString();
                        if (!w.equals("http://minimal")) {
                            attsPerWrapper.putIfAbsent(new Wrapper(w), Sets.newHashSet());
                        }
                    });
        } else {
            // unfold LAV mappings
            for(String f : F) {

                query = "SELECT ?g ?subProperty WHERE{ GRAPH ?g { " +
                        " <"+ f +"> <"+RDFS.domain.getURI() +"> <"+c+">. " +
//                        " OPTIONAL { ?subProperty <"+ RDFS.subPropertyOf.getURI() +"> <"+ f +">. }" +
                        " OPTIONAL { ?subProperty <"+ OWL.sameAs.getURI() +"> <"+ f +">. }" +
                        " } FILTER( str(?g) != 'http://minimal' ) } ";

                ResultSet W = RDFUtil.runAQuery(query, T);
                W.forEachRemaining(wRes -> {
                    // with the filter excluding minimal, all graphs are wrappers
                    String w = wRes.get("g").asResource().getURI();
                    String attribute = f;
                    if(wRes.contains("subProperty")){
                        attribute = wRes.get("subProperty").asResource().getURI();
                    }
                    attsPerWrapper.putIfAbsent(new Wrapper(w), Sets.newHashSet());
                    Set<String> currentSet = attsPerWrapper.get(new Wrapper(w));
                    currentSet.add(attribute);
                    attsPerWrapper.put(new Wrapper(w), currentSet);
                });
            }
        }

        Set<ConjunctiveQuery> candidateCQs = Sets.newHashSet();
        attsPerWrapper.keySet().forEach(w -> {
            ConjunctiveQuery Q = new ConjunctiveQuery(attsPerWrapper.get(w),Sets.newHashSet(),Sets.newHashSet(w));
            candidateCQs.add(Q);
        });

//        Set<ConjunctiveQuery> coveringCQs = Sets.newHashSet();
//        while (!candidateCQs.isEmpty()) {
//            ConjunctiveQuery Q = candidateCQs.stream().sorted((cq1, cq2) -> {
//                Set<String> features1 = cq1.getProjections().stream().map(a1 -> featuresPerAttribute.get(a1)).collect(Collectors.toSet());
//                Set<String> features2 = cq2.getProjections().stream().map(a2 -> featuresPerAttribute.get(a2)).collect(Collectors.toSet());
//                return Integer.compare(
//                        Sets.intersection(featuresPerConceptInQuery.get(c),features1).size(),
//                        Sets.intersection(featuresPerConceptInQuery.get(c),features2).size()
//                );
//            }).reduce((first,second)->second).get(); //get last
//            candidateCQs.remove(Q);
//
//            BasicPattern phi = new BasicPattern();
//            F.forEach(f -> phi.add(new Triple(new ResourceImpl(f).asNode(),
//                    new PropertyImpl(Namespaces.rdfs.val()+"domain").asNode(), new ResourceImpl(c).asNode())));
//
//
////            getCoveringCQs(phi,Q,candidateCQs,coveringCQs);
//        }
        return candidateCQs;
    }

    private static Set<Wrapper> getEdgeCoveringWrappers(String s, String t, String e, Dataset T) {
        // TODO: might need some fix to handle integrated object properties
        Set<Wrapper> coveringWrappers = Sets.newHashSet();
        // ?s a <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource> ensures we retrieve datasources (wrappers) and not the minimal
        String query = " SELECT ?g WHERE { GRAPH ?g { " +
                " <" + e + "> <"+ RDFS.domain.getURI() +"> <" + s + ">. " +
                " <" + e + "> <"+ RDFS.range.getURI() +"> <" + t + ">. " +
//                " ?ds a <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource>. " +
                " } FILTER( str(?g) != 'http://minimal' ) } ";
        ResultSet W = RDFUtil.runAQuery(query, T);
        W.forEachRemaining(wRes -> {
            String w = wRes.get("g").asResource().getURI();
            coveringWrappers.add(new Wrapper(w));
        });
        return coveringWrappers;
    }


    private Set<ConjunctiveQuery> combineSetsOfCQs(Set<ConjunctiveQuery> CQ_A, Set<ConjunctiveQuery> CQ_B,
                                                   Set<Wrapper> edgeCoveringWrappers) {
        if (CQ_A.isEmpty() && CQ_B.isEmpty()) {
            return Sets.newHashSet();
        }
        else if (CQ_A.isEmpty() && !CQ_B.isEmpty()) {
            return CQ_B;
        }
        else if (!CQ_A.isEmpty() && CQ_B.isEmpty()) {
            return CQ_A;
        } else {
            Set<ConjunctiveQuery> CQs = Sets.cartesianProduct(CQ_A, CQ_B).stream()
//            see if the edge is covered by at least a CQ
//                    .filter(cp ->
//                                    !Collections.disjoint(cp.get(0).getWrappers(), edgeCoveringWrappers) ||
//                                            !Collections.disjoint(cp.get(1).getWrappers(), edgeCoveringWrappers)
//                    )
                    .map(cp -> {

                        if(Sets.intersection(cp.get(0).getWrappers(), cp.get(1).getWrappers()).size() > 0){
                            // union. Both conjunctive queries are from the same source (wrapper) but created from different classes
                            return mergeCQs(cp.get(0),cp.get(1));
                        }
                            // handle join. We need to improve if condition?
                        return mergeCQs(cp.get(0),cp.get(1));
                            })
                    .collect(Collectors.toSet());
            return CQs;
        }


    }

//    private ConjunctiveQuery findJoins(ConjunctiveQuery CQ_A, ConjunctiveQuery CQ_B) {
//        Set<String> IDa = Sets.newHashSet();
//        CQ_A.getWrappers().forEach(w -> {
//            IDa.addAll(coveredIDsPerWrapperInQuery.get(w));
//        });
//
//        Set<String> IDb = Sets.newHashSet();
//        CQ_B.getWrappers().forEach(w -> IDb.addAll(coveredIDsPerWrapperInQuery.get(w)));

//        Set<EquiJoin> joinConditions = Sets.newHashSet();
//        Sets.intersection(IDa,IDb).forEach(ID -> {
//            CQ_A.getWrappers().forEach(wA -> {
//                CQ_B.getWrappers().forEach(wB -> {
//                    if (attributePerFeatureAndWrapper.containsKey(new Tuple2<>(wA,ID)) &&
//                            attributePerFeatureAndWrapper.containsKey(new Tuple2<>(wB,ID))) {
//                        String L = attributePerFeatureAndWrapper.get(new Tuple2<>(wA,ID));
//                        String R = attributePerFeatureAndWrapper.get(new Tuple2<>(wB,ID));
//                        if (!L.equals(R) && !joinConditions.contains(new EquiJoin(L,R)) && !joinConditions.contains(new EquiJoin(R,L))) {
//                            joinConditions.add(new EquiJoin(L, R));
//                        }
//                    }
//                });
//            });
//        });
//        ConjunctiveQuery CQ = mergeCQs(CQ_A,CQ_B);
//        CQ.getJoinConditions().addAll(joinConditions);
//        return CQ;
//    }





    //contains all the triples from the global graph that a wrapper covers
    private Map<String,Set<Triple>> allTriplesPerWrapper = Maps.newHashMap();
    //contains all IDs that a wrapper is covering in the query
    private Map<Wrapper,Set<String>> coveredIDsPerWrapperInQuery = Maps.newHashMap();
    // Set of all queried ID features
    private Set<String> queriedIDs = Sets.newHashSet();
    //contains the relation attribute - (sameAs) -> feature
    public Map<String,String> featuresPerAttribute = Maps.newHashMap();
    //given a feature and a wrapper, it returns the corresponding attribute
    private Map<Tuple2<Wrapper,String>,String> attributePerFeatureAndWrapper = Maps.newHashMap();
    //contains the set of features per concept in the query
    private Map<String,Set<String>> featuresPerConceptInQuery = Maps.newHashMap();

    private void populateOptimizedStructures(Dataset T, List<TriplePath> queryPattern) {
        // Populate allTriplesPerWrapper
        // retrieve all graphs containing data sources. Here we exclude the minimal graph
//        RDFUtil.runAQuery("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s a <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource> } }",T).forEachRemaining(w -> {
//            String wrapper = w.get("g").asResource().getURI();
//            BasicPattern triplesForW = new BasicPattern();
//            RDFUtil.runAQuery("SELECT ?s ?p ?o WHERE { GRAPH <" + wrapper + "> { ?s ?p ?o } }", T).forEachRemaining(res -> {
//                triplesForW.add(new Triple(new ResourceImpl(res.get("s").toString()).asNode(),
//                            new PropertyImpl(res.get("p").toString()).asNode(), new ResourceImpl(res.get("o").toString()).asNode()));
//            });
//            allTriplesPerWrapper.put(wrapper, Sets.newHashSet(triplesForW.getList()));
//        });


        // Populate coveredIDsPerWrapperInQuery and queriedIDs
        // TODO: CHANGE sameAs for equivalentClass?
//        RDFUtil.runAQuery("SELECT DISTINCT ?g ?f WHERE { GRAPH ?g { " +
//                        "?ds a <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource>. " +
//                        "?a <" + Namespaces.rdfs.val() + "subClassOf> <" + Namespaces.sc.val() + "identifier> ." +
//                        "?a <" + Namespaces.owl.val() + "sameAs> ?f } }",T)
//                .forEachRemaining(gf -> {
//                    Wrapper w = new Wrapper(gf.get("g").asResource().getURI());
//                        String ID = gf.get("f").asResource().getURI();
//
//                        coveredIDsPerWrapperInQuery.putIfAbsent(w, Sets.newHashSet());
//                        // not sure what is happening here...
//                        coveredIDsPerWrapperInQuery.compute(w, (wrap, IDs) -> {
//                            boolean IDisInTheQuery = false;
//                            for (Triple t : queryPattern.getList()) {
//                                if (t.getSubject().getURI().equals(ID)) IDisInTheQuery = true;
//                            }
//                            if (IDisInTheQuery) {
//                                IDs.add(ID);
//                                queriedIDs.add(ID);
//                            }
//                            return IDs;
//                        });
//                });
        // Populate featuresPerAttribute
        RDFUtil.runAQuery("SELECT DISTINCT ?a ?f ?g WHERE { GRAPH ?g {" +
                "{ ?a <"+ OWL.sameAs.getURI() +"> ?f . " +
                "?f <" + Namespaces.rdf.val() + "type> <" + Namespaces.rdf.val() + "Property> }" +
                "UNION" +
                "{ ?a <" + OWL.sameAs.getURI() + "> ?f . " +
                "?f <" + Namespaces.rdf.val() + "type> <"+Namespaces.nextiadi.val()+"IntegratedDatatypeProperty> } } }",T).forEachRemaining(af -> {
            featuresPerAttribute.putIfAbsent(af.get("a").asResource().getURI(),af.get("f").asResource().getURI());
            attributePerFeatureAndWrapper.put(new Tuple2<>(new Wrapper(af.get("g").asResource().getURI()),
                    af.get("f").asResource().getURI()),af.get("a").asResource().getURI());
        });

        // Populate featuresPerConceptInQuery
        queryPattern.forEach(t -> {
            if (!t.getObject().getURI().equals(Namespaces.nextiadi.val()+"IntegratedDatatypeProperty") &&
                    !t.getObject().getURI().equals(Namespaces.nextiadi.val()+"IntegratedClass") &&
                    !t.getObject().getURI().equals(Namespaces.rdf.val()+"Property")) {
                RDFUtil.runAQuery("SELECT ?f WHERE { GRAPH ?g { " +
                        "{ ?f <" + Namespaces.rdf.val() + "type> <" + Namespaces.nextiadi.val() + "IntegratedClass> }" +
                        "UNION" +
                        "{ ?f <" + Namespaces.rdf.val() + "type> <" + Namespaces.rdfs.val() + "Class> }" +
                        " }}", T).forEachRemaining(q -> {

                    featuresPerConceptInQuery.putIfAbsent(t.getObject().getURI(), Sets.newHashSet());
                    featuresPerConceptInQuery.get(t.getObject().getURI()).add(t.getSubject().getURI());
                });
            }
        });
    }


    /*
    UTILS
     */

    public Boolean isIntegratedClass(String c, Dataset T) {

        String query = " ASK { GRAPH ?g{ <"+ c +"> <"+ RDF.type.getURI() +"> <"+ Namespaces.nextiadi.val()+"IntegratedClass> } } ";
        return RDFUtil.runAskQuery(query, T);
    }

    public void getSubClasses( String integratedC, Dataset T ) {

        String query = " SELECT ?g ?subclass { GRAPH ?g{ ?subclass <"+ RDFS.subClassOf.getURI() +"> <"+ integratedC +"> } } ";
        RDFUtil.runAQuery(query, T)
                .forEachRemaining(t -> {

                    String w = t.get("g").asResource().getURI();
                    String c = t.get("subclass").asResource().getURI();

                });

    }

}
