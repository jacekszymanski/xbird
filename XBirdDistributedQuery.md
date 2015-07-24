XBird/D introduces a distributed query extention to XQuery called XBird Distributed Query, _BDQ_ for short.

## Syntax ##

```
   BDQExpr ::= "execute at" [ VarRef "in" ] Expr "{" Expr "}"
```

## Usage ##

### Configuring a service ###

The distiributed query processor, XBird/D in short, is configurable through [xbird.properties](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/main/conf/xbird/config/xbird.properties).

```
# The port number used by XBird/D.
# This effects host:port as appears in the later.
xbird.rmi.registry.local.port=1099

# The identifying name of the services: name(,name)*
# caution: name should not start with slash.
xbird.rmi.engine.name=xbird/srv-01
```

**Note:** _You can override the settings by putting xbird.properties on the directory where System.getProperty("user.dir") specifies._

### Starting a service ###

Just run a server as in [this document](http://code.google.com/p/xbird/wiki/RunningAndAccessingDatabase) where the remote query executed.

We assume here that the serive _xbird/srv-01_ is run at port 1099 on the host _knuth.naist.jp_.

## Example Query ##

```
declare variable $remote-endpoint := "//knuth.naist.jp:1099/xbird/srv-01";
declare function local:remote-eval($colname) 
{
  execute at $remote-endpoint {
	for $a in fn:collection($colname)/site/closed_auctions/closed_auction
	where $a/price/text() >= 40
	return $a
  }/price
};
local:remote-eval("/repos/xmark1.xml")
```

Nesting distributed queries as in [mapreduce1.xq](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/examples/distributed/mapreduce1.xq) can also be accepted.

## Reference ##
  * [Makoto Yui, Jun Miyazaki, Shunsuke Uemura and Hirokazu Kato. ``XBird/D: Distributed and Parallel XQuery Processing using Remote Proxy'', In Proc. ACM SAC (DTTA track), pp. 1003-1007, 2008.](http://jp.citeulike.org/user/myui/article/2810257)