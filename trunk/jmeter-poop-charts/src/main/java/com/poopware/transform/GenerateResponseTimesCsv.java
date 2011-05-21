package com.poopware.transform;

import java.io.IOException;

public class GenerateResponseTimesCsv {

   // --- ---------------------------------------------------------------------
   // Reads from current directory
   //    ADayInTheLifeOfResults.xml
   //    responsetimes.xsl
   //
   // Writes to current directory
   //    responsetimes.201.2000.csv
   //
   // Need to run with arguments -Xms512m -Xmx512m
   // in order to avoid out of memory error
   // --- ---------------------------------------------------------------------
   public static void main(String[] args) throws IOException {
      String inputFile = "ADayInTheLifeOfResults.xml";
      String outputCsvFile = "requestsAndMilliseconds.text";
      String xslFile = "responsetimes.xsl";

       if ( args.length > 0) {
           if (args.length < 3) {
            System.out.println("Usage: <intputJmeterResults> <outputCsv> <xslTransformFile>");
            return;
           }

           inputFile = args[0];
           outputCsvFile = args[1];
           xslFile = args[2];
       }
       
      // transform  ADayInTheLifeOfResults.xml
      // into       requestsAndMilliseconds.text
      // using      responsetimes.xsl
      TransformResultsRetrieveResponseTimes.xsl(
         inputFile,
         outputCsvFile,
         xslFile);
      
      BucketFiller bf = new BucketFiller();
      // CSV values from jmeter results
      bf.setInputCsvFile(outputCsvFile);
      // Buckets file
      bf.setOutputCsvFile("responsetimes.201.2000.csv");
      // fill the buckets using the information in requestsAndMilliseconds.text
      bf.fill();

      // output the bucket information to responsetimes.201.2000.csv
      bf.outputToCsv();
   }
}
