Undocumented dodgy JMeter/Nmon Chart Generation

# Introduction #

This will not be very detailed!

# Details #
  * Run the Jmeter tests
  * Transform the test results from jtl format to csv format
  * Run one of the chart classes to parse and create a chart

## Generating the CSV file ##
The JMeter chart generation reads the data in csv format. The following command will transform the Jmeter jtl results file into a corresponding csv file in the expected format.

When running the JMeter test configure it so the output goes to a .jtl file. Then run the following to transform it into csv format.
```
cat jmeterResults.jtl | transformJtlToCsv.sh > jmeterResults.csv
```

## Generating a chart ##
  * See createChart.sh for an example
  * See AbstractChart.java for more detail

## Chart Types ##
**Jmeter**
  * ResponseMsOverTime - Response over time, uses BucketFiller to work out averrage response in definable increments
  * UserActivityOverTime - Active threads over time
  * ThroughPutOverTime - Requests per second over time, can use filters to break down to specific requests
  * PctResponseWithinMs - Percent of responses that are within a defined maximum response time
**Nmon**
  * NmonOverTime - Reads from an NMON csv result file, provides server resource details