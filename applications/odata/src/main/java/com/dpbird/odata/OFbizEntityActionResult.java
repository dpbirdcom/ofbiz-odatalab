package  com.dpbird.odata;

import org.apache.olingo.commons.api.data.Entity;

/**
 * Created by S on 2018/10/13.
 */
public class OFbizEntityActionResult {
    private Entity entity;
    private boolean created = false;

    public Entity getEntity() {
        return entity;
    }

    public OFbizEntityActionResult setEntity(final Entity entity) {
        this.entity = entity;
        return this;
    }

    public boolean isCreated() {
        return created;
    }

    public OFbizEntityActionResult setCreated(final boolean created) {
        this.created = created;
        return this;
    }
}
