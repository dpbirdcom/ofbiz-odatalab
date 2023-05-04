package com.dpbird.odata.annotation;

public class DataFieldForActionAbstract extends DataFieldAbstract{
    private String hidden = "false";
    private boolean inline = false;
    private boolean determining = false;

    public boolean isInline() {
        return inline;
    }

    public void setInline(boolean inline) {
        this.inline = inline;
    }

    public String getHidden() {
        return hidden;
    }

    public void setHidden(String hidden) {
        this.hidden = hidden;
    }

    public boolean isDetermining() {
        return determining;
    }

    public void setDetermining(boolean determining) {
        this.determining = determining;
    }
}
