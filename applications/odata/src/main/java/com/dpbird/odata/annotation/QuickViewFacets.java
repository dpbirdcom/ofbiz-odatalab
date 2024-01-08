package com.dpbird.odata.annotation;

import java.util.List;

public class QuickViewFacets extends Term {

    List<ReferenceFacet> referenceFacets;

    public QuickViewFacets(String qualifier) {
        super(qualifier);
        this.termName = "UI.QuickViewFacets";
        this.appliesTo = "EntityType";
    }

    public List<ReferenceFacet> getReferenceFacets() {
        return referenceFacets;
    }

    public void setReferenceFacets(List<ReferenceFacet> referenceFacets) {
        this.referenceFacets = referenceFacets;
    }

}