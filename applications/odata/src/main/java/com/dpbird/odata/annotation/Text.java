package com.dpbird.odata.annotation;

public class Text extends Term{
    private String path;
    private TextArrangementType textArrangement;

    public Text(String path, String textArrangement, String qualifier) {
        super(qualifier);
        this.appliesTo = "Property";
        this.path = path;
        this.textArrangement = TextArrangementType.valueOf(textArrangement);
        this.termName = "Common.Text";
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public TextArrangementType getTextArrangement() {
        return textArrangement;
    }

    public void setTextArrangement(TextArrangementType textArrangement) {
        this.textArrangement = textArrangement;
    }
}
