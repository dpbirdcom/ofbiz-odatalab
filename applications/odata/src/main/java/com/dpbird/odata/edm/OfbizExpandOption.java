package com.dpbird.odata.edm;

import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOptionKind;
import org.apache.olingo.server.core.uri.queryoption.SystemQueryOptionImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OfbizExpandOption  extends SystemQueryOptionImpl implements ExpandOption {

    private final List<ExpandItem> expandItems = new ArrayList<>();

    public OfbizExpandOption() {
        setKind(SystemQueryOptionKind.EXPAND);
    }

    public OfbizExpandOption addExpandItem(final ExpandItem expandItem) {
        expandItems.add(expandItem);
        return this;
    }

    @Override
    public List<ExpandItem> getExpandItems() {
        return Collections.unmodifiableList(expandItems);
    }
}
