package com.poopware.graph;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.poopware.transform.JmeterJtlEntry;

import java.util.*;
import java.io.IOException;
import java.io.FileReader;
import java.awt.*;

import au.com.bytecode.opencsv.CSVReader;

public class UserActivityOverTime extends AbstractGraph {
    private final static Log logger = LogFactory.getLog(UserActivityOverTime.class);
    private long basetime;
    // The mapping of threadname to a thread index number (to allow plot on X axis) 
    private Map<String, Integer> threadIndex = new HashMap();
    // The series of plots for a single thread
    private Map<Integer, XYSeries> threadSeries = new HashMap();
    // The full set of thread activity series
    XYSeriesCollection allSeries = new XYSeriesCollection();    
    
     XYDataset readData(String csvFilename, String filter) throws IOException {
        // Open file and skip header
        CSVReader reader = new CSVReader(new FileReader(csvFilename));
        reader.readNext();

        // For each request add to the appropriate series of data
        String [] nextLine;
        basetime = 0;
        while ((nextLine = reader.readNext()) != null) {

            // Ignore corrupt/blank lines
            if ( nextLine.length < 5) {
                logger.debug("Ignoring line:"+ Arrays.toString(nextLine));
                continue;
            }

            processLine(nextLine);
       }

        return allSeries;
    }

    /**
     * Process a line of data
     * @param nextLine
     */
    private void processLine(String [] nextLine) {
            JmeterJtlEntry jtlEntry = new JmeterJtlEntry(nextLine);
            String requestName = jtlEntry.getRequestLabel();
            // long responseTime = Long.valueOf(nextLine[1]);
            long timestamp = jtlEntry.getTimestamp();
            boolean success = jtlEntry.getSuccess();
            String threadName = jtlEntry.getThreadName();

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

            // If the request is not within the required time band then go on
            if (timestamp < TIME_START_MS || timestamp > TIME_END_MS) return;

            // Add the new activity point to the series
            Integer threadIndex = getThreadIndex(threadName);
            XYSeries series = getSeriesForThread();
            series.add(timestamp, threadIndex);

            // Add it to the full set of series for the chart
            allSeries.addSeries(series);
    }

    /**
     * Return the series of points for the thread
     * @TODO No longer any need for different thread series remove this
     * @return
     */
    private XYSeries getSeriesForThread() {
        // Put all threads into the one series - no need for multiple legends
        int threadIndex = 1;
        // Add to the series of results for the thread
        XYSeries series;
        if (threadSeries.containsKey(threadIndex)) {
            // Existing series
            series = threadSeries.get(threadIndex);
        } else {
            // New series
            series = new XYSeries(threadIndex);
            threadSeries.put(threadIndex, series);
        }
        return series;
    }

    /**
     * Give each thread an index number that can be used
     * on the y axis
     * @param threadName threadname
     * @return index index
     */
    private Integer getThreadIndex(String threadName) {
        int index;
        if (!threadIndex.containsKey(threadName)) {
            index = threadIndex.keySet().size()+1;
            //logger.info("Creating new index ("+index+") for thread: "+threadName);            
            threadIndex.put(threadName, index);
        } else {
            index = threadIndex.get(threadName);
        }
        return index;
    }

   JFreeChart createChart(XYDataset dataset, String chartName) {
           boolean showLegend = false;
        if (dataset.getSeriesCount() <= 30) {
            showLegend = true;
        }

        // create the chart...
        JFreeChart chart = ChartFactory.createScatterPlot(
            chartName,      // chart title
            "Time",                      // x axis label
            "Threads",                      // y axis label
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
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        // OPTIONAL CUSTOMISATION COMPLETED.

        return chart;
    }
    
    /**
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        UserActivityOverTime chart = new UserActivityOverTime();
        try {
            chart.saveChartAsFile(args);
        } catch (IllegalArgumentException iae) {
            return;
        }
        return;
    }
    
}
