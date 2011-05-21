package com.poopware.graph;

import au.com.bytecode.opencsv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.awt.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;

public class PctResponsesWithinMs extends AbstractGraph {
   private final static Log logger = LogFactory.getLog(PctResponsesWithinMs.class);
   private int maxBuckets = 400; // Default, may be overidden
   private static final int INTERVAL_SIZE=10; // size of bucket interval in ms
   private int responsesWithin95Pct = 0;
   XYSeries aggSeries = new XYSeries("% Within Ms"); // Aggregated results
   XYSeriesCollection stragglerChartData = new XYSeriesCollection(); // Stragglers (>=1% above 2 seconds)

    public PctResponsesWithinMs(int maxBuckets) {
        this.maxBuckets = maxBuckets;
    }
    
    /**
     * Format is:
     * [total samples],[% > 2s],[REQ-ID],[0-9ms],[10-19ms],....,[1990-1999ms]
     */
    XYDataset readData(String inputFile, String filter) throws IOException {

        // Full set of sample data
        XYSeriesCollection dataset = new XYSeriesCollection();

        long aggBuckets[] = new long[maxBuckets+1];
        long totalSamplesForAll = 0;
         
        // Open file and skip header
        CSVReader reader = new CSVReader(new FileReader(inputFile));
        reader.readNext();
        
       // For each request
       String [] nextLine;
        while ((nextLine = reader.readNext()) != null) {
            String pctAboveTarget=nextLine[1];
            String requestName=nextLine[2];


            // Filter those required
            // If the request doesn't match the filter then go to the next
            if ( FILTER != null && !requestName.toLowerCase().contains(FILTER)) continue;

            // Maximum number of ms to look at
            double msScale = (maxBuckets > nextLine.length) ? nextLine.length : maxBuckets;

            // Calculate total samples
            double totalSamples=Double.valueOf(nextLine[0]);
            totalSamplesForAll += totalSamples;

            // Straggler?
            boolean straggler=false;
            double pctAboveTargetD = Double.valueOf(pctAboveTarget.replace("%",""));
            if (pctAboveTargetD >= 3) {
                straggler=true;
            }

            // Create series of plots of pct samples against response time bucket
            XYSeries series = new XYSeries(requestName);
            double pctWithinBucketSoFar = 0;
            for (int i=3; i<msScale; i++) {
                double samplesInBucket = Double.valueOf(nextLine[i]);
                if (samplesInBucket == 0) continue;
                double pctWithinIndivdualBucket = (samplesInBucket/totalSamples)*100;
                pctWithinBucketSoFar += pctWithinIndivdualBucket;
                int interval = (i-3+1); // From 1 to maxBuckets
                int msInterval = interval*10; // Each interval is 10ms
                // Add to all requests series
                series.add(msInterval, pctWithinBucketSoFar);
                // Increase aggregate response time for this time interval 
                aggBuckets[interval-1] += samplesInBucket;

            }

            // Add those above 2 seconds
//            double pctAboveTwoSecs = Double.valueOf(nextLine[1].replace("%",""));            
//            if (maxBuckets >= 200 && pctAboveTwoSecs > 0) {
//                series.add(2100, (pctWithinBucketSoFar+pctAboveTwoSecs));
//                aggBuckets[200] += pctAboveTwoSecs;
//            }

            // Add to straggler series
            if (straggler) stragglerChartData.addSeries(series);
            // Add to the full set of series
            dataset.addSeries(series);
        }

       // Create the aggregate graph
       aggSeries.add(0,0);
       double pctWithinAggSoFar = 0;
       double lastPctWithinAggSoFar = 0;
       for (int i=0; i<aggBuckets.length; i++) {
            double samplesInBucket = Double.valueOf(aggBuckets[i]);
            if (samplesInBucket == 0) continue;
            double pctWithinIndivdualInterval = (samplesInBucket/totalSamplesForAll)*100;
            pctWithinAggSoFar += pctWithinIndivdualInterval;
            int msInterval = (i+1)*INTERVAL_SIZE;
            aggSeries.add(msInterval, pctWithinAggSoFar);
            logger.info(msInterval+","+pctWithinAggSoFar);
            if (new Double(pctWithinAggSoFar).intValue() == 95 && 
                new Double(lastPctWithinAggSoFar).intValue() >= 95) {
                responsesWithin95Pct = msInterval;
                logger.info("95% are within:"+responsesWithin95Pct);
            }
            lastPctWithinAggSoFar = pctWithinAggSoFar;
        }

        return dataset;
    }

    private void createAggregatedChart() throws IOException {
        XYSeriesCollection aggDataset = new XYSeriesCollection();
        aggDataset.addSeries(this.aggSeries);
        // Create graph
        String chartName = getChartName()+"-AGGREGATED";
        JFreeChart chart = createChart(aggDataset, chartName);
        String filename = getChartFileName(chartName);
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);
    }

    private void createStragglersChart() throws IOException {
        // Create graph
        String chartName = getChartName()+"-STRAGGLERS";
        JFreeChart chart = createChart(stragglerChartData, chartName);
        String filename = getChartFileName(chartName);
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);
    }

    /**
     * Create a chart based on the aggregated results
     * @throws IOException
     */
    void createExtraCharts() throws IOException {
        createAggregatedChart();
        createStragglersChart();
    }    

    /**
     * Creates the chart containing all requests - the default
     * @param dataset
     * @param name
     * @return
     */
    JFreeChart createChart(XYDataset dataset, String name) {
        boolean showLegend = false;
        if (dataset.getSeriesCount() <= 40) {
            showLegend = true;
        }
        
        // create the chart...
        JFreeChart chart = ChartFactory.createXYLineChart(
            name,      // chart title
            "Response Time (ms)",                      // x axis label
            "% Within Ms",                      // y axis label
            dataset,                  // data
            PlotOrientation.VERTICAL,
            showLegend,                     // include legend
            false,                     // tooltips
            false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...
        chart.setBackgroundPaint(Color.white);

        // get a reference to the plot for further customisation...
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);

        // Set range within 0 to 2 seconds
        ValueAxis domainAxis = plot.getDomainAxis();
        domainAxis.setRange(0.0d, 2000.0d);

        // change the auto tick unit selection to integer units only...
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        return chart;
    }
    
    public static void main(String[] args) throws Exception {
        PctResponsesWithinMs chart = new PctResponsesWithinMs(400);
        try {
            chart.saveChartAsFile(args);
        } catch (IllegalArgumentException iae) {
            return;
        }
    }
}
