package edu.upc.essi.dtim;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import edu.upc.essi.dtim.nextiaqr.functions.QueryRewriting;
import edu.upc.essi.dtim.nextiaqr.functions.QueryRewriting_RDFS_new;
import edu.upc.essi.dtim.nextiaqr.functions.Query_RDFS_new;
import edu.upc.essi.dtim.nextiaqr.jena.GraphOperations;
import edu.upc.essi.dtim.nextiaqr.jena.RDFUtil;
import edu.upc.essi.dtim.nextiaqr.models.metamodel.Namespaces;
import edu.upc.essi.dtim.nextiaqr.models.querying.*;
import edu.upc.essi.dtim.nextiaqr.models.querying.wrapper_impl.CSV_Wrapper;
import edu.upc.essi.dtim.nextiaqr.utils.SQLiteUtils;
import edu.upc.essi.dtim.nextiaqr.utils.Utils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.glassfish.jersey.internal.guava.Sets;

import java.util.*;
import java.util.stream.Collectors;

public class NextiaQR_new {

    SparkSession spark;
    QueryRewriting_RDFS_new queryRDFS = new QueryRewriting_RDFS_new();

    public NextiaQR_new() { this.spark = Utils.getSparkSession(); }
    public NextiaQR_new(SparkSession spark ) { this.spark = spark; }

    public RDFSResult query(Map<String, Model> sourceGraphs, Model globalSchema, Map<String, Model> subgraphs, String query) {
        Dataset T = DatasetFactory.create();
        T.addNamedModel("http://globalSchema",globalSchema);
        sourceGraphs.forEach(T::addNamedModel);
        subgraphs.forEach(T::addNamedModel);

        RewritingResult res = queryRDFS.rewriteToUnionOfConjunctiveQueries(query,T, query) ;
        System.out.println("ConjuctiveQueries:");
        res.getCQs().forEach(System.out::println);
        String SQL = toSQL(res ,T);
        System.out.println(SQL);
        return executeSQL(res.getCQs(),SQL,T);
    }

    public static String toSQL (RewritingResult rewritingResult, Dataset T) {
        if (rewritingResult.getCQs().isEmpty()) return null;
        StringBuilder SQL = new StringBuilder();
        rewritingResult.getCQs().forEach(q -> {
            StringBuilder select = new StringBuilder("SELECT ");
            StringBuilder from = new StringBuilder(" FROM ");
            StringBuilder where = new StringBuilder(" WHERE ");
            //Sort the projections as they are indicated in the interface
            //First remove duplicates based on the features
            List<String> seenFeatures = Lists.newArrayList();
            List<String> withoutDuplicates = Lists.newArrayList();
            q.getProjections().forEach(proj -> {
                if (!seenFeatures.contains(rewritingResult.getFeaturesPerAttribute().get(proj))) {
                    withoutDuplicates.add(proj);
                    seenFeatures.add(rewritingResult.getFeaturesPerAttribute().get(proj));
                }
            });
            //Now do the sorting
            List<String> projections = Lists.newArrayList(withoutDuplicates)
                    .stream().filter(p -> rewritingResult.getProjectionOrder().containsKey(rewritingResult.getFeaturesPerAttribute().get(p))).collect(Collectors.toList());//Lists.newArrayList(q.getProjections());

            //projections.sort(Comparator.comparingInt(s -> listOfFeatures.indexOf(QueryRewriting.featuresPerAttribute.get(s))));
            projections.sort(Comparator.comparingInt(s -> rewritingResult.getProjectionOrder().get(rewritingResult.getFeaturesPerAttribute().get(s))));
//            projections.forEach(proj -> select.append("\""+GraphOperations.nn(proj).split("/")[GraphOperations.nn(proj).split("/").length-1]+"\""+","));
            projections.forEach(proj -> {
                // This sub-code can be improve by creating a structure with the proper labels for each resource.
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

                // label from wrapper + label from global. We need to take care of this, since now we assume labels represent how atts are in the data source. If USER change the label, we will not find the atts in the datasource!!!
                select.append(wrapperAlias+"."+att+" as "+aliasG+" ,");
            });
            //q.getWrappers().forEach(w -> from.append(wrapperIriToID.get(w.getWrapper())+","));

//            q.getWrappers().forEach(w -> from.append(GraphOperations.nn(w.getWrapper())+","));
            q.getWrappers().forEach(w -> {


                QuerySolution result = RDFUtil.runAQuery("SELECT ?wrapperName WHERE { GRAPH ?g { " +
                        "<"+w.getWrapper()+"> <" + RDFS.label + "> ?wrapperName.  " +
                        "<"+w.getWrapper()+"> <"+ RDF.type+">  <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource>. " +
                        "} }", T).next();

                from.append(GraphOperations.nn(result.get("wrapperName").toString())+",");

            });
//            from.append(GraphOperations.nn("museums")+",").append(GraphOperations.nn("artworks")+",");


//            q.getJoinConditions().forEach(j -> where.append(
//                    "\"museums."+GraphOperations.nn(j.getLeft_attribute()).split("/")[GraphOperations.nn(j.getLeft_attribute()).split("/").length-1]+"\""+
//                            " = "+
//                            "\"artworks."+GraphOperations.nn(j.getRight_attribute()).split("/")[GraphOperations.nn(j.getRight_attribute()).split("/").length-1]+"\""+
//                            " AND "));
            q.getJoinConditions().forEach(j -> {
                // This sub-code can be improve by creating a structure with the proper labels for each resource.
                String[] tmp =  j.getLeft_attribute().split("/");
                String idLeft = tmp[tmp.length-2];
                QuerySolution resultLeft = RDFUtil.runAQuery("SELECT ?wrapperName  WHERE { GRAPH ?g { " +
                        "<" + j.getLeft_attribute() + "> <" + RDFS.label + "> ?a. " +
                        "?ds <" + RDFS.label + "> ?wrapperName.   " +
                        "?ds <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/id> \"" + idLeft + "\". " +
                        "} }", T).next();

                tmp =  j.getRight_attribute().split("/");
                String idRight = tmp[tmp.length-2];
                QuerySolution resultRight = RDFUtil.runAQuery("SELECT ?wrapperName  WHERE { GRAPH ?g { " +
                        "<" + j.getRight_attribute() + "> <" + RDFS.label + "> ?a. " +
                        "?ds <" + RDFS.label + "> ?wrapperName.   " +
                        "?ds <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/id> \"" + idRight + "\". " +
                        "} }", T).next();


                where.append( resultLeft.get("wrapperName").toString()+"."+GraphOperations.nn(j.getLeft_attribute()).split("/")[GraphOperations.nn(j.getLeft_attribute()).split("/").length-1]+""+
                        " = "+
                        resultRight.get("wrapperName").toString()+"."+GraphOperations.nn(j.getRight_attribute()).split("/")[GraphOperations.nn(j.getRight_attribute()).split("/").length-1]+""+
                        " AND ");
            } );


//            q.getJoinConditions().forEach(j -> where.append(
//                    "museums."+GraphOperations.nn(j.getLeft_attribute()).split("/")[GraphOperations.nn(j.getLeft_attribute()).split("/").length-1]+""+
//                            " = "+
//                            "artworks."+GraphOperations.nn(j.getRight_attribute()).split("/")[GraphOperations.nn(j.getRight_attribute()).split("/").length-1]+""+
//                            " AND "));

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

    public RDFSResult executeSQL(Set<ConjunctiveQuery> UCQs, String SQL, Dataset T) {
        if (UCQs.isEmpty() || SQL == null) {
            System.out.println("The UCQ is empty, no output is generated");
        } else {
            Set<Wrapper> wrappersInUCQs = UCQs.stream().map(cq -> cq.getWrappers()).flatMap(wrappers -> wrappers.stream()).collect(Collectors.toSet());
            Set<GenericWrapper> wrapperImpls = Sets.newHashSet();

            for (Wrapper w : wrappersInUCQs) {
                System.out.println(w.getWrapper());
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
                        spark.read().option("multiline",true).json(w.getPath()).createOrReplaceTempView(w.getLabel());
//                        spark.read().json(w.getPath()).createOrReplaceTempView(w.getLabel());
                        break;
                    case "CSV":

                        spark.read().option("header",true).csv(w.getPath()).createOrReplaceTempView(w.getLabel());
                        break;
                }
                spark.sql(w.getImplementation()).createOrReplaceTempView(GraphOperations.nn(w.getWrapper()));
            });
            //Add TABLE ALIAS to final SQL
            for (GenericWrapper w : wrapperImpls) {
                SQL = SQL.replace(GraphOperations.nn(w.getWrapper()),GraphOperations.nn(w.getWrapper())+ " AS "+w.getLabel());
            }

            RDFSResult res = new RDFSResult();
            org.apache.spark.sql.Dataset<Row> df = spark.sql(SQL);
            List<String> col = new ArrayList<>();
            df.schema().foreach(f -> col.add(f.name()));
            List<String> rows = df.toJSON().collectAsList();
            res.setColumns(col);
            res.setRows(rows);
           // df.show();
            return res;
        }

        return null;

    }

    public static void main(String[] args) {

        int option = 4;

        Map<String, Model> sourceGraphs = Maps.newHashMap();
        Model globalSchema = ModelFactory.createDefaultModel();
        Map<String, Model> subgraphs = Maps.newHashMap();
        String query = "";

        NextiaQR_new n = new NextiaQR_new();

        switch (option) {

            case 1:
                System.out.println("Option 1 – Join");

                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/fed397e85d1746e080fc7b952855e355",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/museums.ttl", Lang.TTL));
                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/b0e193ea4a81462eb0b24ff5b1801e6d",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/artworks.ttl", Lang.TTL));

                globalSchema = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/globalSchema.ttl", Lang.TTL);


                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/fed397e85d1746e080fc7b952855e355",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/subgraphMuseums.ttl", Lang.TTL));
                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/b0e193ea4a81462eb0b24ff5b1801e6d",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/subgraphArtworks.ttl", Lang.TTL));

//                query = "SELECT ?country ?museum_name ?medium ?displayed_in   WHERE { " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1>." +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> ?country. " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> ?museum_name. " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> ?medium. " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> ?displayed_in " +
//                        " }";
                query = " SELECT ?v1 ?v2 ?v3 ?v4 WHERE { " +
                        " VALUES ( ?v1 ?v2 ?v3 ?v4 ) { ( " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in>   " +
                        " ) } " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> .  " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1>. " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1>. " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class>  " +
                        " } ";

                n.query(sourceGraphs, globalSchema, subgraphs, query );


                break;
            case 2:
                System.out.println("Option 2 – Union");

                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/fed397e85d1746e080fc7b952855e355",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/museums.ttl", Lang.TTL));
                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/b0e193ea4a81462eb0b24ff5b1801e6d",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/artworks.ttl", Lang.TTL));

                globalSchema = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/globalSchema.ttl", Lang.TTL);


                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/fed397e85d1746e080fc7b952855e355",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/subgraphMuseums.ttl", Lang.TTL));
                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/b0e193ea4a81462eb0b24ff5b1801e6d",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/subgraphArtworks.ttl", Lang.TTL));

                query = " SELECT ?v1 WHERE { " +
                        " VALUES ( ?v1 ) { ( " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> " +
                        " ) } " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> ." +
                        " } ";

                n.query(sourceGraphs, globalSchema, subgraphs, query );


                break;

            case 3:

                System.out.println("Option 3 - Join without display in");

                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/fed397e85d1746e080fc7b952855e355",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/museums.ttl", Lang.TTL));
                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/b0e193ea4a81462eb0b24ff5b1801e6d",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/artworks.ttl", Lang.TTL));

                globalSchema = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/globalSchema.ttl", Lang.TTL);


                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/fed397e85d1746e080fc7b952855e355",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/subgraphMuseums.ttl", Lang.TTL));
                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/b0e193ea4a81462eb0b24ff5b1801e6d",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/caseJoin/subgraphArtworks.ttl", Lang.TTL));

                query = " SELECT ?v1 ?v2 ?v3 WHERE { " +
                        " VALUES ( ?v1 ?v2 ?v3  ) { ( " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium>   " +
                        " ) } " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> .  " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1>. " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> ." +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class>  " +
                        " } ";

                n.query(sourceGraphs, globalSchema, subgraphs, query );



                break;
            case 4:
                System.out.println("Option 1 – Join ODIN");

                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/70ea6770f94745a98091d16810e391bb",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/pruebaODIN/source_1.ttl", Lang.TTL));
                sourceGraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/8f9df4709b1444f1834a5f2aa6f55d86",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/pruebaODIN/source_2.ttl", Lang.TTL));

                globalSchema = RDFDataMgr.loadModel("src/test/resources/qr_rdfs/pruebaODIN/globalSchema.ttl", Lang.TTL);


                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/70ea6770f94745a98091d16810e391bb",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/pruebaODIN/subgraph_1.ttl", Lang.TTL));
                subgraphs.put("http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/8f9df4709b1444f1834a5f2aa6f55d86",
                        RDFDataMgr.loadModel("src/test/resources/qr_rdfs/pruebaODIN/subgraph_2.ttl", Lang.TTL));


                query = " SELECT ?v1 ?v2 ?v3 ?v4 WHERE { " +
                        " VALUES ( ?v1 ?v2 ?v3 ?v4) { ( " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/medium> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/country> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/displayed_in> <http://www.essi.upc.edu/DTIM/NextiaDI/name2_name> " +
                        " ) } " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/pop> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/country> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/name2_name> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/medium> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/country> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/pop> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/medium> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/displayed_in> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/country> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/name2_name> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/pop> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/70ea6770f94745a98091d16810e391bb/Object_1> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/displayed_in> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/displayed_in> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/medium> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> . " +
                        " <http://www.essi.upc.edu/DTIM/NextiaDI/name2_name> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/8f9df4709b1444f1834a5f2aa6f55d86/Object_1> . }" +
                        "\n";

//                query = " SELECT ?v1 ?v2 ?v3 ?v4 WHERE { " +
//                        " VALUES ( ?v1 ?v2 ?v3 ?v4 ) { ( " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in>   " +
//                        " ) } " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> .  " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/country> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/museum_name> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ." +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/has_artwork> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> .   " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1>. " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/medium> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> . " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> <http://www.w3.org/2000/01/rdf-schema#domain> <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1>. " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/displayed_in> <http://www.w3.org/2000/01/rdf-schema#range> <http://www.w3.org/2001/XMLSchema#string> .   " +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/fed397e85d1746e080fc7b952855e355/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class> ." +
//                        " <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/b0e193ea4a81462eb0b24ff5b1801e6d/Object_1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2000/01/rdf-schema#Class>  " +
//                        " } ";

                RDFSResult result = n.query(sourceGraphs, globalSchema, subgraphs, query);
                result.getRows().forEach(System.out::println);

                break;

        }


    }

}
