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
        rdf:type     rdf:Property ;
        rdfs:domain  nextiaDI:artworks_collections ;
        rdfs:range   xsd:string .

nextiaSchema:collections.createdAt
        rdf:type     rdf:Property ;
        rdfs:domain  nextiaDI:artworks_collections ;
        rdfs:range   xsd:string .

<http://www.essi.upc.edu/DTIM/NextiaDI/DataSource/Schema/824f259815094f79bb0a5cac03ae8348/artworks.identifier>
        rdfs:subClassOf  <http://schema.org/identifier> .

nextiaDI:title_title  rdf:type  nextiaDI:IntegratedDatatypeProperty ;
        rdfs:domain  nextiaDI:artworks_collections ;
        rdfs:range   xsd:string .

nextiaSchema:collections.idObject
        rdfs:subClassOf  <http://schema.org/identifier> .

nextiaDI:artworks_collections
        rdf:type  nextiaDI:IntegratedClass .

nextiaSchema:collections.madeBy
        rdf:type     rdf:Property ;
        rdfs:domain  nextiaDI:artworks_collections ;
        rdfs:range   xsd:string .

nextiaSchema:collections.domain
        rdf:type     rdf:Property ;
        rdfs:domain  nextiaDI:artworks_collections ;
        rdfs:range   xsd:string .

<http://www.essi.upc.edu/DTIM/NextiaDI/01ad337680d94ef38e4740154d6ddb6e/minimal>
        nextiaDI:isMinimalOf  nextiaDataSource:01ad337680d94ef38e4740154d6ddb6e .

nextiaDI:identifier_idObject
        rdf:type     nextiaDI:IntegratedDatatypeProperty ;
        rdfs:domain  nextiaDI:artworks_collections ;
        rdfs:range   xsd:string .
