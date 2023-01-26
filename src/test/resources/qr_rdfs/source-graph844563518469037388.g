@prefix datasource:       <http://www.essi.upc.edu/DTIM/DataSource/> .
@prefix integration:      <http://www.essi.upc.edu/DTIM/Integration/> .
@prefix nextiaDI:         <http://www.essi.upc.edu/DTIM/NextiaDI/> .
@prefix nextiaDataSource: <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/> .
@prefix nextiaSchema:     <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/> .
@prefix rdf:              <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:             <http://www.w3.org/2000/01/rdf-schema#> .
@prefix sourceSchema:     <http://www.essi.upc.edu/DTIM/DataSource/Schema/> .
@prefix xsd:              <http://www.w3.org/2001/XMLSchema#> .

nextiaSchema:collections.createdAt
        rdf:type                rdf:Property ;
        rdfs:domain             nextiaSchema:collections ;
        rdfs:range              xsd:string ;
        nextiaDataSource:alias  "collections.createdAt" .

nextiaSchema:collections
        rdf:type    rdfs:Class ;
        rdfs:label  "collections" ;
        <http://www.w3.org/2002/07/owl#sameAs>
                nextiaDI:artworks_collections .

nextiaSchema:collections.idObject
        rdf:type                rdf:Property ;
        rdfs:domain             nextiaSchema:collections ;
        rdfs:range              xsd:string ;
        rdfs:subClassOf         <http://schema.org/identifier> ;
        nextiaDataSource:alias  "collections.idObject" ;
        <http://www.w3.org/2002/07/owl#sameAs>
                nextiaDI:identifier_idObject .

nextiaSchema:collections.madeBy
        rdf:type                rdf:Property ;
        rdfs:domain             nextiaSchema:collections ;
        rdfs:range              xsd:string ;
        nextiaDataSource:alias  "collections.madeBy" .

nextiaSchema:collections.title
        rdf:type                rdf:Property ;
        rdfs:domain             nextiaSchema:collections ;
        rdfs:range              xsd:string ;
        nextiaDataSource:alias  "collections.title" ;
        <http://www.w3.org/2002/07/owl#sameAs>
                nextiaDI:title_title .

nextiaSchema:collections.domain
        rdf:type                rdf:Property ;
        rdfs:domain             nextiaSchema:collections ;
        rdfs:range              xsd:string ;
        nextiaDataSource:alias  "collections.domain" .

nextiaDataSource:28cd712c43eb4fddb0e4ea4e6e302737
        rdf:type                  nextiaDI:DataSource ;
        rdfs:label                "collections" ;
        nextiaDataSource:format   "JSON" ;
        nextiaDataSource:graphicalGraph
                "{\"header\":{\"languages\":[\"en\"],\"baseIris\":[\"http://www.w3.org/2000/01/rdf-schema\"],\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/28cd712c43eb4fddb0e4ea4e6e302737\",\"title\":{\"en\":\"collections\"},\"description\":{\"en\":\"new Ontology description\"}},\"namespace\":[],\"class\":[{\"id\":\"Class1\",\"type\":\"RDFS:Class\"},{\"id\":\"Class2\",\"type\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource\"},{\"id\":\"Class3\",\"type\":\"rdfs:Datatype\"},{\"id\":\"Class4\",\"type\":\"rdfs:Datatype\"},{\"id\":\"Class5\",\"type\":\"rdfs:Datatype\"},{\"id\":\"Class6\",\"type\":\"rdfs:Datatype\"},{\"id\":\"Class7\",\"type\":\"rdfs:Datatype\"}],\"classAttribute\":[{\"id\":\"Class1\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/collections\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/\",\"label\":\"collections\"},{\"id\":\"Class2\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/28cd712c43eb4fddb0e4ea4e6e302737\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/\",\"label\":\"28cd712c43eb4fddb0e4ea4e6e302737\"},{\"id\":\"Class3\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"},{\"id\":\"Class4\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"},{\"id\":\"Class5\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"},{\"id\":\"Class6\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"},{\"id\":\"Class7\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"}],\"property\":[{\"id\":\"property1\",\"type\":\"RDF:Property\"},{\"id\":\"property2\",\"type\":\"RDF:Property\"},{\"id\":\"property3\",\"type\":\"RDF:Property\"},{\"id\":\"property4\",\"type\":\"RDF:Property\"},{\"id\":\"property5\",\"type\":\"RDF:Property\"}],\"propertyAttribute\":[{\"id\":\"property1\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/collections.createdAt\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/\",\"label\":\"createdAt\",\"attributes\":[\"object\"],\"domain\":\"Class1\",\"range\":\"Class3\"},{\"id\":\"property2\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/collections.title\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/\",\"label\":\"title\",\"attributes\":[\"object\"],\"domain\":\"Class1\",\"range\":\"Class4\"},{\"id\":\"property3\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/collections.idObject\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/\",\"label\":\"idObject\",\"attributes\":[\"object\"],\"domain\":\"Class1\",\"range\":\"Class5\"},{\"id\":\"property4\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/collections.domain\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/\",\"label\":\"domain\",\"attributes\":[\"object\"],\"domain\":\"Class1\",\"range\":\"Class6\"},{\"id\":\"property5\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/collections.madeBy\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/\",\"label\":\"madeBy\",\"attributes\":[\"object\"],\"domain\":\"Class1\",\"range\":\"Class7\"}]}" ;
        nextiaDataSource:id       "28cd712c43eb4fddb0e4ea4e6e302737" ;
        nextiaDataSource:path     "src/test/resources/qr_rdfs/case2/hDGMeEbvWmSozXxZ_collections.json" ;
        nextiaDataSource:wrapper  "SELECT collections.idObject,collections.domain,collections.title,collections.madeBy,collections.createdAt FROM collections " .
