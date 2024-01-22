package com.dpbird.odata.annotation;

import java.util.List;

public class HeaderFacets extends Term {

    List<ReferenceFacet> referenceFacets;

    public HeaderFacets(String qualifier) {
        super(qualifier);
        this.termName = "UI.HeaderFacets";
        this.appliesTo = "EntityType";
    }

    public List<ReferenceFacet> getReferenceFacets() {
        return referenceFacets;
    }

    public void setReferenceFacets(List<ReferenceFacet> referenceFacets) {
        this.referenceFacets = referenceFacets;
    }

}
