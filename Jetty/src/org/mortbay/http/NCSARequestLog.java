// ========================================================================
// Copyright (c) 2000 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.http;

import org.mortbay.util.Code;
import java.util.TimeZone;
import org.mortbay.util.RolloverFileOutputStream;
import org.mortbay.util.DateCache;
import org.mortbay.util.ByteArrayISO8859Writer;
import java.io.IOException;
import java.io.PrintWriter;

/* ------------------------------------------------------------ */
/** NCSA HTTP Request Log.
 * NCSA common or NCSA extended (combined) request log.
 * @version $Id$
 * @author Tony Thompson
 * @author Greg Wilkins
 */
public class NCSARequestLog implements RequestLog
{
    private String _filename;
    private DateCache _logDateCache;
    private boolean _extended;
    private boolean _append;
    private int _retainDays;
    private RolloverFileOutputStream _fileOut;
    private ByteArrayISO8859Writer _buf=new ByteArrayISO8859Writer();
    private PrintWriter _out=new PrintWriter(_buf);
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param filename Filename, which can be in
     * rolloverFileOutputStream format
     * @see org.mortbay.util.RolloverFileOutputStream
     * @exception IOException 
     */
    public NCSARequestLog(String filename)
        throws IOException
    {
        _filename=filename;
        _logDateCache=new DateCache("dd/MMM/yyyy:HH:mm:ss ZZZ");
        _logDateCache.setTimeZoneID(TimeZone.getDefault().getID());
        _extended=true;
        _append=true;
        _retainDays=31;
    }

    /* ------------------------------------------------------------ */
    public void setFilename(String filename)
    {
        if (filename!=null)
        {
            filename=filename.trim();
            if (filename.length()==0)
                filename=null;
        }
        _filename=filename;        
    }

    /* ------------------------------------------------------------ */
    public String getFilename()
    {
        return _filename;
    }

    /* ------------------------------------------------------------ */
    public String getDatedFilename()
    {
        if (_fileOut==null)
            return null;
        return _fileOut.getDatedFilename();
    }
    
    /* ------------------------------------------------------------ */
    public void setLogDateFormat(String format)
    {
        TimeZone tz=_logDateCache.getTimeZone();
        _logDateCache=new DateCache(format);
        _logDateCache.setTimeZone(tz);
    }

    /* ------------------------------------------------------------ */
    public String getLogDateFormat()
    {
        return _logDateCache.getFormatString();
    }
    
    /* ------------------------------------------------------------ */
    public void setLogTimeZone(String tz)
    {
        _logDateCache.setTimeZone(TimeZone.getTimeZone(tz));
    }

    /* ------------------------------------------------------------ */
    public String getLogTimeZone()
    {
        return _logDateCache.getTimeZone().getID();
    }
    
    /* ------------------------------------------------------------ */
    public int getRetainDays()
    {
        return _retainDays;
    }

    /* ------------------------------------------------------------ */
    public void setRetainDays(int retainDays)
    {
        _retainDays = retainDays;
    }

    /* ------------------------------------------------------------ */
    public boolean isExtended()
    {
        return _extended;
    }

    /* ------------------------------------------------------------ */
    public void setExtended(boolean e)
    {
        _extended=e;
    }
    
    /* ------------------------------------------------------------ */
    public boolean isAppend()
    {
        return _append;
    }

    /* ------------------------------------------------------------ */
    public void setAppend(boolean a)
    {
        _append=a;
    }
    
    /* ------------------------------------------------------------ */
    public void start()
        throws Exception
    {
        _fileOut=new RolloverFileOutputStream(_filename,_append,_retainDays);
    }

    /* ------------------------------------------------------------ */
    public boolean isStarted()
    {
        return _fileOut!=null;
    }
    
    /* ------------------------------------------------------------ */
    public void stop()
    {
        if (_fileOut!=null)
            try{_fileOut.close();}catch(IOException e){Code.ignore(e);}
        _fileOut=null;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param request 
     * @param response 
     * @param responseLength 
     * @return 
     */
    public void log(HttpRequest request,
                    HttpResponse response,
                    int responseLength)
    {
        try{
            synchronized(_buf.getLock())
            {
                if (_fileOut==null)
                    return;
        
                _out.print(request.getRemoteAddr());
                _out.print(" - ");
                String user = (String)request.getAttribute(HttpRequest.__AuthUser);
                _out.print((user==null)?"-":user);
                _out.print(" [");
                _out.print(_logDateCache.format(request.getTimeStamp()));
                _out.print("] \"");
                request.writeRequestLine(_out);
                _out.print("\" ");
                _out.print(response.getStatus());
                if (responseLength>=0)
                {
                    _out.print(' ');
                    _out.print(responseLength);
                    _out.print(' ');
                }
                else
                    _out.print(" - ");
                
                if (_extended)
                {
                    String referer = request.getField(HttpFields.__Referer);
                    if(referer==null)
                        _out.print("- ");
                    else
                    {
                        _out.print('"');
                        _out.print(referer);
                        _out.print("\" ");
                    }
                    
                    String agent = request.getField(HttpFields.__UserAgent);
                    if(agent==null)
                    _out.print('-');
                    else
                    {
                        _out.print('"');
                        _out.print(agent);
                        _out.print('"');
                    }
                }
                _out.println();
                _out.flush();
                _buf.writeTo(_fileOut);
                _buf.reset();
            }
        }
        catch(IOException e)
        {
            Code.warning(e);
        }
    }
}
