package com.poopware.transform;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Arrays;

public class JmeterJtlEntry {
    private final static Log logger = LogFactory.getLog(JmeterJtlEntry.class);
    String[] attributes;
    
    // <httpSample t="326" lt="326" ts="1248913029722" s="true" lb="RequestLabel" rc="302"
    // rm="Moved Temporarily" tn="ThreadName" dt="text" by="192">
    private static final int T = 0;
    private static final int LT = 1;
    private static final int TS = 2;
    private static final int S = 3;
    private static final int LB = 4;
    private static final int RC = 5;
    private static final int RM = 6;
    private static final int TN = 7;
    private static final int DT = 8;
    private static final int BY = 9;    

    public JmeterJtlEntry(String[] attributes) {
       if (attributes == null) {
          logger.error("No attributes provided");
          return;
       }
       if (attributes.length < 10) {
         logger.error("Expected at least 10 items per line:" + Arrays.toString(attributes));
         return;
       }
        
       this.attributes = attributes;
    }

    public long getResponseTime() {
        return Long.valueOf(attributes[T]);
    }

    public long getTimestamp() {
        return Long.valueOf(attributes[TS]);
    }

    public String getRequestLabel() {
        return attributes[LB];
    }

    public String getThreadName() {
        return attributes[TN];
    }

    public boolean getSuccess() {
        return Boolean.valueOf(attributes[S]);
    }

    public long getBytes() {
        return Long.valueOf(attributes[BY]);
    }
}
