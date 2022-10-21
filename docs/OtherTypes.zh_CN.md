## Other Types ##
# EnumType
EnumType是在OData里定义枚举类型，枚举值是通过子元素Element来表达。

```xml
<Schema>
    <EnumType Name="Gender">
        <Member Name="M" Value="1"/>
        <Member Name="F" Value="2"/>
    </EnumType>
    <EntityType Name="Person">
        <Key>
            <PropertyRef Name="partyId"/>
        </Key>
        <Property Name="partyId" Type="Edm.String"/>
        <Property Name="firstName" Type="Edm.String"/>
        <Property Name="lastName" Type="Edm.String"/>
        <Property Name="gender" Type="com.dpbird.Gender"/>
    </EntityType>
    ...
</Schema>
```
上述例子中，定义了Gender这个EnumType。在Person定义中，引用了Gender。
http://server/odata.svc/People('admin')
```json
{
    "@odata.context": "http://server/odata.svc/$metadata#People/$entity",
    "@odata.metadataEtag": "1663051319573",
    "@odata.etag": "1663050393692",
    "partyId": "admin",
    "firstName": "THE",
    "lastName": "ADMINISTRATOR",
    "gender": "M",
}
```

# ComplexType
如果需要用到一些复杂的数据类型，可以定义ComplexType。如果把EntityType看作是数据库的表，那ComplexType可以看作Java Bean。
```xml
<Schema>
    <ComplexType Name="TestObjectOne">
        <Property Name="testObjectOneId" Type="Edm.String" Nullable="false"/>
        <Property Name="amount" Type="Edm.Decimal" Nullable="false"/>
        <Property Name="testDate" Type="Edm.DateTimeOffset"/>
    </ComplexType>
    <Action Name="testImportActionComplex">
        <Parameter Name="partyId" Type="Edm.String"/>
        <Parameter Name="otherParam" Type="Edm.String"/>
        <ReturnType Type="com.dpbird.TestObjectOne"/>
    </Action>
    ...
</Schema>
```
POST http://server/odata.svc/testImportActionComplex
```json
{
  "partyId": "admin",
  "otherParam": "9999"
}
```
返回结果
```json
{
  "@odata.context":"$metadata#com.dpbird.TestObjectOne",
  "@odata.metadataEtag":"1663051319573",
  "testObjectOneId":"10030",
  "amount":10,
  "testDate":"2022-09-13T07:17:36.718Z"
}
```
以上只是一个简单的例子，实际项目中，EnumType和ComplexType，可以用在EntityType的Property，Function和Action的Parameter，Function和Action
的ReturnType，也可以用在ComplexType的Property中。
