package  com.dpbird.odata;

import com.dpbird.odata.edm.*;
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
import org.apache.ofbiz.entity.*;
import org.apache.ofbiz.entity.condition.*;
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
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
import java.util.stream.Collectors;

public class Util {

	public static final String module = Util.class.getName();
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	public static final String DATE_TIME_FORMAT_GMT = "yyyy-MM-dd'T'HH:mm:ss.SSS Z";
	public static final String ODATA_PROPERTIES = "odata.properties";
	public static final String ODATA_LAB_PROPERTIES = "odatalab.properties";
//	private static ResourceBundleMapWrapper uiLabelMap = null;
	private static Map<Locale, ResourceBundleMapWrapper> uiLabelLocaleMap = new HashMap<>();

	/*
	 * @Description:单个对象的所有键值
	 * @Author: S
	 * @Date: 2019/7/30 11:23 AM
	 * @Param: [obj]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 */
	public static Map<String, Object> getKeyAndValue(Object obj) {
		Map<String, Object> map = new HashMap<String, Object>();
		// 得到类对象
		Class userCla = (Class) obj.getClass();
		/* 得到类中的所有属性集合 */
		Field[] fs = userCla.getDeclaredFields();
		for (int i = 0; i < fs.length; i++) {
			Field f = fs[i];
			f.setAccessible(true); // 设置些属性是可以访问的
			Object val = new Object();
			try {
				val = f.get(obj);
				// 得到此属性的值
				map.put(f.getName(), val);// 设置键值
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}

			/*
			 * String type = f.getType().toString();//得到此属性的类型 if
			 * (type.endsWith("String")) {
			 * System.out.println(f.getType()+"\t是String"); f.set(obj,"12") ;
			 * //给属性设值 }else if(type.endsWith("int") ||
			 * type.endsWith("Integer")){
			 * System.out.println(f.getType()+"\t是int"); f.set(obj,12) ; //给属性设值
			 * }else{ System.out.println(f.getType()+"\t"); }
			 */

		}
		// Debug.logInfo("单个对象的所有键值:"+map.toString(),module);
		return map;
	}

	/**
	 * 获取对象的所有键值，包含父类
	 * @param obj
	 * @return
	 */
	public static Map<String, Object> objectFieldValueMap(Object obj, boolean includeSuper) {
		Map<String, Object> map = new HashMap<String, Object>();
		List<Field> fieldList = new ArrayList<>();
		// 得到类对象
		Class tempClass = (Class) obj.getClass();
		while (tempClass != null) {//当父类为null的时候说明到达了最上层的父类(Object类).
		      fieldList.addAll(Arrays.asList(tempClass .getDeclaredFields()));
		      if (includeSuper) {
				  tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
			  } else {
		      	tempClass = null;
			  }
		}
		for (Field f : fieldList) {
			f.setAccessible(true); // 设置些属性是可以访问的
			Object val = new Object();
			try {
				val = f.get(obj);
				// 得到此属性的值
				map.put(f.getName(), val);// 设置键值
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return map;
	}

	/**
	 * 判断object是否为基本类型
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


		//没有指定内容类型,默认是在传头像
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
	 * 向指定URL发送GET方法的请求
	 *
	 * @param url
	 *            发送请求的URL
	 * @param param
	 *            请求参数，请求参数应该是 name1=value1&name2=value2 的形式。
	 * @return URL 所代表远程资源的响应结果
	 */
	public static String sendGet(String url, String param) {
		String result = "";
		BufferedReader in = null;
		try {
			String urlNameString = url + "?" + param;
			URL realUrl = new URL(urlNameString);
			// 打开和URL之间的连接
			URLConnection connection = realUrl.openConnection();
			// 设置通用的请求属性
			connection.setRequestProperty("accept", "*/*");
			connection.setRequestProperty("connection", "Keep-Alive");
			connection.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			// 建立实际的连接
			connection.connect();
			// 获取所有响应头字段
			Map<String, List<String>> map = connection.getHeaderFields();
			// 遍历所有的响应头字段
			for (String key : map.keySet()) {
				System.out.println(key + "--->" + map.get(key));
			}
			// 定义 BufferedReader输入流来读取URL的响应
			in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			System.out.println("发送GET请求出现异常！" + e);
			e.printStackTrace();
		}
		// 使用finally块来关闭输入流
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
	 * 向指定 URL 发送POST方法的请求
	 *
	 * @param url
	 *            发送请求的 URL
	 * @param param
	 *            请求参数，请求参数应该是 name1=value1&name2=value2 的形式。
	 * @return 所代表远程资源的响应结果
	 */
	public static String sendPost(String url, String param) {
		PrintWriter out = null;
		BufferedReader in = null;
		String result = "";
		try {
			URL realUrl = new URL(url);
			// 打开和URL之间的连接
			URLConnection conn = realUrl.openConnection();
			// 设置通用的请求属性
			conn.setRequestProperty("accept", "*/*");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			// 发送POST请求必须设置如下两行
			conn.setDoOutput(true);
			conn.setDoInput(true);
			// 获取URLConnection对象对应的输出流
			out = new PrintWriter(conn.getOutputStream());
			// 发送请求参数
			out.print(param);
			// flush输出流的缓冲
			out.flush();
			// 定义BufferedReader输入流来读取URL的响应
			in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			System.out.println("发送 POST 请求出现异常！" + e);
			e.printStackTrace();
		}
		// 使用finally块来关闭输出流、输入流
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




	//方法：length为产生的位数
	public static String getRandomString(int length){
		//定义一个字符串（A-Z，a-z，0-9）即62位；
		String str="zxcvbnmlkjhgfdsaqwertyuiopQWERTYUIOPASDFGHJKLZXCVBNM1234567890";
		//由Random生成随机数
		Random random=new Random();
		StringBuffer sb=new StringBuffer();
		//长度为几就循环几次
		for(int i=0; i<length; ++i){
			//产生0-61的数字
			int number=random.nextInt(62);
			//将产生的数字通过length次承载到sb中
			sb.append(str.charAt(number));
		}
		//将承载的字符转换成字符串
		return sb.toString();
	}


	/**
	 13      * 获取用户真实IP地址，不使用request.getRemoteAddr();的原因是有可能用户使用了代理软件方式避免真实IP地址,
	 14 *
	 16      * 可是，如果通过了多级反向代理的话，X-Forwarded-For的值并不止一个，而是一串IP值，究竟哪个才是真正的用户端的真实IP呢？
	 17      * 答案是取X-Forwarded-For中第一个非unknown的有效IP字符串。
	 18      *
	 19      * 如：X-Forwarded-For：192.168.1.110, 192.168.1.120, 192.168.1.130,
	 20      * 192.168.1.100
	 21      *
	 22      * 用户真实IP为： 192.168.1.110
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
	 * 签名字符串
	 * @param text 需要签名的字符串
	 * @param key 密钥
	 * @param input_charset 编码格式
	 * @return 签名结果
	 */
	public static String sign(String text, String key, String input_charset) {
		text = text + key;
		return DigestUtils.md5Hex(getContentBytes(text, input_charset));
	}
	/**
	 * 签名字符串
	 * @param text 需要签名的字符串
	 * @param sign 签名结果
	 * @param key 密钥
	 * @param input_charset 编码格式
	 * @return 签名结果
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
			throw new RuntimeException("MD5签名过程中出现错误,指定的编码集不对,您目前指定的编码集是:" + charset);
		}
	}
	/**
	 * 生成6位或10位随机数 param codeLength(多少位)
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
	 * 除去数组中的空值和签名参数
	 * @param sArray 签名参数组
	 * @return 去掉空值与签名参数后的新签名参数组
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
	 * 把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
	 * @param params 需要排序并参与字符拼接的参数组
	 * @return 拼接后字符串
	 */
	public static String createLinkString(Map params) {
		List keys = new ArrayList(params.keySet());
		Collections.sort(keys);
		String prestr = "";
		for (int i = 0; i < keys.size(); i++) {
			String key = (String) keys.get(i);
			String value = (String) params.get(key);
			if (i == keys.size() - 1) {// 拼接时，不包括最后一个&字符
				prestr = prestr + key + "=" + value;
			} else {
				prestr = prestr + key + "=" + value + "&";
			}
		}
		return prestr;
	}
	/**
	 * httpRequest
	 * @param requestUrl 请求地址
	 * @param requestMethod 请求方法
	 * @param outputStr 参数
	 */
	public static String httpRequest(String requestUrl,String requestMethod,String outputStr){
		// 创建SSLContext
		StringBuffer buffer=null;
		try{
			URL url = new URL(requestUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod(requestMethod);
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.connect();
			//往服务器端写内容
			if(null !=outputStr){
				OutputStream os=conn.getOutputStream();
				os.write(outputStr.getBytes("utf-8"));
				os.close();
			}
			// 读取服务器端返回的内容
			InputStream is = conn.getInputStream();
			InputStreamReader isr = new InputStreamReader(is, "utf-8");
			BufferedReader br = new BufferedReader(isr);
			buffer = new StringBuffer();
			String line = null;
			while ((line = br.readLine()) != null) {
				buffer.append(line);
			}
		}catch(Exception e){
			//如果在这一步错误，我认为只是一个警告而已，不能说是错误。
			Debug.logWarning(e.getMessage(), module);
		}
		return buffer.toString();
	}

	/**
	 * httpRequest
	 * @param url 请求地址
	 * @param requestMethod 请求方法
	 * @param outputStr 参数
	 */
	public static byte[] httpPost(String url,String requestMethod,String outputStr){
		HttpPost post = new HttpPost(url);
		byte[] result = null;
		try {
			CloseableHttpClient client = HttpClients.createDefault();
			// 设置通用属性
			StringEntity s = new StringEntity(outputStr,"UTF-8");
			s.setContentEncoding("UTF-8");
			s.setContentType("application/json");
			// 设置发送的数据(数据尽量为json格式)
			post.setEntity(s);
			post.addHeader("content-type", "application/json;charset=UTF-8");
			//  获取数据
			HttpResponse res = client.execute(post);
			//  判断网络请求的是否成功，成功的状态码为200
			if (res.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				//  HttpEntity为消息实体，内容是http传送的报文,获取数据
				HttpEntity entity = res.getEntity();
				result = EntityUtils.toByteArray(entity);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally {
			// 释放连接
			post.releaseConnection();
		}
		return result;
	}

	/**
	*@description: 从Plugin的Config下找到所有UiLabel文件并且返回名称列表
	*@author: S
	*@param: path
	*@createTime: 2021-09-271  2:24 下午
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

	// 获取edmEntityType中的外键Map指向edmFkEntityType
	// pkMap是edmFkEntityType的主键Map
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
		// 我们手头有的是entityName指向referencedEntityName的relation，
		// 我们不需要获取edmEntityType指向edmFkEntityType的relation，因为他们的key是相同的，只是刚好反过来
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
	 * 这个方法为了组装正确的服务参数类型
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
		// 如果要创建的entity，其中有外键，需要从navigationBinding里获取并加上
		List<Link> navigationBindings = entityToWrite.getNavigationBindings();
		for (Link navigationBinding:navigationBindings) {
			String bindingLink = navigationBinding.getBindingLink();
			if (bindingLink == null) { // 这个navigation是collection的，没有外键可言
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
			if (property.isCollection() || property.isComplex()) { // 暂时不支持，跳过
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
						// 如果出错，仅打印，先忽略出错的字段
					}
				} else {
					result.put(name, value);
				}
			}
		}
		return result;
	}

	// 这个方法跟另外一个entityToMap的区别是，可以不用带入delegator和edmProvider参数，
	// 但是也就不支持EnumType对应Enumeration表的处理，适用于ui5的draft的处理
	public static Map<String, Object> entityToMap(Entity entity) {
		Map<String, Object> result = new HashMap<>();
		List<Property> properties = entity.getProperties();
		for (Property property : properties) {
			if (property.isCollection() || property.isComplex()) { // 暂时不支持，跳过
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

	public static GenericValue entityToGenericValue(Delegator delegator, OdataOfbizEntity entity, String entityName) throws OfbizODataException {
		if (entity.getGenericValue() != null) {
			return entity.getGenericValue();
		}
		Map<String, Object> pkMap = Util.retrievePkFromEntity(delegator, entityName, entity);
		try {
			return delegator.findOne(entityName, pkMap, true);
		} catch (GenericEntityException e) {
			throw new OfbizODataException(e.getMessage());
		}
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
		if (edmEntityType == null) { // 将来不会为null的，这种字符串处理参数的方式，要被封杀
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
				//如果是ParamAlias，就从alias里取值
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

	public static List<String> retrieveSimpleOrderByOption(OrderByOption orderByOption) throws OfbizODataException {
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
					} else { // 暂时不支持三级navigation的orderby
						Debug.logInfo("orderByOption has more than 2 segments, will be ignored!", module);
						throw new OfbizODataException("Only support one segment order by");
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
		} catch (OfbizODataException e) {
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
			if(!dir.exists()&&dir.isDirectory()){//判断文件目录是否存在
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
		// TODO: 实现next
		return null;
	}

	// 获取一个Entity里所有用到Enumeration和StatusItem的字段，返回的Map，key是字段名，value是relation的title
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

	// 获取entity的所有外键字段，但是不包括指向Enumeration的字段
	public static Set<String> getEntityFk(ModelEntity modelEntity) {
		Set<String> fkFieldNames = new HashSet<>();
		List<ModelRelation> relationOneList = modelEntity.getRelationsOneList();
		for (ModelRelation relationOne : relationOneList) {
			List<ModelKeyMap> modelKeyMaps = relationOne.getKeyMaps();
			ModelKeyMap modelKeyMap = modelKeyMaps.get(0);
			String fkFieldName = modelKeyMap.getFieldName();
			fkFieldNames.add(fkFieldName);
		}
		return fkFieldNames;
	}

	/**
	 *
	 * @param delegator
	 * 获取系统用户执行关键服务
	 */
	public static GenericValue getSystemUser(Delegator delegator) throws GenericEntityException {
		//使用系统用户运行ofbiz service
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
	 * String转List
	 * @param str 格式["Carrots", "Ginger", "Oranges"]
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
		// 把首字符变成大写，因为所有的entityName，首字符都是大写
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
		OdataOfbizEntity entity = new OdataOfbizEntity();
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
			serviceName = EntityUtilProperties.getPropertyValue(ODATA_LAB_PROPERTIES, property, delegator);
		}
		if (UtilValidate.isEmpty(serviceName)) {
			Map<String, String> entityActions = OfbizMapOdata.CREATE_SERVICE_MAP.get(entityName);
			if (UtilValidate.isNotEmpty(entityActions)) {
				serviceName = entityActions.get(action);
			}
		}
		//没有定义service
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

	public static boolean isMultistageApply(List<UriResource> uriResourceParts, Map<String, QueryOption> queryOptions) {
		if (UtilValidate.isEmpty(uriResourceParts) || UtilValidate.isEmpty(queryOptions) || !queryOptions.containsKey("applyOption")) {
			return false;
		}
		return uriResourceParts.size() > 1 && isGroupBy((ApplyOption) queryOptions.get("applyOption"));
	}

	//从Apply中获取Aggregate
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

	//获取modelEntity跟一个实体的关联字段
	public static List<String> getRelationKey(ModelEntity modelEntity, String relationName) {
		ModelRelation relation = modelEntity.getRelation(relationName);
		List<ModelKeyMap> keyMaps = relation.getKeyMaps();
		List<String> relKey = new ArrayList<>();
		for (ModelKeyMap keyMap : keyMaps) {
			relKey.add(keyMap.getFieldName());
		}
		return relKey;
	}

	//将一个relation转化为Condition
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
				//没有值, 说明没有关联数据
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
					String realValue = "null".equals(valueStr) ?
							null : parseVariable(valueStr, userLogin);
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

	//获取一个数据集合的查询条件
	public static EntityCondition getEntityCollectionQueryCond(EntityCollection entityCollection) {
		List<GenericValue> genericValueList = new ArrayList<>();
		for (Entity entity : entityCollection) {
			OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
			genericValueList.add(ofbizEntity.getGenericValue());
		}
		return getGenericValuesQueryCond(genericValueList);
	}
	//获取一个数据集合的查询条件
	public static EntityCondition getGenericValuesQueryCond(List<GenericValue> genericValues) {
		if (UtilValidate.isEmpty(genericValues)) {
			return null;
		}
		ModelEntity modelEntity = genericValues.get(0).getModelEntity();
		List<ModelField> pkFields = modelEntity.getPkFields();
		if (pkFields.size() == 1) {
			//单主键使用in
			String pkName = modelEntity.getFirstPkFieldName();
			List<Object> primaryKeyList = EntityUtil.getFieldListFromEntityList(genericValues, pkName, true);
			return EntityCondition.makeCondition(pkName, EntityOperator.IN, primaryKeyList);
		} else {
			List<EntityCondition> conditionList = new ArrayList<>();
			//多主键使用多个and拼接
			for (GenericValue genericValue : genericValues) {
				List<EntityCondition> currentConditions = new ArrayList<>();
				for (ModelField pkField : pkFields) {
					String pkName = pkField.getName();
					currentConditions.add(EntityCondition.makeCondition(pkName, EntityOperator.EQUALS, genericValue.get(pkField.getName())));
				}
				conditionList.add(EntityCondition.makeCondition(currentConditions, EntityOperator.AND));
			}
			return EntityCondition.makeCondition(conditionList, EntityOperator.OR);
		}
	}

	/**
	 * @param entityName 实体名称
	 * @param groupBy	分组字段
	 * @param functionField	计算字段
	 * @param function  函数类型
	 * @param byAnd     查询条件
	 */
	public static List<GenericValue> findByFunction(Delegator delegator, String entityName, String groupBy,
													String functionField, String function, Map<String, Object> byAnd) throws GenericEntityException {
		DynamicViewEntity dynamicViewEntity = new DynamicViewEntity();
		dynamicViewEntity.addMemberEntity(entityName, entityName);
		if (groupBy != null) {
			dynamicViewEntity.addAlias(entityName, groupBy, groupBy, null, false, true, null);
		}
		if (functionField != null) {
			dynamicViewEntity.addAlias(entityName, functionField, functionField, null, false, false, function);
		}
		return EntityQuery.use(delegator).from(dynamicViewEntity).where(byAnd).queryList();
	}

	/**
	 * 使用lastUpdatedStamp生成一个ETag
	 */
	public static String getGenericValueETag(GenericValue genericValue) {
		String odataEtag = null;
		if (genericValue.containsKey("lastUpdatedStamp")) {
			Timestamp lastUpdatedStamp = genericValue.getTimestamp("lastUpdatedStamp");
			if (UtilValidate.isNotEmpty(lastUpdatedStamp)) {
				odataEtag = String.valueOf(lastUpdatedStamp.getTime());
			}
		}
		return odataEtag;
	}

	/**
	 * 解析一个selectOption 返回解析每个Item后的字段集合
	 */
	public static List<String> getSelectOptionFields(SelectOption selectOption) {
		if (selectOption == null) {
			return null;
		}
		return selectOption.getSelectItems().stream()
				.map(item -> item.getResourcePath().getUriResourceParts().get(0).getSegmentValue()).collect(Collectors.toList());
	}

	/**
	 * 解析searchOption 获取search所有的词汇集合
	 */
	public static List<String> getSearchOptionWords(SearchOption searchOption) {
		if (searchOption == null) {
			return null;
		}
		String[] searchTextArr;
		if (searchOption.getText() != null) {
			searchTextArr = searchOption.getText().replaceAll("\"", "").split(" ");
		} else {
			//如果是嵌套的search 没有文本只有表达式,需要自己解析
			String searchExpressionText = searchOption.getSearchExpression().toString();
			//remove { } " ' AND
			searchTextArr = searchExpressionText.replaceAll("\\{", "")
					.replaceAll("}", "")
					.replaceAll("\"", "")
					.replaceAll("'", "")
					.split(" AND ");
		}
		return new ArrayList<>(Arrays.asList(searchTextArr));
	}

	/** 判断是否是语义化实体 */
	public static boolean isSemanticEntity(Delegator delegator, EdmEntityType edmEntityType, OfbizAppEdmProvider edmProvider) {
		try {
			OfbizCsdlEntityType entityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
			ModelEntity modelEntity = delegator.getModelReader().getModelEntity(entityType.getOfbizEntity());
			return UtilValidate.isEmpty(modelEntity);
		} catch (OfbizODataException | GenericEntityException e) {
			return true;
		}
	}

	/**
	 * 对EntityCollection中的实体数据进行分页
	 */
	public static void pageEntityCollection(EntityCollection entityCollection, int skip, int top) {
		List<Entity> entities = entityCollection.getEntities();
		List<Entity> entitiesPage;
		if ((skip + top) > entities.size()) {
			if (entities.size() <= skip) {
				return;
			}
			entitiesPage = new ArrayList<>(entities.subList(skip, entities.size()));
		} else {
			entitiesPage = new ArrayList<>(entities.subList(skip, skip + top));
		}
		entityCollection.getEntities().clear();
		entityCollection.getEntities().addAll(entitiesPage);
	}

	/**
	 * 对现有的数据集进行filter
	 */
	public static void filterEntityCollection(EntityCollection entityCollection, FilterOption filterOption,
														  OfbizCsdlEntityType csdlEntityType, OfbizAppEdmProvider edmProvider,
														  Delegator delegator, LocalDispatcher dispatcher, GenericValue userLogin, Locale locale) throws OfbizODataException {
		if (UtilValidate.isEmpty(entityCollection) || entityCollection.getEntities().size() == 0) {
			return;
		}
		try {
			// 1.获取现有数据集的主键 只在这个范围之内做查询
			ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
			EntityCondition primaryKeyCond = getEntityCollectionQueryCond(entityCollection);

			// 2. 解析FilterOption表达式
			OdataExpressionVisitor expressionVisitor = new OdataExpressionVisitor(csdlEntityType, delegator, dispatcher, userLogin, edmProvider);
			DynamicViewEntity dynamicViewEntity = null;
			EntityCondition entityCondition = (EntityCondition) filterOption.getExpression().accept(expressionVisitor);
			if (expressionVisitor.getDynamicViewHolder() != null) {
				dynamicViewEntity = expressionVisitor.getDynamicViewHolder().getDynamicViewEntity();
			}

			// 3.使用主键范围的条件和filter的条件进行查询
			entityCondition = appendCondition(primaryKeyCond, entityCondition);
			EntityQuery entityQuery = EntityQuery.use(delegator).where(entityCondition);
			List<GenericValue> queryResult;
			if (dynamicViewEntity == null) {
				queryResult = entityQuery.from(modelEntity.getEntityName()).cache().queryList();
			} else {
				printDynamicView(dynamicViewEntity, entityCondition, module);
				queryResult = entityQuery.from(dynamicViewEntity).queryList();
				queryResult = queryResult.stream().map(result -> convertToTargetGenericValue(delegator, result, modelEntity))
						.collect(Collectors.toList());
			}
			List<String> resultPrimaryKeys = queryResult.stream().map(GenericEntity::getPkShortValueString).collect(Collectors.toList());

			// 4.根据查询结果移除数据，只保留查询结果中依然存在的数据
			Iterator<Entity> iterator = entityCollection.getEntities().iterator();
			while (iterator.hasNext()) {
				OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) iterator.next();
				String pkValue = ofbizEntity.getGenericValue().getPkShortValueString();
				if (!resultPrimaryKeys.contains(pkValue)) {
					iterator.remove();
				}
			}
		} catch (ODataException | GenericEntityException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Log打印DynamicView
	 */
	public static void printDynamicView(DynamicViewEntity dynamicViewEntity, EntityCondition entityCondition, String module) {
		try {
			String dynamicViewXml = dynamicViewEntity.getViewXml(dynamicViewEntity.getEntityName());
			Debug.logInfo(dynamicViewXml, module);
			if (entityCondition != null) {
				Debug.logInfo(entityCondition.toString(), module);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
