package com.dpbird.odata.annotation;

/**
 * @author scy
 * @date 2024/1/19
 */
public class Contact extends Term {

    private String photo;
    private String fn;
    private String org;
    private String tel;
    private String phoneMobile;
    private String primaryPhone;
    private String email;

    public Contact(String qualifier) {
        super(qualifier);
        this.termName = "Communication.Contact";
        this.appliesTo = "EntityType";
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getFn() {
        return fn;
    }

    public void setFn(String fn) {
        this.fn = fn;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public String getPhoneMobile() {
        return phoneMobile;
    }

    public void setPhoneMobile(String phoneMobile) {
        this.phoneMobile = phoneMobile;
    }

    public String getPrimaryPhone() {
        return primaryPhone;
    }

    public void setPrimaryPhone(String primaryPhone) {
        this.primaryPhone = primaryPhone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
