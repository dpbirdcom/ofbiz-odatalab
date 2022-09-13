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