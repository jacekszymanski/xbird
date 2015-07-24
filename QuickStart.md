# Introduction #

This document intended to provide information for users to run XBird without having a lot of tedious reading.

# Preparation #

Please ensure to prepare Java 5 or later, preferably from Sun, for the runtime environment.
If not installed yet, please download JDK (or JRE) from [here](http://java.sun.com/javase) and install it.

# Download #

Get the recent binary from [this svn page](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/target/). Aside from it, the entire source codes can be download from [here](http://code.google.com/p/xbird/downloads/list).

There are three types of binary as follows:
  * xbird-open-xx\_fat.jar -- _Most dependent library are included in one [fat jar](http://fjep.sourceforge.net/) package for ease use._
  * xbird-open-xx.jar -- _The binary of xbird/open itself. It requires dependent libraries as shown in [this page](LibraryDependencyListing.md)._
  * xbird-xx.war -- _Deployable [WAR](http://en.wikipedia.org/wiki/WAR_(file_format)) file. It provides a similar functionality, called **xqsp** (XQuery Servlet Pages), to jsp._

The following instructions use xbird-open-xx\_fat.jar.

# Command Line Execution #

An exmaple XML file '[bib.xml](http://xbird.googlecode.com/svn/trunk/xbird-open/main/test/resources/example/bib.xml)' is used for the following discussion.
_You can find other XQuery query exmaples in [example directory](http://code.google.com/p/xbird/source/browse/trunk/xbird-open/examples/)._

We here assume that '[bib.xml](http://xbird.googlecode.com/svn/trunk/xbird-open/main/test/resources/example/bib.xml)' is put on your "C:\".
_Please assume "C:\" as "/tmp" if you are unix user._

## execute with fat jar ##

```
 $ java -jar xbird-open-xx_fat.jar -q FILE

 -baseuri URI           # specify implicit baseURI
 -debug                 # Run in debug mode (default=false)
 -encoding ENCODING     # encoding of result XML document (default=UTF-8)
 -help                  # Show help (default=false)
 -o OUTPUT              # output to this file
 -perfmon               # enable performance monitoring (default=false)
 -pp                    # enable XML pretty printting (default=false)
 -pull                  # Execute in Pull mode (default=false)
 -q FILE                # execute query in the specified file
 -t                     # enable timing (default=false)
 -timeout TIME(seconds) # specify timeout in seconds
 -tms                   # enable timing in mill seconds (default=false)
 -wrap                  # Wrap result with dummy root node (default=false)
```

The query below is an exmaple that retrieves books in "[C:\bib.xml](file:///C:/bib.xml)" which title contains "TCP/IP". Please save this query as a file and specify the file path to the above "-q" command line arguments.
```
for $b in fn:doc("file:///C:/bib.xml")/bib/book, 
     $t in $b/title
where fn:contains(fn:string($t), "TCP/IP")
return $b
```

It is also allowed to specify XML files on http protocol as follows:
```
fn:doc("http://xbird.googlecode.com/svn/trunk/xbird-open/main/test/resources/example/bib.xml")
```