// ========================================================================
// Copyright (c) 1997 MortBay Consulting, Sydney
// $Id$
// ========================================================================

package com.mortbay.Util;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


/* ------------------------------------------------------------ */
/** A Log sink.
 * This class represents both a concrete or abstract sink of
 * Log data.  The default implementation logs to a PrintWriter, but
 * derived implementations may log to files, syslog, or other
 * logging APIs.
 *
 * 
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class WriterLogSink
    implements LogSink
{
    /*-------------------------------------------------------------------*/
    protected String _logDateFormat="yyyyMMddHHmmssSSSzzz";
    protected String _logTimezone="GMT";
    protected DateCache _dateFormat=null;
    
    protected boolean _logTimeStamps=true;
    protected boolean _logLabels=true;
    protected boolean _logTags=true;
    protected boolean _logStackSize=true;
    protected boolean _logStackTrace=false;
    protected boolean _logOneLine=false;
    
    /*-------------------------------------------------------------------*/
    protected PrintWriter _out=null;
    protected boolean _started;
    
    /*-------------------------------------------------------------------*/
    private StringBuffer _stringBuffer = new StringBuffer(512);
    
    /* ------------------------------------------------------------ */
    private static final String __lineSeparator =
        System.getProperty("line.separator");
    private static final String __indentBase = 
    "  ";
    private static final String __indentSeparator =
        __lineSeparator+__indentBase;
    private static final int __lineSeparatorLen =
        __lineSeparator.length();
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public WriterLogSink()
    {
        _out=new PrintWriter(System.err);
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param out 
     */
    public WriterLogSink(PrintWriter out)
    {
        _out=out;
    }
    
    /*-------------------------------------------------------------------*/
    /** Set the log options
     *
     * @param logOptions A string of characters as defined for the
     * LOG_OPTIONS system parameter.
     */
    public void setOptions(String dateFormat,
                           String timezone,
                           boolean logTimeStamps,
                           boolean logLabels,
                           boolean logTags,
                           boolean logStackSize,
                           boolean logStackTrace,
                           boolean logOneLine)
    {
        setLogDateFormat(dateFormat);
        setLogTimezone(timezone);
        _logTimeStamps      = logTimeStamps;
        _logLabels          = logLabels;
        _logTags            = logTags;
        _logStackSize       = logStackSize;
        _logStackTrace      = logStackTrace;
        _logOneLine         = logOneLine;
    }
    
    /* ------------------------------------------------------------ */
    public String getLogDateFormat()
    {
        return _logDateFormat;
    }
    /* ------------------------------------------------------------ */
    public void setLogDateFormat(String logDateFormat)
    {
        _logDateFormat = logDateFormat.replace('+',' ');
        _dateFormat = new DateCache(_logDateFormat);
    }
    /* ------------------------------------------------------------ */
    public String getLogTimezone()
    {
        return _logTimezone;
    }
    /* ------------------------------------------------------------ */
    public void setLogTimezone(String logTimezone)
    {
        _logTimezone=logTimezone;
        if (_dateFormat!=null)
            _dateFormat.getFormat().setTimeZone(TimeZone.getTimeZone(logTimezone));
    }
    /* ------------------------------------------------------------ */
    public boolean isLogTimeStamps()
    {
        return _logTimeStamps;
    }
    /* ------------------------------------------------------------ */
    public void setLogTimeStamps(boolean logTimeStamps)
    {
        _logTimeStamps = logTimeStamps;
    }
    /* ------------------------------------------------------------ */
    public boolean isLogLabels()
    {
        return _logLabels;
    }
    /* ------------------------------------------------------------ */
    public void setLogLabels(boolean logLabels)
    {
        _logLabels = logLabels;
    }
    /* ------------------------------------------------------------ */
    public boolean isLogTags()
    {
        return _logTags;
    }
    /* ------------------------------------------------------------ */
    public void setLogTags(boolean logTags)
    {
        _logTags = logTags;
    }
    /* ------------------------------------------------------------ */
    public boolean isLogStackSize()
    {
        return _logStackSize;
    }
    /* ------------------------------------------------------------ */
    public void setLogStackSize(boolean logStackSize)
    {
        _logStackSize = logStackSize;
    }
    /* ------------------------------------------------------------ */
    public boolean isLogStackTrace()
    {
        return _logStackTrace;
    }
    /* ------------------------------------------------------------ */
    public void setLogStackTrace(boolean logStackTrace)
    {
        _logStackTrace = logStackTrace;
    }
    /* ------------------------------------------------------------ */
    public boolean isLogOneLine()
    {
        return _logOneLine;
    }
    /* ------------------------------------------------------------ */
    public void setLogOneLine(boolean logOneLine)
    {
        _logOneLine = logOneLine;
    }

    
    /* ------------------------------------------------------------ */
    /** Log a message.
     * This method formats the log information as a string and calls
     * log(String).  It should only be specialized by a derived
     * implementation if the format of the logged messages is to be changed.
     *
     * @param tag Tag for type of log
     * @param msg The message
     * @param frame The frame that generated the message.
     * @param time The time stamp of the message.
     */
    public void log(String tag,
                    Object msg,
                    Frame frame,
                    long time)
    {
        // Lock static buffer
        synchronized(_stringBuffer)
        {
            _stringBuffer.setLength(0);
            
            // Log the time stamp
            if (_logTimeStamps)
                _stringBuffer.append(_dateFormat.format(time));
        
            // Log the tag
            if (_logTags)
                _stringBuffer.append(tag);

            // Log the label
            if (_logLabels && frame != null)
            {
                _stringBuffer.append(frame.toString());
            }
            
            
            // Determine the indent string for the message and append it
            // to the buffer. Only put a newline in the buffer if the first
            // line is not blank
            String indent = "";
            String indentSeparator = _logOneLine?"\\n ":__lineSeparator;
            if (_stringBuffer.length() > 0)
            	_stringBuffer.append(indentSeparator);
            _stringBuffer.append(__indentBase);
            
            if (_logStackSize && frame != null)
            {
                indent = ((frame._depth>9)?">":">>")+frame._depth+"> ";
                _stringBuffer.append(indent);
            }
            indent = indentSeparator + __indentBase + indent;
            
            // Add stack frame to message
            if (_logStackTrace && frame != null)
                msg = msg + __lineSeparator + frame._stack;

            // Log indented message
            int i=0;
            int last=0;
            String smsg=(msg==null)
                ?"null"
                :((msg instanceof String)
                  ?((String)msg)
                  :msg.toString());
            
            while ((i=smsg.indexOf(__lineSeparator,i))>=last)
            {
                _stringBuffer.append(smsg.substring(last,i));
                _stringBuffer.append(indent);
                i+=__lineSeparatorLen;
                last=i;
            }
            if (smsg.length()>last)
                _stringBuffer.append(smsg.substring(last));

            log(_stringBuffer.toString());
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Log a message.
     * The formatted log string is written to the log sink. The default
     * implementation writes the message to a PrintWriter.
     * @param formattedLog 
     */
    public synchronized void log(String formattedLog)
    {
        _out.println(formattedLog);
        _out.flush();
    }

    /* ------------------------------------------------------------ */
    protected void setWriter(PrintWriter out)
    {
        _out=out;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @param o PrintWriter
     */
    public void initialize(Object o)
    {
        if (o instanceof PrintWriter)
            setWriter((PrintWriter)o);
    }
    
    /* ------------------------------------------------------------ */
    /** Stop a log sink.
     * The default implementation does nothing 
     */
    public void start()
    {
        if (_dateFormat==null)
        {
            setLogDateFormat(_logDateFormat);
            setLogTimezone(_logTimezone);
        }
        _started=true;
    }
    
    /* ------------------------------------------------------------ */
    /** Stop a log sink.
     * An opportunity for subclasses to clean up. The default
     * implementation does nothing 
     */
    public void stop()
    {
        _started=false;
    }

    /* ------------------------------------------------------------ */
    public boolean isStarted()
    {
        return _started;
    }

    /* ------------------------------------------------------------ */
    public void destroy()
    {
        _out=null;
    }
    
    /* ------------------------------------------------------------ */
    public boolean isDestroyed()
    {
        return !_started && _out==null;
    }
    
};




