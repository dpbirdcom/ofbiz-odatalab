package com.dpbird.odata.annotation;

import java.util.List;

public class HeaderFacets extends Term {

    List<ReferenceFacet> referenceFacets;
    List<CollectionFacet> collectionFacets;

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

    public List<CollectionFacet> getCollectionFacets() {
        return collectionFacets;
    }

    public void setCollectionFacets(List<CollectionFacet> collectionFacets) {
        this.collectionFacets = collectionFacets;
    }
}
