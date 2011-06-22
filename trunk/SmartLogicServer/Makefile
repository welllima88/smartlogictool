BINPATH = bin
JPATH = scuba.jar:.
JFLAGS = -g
JC = javac
JAR_CMD = jar cfm
JAR_MANIFEST = MANIFEST.MF
JAR_NAME = SmartLogicServer.jar
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) -d $(BINPATH) -cp $(JPATH) $*.java

CLASSES = \
        SmartLogicServer.java \

default: jar

jar: classes
	cd $(BINPATH); $(JAR_CMD) ../SmartLogicServer.jar $(JAR_MANIFEST) *.class net;

classes: $(CLASSES:.java=.class) \

clean:
	$(RM) bin/*.class
	rm -rf bin/net

