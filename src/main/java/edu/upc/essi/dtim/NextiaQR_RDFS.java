package edu.upc.essi.dtim;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import edu.upc.essi.dtim.nextiaqr.functions.QueryRewritingRDFS;
import edu.upc.essi.dtim.nextiaqr.jena.GraphOperations;
import edu.upc.essi.dtim.nextiaqr.jena.RDFUtil;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.Namespaces;
import edu.upc.essi.dtim.nextiaqr.models.querying.ConjunctiveQuery;
import edu.upc.essi.dtim.nextiaqr.models.querying.GenericWrapper;
import edu.upc.essi.dtim.nextiaqr.models.querying.RDFSResult;
import edu.upc.essi.dtim.nextiaqr.models.querying.Wrapper;
import edu.upc.essi.dtim.nextiaqr.utils.Utils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.spark.sql.Row;
import org.glassfish.jersey.internal.guava.Sets;

import java.util.*;
import java.util.stream.Collectors;

public class NextiaQR_RDFS {

    public static RDFSResult rewriteToUnionOfConjunctiveQueries(Map<String, Model> sourceGraphs, Model minimal,
                                                                Map<String, Model> subgraphs, String query) {
        Dataset T = DatasetFactory.create();
        T.addNamedModel("minimal",minimal);
        sourceGraphs.forEach(T::addNamedModel);
        subgraphs.forEach(T::addNamedModel);

        Set<ConjunctiveQuery> cqs = QueryRewritingRDFS.rewriteToUnionOfConjunctiveQueries(query,T);
        System.out.println("ConjuctiveQueries:");
        cqs.forEach(System.out::println);
        String SQL = toSQL(cqs,T);
        System.out.println(SQL);
        return executeSQL(cqs,SQL,T);
    }

    public static String toSQL (Set<ConjunctiveQuery> UCQ, Dataset T) {
        if (UCQ.isEmpty()) return null;
        StringBuilder SQL = new StringBuilder();
        UCQ.forEach(q -> {
            StringBuilder select = new StringBuilder("SELECT ");
            StringBuilder from = new StringBuilder(" FROM ");
            StringBuilder where = new StringBuilder(" WHERE ");
            //Sort the projections as they are indicated in the interface
            //First remove duplicates based on the features
            List<String> seenFeatures = Lists.newArrayList();
            List<String> withoutDuplicates = Lists.newArrayList();
            q.getProjections().forEach(proj -> {
                if (!seenFeatures.contains(QueryRewritingRDFS.featuresPerAttribute.get(proj))) {
                    withoutDuplicates.add(proj);
                    seenFeatures.add(QueryRewritingRDFS.featuresPerAttribute.get(proj));
                }
            });
            //Now do the sorting
            List<String> projections = Lists.newArrayList(withoutDuplicates);//Lists.newArrayList(q.getProjections());
            projections.sort(Comparator.comparingInt(s -> QueryRewritingRDFS.projectionOrder.get(QueryRewritingRDFS.featuresPerAttribute.get(s))));
            projections.forEach(proj -> {
                //Get the alias in the source graph
                String att =  RDFUtil.runAQuery("SELECT ?a WHERE { GRAPH ?g { " +
                        "<"+proj+"> <"+ Namespaces.nextiaDataSource.val()+"alias> ?a" +
                        "} }",T).next().get("a").toString();
                select.append(att+",");
            });
            //q.getWrappers().forEach(w -> from.append(wrapperIriToID.get(w.getWrapper())+","));
            q.getWrappers().forEach(w -> from.append(GraphOperations.nn(w.getWrapper())+","));
            q.getJoinConditions().forEach(j -> where.append(
                    "\""+GraphOperations.nn(j.getLeft_attribute()).split("/")[GraphOperations.nn(j.getLeft_attribute()).split("/").length-1]+"\""+
                            " = "+
                            "\""+GraphOperations.nn(j.getRight_attribute()).split("/")[GraphOperations.nn(j.getRight_attribute()).split("/").length-1]+"\""+
                            " AND "));
            SQL.append(select.substring(0,select.length()-1));
            SQL.append(from.substring(0,from.length()-1));
            if (!where.toString().equals(" WHERE ")) {
                SQL.append(where.substring(0, where.length() - " AND ".length()));
            }
            SQL.append(" UNION ");
        });
        String SQLstr = SQL.substring(0,SQL.length()-" UNION ".length())+";";
        return SQLstr;
    }

    public static RDFSResult executeSQL(Set<ConjunctiveQuery> UCQs, String SQL, Dataset T) {
        if (UCQs.isEmpty() || SQL == null) {
            System.out.println("The UCQ is empty, no output is generated");
        } else {
            Set<Wrapper> wrappersInUCQs = UCQs.stream().map(cq -> cq.getWrappers()).flatMap(wrappers -> wrappers.stream()).collect(Collectors.toSet());
            Set<GenericWrapper> wrapperImpls = Sets.newHashSet();
            for (Wrapper w : wrappersInUCQs) {
                QuerySolution qs = RDFUtil.runAQuery("SELECT ?f ?p ?w ?l WHERE { GRAPH ?g { " +
                        "<"+w.getWrapper()+"> <"+ Namespaces.nextiaDataSource.val()+"format> ?f ." +
                        "<"+w.getWrapper()+"> <"+ Namespaces.nextiaDataSource.val()+"path> ?p ." +
                        "<"+w.getWrapper()+"> <"+ Namespaces.nextiaDataSource.val()+"wrapper> ?w ." +
                        "<"+w.getWrapper()+"> <"+ Namespaces.rdfs.val()+"label> ?l ." +
                        "} }",T).next();
                GenericWrapper gw = new GenericWrapper(w.getWrapper());
                gw.setFormat(qs.get("f").toString());
                gw.setPath(qs.get("p").toString());
                gw.setImplementation(qs.get("w").toString());
                gw.setLabel(qs.get("l").toString());

                wrapperImpls.add(gw);
            }

            wrapperImpls.forEach(w -> {
                switch (w.getFormat()) {
                    case "JSON":
                        Utils.getSparkSession().read().json(w.getPath()).createOrReplaceTempView(w.getLabel());
                        break;
                    case "CSV":
                        Utils.getSparkSession().read().csv(w.getPath()).createOrReplaceTempView(w.getLabel());
                        break;
                }
                Utils.getSparkSession().sql(w.getImplementation()).createOrReplaceTempView(GraphOperations.nn(w.getWrapper()));
            });

            //Add TABLE ALIAS to final SQL
            for (GenericWrapper w : wrapperImpls) {
                SQL = SQL.replace(GraphOperations.nn(w.getWrapper()),GraphOperations.nn(w.getWrapper())+ " AS "+w.getLabel());
            }

            RDFSResult res = new RDFSResult();
            org.apache.spark.sql.Dataset<Row> df = Utils.getSparkSession().sql(SQL);
            List<String> col = new ArrayList<>();
            df.schema().foreach(f -> col.add(f.name()));
            List<String> rows = df.toJSON().collectAsList();
            res.setColumns(col);
            res.setRows(rows);
            return res;
        }
        return null;
    }

    public static void main(String[] args) {
        Map<String, Model> sourceGraphs = Maps.newHashMap();
        sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/824f259815094f79bb0a5cac03ae8348",
                RDFDataMgr.loadModel("src/test/resources/qr_rdfs/source-graph6289942169144221467.g", Lang.TTL));
        sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/28cd712c43eb4fddb0e4ea4e6e302737",
                RDFDataMgr.loadModel("src/test/resources/qr_rdfs/source-graph844563518469037388.g", Lang.TTL));

        Model minimal = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/minimal-graph5681704536684986734.g", Lang.TTL);

        Map<String, Model> subgraphs = Maps.newHashMap();
        subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/824f259815094f79bb0a5cac03ae8348",
                RDFDataMgr.loadModel("src/test/resources/qr_rdfs/subgraphs2123728943094953028.g", Lang.TTL));
        subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/28cd712c43eb4fddb0e4ea4e6e302737",
                RDFDataMgr.loadModel("src/test/resources/qr_rdfs/subgraphs11519542500731068756.g", Lang.TTL));

        //TODO: use IntegratedDatatypeProperty from nextiaDI class.
        String query = "PREFIX nextiaDI: <http://www.essi.upc.edu/DTIM/NextiaDI/> \n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
                "SELECT ?id ?title " +
                "WHERE { " +
                " VALUES (?id ?title) { ( nextiaDI:identifier_idObject nextiaDI:title_title ) } " +
                " nextiaDI:identifier_idObject  rdfs:domain nextiaDI:artworks_collections . " +
                " nextiaDI:title_title rdfs:domain nextiaDI:artworks_collections . " +
                " nextiaDI:artworks_collection rdf:type nextiaDI:IntegratedClass ." +
                " nextiaDI:identifier_idObject rdf:type nextiaDI:IntegratedDatatypeProperty ." +
                " nextiaDI:title_title rdf:type nextiaDI:IntegratedDatatypeProperty " +
                "}";

//        String query = "PREFIX nextiaDI: <http://www.essi.upc.edu/DTIM/NextiaDI/> \n" +
//                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
//                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
//                "SELECT ?title " +
//                "WHERE { " +
//                " VALUES ( ?title) { ( nextiaDI:title_title ) } " +
//                " nextiaDI:title_title rdf:type nextiaDI:IntegratedDatatypeProperty ." +
//                " nextiaDI:title_title rdfs:domain nextiaDI:artworks_collections . " +
//                " nextiaDI:artworks_collection rdf:type nextiaDI:IntegratedClass ." +
//
//                "}";

        rewriteToUnionOfConjunctiveQueries(sourceGraphs,minimal,subgraphs,query);
    }

}
