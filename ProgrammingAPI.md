This document introduces directions for embedded uses.

## Compiling and executing queries ##

Running a XML query is fairly easy.
```
InputStream input = ..  ; // a query as stream
XQueryProcessor proc = new XQueryProcessor();
// #1 compile a query
XQueryModule module = proc.parse(input);
// #2 execute the compiled expression using ``pull'' mode
Sequence<? extends Item> result = proc.execute(module);
for(Item item : result) {// Note that every Sequence instances implement Iterable. 
    /* do something with item. */
    ..
}
```

## List of examples ##
  * [Example1.java](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/main/test/java/example/Example1.java) - introduces pull/push mode with simple examples
  * [XQTSTestBase](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/main/test/java/xqts/XQTSTestBase.java) - contains full-fledged (and also complex) usage of XBird API