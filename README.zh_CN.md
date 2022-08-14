# ODataLab GBMS
ODataLab是一个基于Apache OFBiz的OData服务

## 快速启动
+ 初始化OFBiz<br>./gradlew cleanAll
+ 加载种子数据<br>./gradlew "ofbiz --load-data readers=seed,seed-initial,ext"
+ 如果仅仅测试，加载测试数据<br>./gradlew "ofbiz --load-data readers=test"
+ 如果金地项目，加载金地数据<br>./gradlew "ofbiz --load-data readers=jindi"
+ 如果京东项目，加载京东数据<br>./gradlew "ofbiz --load-data readers=jingdong"
+ 启动OFBiz<br>./gradlew ofbiz
+ 启动浏览器访问OData服务的metadata<br>http://localhost:8080/gbms/control/odataAppSvc/gbms/$metadata
