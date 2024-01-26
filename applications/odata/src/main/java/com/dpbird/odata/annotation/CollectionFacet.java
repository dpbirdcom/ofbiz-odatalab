package com.dpbird.odata.annotation;

import java.util.List;

public class CollectionFacet extends ReferenceFacet {

    private List<ReferenceFacet> facets;

    public List<ReferenceFacet> getFacets() {
        return facets;
    }

    public void setFacets(List<ReferenceFacet> facets) {
        this.facets = facets;
    }
}
