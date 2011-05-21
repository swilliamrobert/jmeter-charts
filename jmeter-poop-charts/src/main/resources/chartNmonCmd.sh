export cp="/home/jmeter/bin/chart-lib/activation-1.1.jar:/home/jmeter/bin/chart-lib/avalon-framework-4.1.3.jar:/home/jmeter/bin/chart-lib/commons-logging-1.1.jar:/home/jmeter/bin/chart-lib/jcommon-1.0.16.jar:/home/jmeter/bin/chart-lib/jfreechart-1.0.13.jar:/home/jmeter/bin/chart-lib/jms-1.1.jar:/home/jmeter/bin/chart-lib/jmxri-1.2.1.jar:/home/jmeter/bin/chart-lib/jmxtools-1.2.1.jar:/home/jmeter/bin/chart-lib/log4j-1.2.15.jar:/home/jmeter/bin/chart-lib/logkit-1.0.1.jar:/home/jmeter/bin/chart-lib/mail-1.4.jar:/home/jmeter/bin/chart-lib/opencsv-1.8.jar:/home/jmeter/bin/chart-lib/responsetimes-0.1-SNAPSHOT.jar:/home/jmeter/bin/chart-lib/servlet-api-2.3.jar:/home/jmeter/bin/chart-lib/spring-2.5.6.jar:/home/jmeter/bin/chart-lib/xml-apis-1.3.02.jar"

if [ $# -lt 2 ]
then
 echo "Usage: $(basename $0) <chart type> <results folder> <OPTS>"
 echo "chart types:"
 echo "NmonOverTime"
 exit 1
fi

classname=$1
shift
csvFile=$1
shift
OPTS=$*

# Only applies to throughput chart
chartName=$(basename $csvFile)

java  -Xms512m -Xmx5000m -classpath ${cp} com.poopware.${classname} -CHART_NAME $chartName -INPUT_CSV $csvFile -OUTPUT_FOLDER ${pwd} ${OPTS}
