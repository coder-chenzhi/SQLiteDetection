# RAT-TRAP
A Tool for Automated Optimization of Resource Inefficient Database Writes for Mobile Applications

This is a fork of [RAT-TRAP](https://github.com/USC-SQL/RAT-TRAP/tree/master/source_code/SQLiteDetection) with following minor fixes. 
- All dependencies are managed by maven (pom.xml) now.
- Add some comments.
- Reformat code.

## Changes to dependencies
- commons-math.jar: This is a third-party library from Apache, which is used to perform statistical tests.
I add the official artifact to pom.xml.
- commons-bcel.jar: This is a third-party library from Apache, which is used to parse java bytecode.
I add the official artifact to pom.xml.
- soot-trunk.jar: This is a third-party library from Apache, which is used to perform static analysis for Java.
I add the official artifact to pom.xml.

- pixy.jar: This is a third-party library, which is used to parse PHP source code. 
However, there is no official release in maven central repository. 
I add the local jar files to pom.xml.
[Homepage](https://github.com/oliverklee/pixy)

- grappa.jar: This is a third-party library from AT&T, which is used to parse .dot files to get graphs.
The official version is not included in maven central repository. There are some forked versions in github,
but major revisions have been made  to these versions and not compatible to our code.
I add the local jar files to pom.xml.

- graphs.jar: This is a second-party library written by the same author, 
which is used to define some common graph structure. I have added the source code to this projects,
because some comments in the source code are helpful to understand the code. 
[Homepage](https://github.com/USC-SQL/graphs)

- android.jar: This is android runtime jar, which is only useful when analyzing android apps. 


