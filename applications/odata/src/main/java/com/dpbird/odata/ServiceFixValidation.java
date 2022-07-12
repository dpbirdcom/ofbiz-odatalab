package com.dpbird.odata;

import java.util.Map;

/**
 * 这个类用于查询且补齐必选参数,如果在serviceRequireMap中有的话.
 */
public class ServiceFixValidation {


    // 服务名称
    private String serviceName;
    // 实体名称
    private String entityName;
    // 需要补齐的实体字段数据 ( 暂时只支持单个补齐实体 )       fromFieldName | toFieldName
    private   Map<String,Object>  needEntityParams;
    // 已经传入的参数                                      fromFieldName | toFieldName
    private Map<String,Object> requestParams;
    // 查询要用的线索字段
    private Map<String,Object> leads;

    public Map<String, Object> getNeedEntityParams() {
        return needEntityParams;
    }

    public void setNeedEntityParams(Map<String, Object> needEntityParams) {
        this.needEntityParams = needEntityParams;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Map<String, Object> getRequestParams() {
        return requestParams;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }



    public void setRequestParams(Map<String, Object> requestParams) {
        this.requestParams = requestParams;
    }

    public ServiceFixValidation(){
    }

    public Map<String, Object> getLeads() {
        return leads;
    }

    public void setLeads(Map<String, Object> leads) {
        this.leads = leads;
    }

    public ServiceFixValidation(String serviceName,  Map needEntityParams,   Map leads,String entityName){
        this.serviceName = serviceName;
        this.entityName = entityName;
        this.needEntityParams = needEntityParams;
        this.leads = leads;
    }


}
