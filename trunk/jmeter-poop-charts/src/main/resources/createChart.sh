if [ $# -ne 2 ]
then
 echo "Usage: $0 basedir chartname" 
 exit 1
fi
classpath="C:/Program Files/Java/jdk1.6.0_13/jre/lib/charsets.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/deploy.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/javaws.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/jce.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/jsse.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/management-agent.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/plugin.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/resources.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/rt.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/ext/dnsns.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/ext/localedata.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/ext/sunjce_provider.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/ext/sunmscapi.jar;C:/Program Files/Java/jdk1.6.0_13/jre/lib/ext/sunpkcs11.jar;C:/perftest-reporting/target/classes;C:/Documents and Settings/wattss/.m2/repository/commons-logging/commons-logging/1.1/commons-logging-1.1.jar;C:/Documents and Settings/wattss/.m2/repository/log4j/log4j/1.2.15/log4j-1.2.15.jar;C:/Documents and Settings/wattss/.m2/repository/logkit/logkit/1.0.1/logkit-1.0.1.jar;C:/Documents and Settings/wattss/.m2/repository/avalon-framework/avalon-framework/4.1.3/avalon-framework-4.1.3.jar;C:/Documents and Settings/wattss/.m2/repository/javax/servlet/servlet-api/2.3/servlet-api-2.3.jar;C:/Documents and Settings/wattss/.m2/repository/javax/mail/mail/1.4/mail-1.4.jar;C:/Documents and Settings/wattss/.m2/repository/javax/activation/activation/1.1/activation-1.1.jar;C:/Documents and Settings/wattss/.m2/repository/javax/jms/jms/1.1/jms-1.1.jar;C:/Documents and Settings/wattss/.m2/repository/com/sun/jdmk/jmxtools/1.2.1/jmxtools-1.2.1.jar;C:/Documents and Settings/wattss/.m2/repository/com/sun/jmx/jmxri/1.2.1/jmxri-1.2.1.jar;C:/Documents and Settings/wattss/.m2/repository/org/springframework/spring/2.5.6/spring-2.5.6.jar;C:/Documents and Settings/wattss/.m2/repository/jcommon/jcommon/1.0.16/jcommon-1.0.16.jar;C:/Documents and Settings/wattss/.m2/repository/jfree/jfreechart/1.0.13/jfreechart-1.0.13.jar;C:/Documents and Settings/wattss/.m2/repository/xml-apis/xml-apis/1.3.02/xml-apis-1.3.02.jar;C:/Documents and Settings/wattss/.m2/repository/opencsv/opencsv/1.8/opencsv-1.8.jar"
class=com.poopware.graph.ThroughPutOverTime
baseDir=$1
chartName=$2
chartFolder=$baseDir/charts
csvFile=$baseDir/*-responses.csv
args="-CHART_NAME $chartName -INPUT_CSV $csvFile -OUTPUT_FOLDER $chartFolder -AVOID_RAMP -TIME_END_MS 240000 -INTERVAL_SIZE 2000"
#C:/Program Files/Java/jdk1.6.0_13/bin/
java="java"
$java -classpath "$classpath" $class $args
