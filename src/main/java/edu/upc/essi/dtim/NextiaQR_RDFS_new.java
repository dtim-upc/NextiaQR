package edu.upc.essi.dtim;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import edu.upc.essi.dtim.nextiaqr.functions.Query_RDFS_new;
import edu.upc.essi.dtim.nextiaqr.jena.GraphOperations;
import edu.upc.essi.dtim.nextiaqr.jena.RDFUtil;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.Namespaces;
import edu.upc.essi.dtim.nextiaqr.models.querying.*;
import edu.upc.essi.dtim.nextiaqr.utils.Tuple2;
import edu.upc.essi.dtim.nextiaqr.utils.Utils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDFS;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.glassfish.jersey.internal.guava.Sets;

import java.util.*;
import java.util.stream.Collectors;

public class NextiaQR_RDFS_new {


    Query_RDFS_new queryRDFS = new Query_RDFS_new();

    public void query(Map<String, Model> sourceGraphs, Model minimal,
                                                         Map<String, Model> subgraphs, String query) {
        Dataset T = DatasetFactory.create();
        T.addNamedModel("http://minimal",minimal);
        sourceGraphs.forEach(T::addNamedModel);
        subgraphs.forEach(T::addNamedModel);

        RewritingResult res = queryRDFS.rewriteToUnionOfConjunctiveQueries(query,T);

        System.out.println("ConjuctiveQueries:");
        res.getCQs().forEach(System.out::println);
        String SQL = toSQL(res, queryRDFS.parseSPARQL(query)._1 ,T);
//        System.out.println(SQL);
//        return executeSQL(res.getCQs(),SQL,T);
    }

    public String toSQL (RewritingResult rewritingResult, Map<String,Tuple2<Integer, String>> projectionList, Dataset T) {

        if (rewritingResult.getCQs().isEmpty()) return null;
        StringBuilder SQL = new StringBuilder();
        for ( ConjunctiveQuery q : rewritingResult.getCQs() ) {
            StringBuilder select = new StringBuilder("SELECT ");
            StringBuilder from = new StringBuilder(" FROM ");
            StringBuilder where = new StringBuilder(" WHERE ");

            //Sort the projections as they are indicated in the interface
            //Now do the sorting
            List<String> projections = Lists.newArrayList(projectionList.keySet());
            projections.sort(Comparator.comparingInt(s -> rewritingResult.getProjectionOrder().get(rewritingResult.getFeaturesPerAttribute().get(s))));
            projections.forEach(proj -> {
                //Get the alias in the source graph
                String[] tmp =  proj.split("/");
                String id = tmp[tmp.length-2];

                QuerySolution result = RDFUtil.runAQuery("SELECT ?a ?l WHERE { GRAPH ?g { " +
                        "<" + proj + "> <" + RDFS.label + "> ?a. " +
                        "?ds <" + RDFS.label + "> ?l.   " +
                        "?ds <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/id> \"" + id + "\". " +
                        "} }", T).next();

                String att = result.get("a").toString();
                String wrapperAlias = result.get("l").toString();
                // can be improved once nextiadi creates the rdfs:label for integrated resources
                String aliasG = rewritingResult.getFeaturesPerAttribute().get(proj).substring(rewritingResult.getFeaturesPerAttribute().get(proj).lastIndexOf("/")+1).replace(".","_");

                select.append(wrapperAlias+"."+att+" as "+aliasG+" ,");
            });
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
        }
        String SQLstr = SQL.substring(0,SQL.length()-" UNION ".length())+";";
        return SQLstr;

    }


    public static void main(String[] args) {
        int option = 2;

        switch (option) {

            case 1:
                System.out.println("Option 1");
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

                query = "PREFIX nextiaDI: <http://www.essi.upc.edu/DTIM/NextiaDI/> \n" +
                        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
                        "SELECT ?id ?title " +
                        "WHERE { " +
//                        " VALUES (?id ?title) { ( nextiaDI:identifier_idObject nextiaDI:title_title ) } " +
                        " nextiaDI:artworks_collections nextiaDI:identifier_idObject ?id. " +
                        " nextiaDI:artworks_collections nextiaDI:title_title ?title. " +
//                        " nextiaDI:identifier_idObject  rdfs:domain nextiaDI:artworks_collections . " +
//                        " nextiaDI:title_title rdfs:domain nextiaDI:artworks_collections . " +
//                        " nextiaDI:artworks_collection rdf:type nextiaDI:IntegratedClass ." +
//                        " nextiaDI:identifier_idObject rdf:type nextiaDI:IntegratedDatatypeProperty ." +
//                        " nextiaDI:title_title rdf:type nextiaDI:IntegratedDatatypeProperty " +
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
                NextiaQR_RDFS_new n = new NextiaQR_RDFS_new();
                n.query(sourceGraphs, minimal, subgraphs, query);
                break;

            case 2:
                System.out.println("Option 2");

                Map<String, Model> sourceGraphs2 = Maps.newHashMap();
                sourceGraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/38530e7457fd4c27ba2fe6ba7429377f",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/sourceArtist.ttl", Lang.TTL));
                sourceGraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/bac08e07e9794e5495e19ee9287816e0",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/sourceArtworks.ttl", Lang.TTL));

                Model minimal2 = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/minimal2.ttl", Lang.TTL);

                Map<String, Model> subgraphs2 = Maps.newHashMap();
                subgraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/38530e7457fd4c27ba2fe6ba7429377f",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/subgraphArtist.ttl", Lang.TTL));
                subgraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/bac08e07e9794e5495e19ee9287816e0",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/subgraphArtworks.ttl", Lang.TTL));


                String query2 = "PREFIX schema: <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0> \n" +
                        "PREFIX nextiaDI: <http://www.essi.upc.edu/DTIM/NextiaDI/> \n" +
                        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n" +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n" +
                        "SELECT ?v1 ?v2 " +
                        "WHERE { " +
                        " VALUES ( ?v1 ?v2) { ( schema:endYear schema:dateRange ) }  " +
                        " schema:dateRange rdfs:domain schema:Object_1 . " +
                        " schema:dateRange rdf:type rdfs:Property .  " +
                        " schema:Object_4 rdf:type rdfs:Class . " +
                        " schema:endYear rdf:type rdfs:Property . " +
                        " schema:Object_1 rdf:type rdfs:Class . " +
                        " schema:endYear rdfs:domain schema:Object_4 " +
                        "}";
                String query3 = "SELECT ?v1 ?v2 WHERE { " +
                        " VALUES ( ?v1 ?v2) { ( " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> ) } " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> . }";

                String query4 = "SELECT ?endYear WHERE { " +
//                        " VALUES ( ?endYear) { ( " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4>. " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> ?endYear. " +
                        " }";


                NextiaQR_RDFS_new n2 = new NextiaQR_RDFS_new();
                n2.query(sourceGraphs2, minimal2, subgraphs2, query4);
                break;
            case 3:
//                System.out.println("option 3");
//
//
//                Map<String, Model> sourceGraphs2 = Maps.newHashMap();
//                sourceGraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/38530e7457fd4c27ba2fe6ba7429377f",
//                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/sourceArtist.ttl", Lang.TTL));
//                sourceGraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/bac08e07e9794e5495e19ee9287816e0",
//                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/sourceArtworks.ttl", Lang.TTL));
//
//                Model minimal2 = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/minimal2.ttl", Lang.TTL);
//
//                Map<String, Model> subgraphs2 = Maps.newHashMap();
//                subgraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/38530e7457fd4c27ba2fe6ba7429377f",
//                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/subgraphArtist.ttl", Lang.TTL));
//                subgraphs2.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/bac08e07e9794e5495e19ee9287816e0",
//                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/case2/subgraphArtworks.ttl", Lang.TTL));
//
//
//                String query4 = "SELECT ?v1 ?v2 WHERE { " +
//                        " VALUES ( ?v1 ?v2) { ( " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> ) } " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1> ." +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> ." +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/dateRange> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/endYear> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/bac08e07e9794e5495e19ee9287816e0/Object_4> . }";
//
//                NextiaQR_RDFS_new n3 = new NextiaQR_RDFS_new();
//                n3.rewriteToUnionOfConjunctiveQueries(sourceGraphs2, minimal2, subgraphs2, query4);
//                break;

                break;


        }



    }

}
