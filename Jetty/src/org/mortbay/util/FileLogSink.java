// ========================================================================
// Copyright (c) 1997 MortBay Consulting, Sydney
// $Id$
// ========================================================================

package org.mortbay.util;

import java.io.IOException;


/* ------------------------------------------------------------ */
/** File Log Sink.
 * @deprecated Use WriterLogSink
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class FileLogSink extends WriterLogSink
{
    /*-------------------------------------------------------------------*/
    private String _fileName=null;
    
    /* ------------------------------------------------------------ */
    /** Constructor.
     * @deprecated Use WriterLogSink
     */
    public FileLogSink()
        throws IOException
    {
    	super(System.getProperty("LOG_FILE","log.txt"));
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor.
     * @deprecated Use WriterLogSink
     */
    public FileLogSink(String filename)
        throws IOException
    {
        super(filename);
    }
    
}




