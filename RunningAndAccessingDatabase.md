## Introduction ##

The XBird engine has the following three facilities: XQuery query (file/stream)  processor, client/server XML native database and embedded XML database.

This document introduces a way to run the XML database server and lectures how to access the database in client/server fashion. XBird uses RMI as the internal protocol of remote access, and hence, the embedded access achieved in the same way.

## Preparation ##

Download [xbird-xx-src.zip](http://code.google.com/p/xbird/downloads/list) and unzip it. We assume the decompressed directory as $XBIRD\_HOME in this document.

Then, put [xbird-open-xx.jar](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/target/) into the target directory in $XBIRD\_HOME.

## Configuring the Server ##

To appear.

## Running the Server ##

#### a) Running server by a command line ####

On the $XBIRD\_HOME/bin directory, the following command starts the database server.
```
-- on windows
$ server.bat 
-- on unix
$ ./server.sh
```

#### b) Managing a server instance in System Tray ####

#### c) Running a server in user program ####

## Accessing to the Server ##

```
XQEngine engine = new XQEngineClient("//knuth.naist.jp:1099/xbird/srv-01");
QueryRequest request1 = new QueryRequest("1+2", ReturnType.REMOTE_SEQUENCE);
Object result1 = engine.execute(request1);
RemoteSequence remoteSequence = (RemoteSequence) result1;

IFocus<Item> focus = remoteSequence.iterator();
Assert.assertEquals(new XInteger(3), focus.next());
Assert.assertFalse(focus.hasNext());
```

XBird supports three reply patterns: **response**, **callback**, and **poll**.
The **response** reply pattern is used by the default.

Please refer [Request class](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/main/src/java/xbird/engine/Request.java) for details.

## Running Examples ##
  * [XQEngineClientTest.java](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/main/test/java/scenario/engine/XQEngineClientTest.java) - _contains several examples for a client._