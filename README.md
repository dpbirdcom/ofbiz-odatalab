# ODataLab GBMS
ODataLab is an OData service built on Apache OFBiz

## Quick Start
+ Prepare OFBiz<br>./gradlew cleanAll
+ Load seed data<br>./gradlew "ofbiz --load-data readers=seed,ext"
+ Load test data if you just test<br>./gradlew "ofbiz --load-data readers=test"
+ Load jindi data if you working on jindi project<br>./gradlew "ofbiz --load-data readers=jindi"
+ Load jingdong data if you working on jingdong project<br>./gradlew "ofbiz --load-data readers=jingdong"
+ Start OFBiz<br>./gradlew ofbiz
+ Open your browser to visit OData metadata<br>http://localhost:8080/gbms/control/odataAppSvc/gbms/$metadata
