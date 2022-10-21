# levels
OData的请求，$expand选项可以带上$levels参数，让你想几层就几层。
假设我们想请求一些数据，需要子对象的子对象的子对象......，我们就需要expand expand expand...，这样查询语句就会很长，并且不易读懂。
对此，OData有了$levels的解决方案，也就是告诉OData服务，需要自动expand几层，举例如下：
Metadata
```xml
< EntityType Name="Content">
    …  
    < NavigationProperty Name="SubContent" Type="Collection(com.dpbird.Content)" />
</ EntityType>
```
Content有NavigationProperty名为SubContent，其类型也是Content，这时候如果想查询Content的SubContent的SubContent的SubContent，可以这么写：
`Odata.svc/Contents(‘id’)?$expand=SubContent($expand=SubContent($expand=SubContent))`
上面的查询语句看起来有点眼花。

所以，你也可以写成（PS：看起来赏心悦目多了）：
`Odata.svc/Contents(‘id’)?$expand=SubContent($levels=3)`
要注意，这种指定NavigationProperty的level使用，必须是子对象的类型是一样的。在这个例子中，SubContent的类型和SubContent的SubContent类型是一样的。
还有一种levels的使用场景，就是不管3721，把对象的所有NavigationProperty拉出来几层，这时候就要结合*来使用了，比如：

`Odata.svc/Contents(‘id’)$expand=*($levels=3)`
这时候，不光是SubContent，所有其它的NavigationProperty都会expand出来，并且子对象的子对象，也不论是不是同一个类型，也都会expand出来。
这种请求方式最简单粗暴，但是对于服务器的压力可想而知，拉出来的数据量可能也会远远超出你的想象。建议对于levels结合*的使用，服务端需要有所限制，比如说最多支持几层levels，或者最多支持几个NavigationProperty的expand。
