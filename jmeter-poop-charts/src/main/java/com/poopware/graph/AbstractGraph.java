package com.poopware.graph;

import org.jfree.data.xy.XYDataset;
import org.jfree.data.general.Series;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartUtilities;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.poopware.transform.JmeterJtlEntry;

import java.util.Set;
import java.util.HashSet;
import java.io.*;
import java.text.ParseException;

import au.com.bytecode.opencsv.CSVReader;
public abstract class AbstractGraph {
    private final static Log logger = LogFactory.getLog(AbstractGraph.class);

    private enum Option {
        CHART_NAME, INPUT_CSV, OUTPUT_FOLDER, RESPONSE_INT, SESSION_MIN, SESSION_MAX, RESPONSE_INT_MAX, RESPONSE_MIN,
        RESPONSE_MAX, TIME_START_MS, TIME_END_MS, FILTER, AVOID_RAMP, INTERVAL_SIZE
    }
    String CHART_NAME;
    private String INPUT_CSV;
    String OUTPUT_FOLDER;
    int RESPONSE_INT = -1;
    int RESPONSE_INT_MAX = -1;
    int RESPONSE_MIN = -1;
    int RESPONSE_MAX = 99999999;
    long SESSION_MIN = 0; // Maximum sessions to report on
    long SESSION_MAX = 9999999999l; // Minimum sessions to report on
    long TIME_START_MS = 0;
    long TIME_END_MS = 9999999999l;
    long INTERVAL_SIZE = 1000; // Time series interval
    String FILTER;
    int[] gradeIntervals = {0,-1};
    boolean avoidRamp = false; // Flag to avoid ramp up values

    /**
     *
     */
    private void outputUsage() {
        System.out.println("Usage: [-CHART_NAME <NAME> -INPUT_CSV <NAME> -OUTPUT_FOLDER <NAME>] {-RESPONSE_INT <NAME> -RESPONSE_INT_MAX <NAME> -TIME_START_MS <NAME> -TIME_END_MS <NAME>}");
        throw new IllegalArgumentException();
    }

    /**
     * 
     */
    private void validateMandatoryArgs() {
        if (CHART_NAME == null || INPUT_CSV == null || OUTPUT_FOLDER == null) outputUsage();
    }

    /**
     * Create the grade intervals
     * e.g.
     * 0-500 - 0
     * 500-1000 - 1
     * etc.
     */
    private void generateGradeIntervals() {
        if (RESPONSE_INT_MAX == -1 && RESPONSE_INT == -1) return;
        gradeIntervals = new int[(RESPONSE_INT_MAX/RESPONSE_INT)+1];
        int index = 0;
        int currentInterval =0;
        while (currentInterval < RESPONSE_INT_MAX) {
            gradeIntervals[index] = currentInterval;
            index++;
            currentInterval+=RESPONSE_INT;
        }
        gradeIntervals[gradeIntervals.length-1] = -1;
    }

    /**
     * Decide which grade the series got
     * @param reportCard reportCard
     * @return int grade
     */
    int calculateSeriesGrade(long reportCard) {

        int grade = -1;
        for (int i=0; i < gradeIntervals.length; i++) {
            long intervalStart = gradeIntervals[i];
            long intervalEnd = gradeIntervals[i+1];
            if ( intervalEnd == -1) intervalEnd = reportCard + 1; // infinity
            if (reportCard >= intervalStart && reportCard < intervalEnd) {
                grade=i;
                break;
            }
        }
        return grade;
    }    

    /**
     * Usage: <chart name> <input csv> <output folder> <intervalFlag> <filter>
     * @param args args
     * @throws IllegalArgumentException
     */
    void processArgs(String[] args) throws IllegalArgumentException{
        System.out.println("Processing arguments..");
        // Parse options into argument map
        for (int i=0; i<args.length;i++) {
            String optionText=args[i];
            if (optionText == null || !optionText.startsWith("-")) {
                outputUsage();
            }

            // Remove switch
            optionText = optionText.replace("-","");

            // Identify option
            Option option = null;
            try {
                option = Option.valueOf(optionText);
            } catch (IllegalArgumentException iae) {
                System.out.println("Illegal Argument:"+optionText);
                outputUsage();
            }

            // Process command
            switch (option) {
                 case CHART_NAME: CHART_NAME=args[++i]; System.out.println("CHART_NAME:" + CHART_NAME); break;
                 case INPUT_CSV: INPUT_CSV=args[++i]; System.out.println("INPUT_CSV:" + INPUT_CSV); break;
                 case OUTPUT_FOLDER: OUTPUT_FOLDER=args[++i]; System.out.println("OUTPUT_FOLDER:" + OUTPUT_FOLDER); break;
                 case RESPONSE_INT: RESPONSE_INT=Integer.valueOf(args[++i]); System.out.println("RESPONSE_INT:" + RESPONSE_INT); break;
                 case RESPONSE_INT_MAX: RESPONSE_INT_MAX=Integer.valueOf(args[++i]);System.out.println("RESPONSE_INT_MAX:" + RESPONSE_INT_MAX);break;
                 case RESPONSE_MIN: RESPONSE_MIN=Integer.valueOf(args[++i]); System.out.println("RESPONSE_MIN:" + RESPONSE_MIN); break;
                 case RESPONSE_MAX: RESPONSE_MAX=Integer.valueOf(args[++i]);System.out.println("RESPONSE_MAX:" + RESPONSE_MAX);break;
                 case SESSION_MIN: SESSION_MIN=Integer.valueOf(args[++i]); System.out.println("SESSION_MIN:" + SESSION_MIN); break;
                 case SESSION_MAX: SESSION_MAX=Integer.valueOf(args[++i]);System.out.println("SESSION_MAX:" + SESSION_MAX);break;
                 case TIME_START_MS: TIME_START_MS=Long.valueOf(args[++i]);System.out.println("TIME_START_MS:" + TIME_START_MS);break;
                 case TIME_END_MS: TIME_END_MS=Long.valueOf(args[++i]);System.out.println("TIME_END_MS:" + TIME_END_MS);break;
                 case INTERVAL_SIZE: INTERVAL_SIZE=Long.valueOf(args[++i]);System.out.println("INTERVAL_SIZE:" + INTERVAL_SIZE);break;                 
                 case FILTER: FILTER=args[++i];System.out.println("FILTER:" + FILTER);break;
                 case AVOID_RAMP: System.out.println("AVOID_RAMP: true");avoidRamp = true;break;
            }
        }

        if (FILTER != null) FILTER = FILTER.toLowerCase(); // make it case insensitive

        validateMandatoryArgs();
        generateGradeIntervals();
    }

    /**
     * Work out how many sessions before ramp up stops
     * @param csvFilename
     * @throws IOException
     */
    private void avoidRampUp(String csvFilename) throws IOException {
        CSVReader reader = new CSVReader(new FileReader(csvFilename));
        reader.readNext();
        String [] nextLine;
        Set activeSessions = new HashSet();
        while ((nextLine = reader.readNext()) != null) {
            if (nextLine.length < 3) {
                continue;
            }
            JmeterJtlEntry jtlEntry = new JmeterJtlEntry(nextLine);
            activeSessions.add(jtlEntry.getThreadName());
        }
        reader.close();
        SESSION_MIN = activeSessions.size();
        logger.info("Ramp up maximum occurs at "+SESSION_MIN+" sessions - will record details after that");
    }

    /**
     * Format the chart name
     * @return
     */
    String getChartName() {
        // Create a graph for the series in each grade
        String chartName = CHART_NAME;

        if (FILTER != null) chartName+=FILTER;
        // Don't add response band since it makes the filenames really long
        // if (RESPONSE_MAX != -1) chartName+= "-rm"+RESPONSE_MIN + "-rx"+RESPONSE_MAX;
        return chartName;
    }

    String getChartFileName(String chartName) {
        return OUTPUT_FOLDER+File.separator+chartName+".jpg";        
    }

    /**
     * Generate the chart to a jpg
     * @throws java.io.IOException
     */
    void saveChartAsFile(String[] args) throws IOException, ParseException {
        processArgs(args);

        // Avoid the ramp up period?
        if (avoidRamp) avoidRampUp(INPUT_CSV);

        // Read data
        XYDataset dataset = readData(INPUT_CSV, FILTER);

        // Create a graph for the series in each grade
        if (dataset != null) {
            this.createSummaryReport(OUTPUT_FOLDER+File.separator+CHART_NAME+FILTER+".report");
            String chartName = getChartName();
            String filename = getChartFileName(chartName);
            createChartForDataset(chartName, filename, dataset);
        }

        createExtraCharts();
    }

    /**
     * Allows sub-classes to create additional charts
     */
    void createExtraCharts() throws IOException {
        return;        
    }
    
    /**
     * Create a chart from the provided data
     */
    void createChartForDataset(String chartName, String filename, XYDataset dataset) throws IOException {
        logger.info("Writing chart:" + filename);
        JFreeChart chart = createChart(dataset , chartName);
        if (chart == null) {
            logger.warn("Not saving chart "+chartName+" (no chart provided)");
            return;
        }
        logger.info("Saving chart:"+filename);
        ChartUtilities.saveChartAsJPEG(new File(filename), chart, 1000, 600);
    }

    /**
     * Default summary report
     * @param filename
     */
    public void createSummaryReport(String filename) throws IOException {
       String report = createReport();
       if (report == null) return;

       FileWriter fw = new FileWriter(new File(filename));
       BufferedWriter bw = new BufferedWriter(fw);
       bw.write(report);
       bw.flush();
    }

    /**
     * Default implementation of report generation
     * @return
     */
    String createReport() {
        return null;
    };

    /**
     * Create chart from dataset with the provided name
     * @param dataset dataset
     * @param chartName chartName
     * @return JFreeChart chart
     */
    abstract JFreeChart createChart(XYDataset dataset, String chartName);

    /**
     * Read data from file and put into data keyed by the 'grade' of its contents
     * For example, fast responses would be grade 0
     * @param inputFile inputFile
     * @param filter filter
     * @return Reviewed data
     * @throws java.io.IOException exception
     */
    abstract XYDataset readData(String inputFile, String filter) throws IOException, ParseException;
 
}
