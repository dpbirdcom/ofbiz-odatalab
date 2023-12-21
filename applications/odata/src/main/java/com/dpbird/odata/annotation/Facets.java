package com.dpbird.odata.annotation;

import java.util.List;

public class Facets extends Term {

    List<ReferenceFacet> referenceFacets;

    public Facets(String qualifier) {
        super(qualifier);
        this.termName = "UI.Facets";
        this.appliesTo = "EntityType";
    }

    public List<ReferenceFacet> getReferenceFacets() {
        return referenceFacets;
    }

    public void setReferenceFacets(List<ReferenceFacet> referenceFacets) {
        this.referenceFacets = referenceFacets;
    }

}