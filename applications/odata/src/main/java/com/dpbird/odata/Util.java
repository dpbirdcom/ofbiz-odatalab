package  com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.drew.lang.annotations.NotNull;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.collections.ResourceBundleMapWrapper;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.*;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelParam;
import org.apache.ofbiz.service.ModelService;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.commons.core.edm.primitivetype.EdmDateTimeOffset;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.OrderByItem;
import org.apache.olingo.server.api.uri.queryoption.apply.Aggregate;
import org.apache.olingo.server.api.uri.queryoption.apply.AggregateExpression;
import org.apache.olingo.server.api.uri.queryoption.apply.GroupBy;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.*;
import java.sql.Timestamp;
import java.util.*;

public class Util {

	public static final String module = Util.class.getName();
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	public static final String DATE_TIME_FORMAT_GMT = "yyyy-MM-dd'T'HH:mm:ss.SSS Z";
	public static final String ODATA_PROPERTIES = "odata.properties";
//	private static ResourceBundleMapWrapper uiLabelMap = null;
	private static Map<Locale, ResourceBundleMapWrapper> uiLabelLocaleMap = new HashMap<>();

	/*
	 * @Description:???????????????????????????
	 * @Author: S
	 * @Date: 2019/7/30 11:23 AM
	 * @Param: [obj]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 */
	public static Map<String, Object> getKeyAndValue(Object obj) {
		Map<String, Object> map = new HashMap<String, Object>();
		// ???????????????
		Class userCla = (Class) obj.getClass();
		/* ????????????????????????????????? */
		Field[] fs = userCla.getDeclaredFields();
		for (int i = 0; i < fs.length; i++) {
			Field f = fs[i];
			f.setAccessible(true); // ?????????????????????????????????
			Object val = new Object();
			try {
				val = f.get(obj);
				// ?????????????????????
				map.put(f.getName(), val);// ????????????
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}

			/*
			 * String type = f.getType().toString();//???????????????????????? if
			 * (type.endsWith("String")) {
			 * System.out.println(f.getType()+"\t???String"); f.set(obj,"12") ;
			 * //??????????????? }else if(type.endsWith("int") ||
			 * type.endsWith("Integer")){
			 * System.out.println(f.getType()+"\t???int"); f.set(obj,12) ; //???????????????
			 * }else{ System.out.println(f.getType()+"\t"); }
			 */

		}
		// Debug.logInfo("???????????????????????????:"+map.toString(),module);
		return map;
	}
	
	/**
	 * ??????????????????????????????????????????
	 * @param obj
	 * @return
	 */
	public static Map<String, Object> objectFieldValueMap(Object obj, boolean includeSuper) {
		Map<String, Object> map = new HashMap<String, Object>();
		List<Field> fieldList = new ArrayList<>();
		// ???????????????
		Class tempClass = (Class) obj.getClass();
		while (tempClass != null) {//????????????null??????????????????????????????????????????(Object???).
		      fieldList.addAll(Arrays.asList(tempClass .getDeclaredFields()));
		      if (includeSuper) {
				  tempClass = tempClass.getSuperclass(); //????????????,??????????????????
			  } else {
		      	tempClass = null;
			  }
		}
		for (Field f : fieldList) {
			f.setAccessible(true); // ?????????????????????????????????
			Object val = new Object();
			try {
				val = f.get(obj);
				// ?????????????????????
				map.put(f.getName(), val);// ????????????
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return map;
	}
	
	/**
	 * ??????object?????????????????????
	 * @param object
	 * @return
	 */
	public static boolean isBaseType(Object object) {
		Class className = object.getClass();
		if (className.equals(java.sql.Timestamp.class) || className.equals(java.lang.String.class) ||className.equals(java.lang.Integer.class) ||
				className.equals(java.lang.Byte.class) ||
				className.equals(java.lang.Long.class) ||
				className.equals(java.lang.Double.class) ||
				className.equals(java.lang.Float.class) || className.equals(java.math.BigDecimal.class)||
				className.equals(java.lang.Character.class) ||
				className.equals(java.lang.Short.class) ||
				className.equals(java.lang.Boolean.class)) {
			return true;
		}
		return false;
	}

	/**
	 * Create Content & DataResource &&  ASSOC Party
	 * @param delegator
	 * @param userLogin
	 * @param dispatcher
	 * @param pictureKey
	 * @return
	 * @throws GenericServiceException
	 * @throws GenericEntityException
	 */
	public static String createContentAndDataResource(String partyId, Delegator delegator, GenericValue userLogin, LocalDispatcher dispatcher, String pictureKey, String path, String partyContentType)
			throws GenericServiceException, GenericEntityException {


		//????????????????????????,?????????????????????
		if (partyContentType == null) {
			partyContentType = "LGOIMGURL";
		}

		// 1.CREATE DATA RESOURCE
		Map<String, Object> createDataResourceMap = UtilMisc.toMap("userLogin", userLogin, "partyId", "system",
				"dataResourceTypeId", "LOCAL_FILE", "dataCategoryId", "PERSONAL", "dataResourceName", pictureKey,
				"mimeTypeId", "image/jpeg", "isPublic", "Y", "dataTemplateTypeId", "NONE", "statusId", "CTNT_PUBLISHED",
				"objectInfo", path);


		Map<String, Object> serviceResultByDataResource = dispatcher.runSync("createDataResource",
				createDataResourceMap);
		String dataResourceId = (String) serviceResultByDataResource.get("dataResourceId");


		Map<String, Object> createContentMap = UtilMisc.toMap("userLogin", userLogin, "createdByUserLogin", userLogin.get("userLoginId"), "contentTypeId",
				"DOCUMENT", "statusId", "CTNT_PUBLISHED", "mimeTypeId", "image/png", "dataResourceId", dataResourceId);


		Map<String, Object> serviceResultByCreateContentMap = dispatcher.runSync("createContent", createContentMap);
		String contentId = (String) serviceResultByCreateContentMap.get("contentId");


		Map<String, Object> assocContentPartyMap = UtilMisc.toMap("userLogin", userLogin, "contentId", contentId, "partyId", partyId, "partyContentTypeId", partyContentType);
		dispatcher.runSync("createPartyContent", assocContentPartyMap);

		return "success";
	}

	public static URI createId(String entitySetName, EdmEntityType edmEntityType, OfbizCsdlEntityType ofbizCsdlEntityType, GenericValue genericValue) {
		if (ofbizCsdlEntityType.isAutoId()) {
			return null;
		}
		String entityId;
		List<String> entityTypeKeys = edmEntityType.getKeyPredicateNames();
		if (entityTypeKeys.size() == 1) {
			entityId = "'" + genericValue.getString(entityTypeKeys.get(0)) + "'";
		} else {
			StringBuffer sb = new StringBuffer();
			int i = 0;
			String value;
			for (String pkName: entityTypeKeys) {
				EdmPrimitiveType propertyEdmType = (EdmPrimitiveType) edmEntityType.getStructuralProperty(pkName).getType();
				if(propertyEdmType instanceof EdmDateTimeOffset){
					try {
						Timestamp fromDate = (Timestamp)genericValue.get(pkName);
						if (fromDate == null) {
							value = null;
						} else {
							EdmDateTimeOffset propertyDateTimeOffset = (EdmDateTimeOffset) propertyEdmType;
							value = propertyDateTimeOffset.
									valueToString(fromDate, false, null, 3, Constants.DEFAULT_SCALE, true);
//							value = propertyDateTimeOffset.toUriLiteral(value);
							value = URLEncoder.encode(value, "UTF-8");
						}
					} catch (EdmPrimitiveTypeException | UnsupportedEncodingException e) {
						e.printStackTrace();
						throw new ODataRuntimeException("Unable to create id for entity: " + entitySetName, e);
					}
				} else {
					value = genericValue.getString(pkName);
					value = propertyEdmType.toUriLiteral(value);
				}
				sb.append(pkName).append("=").append(value);
				i++;
				if (entityTypeKeys.size() > i) {
					sb.append(",");
				}
			}
			entityId = sb.toString();
		}
		try {
			return new URI(UtilHttp.encodeBlanks(entitySetName + "(" + entityId + ")"));
		} catch (URISyntaxException e) {
			throw new ODataRuntimeException("Unable to create id for entity: " + entitySetName, e);
		}
	}

	public static EdmEntitySet getNavigationTargetEntitySet(EdmBindingTarget startEdmBindingTarget,
															EdmNavigationProperty edmNavigationProperty) {

		String navPropName = edmNavigationProperty.getName();
		Debug.logInfo("================== navPropName = " + navPropName, module);
		EdmBindingTarget edmBindingTarget = startEdmBindingTarget.getRelatedBindingTarget(navPropName);
		if (edmBindingTarget == null) {
			Debug.logInfo("================== target is null", module);
		}
		if(edmBindingTarget instanceof EdmEntitySet){
			Debug.logInfo("================== target is " + edmBindingTarget.getName(), module);
			return (EdmEntitySet) edmBindingTarget;
		}
		return null;
	}


	public static Map<String, Object> getNavigationTargetKeyMap(EdmBindingTarget startEdmBindingTarget,
															EdmNavigationProperty edmNavigationProperty, List<UriParameter> nextKeyPredicates) throws OfbizODataException {
		EdmEntitySet navigationTargetEntitySet = getNavigationTargetEntitySet(startEdmBindingTarget, edmNavigationProperty);
		if (UtilValidate.isEmpty(navigationTargetEntitySet) || UtilValidate.isEmpty(nextKeyPredicates)) {
			return null;
		}
		return uriParametersToMap(nextKeyPredicates, navigationTargetEntitySet.getEntityType());
	}

	public static java.sql.Date getSqlDate(String dateStr){
		return java.sql.Date.valueOf(dateStr);
	}
	public static java.sql.Timestamp getSqlTimestamp(String dateStr){
		return java.sql.Timestamp.valueOf(dateStr);
	}

	/**
	 * ?????????URL??????GET???????????????
	 *
	 * @param url
	 *            ???????????????URL
	 * @param param
	 *            ???????????????????????????????????? name1=value1&name2=value2 ????????????
	 * @return URL ????????????????????????????????????
	 */
	public static String sendGet(String url, String param) {
		String result = "";
		BufferedReader in = null;
		try {
			String urlNameString = url + "?" + param;
			URL realUrl = new URL(urlNameString);
			// ?????????URL???????????????
			URLConnection connection = realUrl.openConnection();
			// ???????????????????????????
			connection.setRequestProperty("accept", "*/*");
			connection.setRequestProperty("connection", "Keep-Alive");
			connection.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			// ?????????????????????
			connection.connect();
			// ???????????????????????????
			Map<String, List<String>> map = connection.getHeaderFields();
			// ??????????????????????????????
			for (String key : map.keySet()) {
				System.out.println(key + "--->" + map.get(key));
			}
			// ?????? BufferedReader??????????????????URL?????????
			in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			System.out.println("??????GET?????????????????????" + e);
			e.printStackTrace();
		}
		// ??????finally?????????????????????
		finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * ????????? URL ??????POST???????????????
	 *
	 * @param url
	 *            ??????????????? URL
	 * @param param
	 *            ???????????????????????????????????? name1=value1&name2=value2 ????????????
	 * @return ????????????????????????????????????
	 */
	public static String sendPost(String url, String param) {
		PrintWriter out = null;
		BufferedReader in = null;
		String result = "";
		try {
			URL realUrl = new URL(url);
			// ?????????URL???????????????
			URLConnection conn = realUrl.openConnection();
			// ???????????????????????????
			conn.setRequestProperty("accept", "*/*");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			// ??????POST??????????????????????????????
			conn.setDoOutput(true);
			conn.setDoInput(true);
			// ??????URLConnection????????????????????????
			out = new PrintWriter(conn.getOutputStream());
			// ??????????????????
			out.print(param);
			// flush??????????????????
			out.flush();
			// ??????BufferedReader??????????????????URL?????????
			in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			System.out.println("?????? POST ?????????????????????" + e);
			e.printStackTrace();
		}
		// ??????finally?????????????????????????????????
		finally {
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return result;
	}




	//?????????length??????????????????
	public static String getRandomString(int length){
		//????????????????????????A-Z???a-z???0-9??????62??????
		String str="zxcvbnmlkjhgfdsaqwertyuiopQWERTYUIOPASDFGHJKLZXCVBNM1234567890";
		//???Random???????????????
		Random random=new Random();
		StringBuffer sb=new StringBuffer();
		//???????????????????????????
		for(int i=0; i<length; ++i){
			//??????0-61?????????
			int number=random.nextInt(62);
			//????????????????????????length????????????sb???
			sb.append(str.charAt(number));
		}
		//????????????????????????????????????
		return sb.toString();
	}


	/**
	 13      * ??????????????????IP??????????????????request.getRemoteAddr();??????????????????????????????????????????????????????????????????IP??????,
	 14 *
	 16      * ???????????????????????????????????????????????????X-Forwarded-For????????????????????????????????????IP???????????????????????????????????????????????????IP??????
	 17      * ????????????X-Forwarded-For???????????????unknown?????????IP????????????
	 18      *
	 19      * ??????X-Forwarded-For???192.168.1.110, 192.168.1.120, 192.168.1.130,
	 20      * 192.168.1.100
	 21      *
	 22      * ????????????IP?????? 192.168.1.110
	 23      *
	 24      * @param request
	 25      * @return
	 26      */
	public static String getIpAddress(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_CLIENT_IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("HTTP_X_FORWARDED_FOR");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

	/**
	 * ???????????????
	 * @param text ????????????????????????
	 * @param key ??????
	 * @param input_charset ????????????
	 * @return ????????????
	 */
	public static String sign(String text, String key, String input_charset) {
		text = text + key;
		return DigestUtils.md5Hex(getContentBytes(text, input_charset));
	}
	/**
	 * ???????????????
	 * @param text ????????????????????????
	 * @param sign ????????????
	 * @param key ??????
	 * @param input_charset ????????????
	 * @return ????????????
	 */
	public static boolean verify(String text, String sign, String key, String input_charset) {
		text = text + key;
		String mysign = DigestUtils.md5Hex(getContentBytes(text, input_charset));
		if (mysign.equals(sign)) {
			return true;
		} else {
			return false;
		}
	}
	/**
	 * getContentBytes
	 * @param content
	 * @param charset
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static byte[] getContentBytes(String content, String charset) {
		if (charset == null || "".equals(charset)) {
			return content.getBytes();
		}
		try {
			return content.getBytes(charset);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("MD5???????????????????????????,????????????????????????,??????????????????????????????:" + charset);
		}
	}
	/**
	 * ??????6??????10???????????? param codeLength(?????????)
	 * @return
	 */
	public static String createCode(int codeLength) {
		String code = "";
		for (int i = 0; i < codeLength; i++) {
			code += (int) (Math.random() * 9);
		}
		return code;
	}
	/**
	 * ???????????????????????????????????????
	 * @param sArray ???????????????
	 * @return ???????????????????????????????????????????????????
	 */
	public static Map paraFilter(Map sArray) {
		Map result = new HashMap();
		if (sArray == null || sArray.size() <= 0) {
			return result;
		}
		for (Object key :  sArray.keySet()) {
			String value = (String) sArray.get(key);
			if (value == null || value.equals("") || key.toString().equalsIgnoreCase("sign")
					|| key.toString().equalsIgnoreCase("sign_type")) {
				continue;
			}
			result.put(key, value);
		}
		return result;
	}
	/**
	 * ????????????????????????????????????????????????=???????????????????????????&???????????????????????????
	 * @param params ?????????????????????????????????????????????
	 * @return ??????????????????
	 */
	public static String createLinkString(Map params) {
		List keys = new ArrayList(params.keySet());
		Collections.sort(keys);
		String prestr = "";
		for (int i = 0; i < keys.size(); i++) {
			String key = (String) keys.get(i);
			String value = (String) params.get(key);
			if (i == keys.size() - 1) {// ?????????????????????????????????&??????
				prestr = prestr + key + "=" + value;
			} else {
				prestr = prestr + key + "=" + value + "&";
			}
		}
		return prestr;
	}
	/**
	 * httpRequest
	 * @param requestUrl ????????????
	 * @param requestMethod ????????????
	 * @param outputStr ??????
	 */
	public static String httpRequest(String requestUrl,String requestMethod,String outputStr){
		// ??????SSLContext
		StringBuffer buffer=null;
		try{
			URL url = new URL(requestUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod(requestMethod);
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.connect();
			//????????????????????????
			if(null !=outputStr){
				OutputStream os=conn.getOutputStream();
				os.write(outputStr.getBytes("utf-8"));
				os.close();
			}
			// ?????????????????????????????????
			InputStream is = conn.getInputStream();
			InputStreamReader isr = new InputStreamReader(is, "utf-8");
			BufferedReader br = new BufferedReader(isr);
			buffer = new StringBuffer();
			String line = null;
			while ((line = br.readLine()) != null) {
				buffer.append(line);
			}
		}catch(Exception e){
			//????????????????????????????????????????????????????????????????????????????????????
			Debug.logWarning(e.getMessage(), module);
		}
		return buffer.toString();
	}

	/**
	 * httpRequest
	 * @param url ????????????
	 * @param requestMethod ????????????
	 * @param outputStr ??????
	 */
	public static byte[] httpPost(String url,String requestMethod,String outputStr){
		HttpPost post = new HttpPost(url);
		byte[] result = null;
		try {
			CloseableHttpClient client = HttpClients.createDefault();
			// ??????????????????
			StringEntity s = new StringEntity(outputStr,"UTF-8");
			s.setContentEncoding("UTF-8");
			s.setContentType("application/json");
			// ?????????????????????(???????????????json??????)
			post.setEntity(s);
			post.addHeader("content-type", "application/json;charset=UTF-8");
			//  ????????????
			HttpResponse res = client.execute(post);
			//  ?????????????????????????????????????????????????????????200
			if (res.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				//  HttpEntity???????????????????????????http???????????????,????????????
				HttpEntity entity = res.getEntity();
				result = EntityUtils.toByteArray(entity);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally {
			// ????????????
			post.releaseConnection();
		}
		return result;
	}

	/**
	*@description: ???Plugin???Config???????????????UiLabel??????????????????????????????
	*@author: S
	*@param: path
	*@createTime: 2021-09-271  2:24 ??????
	*/
	public static List<String> getAllUiLabelNames(String componentPath){
		List<String> uiLabels = new ArrayList<>();
		File f = new File(componentPath+File.separator+"config"+File.separator);
		if (!f.exists()) {
			return uiLabels;
		}

		File fa[] = f.listFiles();

		for (int i = 0; i < fa.length; i++) {
			File fs = fa[i];
			if (fs.isFile()&&fs.getName().contains("UiLabels")){
				String fsName = fs.getName();
				uiLabels.add(fsName.substring(0,fsName.lastIndexOf(".")));
			}
		}
		return uiLabels;
	}

	public static ResourceBundleMapWrapper getUiLabelMap(Locale locale) {
		if (uiLabelLocaleMap.get(locale) == null) {
			ResourceBundleMapWrapper uiLabelMap = UtilProperties.getResourceBundleMap("CommonEntityLabels", locale);
			uiLabelMap.addBottomResourceBundle("DateTimeLabels");
			uiLabelMap.addBottomResourceBundle("CommonErrorUiLabels");
			uiLabelMap.addBottomResourceBundle("CommonUiLabels");
			uiLabelMap.addBottomResourceBundle("SecurityUiLabels");
			uiLabelMap.addBottomResourceBundle("ServiceErrorUiLabels");
			uiLabelMap.addBottomResourceBundle("ExtendedUiLabels");
			uiLabelLocaleMap.put(locale, uiLabelMap);

		}
		return uiLabelLocaleMap.get(locale);
	}


	public static String getRequestComponentName(HttpServletRequest req){
		String contextRoot = (String) req.getAttribute("_CONTEXT_ROOT_");
		ServletContext servletCtx = (ServletContext) req.getAttribute("servletContext");
		String componentName = (String) servletCtx.getAttribute("componentName");
		return componentName;
	}
	public static String getRequestComponentPath(HttpServletRequest req,String componentName){
		String contextRoot = (String) req.getAttribute("_CONTEXT_ROOT_");
		ServletContext servletCtx = (ServletContext) req.getAttribute("servletContext");
		String componentPath = contextRoot.substring(0, contextRoot.lastIndexOf("plugins" + File.separator)+7) + File.separator+
				componentName;
		return componentPath;
	}

	// ??????edmEntityType????????????Map??????edmFkEntityType
	// pkMap???edmFkEntityType?????????Map
	public static Map<String, Object> retrieveFkFromPk(Delegator delegator, Map<String, Object> referencedEntityPkMap,
													   EdmEntityType edmEntityType, EdmEntityType referencedEdmEntityType, String reverseReferenceTitle, String referenceTitle) {
		String entityName = edmEntityType.getName();
		String referencedEntityName = referencedEdmEntityType.getName();
		return retrieveFkFromPk(delegator, referencedEntityPkMap, entityName, referencedEntityName, reverseReferenceTitle, referenceTitle);
	}

	public static Map<String, Object> retrieveFkFromPk(Delegator delegator, Map<String, Object> referencedEntityPkMap,
													   String entityName, String referencedEntityName, String reverseReferenceTitle, String referenceTitle) {
		Map<String, Object> fkMap = new HashMap<String, Object>();
		ModelEntity referencedModelEntity = delegator.getModelEntity(referencedEntityName);
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		ModelRelation modelRelation = null;
		// ModelRelation modelRelation = modelEntity.getRelation(edmFkEntityType.getName());
		ModelRelation reverseModelRelation = null;
		if (referenceTitle != null) {
			modelRelation = modelEntity.getRelation(referenceTitle);
			List<ModelKeyMap> modelKeyMaps = modelRelation.getKeyMaps();
			for (ModelKeyMap modelKeyMap:modelKeyMaps) {
				String relFieldName = modelKeyMap.getRelFieldName();
				String fieldName = modelKeyMap.getFieldName();
				if (UtilValidate.isNotEmpty(referencedEntityPkMap.get(relFieldName))) {
					fkMap.put(fieldName, referencedEntityPkMap.get(relFieldName));
				}
			}
		} else if (reverseReferenceTitle != null) {
			reverseModelRelation = referencedModelEntity.getRelation(reverseReferenceTitle);
			List<ModelKeyMap> reverseModelKeyMaps = reverseModelRelation.getKeyMaps();
			for (ModelKeyMap modelKeyMap:reverseModelKeyMaps) {
				String relFieldName = modelKeyMap.getFieldName();
				String fieldName = modelKeyMap.getRelFieldName();
				if (referencedEntityPkMap.get(relFieldName) != null) {
					fkMap.put(fieldName, referencedEntityPkMap.get(relFieldName));
				}
			}
		}
		// ?????????????????????entityName??????referencedEntityName???relation???
		// ?????????????????????edmEntityType??????edmFkEntityType???relation??????????????????key????????????????????????????????????
		return fkMap;
	}

	@Deprecated
	public static Map<String, Object> retrievePkFromEntity(Entity entity, EdmEntityType edmEntityType) {
		Map<String, Object> pkMap = new HashMap<String, Object>();
		List<String> keyNames = edmEntityType.getKeyPredicateNames();
		for (String keyName:keyNames) {
			Property property = entity.getProperty(keyName);
			Object value = property.getValue();
			pkMap.put(keyName, value);
		}
		return pkMap;
	}

	public static Map<String, Object> retrieveKeyMapFromEntity(Delegator delegator, Entity entity, OfbizCsdlEntityType csdlEntityType) {
		Map<String, Object> pkMap = new HashMap<String, Object>();
		if (csdlEntityType.isAutoId()) {
			Property idProperty = entity.getProperty("id");
			String idValue = (String) idProperty.getValue();
			return Util.keyMapFromId(delegator, csdlEntityType, idValue);
		}
		List<String> keyNames = csdlEntityType.getKeyPropertyNames();
		for (String keyName:keyNames) {
			Property property = entity.getProperty(keyName);
			Object value = property.getValue();
			pkMap.put(keyName, value);
		}
		return pkMap;
	}

	public static Map<String, Object> retrievePkFromEntity(Delegator delegator, String entityName, OdataOfbizEntity entity) {
		Map<String, Object> result = new HashMap<String, Object>();
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		List<String> pkFieldNames = modelEntity.getPkFieldNames();
		for (String pkFieldName:pkFieldNames) {
			Property property = entity.getProperty(pkFieldName);
			Object fieldValue = null;
			if (property.getValueType().equals(ValueType.ENUM)) {
				fieldValue = property.asEnum();
			} else {
				fieldValue = property.getValue();
			}
			result.put(pkFieldName, fieldValue);
		}
		return result;
	}

	public static Map<String, Object> retrievePkMap(Delegator delegator, Map<String, Object> fieldMap, Map<String, Object> result, String entityName) {
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		List<String> pkFieldNames = modelEntity.getPkFieldNames();
		Map<String, Object> pkMap = new HashMap<String, Object>();
		for (String pkFieldName:pkFieldNames) {
			Object value = result.get(pkFieldName);
			if (value == null) {
				value = fieldMap.get(pkFieldName);
			}
			pkMap.put(pkFieldName, value);
		}
		return pkMap;
	}

	public static Map<String, Object> retrieveFieldMap(Delegator delegator, Map<String, Object> otherMap, String entityName) {
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		List<String> fieldNames = modelEntity.getAllFieldNames();
		Map<String, Object> fieldMap = new HashMap<String, Object>();
		for (String fieldName:fieldNames) {
			Object value = otherMap.get(fieldName);
			fieldMap.put(fieldName, value);
		}
		return fieldMap;
	}

	/**
	 * ???????????????????????????????????????????????????
	 */
	@Deprecated
	public static Map<String, Object> convertServiceFieldMap(Delegator delegator, org.apache.olingo.commons.api.data.Entity entityToWrite,
															 ModelService modelService, OfbizAppEdmProvider edmProvider, OData odata, ServiceMetadata serviceMetadata)
															throws GenericEntityException, ODataException {
		Map<String, Object> fieldMap = new HashMap<String, Object>();
		List<Property> properties = entityToWrite.getProperties();
		for (Property property : properties) {
			ModelParam modelParam = modelService.getParam(property.getName());
			String name = property.getName();
			if (modelParam == null) {
				Debug.logInfo("------------------" + name + " is not a property of " + entityToWrite.getType(), module);
				continue;
			}
			String paramType = modelParam.getType();
			String valueString = null;
			if (UtilValidate.isNotEmpty(property.getValue())) {
				valueString = property.getValue().toString();
				if (paramType.equals("String")) {
					if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".Bool")) {
						if ((Integer) property.getValue() == 1) {
							fieldMap.put(name, "Y");
						} else if ((Integer) property.getValue() == 0) {
							fieldMap.put(name, "N");
						}
					} else if (property.getType() != null && property.getType().equals("Edm.Boolean")) {
						if ((boolean) property.getValue()) {
							fieldMap.put(name, "Y");
						} else {
							fieldMap.put(name, "N");
						}
					} else if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".MaritalStatus")) {
						if ((Integer) property.getValue() == 1) {
							fieldMap.put(name, "S");
						} else if ((Integer) property.getValue() == 2) {
							fieldMap.put(name, "M");
						} else if ((Integer) property.getValue() == 3) {
							fieldMap.put(name, "P");
						} else if ((Integer) property.getValue() == 4) {
							fieldMap.put(name, "D");
						} else if ((Integer) property.getValue() == 5) {
							fieldMap.put(name, "W");
						}
					} else if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".Gender")) {
						if ((Integer) property.getValue() == 1) {
							fieldMap.put(name, "M");
						} else if ((Integer) property.getValue() == 2) {
							fieldMap.put(name, "F");
						}
					} else if (property.getValueType().equals(ValueType.ENUM)) {
						// Map<String, OfbizCsdlEnumType> enumTypeMap = edmProvider.getEdmWebConfig().getEnumTypeMap();
						String propertyType = getNameFromFqn(property.getType());
						OfbizCsdlEnumType enumTypeOfbiz = (OfbizCsdlEnumType) edmProvider.getEnumType(new FullQualifiedName(property.getType()));
						String enumId = getEnumIdFromValue(delegator, enumTypeOfbiz, valueString);
						fieldMap.put(name, enumId);
					} else {
						fieldMap.put(name, valueString.replaceAll("'", ""));
					}
				} else if (paramType.equals("BigDecimal") || paramType.equals("java.math.BigDecimal")) {
					fieldMap.put(name, new BigDecimal(valueString));
				} else if (paramType.equals("Timestamp") || paramType.equals("java.sql.Timestamp")) {
					fieldMap.put(name, Timestamp.valueOf(valueString));
				} else if (paramType.equals("Double")) {
					fieldMap.put(name, new Double(valueString));
				} else if (paramType.equals("Long")) {
					fieldMap.put(name, new Long(valueString));
				} else if (paramType.equals("Date") || paramType.equals("java.sql.Date")) {
					Calendar calendar = (GregorianCalendar) property.getValue();
					java.sql.Date date = (new java.sql.Date(calendar.getTime().getTime()));
					fieldMap.put(name, date);
				} else if (paramType.equals("Boolean")) {
					fieldMap.put(name, new Boolean(valueString));
				}
			} else {
				fieldMap.put(name, property.getValue());
			}
		}
		// ??????????????????entity??????????????????????????????navigationBinding??????????????????
		List<Link> navigationBindings = entityToWrite.getNavigationBindings();
		for (Link navigationBinding:navigationBindings) {
			String bindingLink = navigationBinding.getBindingLink();
			if (bindingLink == null) { // ??????navigation???collection????????????????????????
				continue;
			}
			UriResourceEntitySet entitySetResource = null;
			try {
				entitySetResource = odata.createUriHelper().parseEntityId(serviceMetadata.getEdm(), bindingLink, null);
			} catch (DeserializerException e) {
				throw new ODataApplicationException(bindingLink + " is not a valid entity-Id",
						HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
			}
			Map<String, Object> keyMap = Util.uriParametersToMap(entitySetResource.getKeyPredicates(), entitySetResource.getEntityType());
			Iterator<Map.Entry<String, Object>> it = keyMap.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, Object> entry = it.next();
				if (modelService.getParam(entry.getKey()) != null) {
					fieldMap.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return fieldMap;
	}

	public static Map<String, Object> entityToMap(Delegator delegator, OfbizAppEdmProvider edmProvider, Entity entity) {
		Map<String, Object> result = new HashMap<>();
		List<Property> properties = entity.getProperties();
		for (Property property : properties) {
			if (property.isCollection() || property.isComplex()) { // ????????????????????????
				continue;
			}
			String name = property.getName();
			Object value = property.getValue();
			if (UtilValidate.isNotEmpty(value)) {
				if (property.getType() != null && property.getType().equals("Edm.Boolean")) {
					if ((boolean) property.getValue()) {
						result.put(name, "Y");
					} else {
						result.put(name, "N");
					}
				} else if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".MaritalStatus")) {
					if ((Integer) property.getValue() == 1) {
						result.put(name, "S");
					} else if ((Integer) property.getValue() == 2) {
						result.put(name, "M");
					} else if ((Integer) property.getValue() == 3) {
						result.put(name, "P");
					} else if ((Integer) property.getValue() == 4) {
						result.put(name, "D");
					} else if ((Integer) property.getValue() == 5) {
						result.put(name, "W");
					}
				} else if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".Gender")) {
					if ((Integer) property.getValue() == 1) {
						result.put(name, "M");
					} else if ((Integer) property.getValue() == 2) {
						result.put(name, "F");
					}
				} else if (property.getValueType().equals(ValueType.ENUM)) {
					try {
						OfbizCsdlEnumType enumTypeOfbiz = (OfbizCsdlEnumType) edmProvider.getEnumType(new FullQualifiedName(property.getType()));
						String enumId = getEnumIdFromValue(delegator, enumTypeOfbiz, value.toString());
						result.put(name, enumId);
					} catch (ODataException | GenericEntityException e) {
						e.printStackTrace();
						// ???????????????????????????????????????????????????
					}
				} else {
					result.put(name, value);
				}
			}
		}
		return result;
	}

	// ???????????????????????????entityToMap?????????????????????????????????delegator???edmProvider?????????
	// ?????????????????????EnumType??????Enumeration????????????????????????ui5???draft?????????
	public static Map<String, Object> entityToMap(Entity entity) {
		Map<String, Object> result = new HashMap<>();
		List<Property> properties = entity.getProperties();
		for (Property property : properties) {
			if (property.isCollection() || property.isComplex()) { // ????????????????????????
				continue;
			}
			String name = property.getName();
			Object value = property.getValue();
			if (UtilValidate.isNotEmpty(value)) {
				if (property.getType() != null && property.getType().equals("Edm.Boolean")) {
					if ((boolean) value) {
						result.put(name, "Y");
					} else {
						result.put(name, "N");
					}
				} else if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".MaritalStatus")) {
					if ((Integer) value == 1) {
						result.put(name, "S");
					} else if ((Integer) value == 2) {
						result.put(name, "M");
					} else if ((Integer) value == 3) {
						result.put(name, "P");
					} else if ((Integer) value == 4) {
						result.put(name, "D");
					} else if ((Integer) value == 5) {
						result.put(name, "W");
					}
				} else if (property.getType() != null && property.getType().equals(OfbizMapOdata.NAMESPACE + ".Gender")) {
					if ((Integer) value == 1) {
						result.put(name, "M");
					} else if ((Integer) value == 2) {
						result.put(name, "F");
					}
				}
			}
			result.put(name, value);
		}
		return result;
	}

	public static String getEnumIdFromValue(Delegator delegator, OfbizCsdlEnumType enumTypeOfbiz, String valueString)
			throws GenericEntityException {
		EntityCondition entityCondition = null;
		EntityCondition enumTypeCondition = enumTypeOfbiz.getEntityCondition();
		String ofbizEntity = enumTypeOfbiz.getOfbizEntity();
		ModelEntity modelEntity = delegator.getModelEntity(ofbizEntity);
		boolean hasSequenceId = modelEntity.getAllFieldNames().contains("sequenceId");
		String pkFieldName = modelEntity.getFirstPkFieldName();
		if (hasSequenceId) {
			if (valueString.length() == 1) {
				valueString = "0" + valueString;
			}
			entityCondition = EntityCondition.makeCondition("sequenceId", valueString);
		}
		if (enumTypeCondition != null) {
			if (entityCondition != null) {
				entityCondition = EntityCondition.makeCondition(enumTypeCondition, EntityJoinOperator.AND, entityCondition);
			} else {
				entityCondition = enumTypeCondition;
			}
		}
		List<GenericValue> enumerations = delegator.findList(enumTypeOfbiz.getOfbizEntity(), entityCondition, null, UtilMisc.toList(pkFieldName), null, false);
		if (UtilValidate.isNotEmpty(enumerations)) {
			GenericValue enumeration;
			if (hasSequenceId) {
				enumeration = EntityUtil.getFirst(enumerations);
			} else {
				enumeration = enumerations.get(Integer.valueOf(valueString) - 1);
			}
			return enumeration.getString(pkFieldName);
		}
		return null;
	}

	public static GenericValue entityToGenericValue(Delegator delegator, Entity entity, EdmEntityType edmEntityType) throws GenericEntityException {
		Map<String, Object> pkMap = Util.retrievePkFromEntity(entity, edmEntityType);
		return delegator.findOne(edmEntityType.getName(), pkMap, true);
	}

	public static GenericValue entityToGenericValue(Delegator delegator, OdataOfbizEntity entity, String entityName) throws GenericEntityException {
		if (entity.getGenericValue() != null) {
			return entity.getGenericValue();
		}
		Map<String, Object> pkMap = Util.retrievePkFromEntity(delegator, entityName, entity);
		return delegator.findOne(entityName, pkMap, true);
	}

	public static GenericValue entityToGenericValue(Delegator delegator, Entity entity) {
		String entityName = getEntityNameFromEntity(entity);
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		GenericValue genericValue = GenericValue.create(modelEntity);
		List<Property> properties = entity.getProperties();
		for (Property property:properties) {
			String name = property.getName();
			Object value = property.getValue();
			genericValue.put(name, value);
		}
		return genericValue;
	}

	public static String getEntityNameFromEntity(Entity entity) {
		String entityType = entity.getType(); // e.g. com.dpbird.ProductFeature
		return getNameFromFqn(entityType);
	}
	
	public static String getNameFromFqn(String fqn) {
		String name = fqn.substring(fqn.lastIndexOf('.') + 1);
		return name;
	}

	public static Map<String, Object> parametersToMap(Map<String, Parameter> parameters) {
		Map<String, Object> paramMap = new HashMap<>();
		if (UtilValidate.isEmpty(parameters)) {
			return paramMap;
		}
		Iterator<Map.Entry<String, Parameter>> it = parameters.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Parameter> parameterEntry = it.next();
			paramMap.put(parameterEntry.getKey(), parameterEntry.getValue().getValue());
		}
		return paramMap;
	}
	public static Map<String, Object> uriParametersToMap(List<UriParameter> keyParams, EdmEntityType edmEntityType)
			throws OfbizODataException {
		Map<String, Object> pk = new HashMap<String, Object>();
		if (edmEntityType == null) { // ???????????????null?????????????????????????????????????????????????????????
			for (UriParameter keyPredicate : keyParams) {
				String regexp = "\'";
				String keyText = keyPredicate.getText();
				keyText = keyText.replaceAll(regexp, "");
				pk.put(keyPredicate.getName(), keyText);
			}
		} else {
			for (UriParameter keyPredicate : keyParams) {
				EdmProperty edmProperty = edmEntityType.getStructuralProperty(keyPredicate.getName());
				Object valueObj = Util.readPrimitiveValue(edmProperty, keyPredicate.getText());
				pk.put(keyPredicate.getName(), valueObj);
			}
		}
		return pk;
	}

	public static Map<String, Object> getNavigationKey(EdmEntityType startEdmEntityType, List<UriParameter> keyParams, String navigationName, OfbizAppEdmProvider edmProvider, Delegator delegator)
			throws OfbizODataException {
		Map<String, Object> resultPK = new HashMap<>();
		Map<String, Object> startEntityPK = uriParametersToMap(keyParams, startEdmEntityType);
		OfbizCsdlEntityType startCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(startEdmEntityType.getFullQualifiedName());
		try {
			GenericValue startGen = delegator.findOne(startCsdlEntityType.getOfbizEntity(), startEntityPK, false);
			if (UtilValidate.isNotEmpty(startGen)) {
				GenericValue relatedOne = startGen.getRelatedOne(navigationName, false);
				 if (UtilValidate.isNotEmpty(relatedOne)) {
					 resultPK = relatedOne.getPrimaryKey();
				 }
			}
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}
		return resultPK;
	}

	public static Map<String, Object> uriParametersToMap(List<UriParameter> uriParameters, EdmFunction edmFunction) throws OfbizODataException {
		Map<String, Object> paramMap = new HashMap<String, Object>();
		if (edmFunction == null) {
			throw new OfbizODataException("Can't process uriParameters without edmFunction!");
		} else {
			for (UriParameter uriParameter : uriParameters) {
				EdmParameter edmParameter = edmFunction.getParameter(uriParameter.getName());
				Object valueObj = Util.readPrimitiveValue(edmParameter, uriParameter.getText());
				paramMap.put(uriParameter.getName(), valueObj);
			}
		}
		return paramMap;
	}
	public static Map<String, Object> formatAlias(List<AliasQueryOption> aliases) {
		Map<String, Object> aliasMap = new HashMap<>();
		if (UtilValidate.isNotEmpty(aliases)) {
			for (AliasQueryOption alias : aliases) {
				aliasMap.put(alias.getName(), alias.getText());
			}
		}
		return aliasMap;
	}

	public static Map<String, Object> uriParametersToMap(List<UriParameter> uriParameters, EdmFunction edmFunction, List<AliasQueryOption> aliases) throws OfbizODataException {
		Map<String, Object> paramMap = new HashMap<String, Object>();
		Map<String, Object> aliasMap = new HashMap<>();
		if (UtilValidate.isNotEmpty(aliases)) {
			aliasMap = formatAlias(aliases);
		}
		if (edmFunction != null) {
			for (UriParameter uriParameter : uriParameters) {
				String name = uriParameter.getName();
				String text = uriParameter.getText();
				EdmParameter edmParameter = edmFunction.getParameter(name);
				//?????????ParamAlias?????????alias?????????
				if (text == null && aliasMap.containsKey(uriParameter.getAlias())) {
					text = (String) aliasMap.get(uriParameter.getAlias());
				}
				Object valueObj = Util.readPrimitiveValue(edmParameter, text);
				paramMap.put(uriParameter.getName(), valueObj);
			}
		} else {
			throw new OfbizODataException("Can't process uriParameters without edmFunction!");
		}
		return paramMap;
	}

	public static List<String> retrieveSimpleOrderByOption(OrderByOption orderByOption) throws ODataException {
		List<String> orderBy = new ArrayList<String>();
		if (orderByOption != null) {
			List<OrderByItem> orderItemList = orderByOption.getOrders();
			for (OrderByItem orderByItem : orderItemList) {
				Expression expression = orderByItem.getExpression();
				if (expression instanceof Member) {
					UriInfoResource resourcePath = ((Member) expression).getResourcePath();
					if (resourcePath.getUriResourceParts().size() == 1) {
						UriResource uriResource = resourcePath.getUriResourceParts().get(0);
						if (uriResource instanceof UriResourcePrimitiveProperty) {
							EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
							final String sortPropertyName = edmProperty.getName();
							if (orderByItem.isDescending()) {
								orderBy.add("-" + sortPropertyName + " NULLS LAST");
							} else {
								orderBy.add(sortPropertyName + " NULLS LAST");
							}
						}
					} else { // ?????????????????????navigation???orderby
						Debug.logInfo("orderByOption has more than 2 segments, will be ignored!", module);
						throw new ODataException("Only support one segment order by");
					}
				}
			}
		}
		return orderBy;
	}

	public static GenericValue convertToTargetGenericValue(Delegator delegator, GenericValue sourceGenericValue,
														   ModelEntity targetModelEntity) {
		List<String> targetEntityFieldNames = targetModelEntity.getAllFieldNames();
		List<String> automaticFieldNames = targetModelEntity.getAutomaticFieldNames(); // lastUpdatedStamp, lastUpdatedTxStamp, createdStamp, createdTxStamp
		Map<String, Object> targetFields = new HashMap<String, Object>();
		for (String targetEntityFieldName:targetEntityFieldNames) {
			if (automaticFieldNames.contains(targetEntityFieldName)) {
				continue;
			}
			targetFields.put(targetEntityFieldName, sourceGenericValue.get(targetEntityFieldName));
		}
		GenericValue targetGenericValue = GenericValue.create(delegator, targetModelEntity, targetFields);
		return targetGenericValue;
	}

	public static Map<String, Object> getRelationKeyMap(Delegator delegator, String entityName,
														String relationName, OdataOfbizEntity entity, String relEntityName) {
		Map<String, Object> result = new HashMap<String, Object>();
		boolean isOdataView = OdataView.isOdataView(delegator, entityName);

		GenericValue genericValue = null;
		try {
			genericValue = entityToGenericValue(delegator, entity, entityName);
		} catch (GenericEntityException e) {
			e.printStackTrace();
		}

		if(isOdataView) {
		   List<Map<String, Object>> viewKeyMaps = OdataView.getRelationKeyMap(delegator, entityName, relationName);
		   for (Map<String, Object> map : viewKeyMaps) {
			   String fieldName = (String) map.get("fieldName");
			   String relFieldName = (String) map.get("relFieldName");
			   Object fieldValue = genericValue.get(fieldName);
			   result.put(relFieldName, fieldValue);
		   }
		}else {
			ModelEntity modelEntity = delegator.getModelEntity(entityName);
			ModelRelation modelRelation = modelEntity.getRelation(relationName);
			if (modelRelation == null) {
				if (relEntityName != null) {
					modelRelation = modelEntity.getRelation(relEntityName);
				}
			}
			if (modelRelation != null) {
				List<ModelKeyMap> modelKeyMaps = modelRelation.getKeyMaps();
				for (ModelKeyMap modelKeyMap:modelKeyMaps) {
					String fieldName = modelKeyMap.getFieldName();
					String relFieldName = modelKeyMap.getRelFieldName();
					Object fieldValue = genericValue.get(fieldName);
					result.put(relFieldName, fieldValue);
				}
			}
		}
		return result;
	}

	public static File getFile(byte[] bfile, String filePath,String fileName) throws IOException {
		BufferedOutputStream bos = null;
		FileOutputStream fos = null;
		File file = null;
		try {
			File dir = new File(filePath);
			if(!dir.exists()&&dir.isDirectory()){//??????????????????????????????
				dir.mkdirs();
			}
			file = new File(filePath+System.getProperty("file.separator")+fileName);
			fos = new FileOutputStream(file);
			bos = new BufferedOutputStream(fos);
			bos.write(bfile);
			return file;
		} finally {
			if (bos != null) {
				try {
					bos.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	public static URI getNextUri(String rawRequestUri, Integer count) {
		int topIndex = rawRequestUri.indexOf("$top");
		if (topIndex < 0) {
			return null;
		}
		int skipIndex = rawRequestUri.indexOf("$skip");
		// TODO: ??????next
		return null;
	}

	// ????????????Entity???????????????Enumeration???StatusItem?????????????????????Map???key???????????????value???relation???title
	public static Map<String, String> getEntityAutoEnum(ModelEntity modelEntity) {
		List<ModelRelation> relationOneList = modelEntity.getRelationsOneList();
		Map<String, String> enumMap = new HashMap<String, String>();
		for (ModelRelation relationOne:relationOneList) {
			String relEntityName = relationOne.getRelEntityName();
			String title = relationOne.getTitle();
			if (relEntityName.equals("Enumeration") || relEntityName.equals("StatusItem")) {
				List<ModelKeyMap> modelKeyMaps = relationOne.getKeyMaps();
				ModelKeyMap modelKeyMap = modelKeyMaps.get(0);
				String fkFieldName = modelKeyMap.getFieldName();
				enumMap.put(fkFieldName, title + relEntityName);
			} else {
				continue;
			}
		}
		return enumMap;
	}

	// ??????entity?????????????????????????????????????????????Enumeration?????????
	public static Set<String> getEntityFk(ModelEntity modelEntity) {
		Set<String> fkFieldNames = new HashSet<String>();
		List<ModelRelation> relationOneList = modelEntity.getRelationsOneList();
		for (ModelRelation relationOne : relationOneList) {
			String relEntityName = relationOne.getRelEntityName();
			if (UtilValidate.isEmpty(relationOne.getFkName())) {
				continue;
			}
			if (relEntityName.equals("Enumeration") || relEntityName.equals("StatusItem")) {
				continue;
			} else {
				List<ModelKeyMap> modelKeyMaps = relationOne.getKeyMaps();
				ModelKeyMap modelKeyMap = modelKeyMaps.get(0);
				String fkFieldName = modelKeyMap.getFieldName();
				fkFieldNames.add(fkFieldName);
			}
		}
		return fkFieldNames;
	}

	/**
	 *
	 * @param delegator
	 * ????????????????????????????????????
	 */
	public static GenericValue getSystemUser(Delegator delegator) throws GenericEntityException {
		//????????????????????????ofbiz service
		GenericValue admin = delegator.findOne("UserLogin", true, UtilMisc.toMap("userLoginId", "system"));
		return admin;
	}

	public static Object readPrimitiveValue(EdmParameter edmParameter, String value)
			throws OfbizODataException {
		if (value == null) {
			return null;
		}
		try {
			if (value.startsWith("'") && value.endsWith("'")) {
				value = value.substring(1,value.length()-1);
			}
			EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) edmParameter.getType();
			if (edmPrimitiveType instanceof EdmEnumType) {
				EdmEnumType edmEnumType = (EdmEnumType) edmPrimitiveType;
				Object resultValue = null;
				return edmEnumType.fromUriLiteral(value);
			}
			Class<?> javaClass = getJavaClassForPrimitiveType(edmParameter, edmPrimitiveType);
			if (javaClass.getName().equals("java.lang.Boolean")) {
				if (value.equals("true")) {
					value = "Y";
				} else if (value.equals("false")) {
					value = "N";
				}
				return value;
			}
			if (edmParameter.isCollection()) {
				return strToList(value, edmParameter);
			}
			return edmPrimitiveType.valueOfString(value, edmParameter.isNullable(),
					edmParameter.getMaxLength(), edmParameter.getPrecision(), edmParameter.getScale(),
					true, javaClass);
		} catch (EdmPrimitiveTypeException e) {
			e.printStackTrace();
			throw new OfbizODataException("Invalid value: " + value + " for property: "
					+ edmParameter.getName());
		}
	}

	public static Object readPrimitiveValue(EdmProperty edmProperty, String value)
		      throws OfbizODataException {
		if (value == null) {
		  return null;
		}
		try {
			if (value.startsWith("'") && value.endsWith("'")) {
				value = value.substring(1,value.length()-1);
			}
			EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) edmProperty.getType();
			if (edmPrimitiveType instanceof EdmEnumType) {
				EdmEnumType edmEnumType = (EdmEnumType) edmPrimitiveType;
				Object resultValue = null;
				return edmEnumType.fromUriLiteral(value);
			//			  resultValue = edmEnumType.valueOfString(value, edmProperty.isNullable(),
			//			      edmProperty.getMaxLength(), edmProperty.getPrecision(), edmProperty.getScale(),
			//			      edmProperty.isUnicode(), java.lang.Integer.class);
			//			  return resultValue;
			}
			Class<?> javaClass = getJavaClassForPrimitiveType(edmProperty, edmPrimitiveType);
			if (javaClass.getName().equals("java.lang.Boolean")) {
				if (value.equals("true")) {
					value = "Y";
				} else if (value.equals("false")) {
					value = "N";
				}
				return value;
			}
			return edmPrimitiveType.valueOfString(value, edmProperty.isNullable(),
			  edmProperty.getMaxLength(), edmProperty.getPrecision(), edmProperty.getScale(),
			  edmProperty.isUnicode(), javaClass);
		} catch (EdmPrimitiveTypeException e) {
			e.printStackTrace();
		  throw new OfbizODataException(OfbizMapOdata.ERROR_CODE_ONE, "Invalid value: " + value + " for property: "
		          + edmProperty.getName());
	    }
	}

	/**
	 * String???List
	 * @param str ??????["Carrots", "Ginger", "Oranges"]
	 */
	public static List<Object> strToList(String str, EdmParameter parameter) {
		if (str.startsWith("[") && str.endsWith("]")) {
			str = str.substring(1, str.length() - 1);
		}
		str = str.replace("\"", "");
		str = str.replace(" ", "");
		List<Object> list = new ArrayList<>(Arrays.asList(str.split(",")));
		String paramType = parameter.getType().getName();
		if (paramType.contains("Decimal")) {
			//Decimal
			List<Object> decimalList = new ArrayList<>();
			for (Object obj : list) {
				decimalList.add(new BigDecimal(obj.toString()));
			}
			return decimalList;
		} else if (paramType.contains("DateTimeOffset")) {
			//Timestamp
			List<Object> timestampList = new ArrayList<>();
			for (Object obj : list) {
				//2020-03-06T09:59:35.533Z  >>>  2020-03-06 09:59:35.533
				String timeString = obj.toString().replace("T", " ").replace("Z", "");
				timestampList.add(Timestamp.valueOf(timeString));
			}
			return timestampList;
		} else {
			return list;
		}
	}

	static Class<?> getJavaClassForPrimitiveType(EdmMappable edmMappable, EdmPrimitiveType edmPrimitiveType) {
	  Class<?> javaClass = null;
	  if (edmMappable.getMapping() != null && edmMappable.getMapping().getMappedJavaClass() != null) {
		  javaClass = edmMappable.getMapping().getMappedJavaClass();
	  } else {
		  javaClass = edmPrimitiveType.getDefaultType();
	  }
	  return javaClass;
	}

	public static String firstUpperCase(String theString) {
		String newString = theString;
		if (Character.isLowerCase(theString.charAt(0))) {
			newString = (new StringBuilder()).append(Character.toUpperCase(theString.charAt(0))).append(theString.substring(1)).toString();
		}
		return newString;
	}

	public static String firstLowerCase(String theString) {
		String newString = theString;
		if (Character.isUpperCase(theString.charAt(0))) {
			newString = (new StringBuilder()).append(Character.toLowerCase(theString.charAt(0))).append(theString.substring(1)).toString();
		}
		return newString;
	}

	public static FullQualifiedName getFullQualifiedNameByParamName(String paramName) {
		// ??????????????????????????????????????????entityName????????????????????????
		String entityName = firstUpperCase(paramName);
		return new FullQualifiedName(OfbizMapOdata.NAMESPACE, entityName);
	}

	public static OdataOfbizEntity mergeEntity(OdataOfbizEntity destEntity, Entity sourceEntity)
			throws OfbizODataException {
		List<Property> properties = sourceEntity.getProperties();
		for (Property property:properties) {
			String propertyName = property.getName();
			Property responseProperty = destEntity.getProperty(propertyName);
			if (responseProperty == null) {
				destEntity.addProperty(property);
			} else {
				responseProperty.setValue(property.getValueType(), property.getValue());
			}
		}
		return destEntity;
	}

	public static GenericValue updateGenericValue(GenericValue destGenericValue, Map<String, Object> srcValue) {
		Set<Map.Entry<String, Object>> srcValueEntrySet = srcValue.entrySet();
		Iterator<Map.Entry<String, Object>> it = srcValueEntrySet.iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			destGenericValue.set(entry.getKey(), entry.getValue());
		}
		return destGenericValue;
	}

	public static Map<String, Object> prepareServiceParameters(ModelService modelService, Map<String, Object> valueMap) {
		Map<String, Object> serviceParameters = new HashMap<>();
		Set<String> paramNames = modelService.getAllParamNames();
		Set<Map.Entry<String, Object>> srcValueEntrySet = valueMap.entrySet();
		Iterator<Map.Entry<String, Object>> it = srcValueEntrySet.iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			if (paramNames.contains(entry.getKey())) {
				serviceParameters.put(entry.getKey(), entry.getValue());
			}
		}
		return serviceParameters;
	}

	public static Map<String ,Object> prepareEntityFindFieldMap(ModelEntity modelEntity, Map<String, Object> valueMap) {
		Map<String, Object> fieldMap = new HashMap<>();
		List<String> fieldNames = modelEntity.getAllFieldNames();
		for (String fieldName:fieldNames) {
			if (valueMap.get(fieldName) != null) {
				fieldMap.put(fieldName, valueMap.get(fieldName));
			}
		}
		return fieldMap;
	}

	public static Locale getLocale(HttpServletRequest request) {
		Locale localeObject = request != null ? request.getLocale() : null;
		if (localeObject != null) {
			return localeObject;
		}
		HttpSession session = request.getSession();
		// check session first, should override all if anything set there
		localeObject = session != null ? (Locale) session.getAttribute("locale") : null;

		// next see if the userLogin has a value
		if (localeObject == null) {
			Map<?, ?> userLogin = (Map<?, ?>) session.getAttribute("userLogin");
			if (userLogin == null) {
				userLogin = (Map<?,?>) session.getAttribute("autoUserLogin");
			}

			if (userLogin != null) {
				localeObject = (Locale) userLogin.get("lastLocale");
			}
		}

		// no user locale? before global default try appDefaultLocale if specified
		if (localeObject == null) {
			localeObject = Locale.SIMPLIFIED_CHINESE;
		}
		return localeObject;
	}

	public static Map<String, Object> getKeyMapFromEntity(OfbizAppEdmProvider edmProvider, Entity entity) {
		Map<String, Object> keyMap = new HashMap<>();
		String entityTypeFqn = entity.getType();
		CsdlEntityType csdlEntityType = null;
		try {
			csdlEntityType = edmProvider.getEntityType(new FullQualifiedName(entityTypeFqn));
		} catch (OfbizODataException e) {
			e.printStackTrace();
			return keyMap;
		}
		List<CsdlPropertyRef> csdlPropertyRefs = csdlEntityType.getKey();
		for (CsdlPropertyRef csdlPropertyRef:csdlPropertyRefs) {
			String keyName = csdlPropertyRef.getName();
			keyMap.put(keyName, entity.getProperty(keyName).getValue());
		}
		return keyMap;
	}

	public static String generateDraftUUID() {
		UUID uuid = UUID.randomUUID();
		return uuid.toString();
	}
	public static String getSapContextIdFromRequest(ODataRequest oDataRequest) {
		return oDataRequest.getHeader("SAP-ContextId");
	}
	public static void setSapContextIdToResponse(ODataResponse oDataResponse, String sapContextId) {
		oDataResponse.setHeader("SAP-ContextId", sapContextId);
	}

	public static Map<String, Object> keyMapFromId(Delegator delegator, OfbizCsdlEntityType csdlEntityType, String id) {
		Map<String, Object> keyMap = new HashMap<>();
		String entityName = csdlEntityType.getOfbizEntity();
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		List<ModelField> pkFields =  modelEntity.getPkFields();
		List<String> pkValues = StringUtil.split(id, ",");
		if (pkValues.size() < 2) {
			return keyMap;
		}
		for (String pkValue:pkValues) {
			List<String> keyValue = StringUtil.split(pkValue, "=");
			if (keyValue.size() != 2) {
				return keyMap;
			}
			String key = keyValue.get(0);
			String value = keyValue.get(1);
			ModelField modelField = modelEntity.getField(key);
			if (modelField.getType().equals("date-time")) {
				long timeLong = Long.valueOf(value);
				Timestamp timestamp = new Timestamp(timeLong);
				keyMap.put(key, timestamp);
			} else if (modelField.getType().equals("fixed-point")) {
				BigDecimal bigDecimal = new BigDecimal(value);
				keyMap.put(key, bigDecimal);
			}else {
				keyMap.put(key, value);
			}
		}
		return keyMap;
	}

	// Please reference StringUtil.strToMap method
	public static Map<String, Object> StringToKeyMap(String str, String delim, boolean trim, String pairsSeparator, ModelEntity modelEntity) {
		if (UtilValidate.isEmpty(str)) {
			return null;
		}
		Map<String, Object> keyMap = new HashMap<>();
		List<String> elements = StringUtil.split(str, delim);
		pairsSeparator = pairsSeparator == null ? "=" : pairsSeparator;

		for (String s: elements) {

			List<String> e = StringUtil.split(s, pairsSeparator);
			if (e.size() != 2) {
				continue;
			}
			String name = e.get(0);
			String value = e.get(1);
			if (trim) {
				if (name != null) {
					name = name.trim();
				}
				if (value != null) {
					value = value.trim();
				}
			}

			if (value != null && name != null) {
				ModelField modelField = modelEntity.getField(name);
				String fieldType = modelField.getType();
				if (fieldType.equals("date-time")) {
					Timestamp dateTime = null;
					try {
						dateTime = new Timestamp(Long.valueOf(value));
					} catch (Exception e1) {
						dateTime = null;
					}
					keyMap.put(name, dateTime);
				} else if (fieldType.equals("fixed-point") || fieldType.equals("currency-precise") || fieldType.equals("currency-amount")) {
					BigDecimal bigDecimal = new BigDecimal(value);
					keyMap.put(name, bigDecimal);
				} else {
					keyMap.put(name, value);
				}
			}
		}
		return keyMap;
	}

	public static Map<String, Object> makeupFromDate(Map<String, Object> keyMap, ModelEntity modelEntity) {
		if (UtilValidate.isEmpty(keyMap)) {
			return keyMap;
		}
		List<String> pkFieldNames = modelEntity.getPkFieldNames();
		for (String pkFieldName:pkFieldNames) {
			if (pkFieldName.contains("romDate")) {
				if (keyMap.get(pkFieldName) == null) {
					keyMap.put(pkFieldName, UtilDateTime.nowTimestamp());
				}
			}
		}
		return keyMap;
	}

	// Please reference to StringUtil.mapToStr method
	public static String mapToStr(Map<? extends Object, ? extends Object> map) {
		if (map == null) {
			return null;
		}
		StringBuilder buf = new StringBuilder();
		boolean first = true;

		for (Map.Entry<? extends Object, ? extends Object> entry: map.entrySet()) {
			Object key = entry.getKey();
			Object value = entry.getValue();

			if (!(key instanceof String) || (!(value instanceof String) && !(value instanceof Timestamp) && !(value instanceof BigDecimal))) {
				continue;
			}
			String encodedName = null;
			try {
				encodedName = URLEncoder.encode((String) key, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				Debug.logError(e, module);
			}
			String encodedValue = null;
			try {
				if (value instanceof String) {
					encodedValue = URLEncoder.encode((String) value, "UTF-8");
				} else if (value instanceof Timestamp) {
					encodedValue = ((Timestamp) value).getTime() + "";
				} else {
					encodedValue = value + "";
				}
			} catch (UnsupportedEncodingException e) {
				Debug.logError(e, module);
			}

			if (first) {
				first = false;
			} else {
				buf.append(",");
			}

			buf.append(encodedName);
			buf.append("=");
			buf.append(encodedValue);
		}
		return buf.toString();
	}

	public static List<GenericValue> getSkipTopList(EntityListIterator iter, int skip, int top) throws GenericEntityException {
		int startIndex = skip + 1;
//		int endIndex = skip + top;

		List<GenericValue> dataItems = iter.getPartialList(startIndex, top);
		return dataItems;
	}

	public static Entity mapToEntity(OfbizCsdlEntityType csdlEntityType, Map<String, Object> fieldMap) {
		Entity entity = new Entity();
		entity.setType(csdlEntityType.getFullQualifiedNameString());
		List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
		for (CsdlProperty csdlProperty:csdlProperties) {
			String propertyName = csdlProperty.getName();
			if (fieldMap.get(propertyName) == null) {
				continue;
			}
			Object propertyValue = fieldMap.get(propertyName);
			FullQualifiedName propertyFqn = csdlProperty.getTypeAsFQNObject();
			Property property = new Property(propertyFqn.getFullQualifiedNameAsString(), propertyName, ValueType.PRIMITIVE, propertyValue);
			entity.addProperty(property);
		}
		return entity;
	}

	public static String getRelatedFieldName(Delegator delegator, String entityName,
											OfbizCsdlNavigationProperty csdlNavigationProperty,
											String fieldName) {
		List<String> relations = csdlNavigationProperty.getRelAlias().getRelations();
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		String originFieldName = fieldName;
		int i = 0;
		ModelEntity theModelEntity = modelEntity;
		for (String relation:relations) {
			ModelRelation modelRelation = theModelEntity.getRelation(relation);
			ModelKeyMap modelKeyMap = modelRelation.findKeyMap(originFieldName);
			if (modelKeyMap == null) {
				return null;
			}
			String relFieldName = modelKeyMap.getRelFieldName();
			i++;
			if (i >= relations.size()) {
				return relFieldName;
			}
			originFieldName = relFieldName;
			theModelEntity = delegator.getModelEntity(modelRelation.getRelEntityName());
		}
		return null;
	}

	public static Map<String, Object> getRelatedFieldMap (Delegator delegator, String entityName,
														  OfbizCsdlNavigationProperty csdlNavigationProperty,
														  Map<String, Object> fieldMap) {
		Map<String, Object> relatedFieldMap = new HashMap<>();
		Set<Map.Entry<String, Object>> entrySet = fieldMap.entrySet();
		Iterator<Map.Entry<String, Object>> it = entrySet.iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			String relatedFieldName = getRelatedFieldName(delegator, entityName, csdlNavigationProperty, entry.getKey());
			relatedFieldMap.put(relatedFieldName, entry.getValue());
		}
		return relatedFieldMap;
	}

	public static Map<String, Object> getRelatedConditionMap(OfbizCsdlNavigationProperty csdlNavigationProperty) {
		EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
		List<String> relations = relAlias.getRelations();
		Map<String, Map<String, Object>> relationsFieldMap = relAlias.getRelationsFieldMap();
		String lastRelation = relations.get(relations.size() - 1);
		return relationsFieldMap.get(lastRelation);
	}

	public static String getEntityActionService(String entityName, String action, Delegator delegator) throws OfbizODataException {
		String property = "service." + entityName + "." + action;
		String serviceName = EntityUtilProperties.getPropertyValue(ODATA_PROPERTIES, property, delegator);
		if (UtilValidate.isEmpty(serviceName)) {
			Map<String, String> entityActions = OfbizMapOdata.CREATE_SERVICE_MAP.get(entityName);
			if (UtilValidate.isNotEmpty(entityActions)) {
				serviceName = entityActions.get(action);
			}
		}
		//????????????service
		if (UtilValidate.isEmpty(serviceName)) {
			throw new OfbizODataException(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode() + "", "No defined service found: " + action + entityName);
		}
		return serviceName;
	}

	public static boolean isAggregate(ApplyOption applyOption) {
		for (ApplyItem applyItem : applyOption.getApplyItems()) {
			if (applyItem.getKind().equals(ApplyItem.Kind.AGGREGATE)) {
				return true;
			}
			if (applyItem instanceof GroupBy) {
				GroupBy groupBy = (GroupBy) applyItem;
				if (groupBy.getApplyOption() != null) {
					return isAggregate(groupBy.getApplyOption());
				}
			}
		}
		return false;
	}


	public static boolean isAggregateCount(AggregateExpression aggregateExpression) {
		List<UriResource> path = aggregateExpression.getPath();
		if (UtilValidate.isNotEmpty(path)) {
			return "$count".equals(path.get(0).getSegmentValue());
		}
		return false;
	}

	//???Apply?????????Aggregate
	public static Aggregate getApplyAggregate(ApplyOption applyOption) {
		Aggregate aggregate = null;
		for (ApplyItem applyItem : applyOption.getApplyItems()) {
			if (applyItem.getKind().equals(ApplyItem.Kind.AGGREGATE)) {
				aggregate = (Aggregate) applyItem;
			} else if (applyItem.getKind().equals(ApplyItem.Kind.GROUP_BY)) {
				GroupBy groupBy = (GroupBy) applyItem;
				aggregate = getApplyAggregate(groupBy.getApplyOption());
			}
		}
		return aggregate;
	}

	public static boolean isGroupBy(ApplyOption applyOption) {
		for (ApplyItem applyItem : applyOption.getApplyItems()) {
			if (applyItem.getKind().equals(ApplyItem.Kind.GROUP_BY) || applyItem instanceof GroupBy) {
				return true;
			}
		}
		return false;
	}

	//??????modelEntity??????????????????????????????
	public static List<String> getRelationKey(ModelEntity modelEntity, String relationName) {
		ModelRelation relation = modelEntity.getRelation(relationName);
		List<ModelKeyMap> keyMaps = relation.getKeyMaps();
		List<String> relKey = new ArrayList<>();
		for (ModelKeyMap keyMap : keyMaps) {
			relKey.add(keyMap.getFieldName());
		}
		return relKey;
	}

	//?????????relation?????????Condition
	public static EntityCondition relationToCondition(GenericValue genericValue, String relationName) {
		ModelEntity modelEntity = genericValue.getModelEntity();
		ModelRelation relation = modelEntity.getRelation(relationName);
		if (relation == null) {
			return null;
		}
		List<ModelKeyMap> keyMaps = relation.getKeyMaps();
		EntityCondition entityCondition = null;
		for (ModelKeyMap keyMap : keyMaps) {
			Object fieldValue = genericValue.get(keyMap.getFieldName());
			if (fieldValue == null) {
				//?????????, ????????????????????????
				return null;
			}
			EntityCondition condition = EntityCondition.makeCondition(keyMap.getRelFieldName(), fieldValue);
			entityCondition = appendCondition(entityCondition, condition);
		}
		return entityCondition;
	}

	public static Entity addEntitySetConditionToEntity(Delegator delegator, OfbizCsdlEntitySet navigationBindingEntitySet, Entity entityToWrite, GenericValue userLogin) {
//		Map<String, Object> conditionMap = navigationBindingEntitySet.getConditionMap();
		Map<String, Object> conditionMap = parseConditionMap(navigationBindingEntitySet.getConditionStr(), userLogin);
		if (UtilValidate.isEmpty(conditionMap)) {
			return entityToWrite;
		}
		Set<Map.Entry<String, Object>> entrySet = conditionMap.entrySet();
		Iterator<Map.Entry<String, Object>> it = entrySet.iterator();
		while (it.hasNext()) {
			Map.Entry<String, Object> entry = it.next();
			if (entityToWrite.getProperty(entry.getKey()) == null) {
				Property property = new Property(null, entry.getKey(), ValueType.PRIMITIVE, entry.getValue());
				entityToWrite.addProperty(property);
			}
		}
		return entityToWrite;
	}

	public static EntityCondition parseEntityCondition(String conditionStr, GenericValue userLogin) {
		if (conditionStr == null) {
			return null;
		}
		boolean isAnd = false;
		boolean isAllEqual = true;
		String[] expressions;
		EntityJoinOperator operator;
		if (conditionStr.contains(" or ")) {
			expressions = conditionStr.split(" or ");
			operator = EntityJoinOperator.OR;
		} else {
			isAnd = true;
			operator = EntityJoinOperator.AND;
			expressions = conditionStr.split(" and ");
		}
		List<EntityCondition> entityConditionList = new ArrayList<>();
		for (String expression:expressions) {
			expression = expression.trim();
			EntityCondition entityCondition = null;
			if (expression.contains("=")) {
				String[] keyValue = expression.split("=");
				if (UtilValidate.isNotEmpty(keyValue)) {
					String key = keyValue[0].trim();
					String valueStr = keyValue[1].trim();
					String realValue = parseVariable(valueStr, userLogin);
					entityCondition = EntityCondition.makeCondition(key, realValue);
				}
			} else if (expression.contains(" not in ")) {
				String[] keyValue = expression.split(" not in ");
				String key = keyValue[0].trim();
				String valueStr = keyValue[1].trim();
				valueStr = valueStr.substring(1, valueStr.length() - 1);
				List<String> values = StringUtil.split(valueStr, ",");
				entityCondition = EntityCondition.makeCondition(key, EntityJoinOperator.NOT_IN, values);
				isAllEqual = false;
			} else if (expression.contains(" in ")) {
				String[] keyValue = expression.split(" in ");
				String key = keyValue[0].trim();
				String valueStr = keyValue[1].trim();
				valueStr = valueStr.substring(1, valueStr.length() - 1);
				List<String> values = StringUtil.split(valueStr, ",");
				entityCondition = EntityCondition.makeCondition(key, EntityJoinOperator.IN, values);
				isAllEqual = false;
			}
			if (entityCondition != null) {
				entityConditionList.add(entityCondition);
			}
		}
		return new EntityConditionList<>(entityConditionList, operator);
	}

	private static String parseVariable(String valueStr, GenericValue object) {
		if (valueStr.contains("${")) {
			if (object == null) {
				return valueStr;
			}
			String valueField = valueStr.substring(valueStr.indexOf('.') + 1, valueStr.length() - 1);
			return object.getString(valueField);
		} else {
			return valueStr;
		}
	}

	public static Map<String, Object> parseConditionMap(String conditionStr, GenericValue userLogin) {
		Map<String, Object> conditionMap = new HashMap<>();
		if (conditionStr == null) {
			return conditionMap;
		}
		String[] expressions;
		if (conditionStr.contains(" or ")) {
			return conditionMap;
		} else {
			expressions = conditionStr.split(" and ");
		}
		for (String expression:expressions) {
			expression = expression.trim();
			if (expression.contains("=")) {
				String[] keyValue = expression.split("=");
				if (UtilValidate.isNotEmpty(keyValue)) {
					String key = keyValue[0].trim();
					String valueStr = keyValue[1].trim();
					String realValue = parseVariable(valueStr, userLogin);
					conditionMap.put(key, realValue);
				}
			} else {
				conditionMap.clear();
				return conditionMap;
			}
		}
		return conditionMap;
	}

	public static EntityCondition appendCondition(EntityCondition originalCondition, EntityCondition laterCondition) {
		if (originalCondition == null) {
			return laterCondition;
		} else {
			return EntityCondition.makeCondition(originalCondition, laterCondition);
		}
	}

	/**
	 * ????????????????????????resourceParts ?????????????????????entitySet??????navigation
	 *
	 * @return ???????????????Entity???Navigation
	 */
	public static Map<String, Object> getEntityAndNavigationFromResource(List<UriResource> resourceParts, Map<String, Object> odataContext) throws OfbizODataException {
		//first EntitySet
		UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
		EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
		Map<String, Object> keyMap = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntitySet.getEntityType());
		if (resourceParts.size() == 1) {
			return UtilMisc.toMap("edmEntitySet", edmEntitySet, "keyMap", keyMap);
		}
		//first Navigation
		UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
		EdmNavigationProperty edmNavigation = uriResourceNavigation.getProperty();
		EdmEntitySet navigationEntitySet = Util.getNavigationTargetEntitySet(edmEntitySet, edmNavigation);
		List<UriParameter> navKeyPredicates = uriResourceNavigation.getKeyPredicates();
		Map<String, Object> navKeyMap = null;
		if (UtilValidate.isNotEmpty(navKeyPredicates)) {
			navKeyMap = Util.uriParametersToMap(navKeyPredicates, navigationEntitySet.getEntityType());
		}

		//?????????????????????Navigation ???????????????????????????
		for (int i = 2; i < resourceParts.size(); i++) {
			//??????navigation PrimaryKey
			if (navKeyMap == null) {
				Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet,
						"edmNavigationProperty", edmNavigation);
				Map<String, QueryOption> queryParams = UtilMisc.toMap("keyMap", keyMap);
				OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
				OdataOfbizEntity relatedEntity = (OdataOfbizEntity) ofbizOdataReader.getRelatedEntity(keyMap, edmNavigation, null);
				navKeyMap = relatedEntity.getKeyMap();
			}
			if (UtilValidate.isEmpty(navKeyMap)) {
				return null;
			}

			//??????????????????Navigation?????????????????????????????????????????????Navigation???????????????Navigation??????EntitySet
			edmEntitySet = navigationEntitySet;
			keyMap = navKeyMap;
			UriResourceNavigation nextUriNavigation = (UriResourceNavigation) resourceParts.get(i);
			edmNavigation = nextUriNavigation.getProperty();
			navigationEntitySet = Util.getNavigationTargetEntitySet(navigationEntitySet, edmNavigation);
			navKeyMap = null;
			if (UtilValidate.isNotEmpty(nextUriNavigation.getKeyPredicates())) {
				navKeyMap = Util.uriParametersToMap(nextUriNavigation.getKeyPredicates(), navigationEntitySet.getEntityType());
			}
		}
		return UtilMisc.toMap("edmEntitySet", edmEntitySet, "keyMap", keyMap, "edmNavigation", edmNavigation, "navKeyMap", navKeyMap);

	}
	//???????????????????????????????????????
	public static EntityCondition getGenericValuesQueryCond(EntityCollection entityCollection, boolean addPrefix) {
		List<GenericValue> genericValueList = new ArrayList<>();
		for (Entity entity : entityCollection) {
			OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
			genericValueList.add(ofbizEntity.getGenericValue());
		}
		return getGenericValuesQueryCond(genericValueList, addPrefix);
	}
	//???????????????????????????????????????
	public static EntityCondition getGenericValuesQueryCond(List<GenericValue> genericValues, boolean addPrefix) {
		if (UtilValidate.isEmpty(genericValues)) {
			return null;
		}
		StringBuilder querySql = new StringBuilder();
		ModelEntity modelEntity = genericValues.get(0).getModelEntity();
		List<ModelField> pkFields = modelEntity.getPkFields();
		List<String> pkNames = new ArrayList<>();
		String prefix = addPrefix ? modelEntity.getEntityName() + "." : "";
		for (ModelField modelField : pkFields) {
			pkNames.add(prefix + modelField.getColName());
		}
		String fieldStr = pkNames.toString().replace("[", "(").replace("]", ")");
		querySql.append(fieldStr); //columns
		querySql.append(" IN ");

		List<String> pkValues = new ArrayList<>();
		for (GenericValue genericValue : genericValues) {
			List<String> pkValue = new ArrayList<>();
			for (ModelField modelField : pkFields) {
				String value = genericValue.get(modelField.getName()).toString();
				pkValue.add("'" + value + "'");
			}
			pkValues.add(pkValue.toString());
		}
		String valueStr = pkValues.toString().replaceAll("\\[", "(").replace("]", ")");
		querySql.append(valueStr); //values
		return EntityCondition.makeConditionWhere(querySql.toString());
	}

}
