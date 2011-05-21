<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
   <xsl:output method="text"/>
   <xsl:template match="httpSample"><xsl:value-of select="@lb"/>,<xsl:value-of select="@t"/>,<xsl:value-of select="@ts"/>,<xsl:value-of select="@s"/>,<xsl:value-of select="@tn"/>,<xsl:value-of select="@ng"/>,<xsl:value-of select="@na"/></xsl:template>
</xsl:stylesheet>
<!-- 

   time:<xsl:value-of select="@t"/>,
   <xsl:value-of select="@lt"/>,
   timestamp:<xsl:value-of select="@ts"/>,
   success:<xsl:value-of select="@s"/>,
   label:<xsl:value-of select="@lb"/>,
   responseCode:<xsl:value-of select="@rc"/>,
   responseMessage:<xsl:value-of select="@rm"/>,
   threadName:<xsl:value-of select="@tn"/>,
   dataType:<xsl:value-of select="@dt"/>,
   <xsl:value-of select="@by"/>

na	 Number of active threads for all thread groups
ng	 Number of active threads in this group
rc	 Response Code (e.g. 200)
rm	 Response Message (e.g. OK)
s	 Success flag (true/false)
sc	 Sample count (1, unless multiple samples are aggregated)
t	 Elapsed time (milliseconds)
tn	 Thread Name

<httpSample t="32" 
            lt="32" 
            ts="1236852790285" 
            s="true" 
            lb="Logout GET (response 302)" 
            rc="302" 
            rm="Moved Temporarily" 
            tn="40 Users 100 2-1" 
            dt="text" 
            by="204"
            >


            
hs
      
  
[rc]    302
        false
        false
  
-->