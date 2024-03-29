package edu.upc.essi.dtim.nextiaqr.functions;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import edu.upc.essi.dtim.nextiaqr.jena.RDFUtil;
import edu.upc.essi.dtim.nextiaqr.models.graph.CQVertex;
import edu.upc.essi.dtim.nextiaqr.models.graph.IntegrationEdge;
import edu.upc.essi.dtim.nextiaqr.models.graph.IntegrationGraph;
import edu.upc.essi.dtim.nextiaqr.models.graph.RelationshipEdge;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.GlobalGraph;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.Namespaces;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.SourceGraph;
import edu.upc.essi.dtim.nextiaqr.models.querying.ConjunctiveQuery;
import edu.upc.essi.dtim.nextiaqr.models.querying.EquiJoin;
import edu.upc.essi.dtim.nextiaqr.models.querying.RewritingResult;
import edu.upc.essi.dtim.nextiaqr.models.querying.Wrapper;
import edu.upc.essi.dtim.nextiaqr.utils.Tuple2;
import edu.upc.essi.dtim.nextiaqr.utils.Tuple3;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.RDFS;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class QueryRewritingRDFS {

    private boolean isWrapper(String w) {
        return w.contains("Wrapper") || w.contains("DataSource");
    }
    
    private void addTriple(BasicPattern pattern, String s, String p, String o) {
        pattern.add(new Triple(new ResourceImpl(s).asNode(), new PropertyImpl(p).asNode(), new ResourceImpl(o).asNode()));
    }
    //Used for adding triples in-memory
    private void addTriple(Model model, String s, String p, String o) {
        model.add(new ResourceImpl(s), new PropertyImpl(p), new ResourceImpl(o));
    }

    private OntModel ontologyFromPattern(BasicPattern PHI_p) {
        OntModel o = ModelFactory.createOntologyModel();
        PHI_p.getList().forEach(t -> addTriple(o, t.getSubject().getURI(), t.getPredicate().getURI(), t.getObject().getURI()));
        return o;
    }

    public Map<String,Integer> projectionOrder = Maps.newHashMap();
    public Tuple3<Set<String>, BasicPattern, InfModel> parseSPARQL(String SPARQL) {
        // Compile the SPARQL using ARQ and generate its <pi,phi> representation
        Query q = QueryFactory.create(SPARQL);
        Op ARQ = Algebra.compile(q);
        Set<String> PI = Sets.newHashSet();
        ((OpTable)((OpJoin)((OpProject)ARQ).getSubOp()).getLeft()).getTable().rows().forEachRemaining(r -> {
            int i = 0;
            for (Iterator<Var> it = r.vars(); it.hasNext(); ) {
                Var v = it.next();
                PI.add(r.get(v).getURI());
                projectionOrder.put(r.get(v).getURI(),i);
                ++i;
            }
        });
        BasicPattern PHI_p = ((OpBGP)((OpJoin)((OpProject)ARQ).getSubOp()).getRight()).getPattern();
        OntModel PHI_o_ontmodel = ontologyFromPattern(PHI_p);
        Reasoner reasoner = ReasonerRegistry.getTransitiveReasoner(); //RDFS entailment subclass+superclass
        InfModel PHI_o = ModelFactory.createInfModel(reasoner,PHI_o_ontmodel);
        return new Tuple3<>(PI,PHI_p,PHI_o);
    }


    private Map<BasicPattern, Map<Set<Wrapper>,Boolean>> coveringCache = Maps.newHashMap();
    private boolean covering(Set<Wrapper> W, BasicPattern PHI_p) {
        if (coveringCache.containsKey(PHI_p) && coveringCache.get(PHI_p).containsKey(W)) return coveringCache.get(PHI_p).get(W);
        Set<Triple> coveredPattern = Sets.newHashSet();
        W.forEach(w -> {
            coveredPattern.addAll(allTriplesPerWrapper.get(w.getWrapper()));
        });
        coveringCache.putIfAbsent(PHI_p,Maps.newHashMap());
        coveringCache.get(PHI_p).put(W,coveredPattern.containsAll(Sets.newHashSet(PHI_p.getList())));
        return coveringCache.get(PHI_p).get(W);
//        return coveredPattern.containsAll(Sets.newHashSet(PHI_p.getList()));
    }

    private boolean minimal(Set<Wrapper> W, BasicPattern PHI_p) {
        for (Wrapper w : W) {
            if (covering(Sets.difference(W,Sets.newHashSet(w)),PHI_p)) return false;
        }
        return true;
    }

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

    private void populateOptimizedStructures(Dataset T, BasicPattern queryPattern) {
        // Populate allTriplesPerWrapper
        RDFUtil.runAQuery("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }",T).forEachRemaining(w -> {
            String wrapper = w.get("g").asResource().getURI();
            if (isWrapper(wrapper)) {
                BasicPattern triplesForW = new BasicPattern();
                RDFUtil.runAQuery("SELECT ?s ?p ?o WHERE { GRAPH <" + wrapper + "> { ?s ?p ?o } }", T).forEachRemaining(res -> {
                    triplesForW.add(new Triple(new ResourceImpl(res.get("s").toString()).asNode(),
                            new PropertyImpl(res.get("p").toString()).asNode(), new ResourceImpl(res.get("o").toString()).asNode()));
                });
                allTriplesPerWrapper.put(wrapper,Sets.newHashSet(triplesForW.getList()));
            }
        });

        // Populate coveredIDsPerWrapperInQuery and queriedIDs
        RDFUtil.runAQuery("SELECT DISTINCT ?g ?f WHERE { GRAPH ?g {" +
                "?a <" + Namespaces.rdfs.val() + "subClassOf> <" + Namespaces.sc.val() + "identifier> ." +
                "?a <" + Namespaces.owl.val() + "sameAs> ?f } }",T)
                .forEachRemaining(gf -> {
            Wrapper w = new Wrapper(gf.get("g").asResource().getURI());
            if (isWrapper(w.getWrapper())) {
                String ID = gf.get("f").asResource().getURI();

                coveredIDsPerWrapperInQuery.putIfAbsent(w, Sets.newHashSet());
                coveredIDsPerWrapperInQuery.compute(w, (wrap, IDs) -> {
                    boolean IDisInTheQuery = false;
                    for (Triple t : queryPattern.getList()) {
                        if (t.getSubject().getURI().equals(ID)) IDisInTheQuery = true;
                    }
                    if (IDisInTheQuery) {
                        IDs.add(ID);
                        queriedIDs.add(ID);
                    }
                    return IDs;
                });
            }
        });
        // Populate featuresPerAttribute
        RDFUtil.runAQuery("SELECT DISTINCT ?a ?f ?g WHERE { GRAPH ?g {" +
                "{ ?a <" + Namespaces.owl.val() + "sameAs> ?f . " +
                "?f <" + Namespaces.rdf.val() + "type> <" + Namespaces.rdf.val() + "Property> }" +
                "UNION" +
                "{ ?a <" + Namespaces.owl.val() + "sameAs> ?f . " +
                "?f <" + Namespaces.rdf.val() + "type> <"+Namespaces.nextiadi.val()+"IntegratedDatatypeProperty> } } }",T).forEachRemaining(af -> {
            featuresPerAttribute.putIfAbsent(af.get("a").asResource().getURI(),af.get("f").asResource().getURI());
            attributePerFeatureAndWrapper.put(new Tuple2<>(new Wrapper(af.get("g").asResource().getURI()),
                    af.get("f").asResource().getURI()),af.get("a").asResource().getURI());
        });


//        featuresPerAttribute.putIfAbsent("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/90d09eb90ea146b4a243f607ec13581d/p2.createdAt","http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/90d09eb90ea146b4a243f607ec13581d/p2.createdAt");
//        attributePerFeatureAndWrapper.put(new Tuple2<>(new Wrapper("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/90d09eb90ea146b4a243f607ec13581d"),
//                "http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/90d09eb90ea146b4a243f607ec13581d/p2.createdAt"),
//                "http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/90d09eb90ea146b4a243f607ec13581d/p2.createdAt");
//

        // Populate attributePerFeatureAndWrapper
        /*allTriplesPerWrapper.forEach((w,triples) -> {
            triples.stream()
                .filter(t -> t.getPredicate().getURI().equals(GlobalGraph.HAS_FEATURE.val()))
                .map(t -> t.getObject().getURI())
                .forEach(f -> {
                    RDFUtil.runAQuery("SELECT ?a WHERE { GRAPH ?g { ?a <" + Namespaces.owl.val() + "sameAs> <"+f+"> . " +
                            "<"+w+"> <"+SourceGraph.HAS_ATTRIBUTE.val()+"> ?a } }",T)
                        .forEachRemaining(a -> {
                            attributePerFeatureAndWrapper.put(new Tuple2<>(new Wrapper(w),f),a.get("a").toString());
                        });
                    });
        });*/

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

    private Set<ConjunctiveQuery> combineSetsOfCQs(Set<ConjunctiveQuery> CQ_A, Set<ConjunctiveQuery> CQ_B,
                                                          Set<Wrapper> edgeCoveringWrappers, BasicPattern PHI_p) {
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
                    //see if the edge is covered by at least a CQ
                    .filter(cp ->
                                    !Collections.disjoint(cp.get(0).getWrappers(), edgeCoveringWrappers) ||
                                            !Collections.disjoint(cp.get(1).getWrappers(), edgeCoveringWrappers)
                            /**
                             !( // some query must cover the edge
                             Sets.intersection(cp.get(0).getWrappers(),edgeCoveringWrappers).isEmpty() &&
                             Sets.intersection(cp.get(1).getWrappers(),edgeCoveringWrappers).isEmpty()
                             ) && !( //both queries can't cover the edge
                             !Sets.intersection(cp.get(0).getWrappers(),edgeCoveringWrappers).isEmpty() &&
                             !Sets.intersection(cp.get(1).getWrappers(),edgeCoveringWrappers).isEmpty()
                             ) //|| (cp.get(0).equals(cp.get(1)))
                             **/
                    )
//                .filter(cp -> !Sets.intersection(edgeCoveringWrappers,cp.get(0).getWrappers()).isEmpty() ||
//                           !Sets.intersection(edgeCoveringWrappers,cp.get(1).getWrappers()).isEmpty()
//               )
                   // .filter(cp -> minimal(Sets.union(cp.get(0).getWrappers(), cp.get(1).getWrappers()), PHI_p))
                    .map(cp -> findJoins(cp.get(0), cp.get(1)))
                    .collect(Collectors.toSet());
            return CQs;
        }

    }

    private ConjunctiveQuery mergeCQs(ConjunctiveQuery CQ_A, ConjunctiveQuery CQ_B) {
        ConjunctiveQuery mergedCQ = new ConjunctiveQuery();
        mergedCQ.getProjections().addAll(Sets.union(CQ_A.getProjections(),CQ_B.getProjections()));
        mergedCQ.getJoinConditions().addAll(Sets.union(CQ_A.getJoinConditions(),CQ_B.getJoinConditions()));
        mergedCQ.getWrappers().addAll(Sets.union(CQ_A.getWrappers(),CQ_B.getWrappers()));

        return mergedCQ;
    }

    private ConjunctiveQuery findJoins(ConjunctiveQuery CQ_A, ConjunctiveQuery CQ_B) {
        Set<String> IDa = Sets.newHashSet();
        CQ_A.getWrappers().forEach(w -> {
            IDa.addAll(coveredIDsPerWrapperInQuery.get(w));
        });

        Set<String> IDb = Sets.newHashSet();
        CQ_B.getWrappers().forEach(w -> IDb.addAll(coveredIDsPerWrapperInQuery.get(w)));

        Set<EquiJoin> joinConditions = Sets.newHashSet();
        Sets.intersection(IDa,IDb).forEach(ID -> {
            CQ_A.getWrappers().forEach(wA -> {
                CQ_B.getWrappers().forEach(wB -> {
                    if (attributePerFeatureAndWrapper.containsKey(new Tuple2<>(wA,ID)) &&
                        attributePerFeatureAndWrapper.containsKey(new Tuple2<>(wB,ID))) {
                            String L = attributePerFeatureAndWrapper.get(new Tuple2<>(wA,ID));
                            String R = attributePerFeatureAndWrapper.get(new Tuple2<>(wB,ID));
                            if (!L.equals(R) && !joinConditions.contains(new EquiJoin(L,R)) && !joinConditions.contains(new EquiJoin(R,L))) {
                                joinConditions.add(new EquiJoin(L, R));
                            }
                    }
                });
            });
        });
        ConjunctiveQuery CQ = mergeCQs(CQ_A,CQ_B);
        CQ.getJoinConditions().addAll(joinConditions);
        return CQ;
    }

    private void getCoveringCQs(BasicPattern G, ConjunctiveQuery currentCQ, Set<ConjunctiveQuery> candidateCQs, Set<ConjunctiveQuery> coveringCQs) {
        if (covering(currentCQ.getWrappers(),G)) {
            coveringCQs.add(currentCQ);
        }
        else if (!candidateCQs.isEmpty()) {
            ConjunctiveQuery CQ = candidateCQs.iterator().next();

            Set<String> currentFeatures = currentCQ.getProjections().stream().map(a -> featuresPerAttribute.get(a)).collect(Collectors.toSet());
            Set<String> contributedFeatures = CQ.getProjections().stream().map(a -> featuresPerAttribute.get(a)).collect(Collectors.toSet());

            if (!Sets.union(currentFeatures,contributedFeatures).equals(currentFeatures)) {

//            if (Sets.union(currentCQ.getProjections(),CQ.getProjections()).containsAll(currentCQ.getProjections()) &&
//                    !Sets.union(currentCQ.getProjections(),CQ.getProjections()).equals(currentCQ.getProjections())) {
                ConjunctiveQuery newCQ = findJoins(currentCQ,CQ);
                getCoveringCQs(G,newCQ,Sets.difference(candidateCQs,Sets.newHashSet(CQ)),coveringCQs);
            }
        }
    }

    private Set<ConjunctiveQuery> getConceptCoveringCQs(String c, InfModel PHI_o, Dataset T) {
        Map<Wrapper,Set<String>> attsPerWrapper = Maps.newHashMap();
        Set<String> F = Sets.newHashSet();
        String query = "SELECT DISTINCT ?f WHERE { " +
                "{ ?f <" + Namespaces.rdfs.val() + "domain> <"+c+"> . " +
                "?f <" + Namespaces.rdf.val() + "type> <" + Namespaces.rdf.val() + "Property> }" +
                "UNION" +
                "{ ?f <" + Namespaces.rdfs.val() + "domain> <"+c+"> . " +
                "?f <" + Namespaces.rdf.val() + "type> <"+Namespaces.nextiadi.val()+"IntegratedDatatypeProperty> } }";
        System.out.println("Q is:\n"+query);
        RDFUtil.runAQuery(query,PHI_o)
                    .forEachRemaining(f -> F.add(f.get("f").asResource().getURI()));
        //Case when the Concept has no data, we need to identify the wrapper using the concept instead of the feature
        if (F.isEmpty()) {
            System.out.println("Case not checked when concept has no data");
//            System.exit(100);
            //TODO check this!!!
            RDFUtil.runAQuery("SELECT ?g WHERE { GRAPH ?g { <" + c + "> <" + Namespaces.rdf.val() + "type" + "> <" + GlobalGraph.CONCEPT.val() + "> } }", T)
                    .forEachRemaining(wrapper -> {
                        String w = wrapper.get("g").toString();
                        if (isWrapper(w)) {
                            attsPerWrapper.putIfAbsent(new Wrapper(w), Sets.newHashSet());
                        }
                    });
        } else {
            //Unfold LAV mappings
            F.forEach(f -> {
                ResultSet W = RDFUtil.runAQuery("SELECT ?g " +
                        "WHERE { GRAPH ?g { <" + f + "> <" + Namespaces.rdfs.val() + "domain> <" + c + "> } }", T);
                W.forEachRemaining(wRes -> {
                    String w = wRes.get("g").asResource().getURI();
                    if (isWrapper(w)) {
                    /*ResultSet rsAttr = RDFUtil.runAQuery("SELECT ?a " +
                            "WHERE { GRAPH ?g { ?a <" + Namespaces.owl.val() + "sameAs> <" + f + "> . " +
                            "<" + w + "> <" + SourceGraph.HAS_ATTRIBUTE.val() + "> ?a } }", T);
                    String attribute = rsAttr.nextSolution().get("a").asResource().getURI();*/
                        String attribute = attributePerFeatureAndWrapper.get(new Tuple2<>(new Wrapper(w), f));
                        attsPerWrapper.putIfAbsent(new Wrapper(w), Sets.newHashSet());
                        Set<String> currentSet = attsPerWrapper.get(new Wrapper(w));
                        currentSet.add(attribute);
                        attsPerWrapper.put(new Wrapper(w), currentSet);
                    }
                });
            });
        }

        Set<ConjunctiveQuery> candidateCQs = Sets.newHashSet();
        attsPerWrapper.keySet().forEach(w -> {
            ConjunctiveQuery Q = new ConjunctiveQuery(attsPerWrapper.get(w),Sets.newHashSet(),Sets.newHashSet(w));
            candidateCQs.add(Q);
        });
        /////////////////////////////////////////////////////////////////////////////////////////////////////
        //20211203 -- Post-process. Consider also these wrappers that cover the concept but not the features
//        RDFUtil.runAQuery("SELECT ?g WHERE { GRAPH ?g { <" + c + "> <" + Namespaces.rdf.val() + "type" + "> <" + GlobalGraph.CONCEPT.val() + "> } }", T)
//                .forEachRemaining(wrapper -> {
//                    String w = wrapper.get("g").toString();
//                    if (isWrapper(w) && !attsPerWrapper.keySet().contains(new Wrapper(w))) {
//                        ConjunctiveQuery Q = new ConjunctiveQuery(Sets.newHashSet(),Sets.newHashSet(),Sets.newHashSet(new Wrapper(w)));
//                        candidateCQs.add(Q);
//                    }
//                });
        /////////////////////////////////////////////////////////////////////////////////////////////////////
        //return candidateCQs;

        Set<ConjunctiveQuery> coveringCQs = Sets.newHashSet();
        while (!candidateCQs.isEmpty()) {
            ConjunctiveQuery Q = candidateCQs.stream().sorted((cq1, cq2) -> {
                Set<String> features1 = cq1.getProjections().stream().map(a1 -> featuresPerAttribute.get(a1)).collect(Collectors.toSet());
                Set<String> features2 = cq2.getProjections().stream().map(a2 -> featuresPerAttribute.get(a2)).collect(Collectors.toSet());
                return Integer.compare(
                        Sets.intersection(featuresPerConceptInQuery.get(c),features1).size(),
                        Sets.intersection(featuresPerConceptInQuery.get(c),features2).size()
                );
            }).reduce((first,second)->second).get(); //get last
            candidateCQs.remove(Q);

            BasicPattern phi = new BasicPattern();
            F.forEach(f -> phi.add(new Triple(new ResourceImpl(f).asNode(),
                    new PropertyImpl(Namespaces.rdfs.val()+"domain").asNode(), new ResourceImpl(c).asNode())));


            getCoveringCQs(phi,Q,candidateCQs,coveringCQs);
        }
        return coveringCQs;

    }

    private Set<Wrapper> getEdgeCoveringWrappers(String s, String t, String e, Dataset T) {
        Set<Wrapper> coveringWrappers = Sets.newHashSet();
        ResultSet W = RDFUtil.runAQuery("SELECT ?g " +
                "WHERE { GRAPH ?g { <" + s + "> <" + e + "> <" + t + "> } }", T);
        W.forEachRemaining(wRes -> {
            String w = wRes.get("g").asResource().getURI();
            if (isWrapper(w))
                coveringWrappers.add(new Wrapper(w));
        });
        return coveringWrappers;
    }

    @SuppressWarnings("Duplicates")
    public RewritingResult rewriteToUnionOfConjunctiveQueries(String query, Dataset T) {
        RewritingResult res = new RewritingResult();

        Tuple3<Set<String>, BasicPattern, InfModel> queryStructure = parseSPARQL(query);

        BasicPattern PHI_p = queryStructure._2;
        populateOptimizedStructures(T,PHI_p);
        InfModel PHI_o = queryStructure._3;

        //Identify query-related concepts
        Graph<String, RelationshipEdge> conceptsGraph = new SimpleDirectedGraph<>(RelationshipEdge.class);
        PHI_p.getList().forEach(t -> {
            // Add only concepts so its easier to populate later the list of concepts
            // TODO J: this should add integrated but also non-integrated classes
            if ( RDFUtil.runAskQuery("ASK WHERE { GRAPH ?g {" +
                    "{ <"+t.getSubject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.rdfs.val()+"Class> . <"+t.getObject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.rdfs.val()+"Class> .  } " +
                    "UNION" +
                    "{ <"+t.getSubject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.rdfs.val()+"Class> . <"+t.getObject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.nextiadi.val()+"IntegratedClass> .  } " +
                    "UNION" +
                    "{ <"+t.getSubject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.nextiadi.val()+"IntegratedClass> . <"+t.getObject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.rdfs.val()+"Class> .  } " +
                    "UNION" +
                    "{ <"+t.getSubject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.nextiadi.val()+"IntegratedClass> . <"+t.getObject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.nextiadi.val()+"IntegratedClass> .  } " +
                    "} }",T) && !t.getObject().getURI().equals(Namespaces.sc.val()+"identifier")) {
                conceptsGraph.addVertex(t.getSubject().getURI());
                conceptsGraph.addVertex(t.getObject().getURI());
                conceptsGraph.addEdge(t.getSubject().getURI(), t.getObject().getURI(),
                        new RelationshipEdge(t.getPredicate().getURI()) /*UUID.randomUUID().toString()*/);

            }
//            else if ( RDFUtil.runAskQuery("ASK WHERE { GRAPH ?g {" +
//                    "{ <"+t.getSubject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.rdfs.val()+"Class> . " +
//                    " <"+t.getObject().getURI()+"> <"+Namespaces.rdf.val()+"type> <"+Namespaces.rdfs.val()+"Class> ." +
//                    " <"+t.getSubject().getURI()+"> ?p <"+t.getObject().getURI()+">  } " +
//                    "} }",T) && !t.getObject().getURI().equals(Namespaces.sc.val()+"identifier")  ) {
//                System.out.println("entra");
//            }

            //if (!t.getPredicate().getURI().equals(GlobalGraph.HAS_FEATURE.val()) && !t.getObject().getURI().equals(Namespaces.sc.val()+"identifier")) {
            //    conceptsGraph.addVertex(t.getSubject().getURI());
            //    conceptsGraph.addVertex(t.getObject().getURI());
            //    conceptsGraph.addEdge(t.getSubject().getURI(), t.getObject().getURI(),
            //            new RelationshipEdge(t.getPredicate().getURI()) /*UUID.randomUUID().toString()*/);
            //}
        });

//        conceptsGraph.addVertex("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1");
//        conceptsGraph.addVertex("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4");
//        conceptsGraph.addEdge( "http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1", "http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4",
//                new RelationshipEdge("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange") /*UUID.randomUUID().toString()*/);


        // This is required when only one concept is queried, where all edges are hasFeature
        // Javier: I think there will be more bugs, try to validate with more examples.
        if (conceptsGraph.vertexSet().isEmpty()) {
//            conceptsGraph.addVertex(PHI_p.getList().get(0).getObject().getURI());
            for(Triple t : PHI_p.getList()){
                if(t.getPredicate().getURI().equals(RDFS.domain.getURI()) ) {
                    conceptsGraph.addVertex(t.getObject().getURI());
                    System.out.println("****--" + t.getObject().getURI());
//                    not sure if the break is wrong?
//                    break;
                }
            }
            if (conceptsGraph.vertexSet().isEmpty()) {
                System.out.println("PROBLEM IDENTIFYING WHEN ONLY ONE CONCEPT IS QUERIED.");
            }
        }

        IntegrationGraph G = new IntegrationGraph();
        conceptsGraph.vertexSet().forEach(c -> {
            Set<ConjunctiveQuery> CQs = getConceptCoveringCQs(c,PHI_o,T);
            G.addVertex(new CQVertex(c,CQs));
        });
        conceptsGraph.edgeSet().forEach(e -> {
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
                D.get(new CQVertex(t.getSubject().getURI())).add(t);
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
            addTriple(both,conceptsGraph.getEdgeSource(new RelationshipEdge(e.getLabel())),
                    e.getLabel(),conceptsGraph.getEdgeTarget(new RelationshipEdge(e.getLabel())));
            Set<ConjunctiveQuery> Q = combineSetsOfCQs(Qs, Qt, edgeCoveringWrappers,both);

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

/*
        G.vertexSet().iterator().next().getCQs().forEach(cq -> {
            System.out.println(cq + " --> "+covering(cq.getWrappers(),PHI_p));
        });
*/
        Set<ConjunctiveQuery> ucqs = G.vertexSet().iterator().next().getCQs();
        Set<ConjunctiveQuery> out = ucqs.stream().filter(cq -> minimal(cq.getWrappers(),PHI_p))
                .filter(cq -> !(cq.getWrappers().size()>1 && cq.getJoinConditions().size()==0))
                .filter(cq -> cq.getJoinConditions().size()>=(cq.getWrappers().size()-1))
                .filter(cq -> minimal(cq.getWrappers(),PHI_p))
                //.filter(cq -> covering(cq.getWrappers(),PHI_p))
                .filter(cq -> cq.getProjections().size() >= projectionOrder.size())
                .collect(Collectors.toSet());

        res.setCQs(out);
        res.setFeaturesPerAttribute(Maps.newHashMap(featuresPerAttribute));
        res.setProjectionOrder(Maps.newHashMap(projectionOrder));
        return res;

    }



}
