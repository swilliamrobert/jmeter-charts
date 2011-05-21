package com.poopware.graph;

import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.general.Series;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.poopware.transform.JmeterJtlEntry;

import java.io.IOException;
import java.io.FileReader;
import java.io.File;
import static java.io.File.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;

import au.com.bytecode.opencsv.CSVReader;

public class ResponseMsOverTime extends AbstractGraph {
    private final static Log logger = LogFactory.getLog(ResponseMsOverTime.class);

    /** THESE MAKE THE CLASS STATEFUL (but it only gets used once) **/
    // A series of datapoints for each request type (sampler)
    private Map<String, XYSeries> requestSeries = new HashMap();
    private XYSeriesCollection chartSeries = new XYSeriesCollection();

    // Base time
    long basetime = 0;

    /**
     * Return the series of points for the request
     * @param requestName
     * @return
     */
    private XYSeries getSeriesForRequest(String requestName) {
        // Add to the series of results for the request
        XYSeries series = null;
        if (requestSeries.containsKey(requestName)) {
            // Existing series
            series = requestSeries.get(requestName);
        } else {
            // New series
            series = new XYSeries(requestName);
            requestSeries.put(requestName, series);
            chartSeries.addSeries(series);
        }
        return series;
    }

    /**
     * Process a line of data
     * @param nextLine
     */
    private void processLine(String [] nextLine) {
            JmeterJtlEntry jtlEntry = new JmeterJtlEntry(nextLine);
            String requestName = jtlEntry.getRequestLabel();
            long responseTime = jtlEntry.getResponseTime();
            long timestamp = jtlEntry.getTimestamp();
            boolean success=jtlEntry.getSuccess();        

            // Place failed requests into their own series
            if (!success) requestName+="-FAIL";

            // If the request doesn't match the filter then go to the next
            if ( FILTER != null ) {
                // Ignore requests that are failures unless requested
                if (!success && !FILTER.equals("fail")) return;
                // Apply filter (TODO: Change this to regex filter)
                if (!requestName.toLowerCase().contains(FILTER)) return;
            }

            // Set the basetime to the first timestamp
            if (basetime == 0) basetime = timestamp;
            timestamp=(timestamp-basetime);


            // Add the new data point to the series - if it fits within range
            if (responseTime >= RESPONSE_MIN && responseTime <= RESPONSE_MAX) {
                XYSeries series = getSeriesForRequest(requestName);
                series.add(timestamp, responseTime);
            }

    }

//    /**
//     * For each request series place it into the chart based on its grade
//     * <p>
//     * For example, all request series with maximum < 100 ms go into chart grade 0
//     * @return
//     */
//    private Map<Integer, XYDataset> calculateGrades(Map<String, Long> seriesReportCards) {
//        Map<Integer, XYDataset> gradedDatasets = new HashMap();
//        Set requestSet = seriesReportCards.keySet();
//        for (Iterator it = requestSet.iterator(); it.hasNext();) {
//            String requestName = (String) it.next();
//            // What was the request report card?
//            Long reportCard = seriesReportCards.get(requestName);
//            // What grade did this request have?
//            Integer grade = calculateSeriesGrade(reportCard);
//            // logger.debug("requestName:"+requestName+" reportCard:"+reportCard+" grade:"+grade);
//            // Add to the chart that fits this grade
//            TimeSeriesCollection dataset;
//            if (!gradedDatasets.containsKey(grade)) {
//                logger.debug("Creating graded dataset for:"+grade);
//                dataset = new TimeSeriesCollection();
//                gradedDatasets.put(grade, dataset);
//            } else {
//                dataset = (TimeSeriesCollection) gradedDatasets.get(grade);
//            }
//            // Add the request data to the graded chart data
//            dataset.addSeries(requestSeries.get(requestName));
//        }
//        return gradedDatasets;
//    }

   /**
     * Format is:
     * [REQ-ID],[RESPONSE TIME],[TIMESTAMP],[SUCCESS FLAG]
     */
    XYDataset readData(String csvFilename, String requestFilter) throws IOException {
        // Open file and skip header
        CSVReader reader = new CSVReader(new FileReader(csvFilename));
        reader.readNext();

        // For each request add to the appropriate series of data
        String [] nextLine;
        basetime = 0;
        while ((nextLine = reader.readNext()) != null) {

            // Ignore corrupt/blank lines
            if ( nextLine.length < 4) {
                logger.debug("Ignoring line:"+ Arrays.toString(nextLine));
                continue;
            }

            processLine(nextLine);
       }
        return chartSeries;
   }

   JFreeChart createChart(XYDataset dataset, String name) {
        boolean showLegend = false;
        if (dataset.getSeriesCount() <= 40) {
            showLegend = true;
        }

        // create the chart...
        JFreeChart chart = ChartFactory.createScatterPlot(
            name,      // chart title
            "Time",                      // x axis label
            "Response Time (ms)",                      // y axis label
            dataset,                  // data
            PlotOrientation.VERTICAL,
            showLegend,                     // include legend
            false,                     // tooltips
            false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...
        chart.setBackgroundPaint(Color.white);

        //StandardLegend legend = (StandardLegend) chart.getLegend();
        //legend.setDisplaySeriesShapes(true);

        // get a reference to the plot for further customisation...
        XYPlot plot = chart.getXYPlot();

        plot.setBackgroundPaint(Color.lightGray);
        //plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);

        //XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        //renderer.setSeriesLinesVisible(0, false);
        //renderer.setSeriesShapesVisible(1, false);
        //plot.setRenderer(renderer);

        // change the auto tick unit selection to integer units only...
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        /// rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        // OPTIONAL CUSTOMISATION COMPLETED.

        return chart;
    }

    /**
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        ResponseMsOverTime chart = new ResponseMsOverTime();
        chart.saveChartAsFile(args);
        return;
    }

}
