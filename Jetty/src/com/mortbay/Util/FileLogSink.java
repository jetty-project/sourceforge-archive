// ========================================================================
// Copyright (c) 1997 MortBay Consulting, Sydney
// $Id$
// ========================================================================

package com.mortbay.Util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;


/* ------------------------------------------------------------ */
/** File Log Sink.
 * This implementation of Log Sink writes logs to a file. Currently
 * it is a trivial implementation and represents a place holder for
 * future implementations of file handling which is dated, rolling,
 * etc.
 *
 * @see
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class FileLogSink extends WriterLogSink
{
    /*-------------------------------------------------------------------*/
    private String _fileName=null;
    
    /* ------------------------------------------------------------ */
    public FileLogSink()
        throws IOException
    {
    	this(System.getProperty("LOG_FILE","log.txt"));
    }
    
    /* ------------------------------------------------------------ */
    public FileLogSink(String filename)
        throws IOException
    {
        super(new PrintWriter(new FileWriter(filename)));
        _fileName=filename;
    }
    
    /* ------------------------------------------------------------ */
    public void stop()
    {
        _out.close();
    }
}




