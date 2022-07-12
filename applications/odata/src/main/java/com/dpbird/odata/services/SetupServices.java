package com.dpbird.odata.services;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import com.dpbird.odata.OfbizMapOdata;

/**
 * openUI的初始化数据的service
 **/
public class SetupServices {

    public final static String module = SetupServices.class.getName();
    public static final String resource = "OrderUiLabels";


    /**
     * 创建客户沟通且发送注册成功邮件
     * (为确保单一业务无错,将此步骤从registerToCreateTenant分离出单独服务)
     * @author S
     * @param dctx
     * @param context
     * @return
     * @throws GenericServiceException
     * @throws GenericEntityException
     */
    public static Map<String, Object> createCommAndSendTempMail(DispatchContext dctx, Map<String, ? extends Object> context) throws GenericServiceException, GenericEntityException {


        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        GenericValue userLogin = (GenericValue) context.get("userLogin");

        // 新二级域名
        String domainName = (String) context.get("domainName");
        // 收件人地址
        String emailAddress = (String) context.get("emailAddress");
        // 客户id
        String toPartyId = (String) context.get("toPartyId");

        // 去创建沟通
        Map<String,Object> commResult= dispatcher.runSync("createCommunicationEvent",
                UtilMisc.toMap("userLogin",userLogin,
                        "communicationEventTypeId",OfbizMapOdata.AUTO_MAIL_COMM,
                        "partyIdTo",toPartyId,
                        "roleTypeIdTo", OfbizMapOdata.ADDRESSEE_ROLE));
        if(ServiceUtil.isError(commResult)){
            Debug.logError("createCommunicationEvent fail.. ",module);
            return commResult;
        }

        Map<String,Object> bodyParameters = new HashMap<>();
        bodyParameters.put("o3Url",domainName);
        bodyParameters.put("userLoginId",emailAddress);

        // 发送注册成功邮件
        Map<String,Object> sendMailResult  =  dispatcher.runSync("sendMailFromTemplateSetting",
                UtilMisc.toMap("userLogin",userLogin,"bodyParameters",bodyParameters,
                        "emailTemplateSettingId", OfbizMapOdata.DEFAULT_MAIL_TEMP_ID,
                        "sendFrom",OfbizMapOdata.DEFAULT_MAIL_FROM,
                        "sendTo",emailAddress,
                        "subject","O3 Registered Successfully!",
                        "communicationEventId",commResult.get("communicationEventId")));
        if(ServiceUtil.isError(sendMailResult)){
            Debug.logError("sendMailFromTemplateSetting fail.. ",module);
            return sendMailResult;
        }



        return ServiceUtil.returnSuccess();
    }

    /**
     * 创建客户/组织
     *
     * @param dctx
     * @param context
     * @return
     * @throws GenericServiceException
     * @throws GenericEntityException
     */
    public static Map<String, Object> createOrganization(DispatchContext dctx, Map<String, ? extends Object> context) throws GenericServiceException, GenericEntityException {


        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        String groupName = (String) context.get("groupName");
        String groupNameLocal = (String) context.get("groupNameLocal");
        String officeSiteName = (String) context.get("officeSiteName");
        String numEmployeesStr = (String) context.get("numEmployees");
        String tickerSymbol = (String) context.get("tickerSymbol");
        String description = (String) context.get("description");
        String comments = (String) context.get("comments");

        String domainName = (String) context.get("domainName");
        String emailAddress = (String) context.get("emailAddress");

        String contactNumber = (String) context.get("contactNumber");
        String areaCode = (String) context.get("areaCode");
        String countryCode = (String) context.get("countryCode");
        String extension = (String) context.get("extension");

        String faxContactNumber = (String) context.get("faxContactNumber");
        String faxAreaCode = (String) context.get("faxAreaCode");
        String faxCountryCode = (String) context.get("faxCountryCode");
        String faxExtension = (String) context.get("faxExtension");

        String toName = (String) context.get("toName");
        String address1 = (String) context.get("address1");
        String countryGeoId = (String) context.get("countryGeoId");
        String stateProvinceGeoId = (String) context.get("stateProvinceGeoId");
        String cityGeoId = (String) context.get("cityGeoId");
        String countyGeoId = (String) context.get("countyGeoId");

        String externalId = (String) context.get("externalId");
        String preferredCurrencyUomId = (String) context.get("preferredCurrencyUomId");
        String statusId = (String) context.get("statusId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");


        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        GenericValue system = delegator.findOne("UserLogin", false, UtilMisc.toMap("userLoginId", "system"));

        String partyId = (String) context.get("partyId");


        Map<String, Object> createPartyGroupMap = UtilMisc.toMap("groupName", groupName, "groupNameLocal", groupNameLocal, "officeSiteName", officeSiteName,
                "numEmployees", numEmployeesStr == null ? null : new Long(numEmployeesStr)
                , "tickerSymbol", tickerSymbol, "description", description, "comments", comments);
        if (UtilValidate.isNotEmpty(partyId)) {
            createPartyGroupMap.put("partyId", partyId);
        }
        Map<String, Object> createPartyGroupResult = dispatcher.runSync("createPartyGroup",
                createPartyGroupMap);

        if (ServiceUtil.isError(createPartyGroupResult)) {
            return createPartyGroupResult;
        }

        partyId = (String) createPartyGroupResult.get("partyId");


        // create PartyRole
        Map<String, Object> createPartyRoleResult = dispatcher.runSync("createPartyRole",
                UtilMisc.toMap("userLogin", system, "partyId", partyId, "roleTypeId", "INTERNAL_ORGANIZATIO"));
        if (ServiceUtil.isError(createPartyRoleResult)) {
            return createPartyRoleResult;
        }


        // create PartyRole
        Map<String, Object> createPartyRoleCarrResult = dispatcher.runSync("createPartyRole",
                UtilMisc.toMap("userLogin", system, "partyId", partyId, "roleTypeId", "CARRIER"));
        if (ServiceUtil.isError(createPartyRoleCarrResult)) {
            return createPartyRoleCarrResult;
        }


        //  create  DOMAIN_NAME
        Map<String, Object> createPartyContactMechResult = new HashMap<>();
        if (UtilValidate.isNotEmpty(domainName)) {
            createPartyContactMechResult = dispatcher.runSync("createPartyContactMech",
                    UtilMisc.toMap("userLogin", system, "partyId", partyId,
                            "contactMechTypeId", "DOMAIN_NAME", "infoString", domainName,"roleTypeId","INTERNAL_ORGANIZATIO","allowSolicitation","Y"));
            if (ServiceUtil.isError(createPartyContactMechResult)) {
                return createPartyContactMechResult;
            }
        }


        // create emailAddress
        if (UtilValidate.isNotEmpty(emailAddress)) {
            createPartyContactMechResult = dispatcher.runSync("createPartyEmailAddress",
                    UtilMisc.toMap("userLogin", system, "partyId", partyId, "emailAddress", emailAddress,"roleTypeId","INTERNAL_ORGANIZATIO","allowSolicitation","Y"));
            if (ServiceUtil.isError(createPartyContactMechResult)) {
                return createPartyContactMechResult;
            }
        }


        // createPartyTelecomNumber
        if (UtilValidate.isNotEmpty(contactNumber) && UtilValidate.isNotEmpty(countryCode)) {
            createPartyContactMechResult = dispatcher.runSync("createPartyTelecomNumber",
                    UtilMisc.toMap("userLogin", system, "partyId", partyId, "contactNumber", contactNumber
                            , "areaCode", areaCode, "countryCode", countryCode, "extension", extension,"roleTypeId","INTERNAL_ORGANIZATIO","allowSolicitation","Y"));
            if (ServiceUtil.isError(createPartyContactMechResult)) {
                return createPartyContactMechResult;
            }
        }

        // create Fax FAX_NUMBER
        if (UtilValidate.isNotEmpty(faxContactNumber) && UtilValidate.isNotEmpty(faxAreaCode)) {
            createPartyContactMechResult = dispatcher.runSync("createPartyTelecomNumber",
                    UtilMisc.toMap("userLogin", system, "partyId", partyId, "contactNumber", faxContactNumber
                            , "areaCode", faxAreaCode, "countryCode", faxCountryCode, "extension", faxExtension
                            , "contactMechPurposeTypeId", "FAX_NUMBER","roleTypeId","INTERNAL_ORGANIZATIO","allowSolicitation","Y"));
            if (ServiceUtil.isError(createPartyContactMechResult)) {
                return createPartyContactMechResult;
            }
        }
        //address1 如果需要创建联系方式，那么必须传address1（2019.3.14改为全部非必传）
        // 经过讨论，暂时情况下一切形式地址信息全部存储。对应的把createPartyPostalAddress和createPostalAddress
        //  的city和address1的必填去掉
        if (UtilValidate.isNotEmpty(address1)
                || UtilValidate.isNotEmpty(cityGeoId)
                || UtilValidate.isNotEmpty(countyGeoId)
                || UtilValidate.isNotEmpty(stateProvinceGeoId)
                || UtilValidate.isNotEmpty(countryGeoId)) {

            String city = cityGeoId;
            if(UtilValidate.isNotEmpty(city)) {
                GenericValue geo = delegator.findOne("Geo", true, UtilMisc.toMap("geoId", cityGeoId));
                city = geo.getString("geoName");
            }
            Map<String, Object> createPostalAddressResultMap = dispatcher.runSync("createPartyPostalAddress",
                    UtilMisc.toMap("userLogin", system, "address1", address1, "city", city
                            , "cityGeoId", cityGeoId, "countyGeoId", countyGeoId, "toName", toName,
                            "stateProvinceGeoId", stateProvinceGeoId, "countryGeoId", countryGeoId, "partyId", partyId,"roleTypeId","INTERNAL_ORGANIZATIO","allowSolicitation","Y"));
            if (ServiceUtil.isError(createPostalAddressResultMap)) {
                return createPostalAddressResultMap;
            }
        }

        String endMonth = "12";
        String endDate = "31";
        Timestamp fromDateStamp = UtilDateTime.getYearStart(UtilDateTime.nowTimestamp());
        String curYearString = UtilDateTime.timeStampToString(fromDateStamp,"yyyy", TimeZone.getDefault(), Locale.getDefault());
        Map<String,Object> inMap = new HashMap<>();
        inMap.put("fromDate",fromDateStamp);
        inMap.put("thruDate",UtilDateTime.getTimestamp(UtilDateTime.toSqlDate(endMonth ,endDate , curYearString).getTime()));
        inMap.put("isClosed","N");
        inMap.put("organizationPartyId",partyId);
        inMap.put("periodName",curYearString);
        inMap.put("periodNum",1L);
        inMap.put("periodTypeId","FISCAL_YEAR");
        inMap.put("userLogin",userLogin);
        Map<String, Object> createCustomTimePeriodResult = dispatcher.runSync("createCustomTimePeriod",
                inMap);

        if (ServiceUtil.isError(createCustomTimePeriodResult)) {
            return createCustomTimePeriodResult;
        }

        resultMap.put("partyId", partyId);

        return resultMap;
    }



    /**
     * 完成初始化
     *
     * @param dctx
     * @param context
     * @return
     * @throws GenericServiceException
     * @throws GenericEntityException
     */
    public static Map<String, Object> completeSetup(DispatchContext dctx, Map<String, ? extends Object> context) throws GenericServiceException, GenericEntityException {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        Map<String,Object> resultMap = ServiceUtil.returnSuccess();

        String partyId = (String) context.get("partyId");

        String roleTypeId = (String) context.get("roleTypeId");

        GenericValue userLogin = (GenericValue) context.get("userLogin");

        //create GL journal for suspended transactions (failed post)
        Map<String,Object> createGlJournalCtx = new HashMap<>();
        createGlJournalCtx.put("organizationPartyId",partyId);
        createGlJournalCtx.put("glJournalName","Suspense transactions");
        createGlJournalCtx.put("userLogin",userLogin);
        Map<String, Object> createGlJournalResult = dispatcher.runSync("createGlJournal",
                createGlJournalCtx);
        if (ServiceUtil.isError(createGlJournalResult)) {
            return createGlJournalResult;
        }

        Map<String,Object> acctgPreferenceCtx = new HashMap<>();
        acctgPreferenceCtx.put("errorGlJournalId",createGlJournalResult.get("glJournalId"));

        GenericValue partyGroup = delegator.findOne("PartyGroup",false,UtilMisc.toMap("partyId",partyId));

        String invoiceIdPrefix = "CI";
        if(UtilValidate.isNotEmpty(partyGroup)&&UtilValidate.isNotEmpty(partyGroup.getString("groupName"))){
            invoiceIdPrefix = partyGroup.getString("groupName").toUpperCase().substring(0, 2)+"CI";
        }

        acctgPreferenceCtx.put("partyId",partyId);
        //这里我们没数据
        acctgPreferenceCtx.put("taxFormId","");
        acctgPreferenceCtx.put("cogsMethodId","COGS_AVG_COST");
        //这里根据所选国家给值，默认CNY
        acctgPreferenceCtx.put("baseCurrencyUomId","CNY");
        acctgPreferenceCtx.put("oldInvoiceSequenceEnumId","INVSQ_ENF_SEQ");
        acctgPreferenceCtx.put("invoiceIdPrefix",invoiceIdPrefix);
        acctgPreferenceCtx.put("oldQuoteSequenceEnumId","INVSQ_ENF_SEQ");
        acctgPreferenceCtx.put("quoteIdPrefix","QU");
        acctgPreferenceCtx.put("oldOrderSequenceEnumId","INVSQ_ENF_SEQ");
        acctgPreferenceCtx.put("orderIdPrefix","OD");
        acctgPreferenceCtx.put("userLogin",userLogin);
        Map<String, Object> createPartyAcctgPreferenceResult = dispatcher.runSync("createPartyAcctgPreference",
                acctgPreferenceCtx);
        if (ServiceUtil.isError(createPartyAcctgPreferenceResult)) {
            return createPartyAcctgPreferenceResult;
        }

        //run demo General Chart Of Accounts
        List<GenericValue> glAccountList = delegator.findAll("GlAccount",false);
        if(UtilValidate.isEmpty(glAccountList)){

            Map<String,Object> importChartOfAccountCtx = new HashMap<>();
            importChartOfAccountCtx.put("filename",System.getProperty("ofbiz.home")+"/applications/accounting/data/DemoGeneralChartOfAccounts.xml");
            importChartOfAccountCtx.put("userLogin",userLogin);
            Map<String, Object> entityImportResult = dispatcher.runSync("entityImport",
                    importChartOfAccountCtx);
            if (ServiceUtil.isError(entityImportResult)) {
                return entityImportResult;
            }
        }

        //create General Ledger Setup
        Timestamp nowTimestamp = UtilDateTime.nowTimestamp();

        Map<String,Object> placeholderValues = new HashMap<>();
        placeholderValues.put("orgPartyId",partyId);
        placeholderValues.put("fromDate",nowTimestamp.toString());

        Map<String,Object> importFileCtx = new HashMap<>();
        importFileCtx.put("filename",System.getProperty("ofbiz.home")+"/applications/commonext/data/GlAccountData.xml");
        importFileCtx.put("placeholderValues",placeholderValues);
        importFileCtx.put("userLogin",userLogin);
        Map<String, Object> importFileResult = dispatcher.runSync("entityImport",
                importFileCtx);
        if (ServiceUtil.isError(importFileResult)) {
            return importFileResult;
        }

        return resultMap;
    }

}
