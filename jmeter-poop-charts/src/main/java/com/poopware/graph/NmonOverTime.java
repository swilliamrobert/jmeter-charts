package com.poopware.graph;

import au.com.bytecode.opencsv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.List;
import java.awt.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYAreaRenderer2;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;

public class NmonOverTime extends AbstractGraph {
    private final static Log logger = LogFactory.getLog(NmonOverTime.class);
    
    public enum NmonEntryType {
        ZZZZ, CPU_ALL, CPU_ALL_USER, CPU_ALL_SYS, TOP, TOP_MAJOR_FAULT, TOP_TOTAL_HTTPD,
        JVM_ACTIVE_THREADS, JVM_FREE_MEMORY, JVM_TOTAL_MEMORY,
        OHS_ACTIVE_CONN, OHS_BUSY_CHILD, OHS_NUM_CHILD,
        JDBC_POOL_SIZE, JDBC_FREE_POOL_SIZE, JDBC_USE_TIME, JDBC_MIN_FREE_POOL_SIZE,
        WCTX_ACTIVE_SESSIONS, WCTX_TOTAL_SESSIONS, MEM_FREE, SWAP_TOTAL, SWAP_FREE, SWP_ACTIVITY, MEM, VM, VM_ACTIVITY
    }

    // NmonEntryType -> (Date, (NmonEntryType-Values))
    private Map<NmonEntryType, Map<Date, List<Double>>> allSeriesData = new HashMap();
    // Summary report entries for each type
    private Map<NmonEntryType, SummaryReportEntry> summaryReport = new HashMap();
    // Map of Nmon timeId's to real dates
    private Map<String, Date> nmonDates = new HashMap();
    // Maps dms string label to entry type
    private Map<String, NmonEntryType> dmstoolTypes = new HashMap();
    // Series of plots for each series
    private Map<NmonEntryType, TimeSeries> allChartSeries = new HashMap();
    // Dataset for chart
    private TimeSeriesCollection allChartSeriesCollection = new TimeSeriesCollection();
    private Date baseTime = null;

    {
        // Maps DMSTool entries to NmonEntryTypes
        dmstoolTypes.put("JVM.activeThreads.value",NmonEntryType.JVM_ACTIVE_THREADS);
        dmstoolTypes.put("JVM.freeMemory.value",NmonEntryType.JVM_FREE_MEMORY);
        dmstoolTypes.put("JVM.totalMemory.value",NmonEntryType.JVM_TOTAL_MEMORY);
        dmstoolTypes.put("ohs_server.connection.active",NmonEntryType.OHS_ACTIVE_CONN);
        dmstoolTypes.put("ohs_server.busyChildren.value",NmonEntryType.OHS_BUSY_CHILD);
        dmstoolTypes.put("ohs_server.numChildren.value",NmonEntryType.OHS_NUM_CHILD);
        dmstoolTypes.put("jdbc_connection_pool_stats.PoolSize.value",NmonEntryType.JDBC_POOL_SIZE);
        dmstoolTypes.put("jdbc_connection_pool_stats.FreePoolSize.value",NmonEntryType.JDBC_FREE_POOL_SIZE);
        dmstoolTypes.put("jdbc_connection_pool_stats.UseTime.time",NmonEntryType.JDBC_USE_TIME);
        dmstoolTypes.put("jdbc_connection_pool_stats.FreePoolSize.minValue",NmonEntryType.JDBC_MIN_FREE_POOL_SIZE);
        dmstoolTypes.put("oc4j_context.sessionActivation.active",NmonEntryType.WCTX_ACTIVE_SESSIONS);
        dmstoolTypes.put("oc4j_context.sessionActivation.completed",NmonEntryType.WCTX_TOTAL_SESSIONS);        
    }

    /**
     * Read nmon/dmstool data and generate chart series
     * @param nmonFilename
     * @param filter
     * @return
     * @throws IOException
     * @throws ParseException
     */
    TimeSeriesCollection readData(String nmonFilename, String filter) throws IOException, ParseException {
        // Open file and skip header
        CSVReader reader = new CSVReader(new FileReader(nmonFilename));
        reader.readNext();

        String nextLine[];
        while ((nextLine = reader.readNext()) != null) {
            NmonEntryType nmonEntry = null;
            String metricType = nextLine[1];
            String nmonType = nextLine[0];

            try {
                if (dmstoolTypes.containsKey(metricType)) {
                    // DMS entry
                    nmonEntry = dmstoolTypes.get(metricType);                   
                    parseDmsLine(nmonEntry, nextLine);
                } else {
                    // NMON entry
                    nmonEntry = NmonEntryType.valueOf(nmonType);
                    parseNmonLine(nmonEntry, nextLine);
                }
            } catch (IllegalArgumentException iae) {
                continue;
            }
        }

        // Ensure all entries are summarised into timestamp intervals
        summariseAllNmonEntriesByTimestamp();

        // The main series of data are processed by createExtraCharts
        TimeSeriesCollection emptySeries = new TimeSeriesCollection();

        return emptySeries;
    }

    /**
     * Parse an Nmon Line Entry
     * @param nmonEntry
     * @param nmonLine
     */
    private void parseNmonLine(NmonEntryType nmonEntry, String[] nmonLine) throws ParseException {
        try {
            switch (nmonEntry) {
                 case ZZZZ: parseTimestamp(nmonLine);break;
                 case CPU_ALL: parseCpuAll(nmonLine);break;
                 case TOP: parseTop(nmonLine);break;
                 case MEM: parseMem(nmonLine);break;
                 case VM: parseVM(nmonLine); break;
            }
        } catch (NumberFormatException nfe) {
            logger.warn("Parse Error - Ignored: "+Arrays.toString(nmonLine));
        }
    }

    /**
     * Return the NMON time for the specified timeId
     * @param timeId
     * @return
     */
    private Date getNmonDate(String timeId) {
           if (!nmonDates.containsKey(timeId)) {
               logger.error("Could not find nmon date for timeId:"+timeId);
               return null;
           }
           return nmonDates.get(timeId);
    }

    /**
     * Parse the Top entry
     * TOP,+PID,Time,%CPU,%Usr,%Sys,Size,ResSet,ResText,ResData,ShdLib,MajorFault,MinorFault,Command
     * TOP,0001483,T0002,1.1,0.9,0.2,3280232,1800684,36,0,8908,3,0,java
     */
    private void parseTop(String[] top) {
        if (top.length < 3) {
            logger.warn("Ignoring:"+Arrays.toString(top));
            return;
        }
        String timeId = top[2];
        Date time = getNmonDate(timeId);
        String command = top[13];
        Double majorFault = Double.valueOf(top[11]);
        if (command.equals("httpd")) {
            addNmonEntryToSeries(NmonEntryType.TOP_TOTAL_HTTPD, time, 1d);
        }
        // Average will be meaningless, total will be relevant
        addNmonEntryToSeries(NmonEntryType.TOP_MAJOR_FAULT, time, majorFault);
    }

    /**
     * Parse Mem entry
     * MEM,Memory MB disc-dev-4,memtotal,hightotal,lowtotal,swaptotal,memfree,highfree,lowfree,swapfree,memshared,cached,active,bigfree,buffers,swapcached,inactive
     * MEM,T0001,7741.9,0.0,7741.9,6096.3,5103.7,0.0,5103.7,6014.6,-0.0,770.2,2308.7,-1.0,102.0,31.9,203.7
     * @param mem
     */
    private void parseMem(String[] mem) {
        if (mem.length < 3) {
            logger.warn("Ignoring:"+Arrays.toString(mem));
            return;
        }
        String timeId = mem[1];
        Date time = getNmonDate(timeId);
        double memFree = Double.valueOf(mem[6]);
        double swapFree = Double.valueOf(mem[5]);

        addNmonEntryToSeries(NmonEntryType.MEM_FREE, time, memFree);
        addNmonEntryToSeries(NmonEntryType.SWAP_FREE, time, swapFree);
    }

    /**
     * Parse VM
     * VM,T0001,Paging and Virtual Memory,nr_dirty,nr_writeback,nr_unstable,nr_page_table_pages,nr_mapped,nr_slab,pgpgin,pgpgout,pswpin,pswpout,pgfree,pgactivate,pgdeactivate,pgfault,pgmajfault,pginodesteal,slabs_scanned,kswapd_steal,kswapd_inodesteal,pageoutrun,allocstall,pgrotated,pgalloc_high,pgalloc_normal,pgalloc_dma,pgrefill_high,pgrefill_normal,pgrefill_dma,pgsteal_high,pgsteal_normal,pgsteal_dma,pgscan_kswapd_high,pgscan_kswapd_normal,pgscan_kswapd_dma,pgscan_direct_high,pgscan_direct_normal,pgscan_direct_dma
     * @param vm
     */
    private void parseVM(String[] vm) {
        if (vm.length < 3) {
            logger.warn("Ignoring:"+Arrays.toString(vm));
            return;
        }
        String timeId = vm[1];
        Date time = getNmonDate(timeId);
        double pswpin = Double.valueOf(vm[9]);
        double pswpout = Double.valueOf(vm[10]);
        double pgpgout = Double.valueOf(vm[11]);
        double pgpgin = Double.valueOf(vm[12]);

        double swapActivity = pswpin + pswpout + pgpgout + pgpgin;

        addNmonEntryToSeries(NmonEntryType.SWP_ACTIVITY, time, swapActivity);        
    }

    // Parse the CPU entry
    private void parseCpuAll(String[] cpuAll) {
        String timeId = cpuAll[1];
        Date time = getNmonDate(timeId);        
        Double user = Double.valueOf(cpuAll[2]);
        Double sys = Double.valueOf(cpuAll[3]);
        String wait = cpuAll[4];
        addNmonEntryToSeries(NmonEntryType.CPU_ALL_USER, time, user);
        addNmonEntryToSeries(NmonEntryType.CPU_ALL_SYS, time, sys+user);
    }

    // Parse the CPU entry
    private void parseDmsLine(NmonEntryType dmsType, String[] dmsLine) {
        String timeString = dmsLine[0];
        Date time = null;         
        SimpleDateFormat dmsDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        try {
            time = dmsDateFormat.parse(timeString);
            if (this.baseTime == null) this.baseTime = time;            
        } catch (ParseException e) {
            logger.warn("Could not read date:"+timeString);
        }
        Double threads = Double.valueOf(dmsLine[2]);
        addNmonEntryToSeries(dmsType, time, threads);
    }

    /**
     * Add an entry for the specified type and time
     * @param nmonEntry
     * @param time
     * @param value
     */
    private void addNmonEntryToSeries(NmonEntryType nmonEntry, Date time, Double value) {
        Map<Date, List<Double>> seriesNmonEntry = null;
        SummaryReportEntry summaryReportEntry = null;

        // Does the complete set of series contain data for the nmonEntry?
        if (!allSeriesData.containsKey(nmonEntry)) {
            // Create the series aggregator
            seriesNmonEntry = new HashMap<Date, List<Double>>();
            allSeriesData.put(nmonEntry, seriesNmonEntry);
            // Create the summary report instance
            summaryReportEntry = new SummaryReportEntry();
            summaryReport.put(nmonEntry, new SummaryReportEntry());
            // Create the nmon chart data instance
            TimeSeries nmonChartSeries = new TimeSeries(nmonEntry);
            allChartSeries.put(nmonEntry, nmonChartSeries);
            allChartSeriesCollection.addSeries(nmonChartSeries);
        } else {
            seriesNmonEntry = allSeriesData.get(nmonEntry);
            summaryReportEntry = summaryReport.get(nmonEntry);
        }

        // Does the nmonEntry series contain data for the timestmap
        List<Double> seriesNmonEntryValues = null;
        if (!seriesNmonEntry.containsKey(time)) {
            seriesNmonEntryValues = new ArrayList<Double>();
            seriesNmonEntry.put(time, seriesNmonEntryValues);
        } else {
            seriesNmonEntryValues = seriesNmonEntry.get(time);
        }

        // Add another value for the provided timestamp
        seriesNmonEntryValues.add(value);

        // Update the summary report
        summaryReportEntry.nmonEntry = nmonEntry;
        summaryReportEntry.totalItems ++;
        summaryReportEntry.totalValue += value;
        summaryReportEntry.timeInMs = (time.getTime() - baseTime.getTime());
    }

    /**
     * Replace multiple entries per timestamp with a single average
     * Used for series over time
     */
    private void summariseAllNmonEntriesByTimestamp() {

        // Go through each Nmon entry and average values for each timestamp
        for (NmonEntryType nmonEntry : allSeriesData.keySet()) {
            Map<Date, List<Double>> valuesOverTime = allSeriesData.get(nmonEntry);

            // Go through each value and average out for the timestamp
            for (Date timestamp : valuesOverTime.keySet()) {
                List<Double> values = valuesOverTime.get(timestamp);
                Double totalValue = 0d;
                // Sum the values against the timestamp
                for (Double value : values) {
                    totalValue += value;
                }

                // Calculate the average and make it the only entry in the list
                Double valueForPointInTime = null;
                
                // Average doesn't apply to TOTAL
                if (nmonEntry.equals(NmonEntryType.TOP_TOTAL_HTTPD)) {
                    logger.debug(timestamp+":"+totalValue);
                    valueForPointInTime = totalValue;
                } else {
                    valueForPointInTime = totalValue/Double.valueOf(values.size());
                }
                values.clear();
                values.add(valueForPointInTime);

                // Add the amount for the timestamp to the chart series
                TimeSeries nmonChartSeries = allChartSeries.get(nmonEntry);
                Long pointInTime = timestamp.getTime() - baseTime.getTime();
//                logger.debug("Entering new series point:"+pointInTime+" "+valueForPointInTime);
                nmonChartSeries.add(new FixedMillisecond(pointInTime), valueForPointInTime);
            }

            // Log the aggregates for the nmonEntry
            logger.debug(summaryReport.get(nmonEntry));
        }
    }

    /**
     * Parse the timestamp entry
     * @param timestamp
     * @throws ParseException
     */
    private void parseTimestamp(String[] timestamp) throws ParseException {
        String timeId = timestamp[1];
        String time = timestamp[2];
        String date = timestamp[3];
        SimpleDateFormat nmonDateFormat = new SimpleDateFormat("HH:mm:ss dd-MMM-yyyy");
        Date nmonDate = nmonDateFormat.parse(time+" "+date);
        nmonDates.put(timeId, nmonDate);
        if (baseTime == null) baseTime = nmonDate;
    }

    /**
     * Data structure to hold summary info about nmon entries
     */
    private class SummaryReportEntry {
        NmonEntryType nmonEntry = null;
        Long totalItems = 0l;
        Long timeInMs = 0l;
        Double totalValue = 0d;

        public Double getTotalAverage() {
            return Double.valueOf(totalValue)/Double.valueOf(totalItems);
        }

        public Double getTotalAveragePerSec() {
            return (Double.valueOf(totalValue)/Double.valueOf(timeInMs))/1000;
        }

        public String toString() {
            return CHART_NAME+","+nmonEntry+","+getTotalAverage()+","+getTotalAveragePerSec()+","+totalValue+","+timeInMs;
        }
    }

    /**
     * Write out the summary to a file
     */
    @Override
    String createReport() {
       StringBuffer csv = new StringBuffer();
       System.out.println("\n");
       for (NmonEntryType nmonEntry : summaryReport.keySet()) {
           SummaryReportEntry summaryEntry = summaryReport.get(nmonEntry);
           csv.append(summaryEntry+"\n");
           System.out.println(summaryEntry);
       }
       return csv.toString();
    }

    private void createJVMChart() throws IOException {
        if (!allChartSeries.containsKey(NmonEntryType.JVM_FREE_MEMORY)) return;
        String chartName = getChartName()+"-JVM";
        String filename = getChartFileName(chartName);
        TimeSeriesCollection seriesCollection = new TimeSeriesCollection();
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.JVM_FREE_MEMORY));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.JVM_TOTAL_MEMORY));
        JFreeChart chart = createChartObject(seriesCollection, chartName, "Memory (KB)");

        // Add Threads
        XYPlot plot = chart.getXYPlot();
        addAxis(plot, 5, NmonEntryType.JVM_ACTIVE_THREADS, AxisLocation.TOP_OR_RIGHT);

        // Add Actiove Sessions
        addAxis(plot, 6, NmonEntryType.WCTX_ACTIVE_SESSIONS, AxisLocation.TOP_OR_RIGHT);

        // Create graph
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);
    }

    private void createOHSChart() throws IOException {
        if (!allChartSeries.containsKey(NmonEntryType.OHS_BUSY_CHILD)) return;
        String chartName = getChartName()+"-OHS";
        String filename = getChartFileName(chartName);
        TimeSeriesCollection seriesCollection = new TimeSeriesCollection();
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.OHS_BUSY_CHILD));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.OHS_NUM_CHILD));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.OHS_ACTIVE_CONN));        
        JFreeChart chart = createChartObject(seriesCollection, chartName, "Processes");

        // Add Actiove Sessions
        XYPlot plot = chart.getXYPlot();        
        addAxis(plot, 5, NmonEntryType.WCTX_ACTIVE_SESSIONS, AxisLocation.TOP_OR_RIGHT);

        // Create graph
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);
    }

    private void createJDBCChart() throws IOException {
        if (!allChartSeries.containsKey(NmonEntryType.JDBC_FREE_POOL_SIZE)) return;        
        String chartName = getChartName()+"-JDBC";
        String filename = getChartFileName(chartName);
        TimeSeriesCollection seriesCollection = new TimeSeriesCollection();        
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.OHS_BUSY_CHILD));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.JDBC_FREE_POOL_SIZE));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.JDBC_MIN_FREE_POOL_SIZE));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.JDBC_POOL_SIZE));
        JFreeChart chart = createChartObject(seriesCollection, chartName, "Connections");

        // Add Actiove Sessions
        XYPlot plot = chart.getXYPlot();
        addAxis(plot, 5, NmonEntryType.WCTX_ACTIVE_SESSIONS, AxisLocation.TOP_OR_RIGHT);        

//        // Add Threads
//        XYPlot plot = chart.getXYPlot();
//        addAxis(plot, 3, NmonEntryType.JDBC_USE_TIME, AxisLocation.TOP_OR_RIGHT);
//        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.JDBC_USE_TIME));

        // Create graph
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);
    }

    public void createNmonChart() throws IOException {
        if (!allChartSeries.containsKey(NmonEntryType.CPU_ALL_SYS)) return;

        String chartName = getChartName()+"-NMON";
        String filename = getChartFileName(chartName);

        TimeSeriesCollection seriesCollection = new TimeSeriesCollection();
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.CPU_ALL_USER));
        seriesCollection.addSeries(allChartSeries.get(NmonEntryType.CPU_ALL_SYS));


        JFreeChart chart = createChartObject(seriesCollection, chartName, "CPU %");

        // Add Threads
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(new XYAreaRenderer2());
        plot.setForegroundAlpha(0.50f);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

        addAxis(plot, 3, NmonEntryType.TOP_MAJOR_FAULT, AxisLocation.TOP_OR_RIGHT, Color.DARK_GRAY);
        //addAxis(plot, 3, NmonEntryType.SWP_ACTIVITY, AxisLocation.TOP_OR_RIGHT, Color.RED);
        addAxis(plot, 4, NmonEntryType.TOP_TOTAL_HTTPD, AxisLocation.TOP_OR_RIGHT, Color.RED);
        addAxis(plot, 5, NmonEntryType.MEM_FREE, AxisLocation.TOP_OR_LEFT, Color.BLUE);
        addAxis(plot, 6, NmonEntryType.SWAP_FREE, AxisLocation.TOP_OR_LEFT, Color.CYAN);


        // Fix Range for CPU to 100
        ValueAxis rangeAxis = plot.getRangeAxis(0);
        rangeAxis.setRange(0.0d, 100.0d);

        // Create graph
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);        
    }

    void createExtraCharts() throws IOException {
        createJVMChart();
        createOHSChart();
        createJDBCChart();
        createNmonChart();
    }

    /**
     * Helper method to create a standard chart
     * @param dataset
     * @param title
     * @param YAxisLabel
     * @return
     */
    JFreeChart createChartObject(XYDataset dataset, String title, String YAxisLabel) {
        boolean showLegend = false;
        if (dataset.getSeriesCount() <= 40) {
            showLegend = true;
        }

        // create the chart...
        JFreeChart chartObject = ChartFactory.createTimeSeriesChart(
            title,                    // chart title
            "Time",                    // x axis label
            YAxisLabel,                    // y axis label
            dataset,                  // data
            showLegend,               // include legend
            false,                    // tooltips
            false                     // urls
        );

        XYPlot plot = chartObject.getXYPlot();
        DateAxis axis = (DateAxis) plot.getDomainAxis();
        axis.setDateFormatOverride(new SimpleDateFormat("mm:ss"));

        return chartObject;
    }

    /**
     * Main chart created from createExtraCharts
     * @param dataset
     * @param name
     * @return
     */
    JFreeChart createChart(XYDataset dataset, String name) {
        return null;
//        boolean showLegend = false;
//        if (dataset.getSeriesCount() <= 40) {
//            showLegend = true;
//        }
//
//        // create the chart...
//        JFreeChart chart = ChartFactory.createTimeSeriesChart(
//            name,                       // chart title
//            "Minutes",                  // x axis label
//            "Total CPU",                // y axis label
//            dataset,                    // data
//            showLegend,                 // include legend
//            false,                      // tooltips
//            false                       // urls
//        );
//
//        chart.setBackgroundPaint(Color.white);
//
//        // get a reference to the plot for further customisation...
//        XYPlot plot = chart.getXYPlot();
//        DateAxis axis = (DateAxis) plot.getDomainAxis();
//        axis.setDateFormatOverride(new SimpleDateFormat("mm:ss"));
//
//        addAxis(plot, 2, NmonEntryType.TOP_MAJOR_FAULT, AxisLocation.BOTTOM_OR_RIGHT);
//        addAxis(plot, 3, NmonEntryType.CPU_ALL_SYS, AxisLocation.TOP_OR_RIGHT);
//        addAxis(plot, 4, NmonEntryType.TOP_TOTAL_HTTPD, AxisLocation.TOP_OR_LEFT);
//
//        // get a reference to the plot for further customisation...
//        plot.setBackgroundPaint(Color.lightGray);
//        plot.setDomainGridlinePaint(Color.white);
//        plot.setRangeGridlinePaint(Color.white);
//
//        return chart;
    }

    private void addAxis(XYPlot plot, int index, NmonEntryType nmonEntryType, AxisLocation axisLocation, Color colour) {
        if (!allChartSeries.containsKey(nmonEntryType)) {
            logger.warn("Could not find series for "+nmonEntryType);
            return;
        }        
        addAxis(plot, index, nmonEntryType, axisLocation);
        XYItemRenderer renderer = plot.getRenderer(index);
        renderer.setSeriesPaint(index, colour);

    }
    /**
     * Add axis based on series for entry type
     * @param plot
     * @param index
     * @param nmonEntryType
     * @param axisLocation
     */
    private void addAxis(XYPlot plot, int index, NmonEntryType nmonEntryType, AxisLocation axisLocation) {
        if (!allChartSeries.containsKey(nmonEntryType)) {
            logger.warn("Could not find series for "+nmonEntryType);
            return;
        }
        NumberAxis axis = new NumberAxis(nmonEntryType.toString());
        plot.setRangeAxis(index, axis);
        plot.setRangeAxisLocation(index, axisLocation);
        TimeSeriesCollection seriesCollection = new TimeSeriesCollection();
        seriesCollection.addSeries(allChartSeries.get(nmonEntryType));      
        plot.setDataset(index, seriesCollection);
        plot.mapDatasetToRangeAxis(index, index);
        // Provide own renderer for unique colour
        XYItemRenderer renderer = new StandardXYItemRenderer();
        renderer.setSeriesStroke(index, new BasicStroke(4.0f));
        plot.setRenderer(index, renderer);        
    }


    public static void main(String[] args) throws Exception {
        NmonOverTime nmonChart = new NmonOverTime();
        nmonChart.saveChartAsFile(args);
        return;
    }
}

