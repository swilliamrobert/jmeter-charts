package com.poopware.transform;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import au.com.bytecode.opencsv.CSVReader;

public class BucketFiller {
   private final static Log logger = LogFactory.getLog(BucketFiller.class);
    
   private SortedMap<String, int[]> bucketMap;
   private final static int NUMBEROFBUCKETS = 400;
   private final static int INCREMENT = 10;        // milliseconds

   private String inputCsvFile = "requestsAndMilliseconds.text";
   private String outputCsvFile = "requestsInBuckets.csv";


    public void setInputCsvFile(String inputCsvFile) {
        this.inputCsvFile = inputCsvFile;
    }

    public void setOutputCsvFile(String outputCsvFile) {
        this.outputCsvFile = outputCsvFile;
    }    

   // --- ---------------------------------------------------------------------
   // Fill the buckets using the requests (Login GET) and the milliseconds (32)
   // --- ---------------------------------------------------------------------
   void fill() throws IOException {
        bucketMap = new TreeMap<String,int[]>();
        File inputCsv = new File(inputCsvFile);
        int failCount = 0;
        if (!inputCsv.exists()) {
         logger.error("Input file doesn't exist " + inputCsv.getName());
         return;
        }

        CSVReader reader = new CSVReader(new FileReader(inputCsv));
        String la[];
        while ((la = reader.readNext()) != null) {

            if (la.length < 4) {
                logger.error("Expected at least 4 items per line:" + Arrays.toString(la));
                continue;
            }

            JmeterJtlEntry jtlEntry = new JmeterJtlEntry(la);
            String request = jtlEntry.getRequestLabel();
            long milliseconds = jtlEntry.getResponseTime();

            if (!jtlEntry.getSuccess()) {
                failCount++;
                continue;
            }
            jtlEntry.getResponseTime();
            store(request, milliseconds);
        }

        if (failCount > 0) {
            logger.warn(failCount+" requests were ignored because they had failed");
        }

   }
   
   // --- ---------------------------------------------------------------------
   // Given the request type and the milliseconds, update
   // requestsWithTheirResponseTimeBuckets.
   // --- ---------------------------------------------------------------------
   private void store(String request, long milliseconds) {

      int[] responseTimeBuckets = bucketMap.get(request);
      if (responseTimeBuckets == null) {
         responseTimeBuckets = new int[NUMBEROFBUCKETS];
         bucketMap.put(request,responseTimeBuckets);
      }
      int index = whichBucketToIncrement(milliseconds);
      responseTimeBuckets[index] = responseTimeBuckets[index] + 1;
   }

   private int whichBucketToIncrement(long milliseconds) {
      int index = 0;
      if (milliseconds >= INCREMENT * NUMBEROFBUCKETS) {
         index = NUMBEROFBUCKETS - 1; // last bucket
      } else if (milliseconds < 1) {
         index = 0; // first bucket
      } else {
         // round down
         index = (int)Math.floor(milliseconds/INCREMENT);
      }
      return index;
   }
   
   // --- ---------------------------------------------------------------------
   // Given bucketMap in format {Login GET,[0][5][50][121][67]...}
   // output details to a csv file
   // --- ---------------------------------------------------------------------
   void outputToCsv() {
      
      Assert.notNull(bucketMap, "bucketMap is null");
      Assert.notEmpty(bucketMap, "bucketMap is empty");
      
      try {
         PrintStream csv 
           = new PrintStream(new FileOutputStream(outputCsvFile));

         // Header
         int increment = INCREMENT;
         csv.print("n,%>2000,Label");
         for (int i = 0; i < NUMBEROFBUCKETS - 1; i++) {
            csv.print(",");
            csv.print(increment * i);
            csv.print("|");
            csv.print((increment * (i +1)) -1);
         }
         csv.print("," + (increment * (NUMBEROFBUCKETS-1)) + ("|")); // last column
         csv.println();
         
         // Data
         NumberFormat formatter = new DecimalFormat("0.000"); 
         for (String label : bucketMap.keySet()) {
            int[] buckets = bucketMap.get(label);
            int n = 0;
            StringBuffer sb = new StringBuffer();
            for (int bucket : buckets) {
               sb.append("," + bucket);
               n = n + bucket;
            }
            int numberOverTwoSeconds = numberOverTwoSeconds(buckets);
            double percentageOverTwoSeconds = (numberOverTwoSeconds * 100.0)/n;
            String s = formatter.format (percentageOverTwoSeconds) ; 
            csv.println(n + "," + s + "%," + label + sb);
         }
      } catch (FileNotFoundException fnf) {
         throw new RuntimeException(fnf);
      }
   }

   // --- ---------------------------------------------------------------------
   // What is the number of responses that took more than two seconds
   // for this request type ?
   // --- ---------------------------------------------------------------------
   private int numberOverTwoSeconds(int[] buckets) {
      int result = 0;
      for (int i = 0; i <= buckets.length; i++) {
         if (i * INCREMENT > 2000) {
            result = result + buckets[i -1];
         }
      }
      return result;
   }

    public static void main(String args[]) throws IOException {
        if (args.length < 2) {
            System.out.println("BucketFiller <inputJtlCsv> <outputBucketsCsv>");
            return;
        }

        String inputCsv=args[0];
        String outputCsv=args[1];

        BucketFiller filler = new BucketFiller();
        filler.setInputCsvFile(inputCsv);
        filler.setOutputCsvFile(outputCsv);
        filler.fill();
        filler.outputToCsv();
    }
}
