package com.poopware.graph;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;

import com.poopware.transform.JmeterJtlEntry;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.text.SimpleDateFormat;

import au.com.bytecode.opencsv.CSVReader;

public class ThroughPutOverTime extends AbstractGraph {
    private static final Log logger = LogFactory.getLog(ThroughPutOverTime.class);
    /** THESE MAKE THE CLASS STATEFUL (but it only gets used once) **/

    // Base time
    long basetime = 0;
   // Create the series and default report card to 0
   TimeSeries throughputSeries = new TimeSeries("Throughput");
   TimeSeries responseTimeSeries = new TimeSeries("ResponseTime");
   TimeSeries activeSessionsSeries = new TimeSeries("ActiveSessions");
   TimeSeries pctWithin2SecondsSeries = new TimeSeries("PctWithin2Seconds");

   // Per Request Summary
   Map<String, RequestSummaryEntry> requestSummary = new HashMap();

   // Response times
   double avgResponseTime = 0;
   double maxResponseTime = 0;
   double pctResponseTime = 0;
   long totalResponseTime = 0;

   // Requests per second
   long totalRequests = 0;
   long totalFailedRequests = 0;
   double avgThroughput = 0;
   double maxThroughput = 0;
   double totalThroughput = 0;
   double rqmtThroughput = 0; // Throughput at which the response time hit requirement

   double maxAverageResponseTimeForInterval = 0; // Maximum average slice    

   // Total number of started (active?) threads
   long maxActiveSessions = 0;
   long rqmtActiveSessions = 0; // Active sessions at which point the response time hit requirement
   Set activeSessionSet = new HashSet();
   Set allSessionSet = new HashSet(); // All sessions (failed and success)

   // Pct requests above 2 seconds
   long totalAbove2Seconds = 0;
   double pctAbove2Seconds = 0;
   // Pct requests above 1 second
   long totalAbove1Second = 0;
   double pctAbove1Second = 0;    

   // Time passed in ms
   long totalTime = 0;

   /**
     * Format is:
     * [REQ-ID],[RESPONSE TIME],[TIMESTAMP],[SUCCESS FLAG]
     */
    XYDataset readData(String csvFilename, String requestFilter) throws IOException {
        // List split datasets into groups based on good, medium, bad, very bad
        Map<String, Long> seriesReportCards = new HashMap<String, Long>();

        // Open file and skip header
        CSVReader reader = new CSVReader(new FileReader(csvFilename));

        // For each request add to the appropriate series of data
        String [] nextLine;
        long basetime = 0;

        // Time interval size (in ms)
        long intervalSize = INTERVAL_SIZE;

        // For each interval how many requests?
        Map<Long, Long> throughputData = new HashMap();
        Map<Long, Long> responseData = new HashMap();
        Map<Long, Long> requestsAbove2Seconds = new HashMap();
        Map<Long, Long> activeSessions = new HashMap();


        boolean isFailureReport="fail".equals(FILTER);
        long currentActiveSessions = 0;
        long intervalAtWhichRequirementBroken = -1;
        long lastInterval = -1;
        int i=0;
        while ((nextLine = reader.readNext()) != null) {
            JmeterJtlEntry jtlEntry = new JmeterJtlEntry(nextLine);

            // Ignore corrupt/blank lines
            if ( nextLine.length < 4) {
                logger.info("Ignoring line:"+ Arrays.toString(nextLine));
                continue;
            }

            ///////////////////////////////////////////////////////////////////////////////////
            // Read values
            String requestName = jtlEntry.getRequestLabel();
            long responseTime = jtlEntry.getResponseTime();
            long timestamp = jtlEntry.getTimestamp();
            boolean success=jtlEntry.getSuccess();
            String threadId=jtlEntry.getThreadName();
            // Calculate timestamp
            if (basetime == 0) basetime = timestamp;
            timestamp=(timestamp-basetime);
            // Calculate interval
            long currentInterval = 0;
            if (timestamp > intervalSize) {
                currentInterval = timestamp/intervalSize;
                //logger.info("Interval = "+currentInterval);
            }

            ///////////////////////////////////////////////////////////////////////////////////
            // FILTER OUT REQUESTS - FAILED, FILTER FAIL, THRESHOLDS
            allSessionSet.add(threadId);

            // Ignore requests that are failures unless requested
            if (!success) {
                // Thread failed so remove it from the session count
                activeSessionSet.remove(threadId);
                totalFailedRequests++;
            } else {
                // If active sessions isn't within required then continue
                activeSessionSet.add(threadId);
            }

            // Failure response times ignored in anything but failure test
            // The thread is deemed as failed for this instance and the
            // total successful threads is reduced
            if (!success) {
                if (!isFailureReport) {
                    // Add to 'failed request' summary
                    addRequestSummaryEntry(requestName, -1);
                    continue;
                }

                // Failure report uses FAIL as filter
                requestName+="-FAIL";
            }

            // If the request doesn't match the filter then go to the next
            if ( FILTER != null ) {
                // Apply filter
                if (!requestName.toLowerCase().contains(FILTER)) continue;
            }

            // Count number of succesful requests for this interval
            currentActiveSessions = activeSessionSet.size();

            // Note the high threshold of sessions
            if (currentActiveSessions > maxActiveSessions) maxActiveSessions = currentActiveSessions;

            // Are we above the minimum threshold?
            if (allSessionSet.size() < SESSION_MIN) {
                // Reset the basetime - averages must be calculated after the ramp
                basetime = 0;
                continue;
            }

            // If time isn't within required then continue
            if (timestamp < TIME_START_MS || timestamp > TIME_END_MS) continue;

            ////////////////////////////////////////////////////////////////////////////////////////////
            // RECORD DETAILS OF REQUEST

            // Increase aggregates
            totalRequests++;
            if (timestamp > totalTime) totalTime = timestamp;
            totalResponseTime = responseTime + totalResponseTime;
            if (responseTime >= 2000) totalAbove2Seconds++;
            if (responseTime >= 1000) totalAbove1Second++;            
            if (responseTime > maxResponseTime) maxResponseTime = responseTime;

            // Add active sessions
            activeSessions.put(currentInterval, currentActiveSessions);

            // Add 'per request' summary
            addRequestSummaryEntry(requestName, responseTime);

            // Add total for number above 2 seconds for this interval
            if (responseTime > 2000) {
                if (!requestsAbove2Seconds.containsKey(currentInterval)) {
                    requestsAbove2Seconds.put(currentInterval, 1l);
                } else {
                    long currentResponsesAbove2Secs = requestsAbove2Seconds.get(currentInterval);
                    requestsAbove2Seconds.put(currentInterval, ++currentResponsesAbove2Secs);
                }                
            }

            // Add throughpout/response data (avged into intervals)
            if (!throughputData.containsKey(currentInterval)) {
                // First entry
                throughputData.put(currentInterval, 1l);
                responseData.put(currentInterval, responseTime);
                logger.debug(currentInterval+":"+responseTime);
            } else {
                // Add to existing entry
                long requestCount = throughputData.get(currentInterval);
                requestCount++;
                throughputData.put(currentInterval, requestCount);
                // Add response time
                long currentResponseTotalForInterval = responseData.get(currentInterval);
                currentResponseTotalForInterval += responseTime;
                logger.debug(currentInterval+":"+currentResponseTotalForInterval);
                responseData.put(currentInterval, currentResponseTotalForInterval);
            }
       }     

       // Work on aggregates over time
       long requestsSoFar = 0;
       long requestsAbove2SecsSoFar = 0;
       for (Long interval : throughputData.keySet()) {
           // Size of interval/timestamp in seconds
           double intervalSizeInSeconds = Double.valueOf(intervalSize)/1000;
           double timeInSeconds = interval*intervalSizeInSeconds;
           long timeInMilliseconds = interval * intervalSize;

           // Average requests sent per second for the interval
           double requestCount = throughputData.get(interval);
           double requestCountPerSecond = Double.valueOf(requestCount)/intervalSizeInSeconds;

           // How many requests sent so far
           requestsSoFar += requestCount;

            // Add active sessions
            activeSessionsSeries.add(new FixedMillisecond(timeInMilliseconds), activeSessions.get(interval));         

           // Add pct within 2 seconds so far
           if (requestsAbove2Seconds.containsKey(interval)) {
               // Responses above 2 seconds for this interval - add them to those so far
               requestsAbove2SecsSoFar += requestsAbove2Seconds.get(interval);                              
           }

           double pctWithin2SecondsSoFar = 100-((Double.valueOf(requestsAbove2SecsSoFar)/Double.valueOf(requestsSoFar))*100);
           pctWithin2SecondsSeries.add(new FixedMillisecond(timeInMilliseconds), pctWithin2SecondsSoFar);
           logger.debug("Pct within 2 seconds:"+requestsAbove2SecsSoFar+"-"+requestsSoFar+"-"+requestsAbove2SecsSoFar+"-"+pctWithin2SecondsSoFar);

           // Average response time per second
           double responseTimeTotal = responseData.get(interval);
           double averageResponseTimeForInterval = Double.valueOf(responseTimeTotal)/requestCount;
           if (averageResponseTimeForInterval > maxAverageResponseTimeForInterval) {
               maxAverageResponseTimeForInterval = averageResponseTimeForInterval;
           }

           // Add to the chart data points
           responseTimeSeries.add(new FixedMillisecond(timeInMilliseconds), averageResponseTimeForInterval);
           throughputSeries.add(new FixedMillisecond(timeInMilliseconds), requestCountPerSecond);
           if (intervalAtWhichRequirementBroken == interval) rqmtThroughput = requestCountPerSecond;
           if (requestCountPerSecond > maxThroughput) maxThroughput = requestCountPerSecond;

           logger.debug("Interval requests " + timeInSeconds + ":"+requestCount);
           logger.debug("Interval response time " + timeInSeconds + ":" + averageResponseTimeForInterval);
       }

       // Total averages
       if (totalRequests > 0 && totalTime > 0) {
           avgResponseTime = totalResponseTime/totalRequests;
           avgThroughput = totalRequests/(Double.valueOf(totalTime)/1000);
           pctAbove2Seconds = (totalAbove2Seconds/Double.valueOf(totalRequests)) * 100;
           pctAbove1Second = (totalAbove1Second/Double.valueOf(totalRequests)) * 100;
       }

       // We only have a single grade for this data
       TimeSeriesCollection seriesCollection = new TimeSeriesCollection();
       seriesCollection.addSeries(throughputSeries);

       return seriesCollection;
   }

    /**
     * Individual request performance summary
     * response time of -1 indicates failed request
     * @param request
     * @param responseTime
     */
    public void addRequestSummaryEntry(String request, long responseTime) {
        RequestSummaryEntry requestSummaryEntry;
        if (!requestSummary.containsKey(request)) {
            requestSummaryEntry = new RequestSummaryEntry();
            requestSummary.put(request, requestSummaryEntry);
        } else {
            requestSummaryEntry = requestSummary.get(request);
        }

        // Failed request
        if (responseTime == -1) {
            requestSummaryEntry.totalFailedRequests++;
            return;
        }

        // Successful request
        if (responseTime > 2000) requestSummaryEntry.totalAbove2Seconds++;
        requestSummaryEntry.totalRequests++;
        requestSummaryEntry.totalResponseTime+=responseTime;
        if (responseTime > requestSummaryEntry.maxResponseTime) {
            requestSummaryEntry.maxResponseTime = responseTime;
        }

    }

    /**
     * 'per request' summary report
     */
    public void createRequestSummaryReport() {
       String filename = OUTPUT_FOLDER + File.separator + CHART_NAME + FILTER +".report-requests";        

       StringBuffer csv = new StringBuffer();
       writeRequestSummaryCsvHeader(csv);
       for (String requestName : requestSummary.keySet()) {
           RequestSummaryEntry requestSummaryEntry = requestSummary.get(requestName);
           String AVOID_RAMP="";
           if (avoidRamp) {
             AVOID_RAMP="NORAMP";
           }
           csv.append(CHART_NAME+FILTER+AVOID_RAMP+",");
           csv.append(requestName+",");
           csv.append(requestSummaryEntry.getAverageResponseTime()+",");
           csv.append(requestSummaryEntry.getPctWithin2Seconds()+",");           
           csv.append(requestSummaryEntry.maxResponseTime+",");
           csv.append(requestSummaryEntry.totalRequests+",");
           csv.append(requestSummaryEntry.totalResponseTime+",");
           csv.append(requestSummaryEntry.totalAbove2Seconds+",");
           csv.append(requestSummaryEntry.totalFailedRequests+"\n");
       }

       logger.info("Creating request summary report:"+filename);
       try {
           FileWriter fw = new FileWriter(new File(filename));
           BufferedWriter bw = new BufferedWriter(fw);
           bw.write(csv.toString());
           bw.flush();
       } catch (IOException ioe) {
           logger.error("Could not create report:"+filename,ioe);
           return;
       }
    }

    private void writeRequestSummaryCsvHeader(StringBuffer csv) {
       csv.append("CHART_NAME,");
       csv.append("requestName,");
       csv.append("avgResponseTime,");
       csv.append("pctWithin2Seconds,");
       csv.append("maxResponseTime,");
       csv.append("totalRequests,");
       csv.append("totalTimeInSecs,");
       csv.append("totalAbove2Seconds,");
       csv.append("totalFailedRequests"+"\n");       
    }

    /**
     * Write out the summary to a file
     */
    @Override
    String createReport() {

       createRequestSummaryReport();
                
       StringBuffer report = new StringBuffer();
       report.append("--------------------------------------\n");        
       report.append(super.CHART_NAME+"\n");
       report.append("--------------------------------------\n");
       report.append("MaxActiveSessions:"+maxActiveSessions+"\n");
       report.append("PctWithin2secs:"+(100d-pctAbove2Seconds)+"%"+"\n");
       report.append("--------------------------------------\n");        
       report.append("RqmtSessions:"+rqmtActiveSessions+"\n");
       report.append("RqmtThroughput:"+rqmtThroughput+"rps"+"\n");
       report.append("--------------------------------------\n");
       report.append("AvgReponseTime:"+avgResponseTime+"ms"+"\n");
       report.append("AvgThroughput:"+avgThroughput+"rps"+"\n");
       report.append("--------------------------------------\n");        
       report.append("MaxResponseTime:"+maxResponseTime+"ms"+"\n");       
       report.append("MaxThroughput:"+maxThroughput+"rps"+"\n");
       report.append("PctAbove2secs:"+pctAbove2Seconds+"%"+"\n");
       report.append("PctAbove1sec:"+pctAbove1Second+"%"+"\n");
       report.append("TotalRequests:"+totalRequests+"\n");
       report.append("TotalTimeInSeconds:"+(Double.valueOf(totalTime)/1000)+"\n");
       report.append("TotalRequestsAbove2secs:"+totalAbove2Seconds+"\n");
       report.append("TotalRequestsAbove1sec:"+totalAbove1Second+"\n");
       report.append("TotalFailedRequests:"+totalFailedRequests+"\n");
       report.append("--------------------------------------\n");
       logger.info("\n"+report+"\n");

       StringBuffer csv = new StringBuffer();
       writeSummaryCsvHeader(csv);
       String AVOID_RAMP="";
       if (avoidRamp) {
         AVOID_RAMP="NORAMP";
       }
       csv.append(CHART_NAME+FILTER+AVOID_RAMP+",");
       csv.append(maxActiveSessions+",");
       csv.append((100d-pctAbove2Seconds)+",");
       csv.append(rqmtActiveSessions+",");
       csv.append(rqmtThroughput+",");
       csv.append(avgResponseTime+",");
       csv.append(avgThroughput+",");
       csv.append(maxResponseTime+",");
       csv.append(maxThroughput+",");
       csv.append(pctAbove2Seconds+",");
       csv.append(pctAbove1Second+",");        
       csv.append(totalRequests+",");
       csv.append((Double.valueOf(totalTime)/1000)+",");
       csv.append(totalAbove2Seconds+",");
       csv.append(totalAbove1Second+",");
       csv.append(totalFailedRequests+"\n");

       return csv.toString();
    }

    private void writeSummaryCsvHeader(StringBuffer csv) {
       csv.append("CHART_NAME,");
       csv.append("maxActiveSessions,");
       csv.append("pctWithin2Seconds,");
       csv.append("rqmtActiveSessions,");
       csv.append("rqmtThroughput,");
       csv.append("avgResponseTime,");
       csv.append("avgThroughput,");
       csv.append("maxResponseTime,");
       csv.append("maxThroughput,");
       csv.append("pctAbove2Seconds,");
       csv.append("pctAbove1Second,");
       csv.append("totalRequests,");
       csv.append("totalTimeInSecs,");
       csv.append("totalAbove2Seconds,");
       csv.append("totalAbove1Second,");
       csv.append("totalFailedRequests"+"\n");        
    }

    JFreeChart createChart(XYDataset dataset, String name) {
        boolean showLegend = false;
        if (dataset.getSeriesCount() <= 40) {
            showLegend = true;
        }

        // create the chart...
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            name,      // chart title
            "Minutes",                      // x axis label
            "HTTP Requests",                      // y axis label
            dataset,                  // data
            showLegend,                     // include legend
            false,                     // tooltips
            false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...
        chart.setBackgroundPaint(Color.white);

        // get a reference to the plot for further customisation...
        XYPlot plot = chart.getXYPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("mm"));
        ValueAxis axis1 = plot.getRangeAxis();
        axis1.setLabelPaint(Color.red);

        NumberAxis axis2 = new NumberAxis("ResponseTime (ms)");
        plot.setRangeAxis(2, axis2);
        plot.setRangeAxisLocation(2, AxisLocation.BOTTOM_OR_RIGHT);       
        TimeSeriesCollection responseTimeDataset = new TimeSeriesCollection();
        responseTimeDataset.addSeries(responseTimeSeries);
        plot.setDataset(2, responseTimeDataset);
        plot.mapDatasetToRangeAxis(2, 2);
        XYItemRenderer renderer2 = new StandardXYItemRenderer();
        plot.setRenderer(2, renderer2);
        // Fix to 2 seconds if under 2 seconds - helps with comparing different charts
        if (maxAverageResponseTimeForInterval < 2000) {
            axis2.setRange(0.0d, 2000.0d);
        }
        renderer2.setSeriesPaint(0, Color.blue);
        axis2.setLabelPaint(Color.blue);

        NumberAxis axis3 = new NumberAxis("ActiveSessions");
        plot.setRangeAxis(3, axis3);
        plot.setRangeAxisLocation(3, AxisLocation.TOP_OR_RIGHT);
        TimeSeriesCollection activeSessionsDataset = new TimeSeriesCollection();
        activeSessionsDataset.addSeries(activeSessionsSeries);
        plot.setDataset(3, activeSessionsDataset);
        plot.mapDatasetToRangeAxis(3, 3);
        XYItemRenderer renderer3 = new StandardXYItemRenderer();
        plot.setRenderer(3, renderer3);
        // Fix to 2550 users if > 2000 active users
        if (this.maxActiveSessions > 2000) {
            axis3.setRange(0, 2590);
        }        
        renderer3.setSeriesPaint(0, Color.green);
        axis3.setLabelPaint(Color.green);

        NumberAxis axis4 = new NumberAxis("Pct Within 2 Seconds");
        plot.setRangeAxis(4, axis4);
        plot.setRangeAxisLocation(4, AxisLocation.TOP_OR_LEFT);
        TimeSeriesCollection pctWithin2SecondsDataset = new TimeSeriesCollection();
        pctWithin2SecondsDataset.addSeries(pctWithin2SecondsSeries);
        plot.setDataset(4, pctWithin2SecondsDataset);
        plot.mapDatasetToRangeAxis(4, 4);
        XYItemRenderer renderer4 = new StandardXYItemRenderer();
        plot.setRenderer(4, renderer4);
        renderer4.setSeriesPaint(0, Color.orange);
        axis4.setLabelPaint(Color.orange);
        // Fix to 100%
        axis4.setRange(50, 105);      

        // get a reference to the plot for further customisation...
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);

        return chart;
    }

    /**
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        ThroughPutOverTime chart = new ThroughPutOverTime();
        chart.saveChartAsFile(args);
        return;
    }

    private class RequestSummaryEntry {
        long requestName;
        long totalRequests; // Successful requests
        long totalFailedRequests;        
        long totalResponseTime;
        public long maxResponseTime;
        public long totalAbove2Seconds;

        public double getAverageResponseTime() {
            return Double.valueOf(totalResponseTime)/Double.valueOf(totalRequests);
        }
        public double getPctWithin2Seconds() {
            return 100d-((Double.valueOf(totalAbove2Seconds)/Double.valueOf(totalRequests))*100);
        }
    }
}

