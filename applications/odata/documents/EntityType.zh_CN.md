# EntityType
EntityType是OData里最基础的对象，也是经常被许多人认为就应该直接对应于数据库的表。但是我们要知道，
OData是面向客户端的数据表达，而数据库其实是面向服务端的数据表达，这两者之间是不同的。

Property是EntityType里最常用的标签，也可以对应地理解为数据库表的字段。
Property的Type（也好比是数据库字段类型）可以是Edm.Boolean、Edm.String、
Edm.Int64、Edm.Double、Edm.Decimal、Edm.Date、Edm.DateTimeOffset等，这些
都是程序世界经常看到的原始类型。Type还可以是其它的EntityType，Type可以是EnumType，
Type可以是ComplexType。EnumType和ComplexType今后文章中会介绍。更有趣的是，Type可以
是Collection，也就是某种类型的数组。

Key代表这个EntityType的主键，里面引用到了某一个或多个Property

NavigationProperty是当前EntityType关联到另外一个EntityType，可以关联一个，
也可以关联多个（在Type里用Collection表示）。

```xml
<Schema>
    <EntityType Name="ProductType">
        <Key>
            <PropertyRef Name="productTypeId"/>
        </Key>
        <Property Name="productTypeId" Type="Edm.String"/>
        <Property Name="description" Type="Edm.String"/>
    </EntityType>
    <EntityType Name="ProductPrice">
        <Key>
            <PropertyRef Name="productId"/>
            <PropertyRef Name="currencyUomId"/>
        </Key>
        <Property Name="productId" Type="Edm.String"/>
        <Property Name="currencyUomId" Type="Edm.String"/>        
    </EntityType>
    <EntityType Name="ProductCategory">
        <Key>
            <PropertyRef Name="productCategoryId"/>
        </Key>
        <Property Name="productCategoryId" Type="Edm.String"/>
        <Property Name="productCategoryName" Type="Edm.String"/>
    </EntityType>
    <EntityType Name="Product">
        <Key>
            <PropertyRef Name="productId"/>
        </Key>
        <Property Name="productId" Type="Edm.String"/>
        <Property Name="productName" Type="Edm.String"/>
        <Property Name="productType" Type="com.dpbird.ProductType"/>
        <Property Name="primaryProductCategoryId" Type="Edm.String"/>
        <Property Name="productTags" Type="Collection(Edm.String)"/>
        <NavigationProperty Name="PrimaryProductCategory" Type="com.dpbird.ProductCategory">
            <ReferentialConstraint Property="primaryProductCategoryId" ReferencedProperty="productCategoryId"/>
        </NavigationProperty>
        <NavigationProperty Name="ProductPrice" Type="Collection(com.dpbird.ProductPrice)"/>
    </EntityType>
    ...
</Schema>
```
上述例子中，PrimaryProductCategory这个NavigationProperty，是非Collection，也就是
Product只能通过PrimaryProductCategory对应到一个ProductCategory对象。ProductPrice这个
NavigationProperty是Collection的，Product能够通过ProductPrice对应到多个ProductPrice对象。
productType这个Property的Type是另外一个EntityType，ProductType，注意这里另外的一个EntityType是full name。
productTas这个Property的Type是个Collection，也就是一个字符串的数组。