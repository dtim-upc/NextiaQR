@prefix datasource:       <http://www.essi.upc.edu/DTIM/DataSource/> .
@prefix integration:      <http://www.essi.upc.edu/DTIM/Integration/> .
@prefix nextiaDI:         <http://www.essi.upc.edu/DTIM/NextiaDI/> .
@prefix nextiaDataSource: <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/> .
@prefix nextiaSchema:     <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/28cd712c43eb4fddb0e4ea4e6e302737/> .
@prefix rdf:              <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:             <http://www.w3.org/2000/01/rdf-schema#> .
@prefix sourceSchema:     <http://www.essi.upc.edu/DTIM/DataSource/Schema/> .
@prefix xsd:              <http://www.w3.org/2001/XMLSchema#> .

<http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.created_by>
        rdf:type                rdf:Property ;
        rdfs:domain             <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks> ;
        rdfs:range              xsd:string ;
        nextiaDataSource:alias  "artworks.created_by" .

<http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks>
        rdf:type    rdfs:Class ;
        rdfs:label  "artworks" ;
        <http://www.w3.org/2002/07/owl#sameAs>
                nextiaDI:artworks_collections .

nextiaDataSource:824f259815094f79bb0a5cac03ae8348
        rdf:type                  nextiaDI:DataSource ;
        rdfs:label                "artworks" ;
        nextiaDataSource:format   "JSON" ;
        nextiaDataSource:graphicalGraph
                "{\"header\":{\"languages\":[\"en\"],\"baseIris\":[\"http://www.w3.org/2000/01/rdf-schema\"],\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/824f259815094f79bb0a5cac03ae8348\",\"title\":{\"en\":\"artworks\"},\"description\":{\"en\":\"new Ontology description\"}},\"namespace\":[],\"class\":[{\"id\":\"Class1\",\"type\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource\"},{\"id\":\"Class2\",\"type\":\"RDFS:Class\"},{\"id\":\"Class3\",\"type\":\"rdfs:Datatype\"},{\"id\":\"Class4\",\"type\":\"rdfs:Datatype\"},{\"id\":\"Class5\",\"type\":\"rdfs:Datatype\"}],\"classAttribute\":[{\"id\":\"Class1\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/824f259815094f79bb0a5cac03ae8348\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/\",\"label\":\"824f259815094f79bb0a5cac03ae8348\"},{\"id\":\"Class2\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/\",\"label\":\"artworks\"},{\"id\":\"Class3\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"},{\"id\":\"Class4\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"},{\"id\":\"Class5\",\"iri\":\"http://www.w3.org/2001/XMLSchema#string\",\"baseIri\":\"http://www.w3.org/2001/XMLSchema/\",\"label\":\"string\"}],\"property\":[{\"id\":\"property1\",\"type\":\"RDF:Property\"},{\"id\":\"property2\",\"type\":\"RDF:Property\"},{\"id\":\"property3\",\"type\":\"RDF:Property\"}],\"propertyAttribute\":[{\"id\":\"property1\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.title\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/\",\"label\":\"title\",\"attributes\":[\"object\"],\"domain\":\"Class2\",\"range\":\"Class3\"},{\"id\":\"property2\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.identifier\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/\",\"label\":\"identifier\",\"attributes\":[\"object\"],\"domain\":\"Class2\",\"range\":\"Class4\"},{\"id\":\"property3\",\"iri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.created_by\",\"baseIri\":\"http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/\",\"label\":\"created_by\",\"attributes\":[\"object\"],\"domain\":\"Class2\",\"range\":\"Class5\"}]}" ;
        nextiaDataSource:id       "824f259815094f79bb0a5cac03ae8348" ;
        nextiaDataSource:path     "src/test/resources/qr_rdfs/case2/709742hBJg26B9Ls_artworks.json" ;
        nextiaDataSource:wrapper  "SELECT artworks.title,artworks.created_by,artworks.identifier FROM artworks " .

<http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.identifier>
        rdf:type                rdf:Property ;
        rdfs:domain             <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks> ;
        rdfs:range              xsd:string ;
        rdfs:subClassOf         <http://schema.org/identifier> ;
        nextiaDataSource:alias  "artworks.identifier" ;
        <http://www.w3.org/2002/07/owl#sameAs>
                nextiaDI:identifier_idObject .

<http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.title>
        rdf:type                rdf:Property ;
        rdfs:domain             <http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks> ;
        rdfs:range              xsd:string ;
        nextiaDataSource:alias  "artworks.title" ;
        <http://www.w3.org/2002/07/owl#sameAs>
                nextiaDI:title_title .
