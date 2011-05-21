package com.poopware.transform;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TransformResultsRetrieveResponseTimes {

   protected final static Log logger 
      = LogFactory.getLog(TransformResultsRetrieveResponseTimes.class);

   // --- ---------------------------------------------------------------------
   // Apply the xslFilename to inFilename.xml and output to outFilename.xml.
   // --- ---------------------------------------------------------------------
   static void xsl(String inFilename, String outFilename, String xslFilename) {
      try {
         logger.info("Creating transformer factory");
         TransformerFactory factory = TransformerFactory.newInstance();

         logger.info("The factory creates a template containing the xsl file");
         Templates template = factory.newTemplates(new StreamSource(
               new FileInputStream(xslFilename)));

         logger.info("Using the template to create a transformer");
         Transformer xformer = template.newTransformer();

         logger.info("Preparing the input and output files");
         Source source = new StreamSource(new FileInputStream(inFilename));
         Result result = new StreamResult(new FileOutputStream(outFilename));

         logger.info("Applying the xsl file to the source file " +
                     "and writing the result to the output file");
         xformer.transform(source, result);
         logger.info("Finished transformation");
      } catch (FileNotFoundException fnfe) {
         throw new RuntimeException(fnfe);
      } catch (TransformerConfigurationException tce) {
         throw new RuntimeException(tce);
         // An error occurred in the XSL file
      } catch (TransformerException te) {
         // An error occurred while applying the XSL file
         // Get location of error in input file
         SourceLocator locator = te.getLocator();
         int col = locator.getColumnNumber();
         int line = locator.getLineNumber();
         String publicId = locator.getPublicId();
         String systemId = locator.getSystemId();
         throw new RuntimeException(col + " " + line + " " 
                              + publicId + " " + systemId, te);
      }
   }
}
