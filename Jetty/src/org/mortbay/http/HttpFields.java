// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.http;

import org.mortbay.util.Code;
import org.mortbay.util.DateCache;
import org.mortbay.util.LineInput;
import org.mortbay.util.QuotedStringTokenizer;
import org.mortbay.util.StringMap;
import org.mortbay.util.StringUtil;
import org.mortbay.util.SingletonList;
import org.mortbay.util.UrlEncoded;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import javax.servlet.http.Cookie;

/* ------------------------------------------------------------ */
/** HTTP Fields.
 * A collection of HTTP header and or Trailer fields.
 * This class is not synchronized and needs to be protected from
 * concurrent access.
 *
 * This class is not synchronized as it is expected that modifications
 * will only be performed by a single thread.
 *
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class HttpFields
{        
    /* ------------------------------------------------------------ */
    /** General Fields.
     */
    public final static String
        __CacheControl = "Cache-Control",
        __Connection = "Connection",
        __Date = "Date",
        __Pragma = "Pragma",
        __Trailer = "Trailer",
        __TransferEncoding = "Transfer-Encoding",
        __Upgrade = "Upgrade",
        __Via = "Via",
        __Warning = "Warning";
        
    /* ------------------------------------------------------------ */
    /** Entity Fields.
     */
    public final static String
        __Allow = "Allow",
        __ContentEncoding = "Content-Encoding",
        __ContentLanguage = "Content-Language",
        __ContentLength = "Content-Length",
        __ContentLocation = "Content-Location",
        __ContentMD5 = "Content-MD5",
        __ContentRange = "Content-Range",
        __ContentType = "Content-Type",
        __Expires = "Expires",
        __LastModified = "Last-Modified";
    
    /* ------------------------------------------------------------ */
    /** Request Fields.
     */
    public final static String
        __Accept = "Accept",
        __AcceptCharset = "Accept-Charset",
        __AcceptEncoding = "Accept-Encoding",
        __AcceptLanguage = "Accept-Language",
        __Authorization = "Authorization",
        __Expect = "Expect",
        __Forwarded = "Forwarded",
        __From = "From",
        __Host = "Host",
        __IfMatch = "If-Match",
        __IfModifiedSince = "If-Modified-Since",
        __IfNoneMatch = "If-None-Match",
        __IfRange = "If-Range",
        __IfUnmodifiedSince = "If-Unmodified-Since",
        __KeepAlive = "keep-alive",
        __MaxForwards = "Max-Forwards",
        __ProxyAuthorization = "Proxy-Authorization",
        __Range = "Range",
        __RequestRange = "Request-Range",
        __Referer = "Referer",
        __TE = "TE",
        __UserAgent = "User-Agent";

    /* ------------------------------------------------------------ */
    /** Response Fields.
     */
    public final static String
        __AcceptRanges = "Accept-Ranges",
        __Age = "Age",
        __ETag = "ETag",
        __Location = "Location",
        __ProxyAuthenticate = "Proxy-Authenticate",
        __RetryAfter = "Retry-After",
        __Server = "Server",
        __ServletEngine = "Servlet-Engine",
        __Vary = "Vary",
        __WwwAuthenticate = "WWW-Authenticate";
     
    /* ------------------------------------------------------------ */
    /** Other Fields.
     */
    public final static String __Cookie = "Cookie";
    public final static String __SetCookie = "Set-Cookie";
    public final static String __SetCookie2 = "Set-Cookie2";
    public final static String __MimeVersion ="MIME-Version";
    public final static String __Identity ="identity";


    
    /* ------------------------------------------------------------ */
    /** Private class to hold Field name info
     */
    private static class FieldInfo
    {
        String _name;
        String _lname;
        boolean _singleValued;
        boolean _inlineValues;
        int _hashCode;
        static int __hashCode;
        
        FieldInfo(String name, boolean single, boolean inline)
        {
            synchronized(FieldInfo.class)
            {
                _name=name;
                _lname=StringUtil.asciiToLowerCase(name);
                _singleValued=single;
                _inlineValues=inline;
                __info.put(name,this);
                if (!name.equals(_lname))
                    __info.put(_lname,this);
                _hashCode=__hashCode++;
            }
        }

        public String toString()
        {
            return "["+_name+","+_singleValued+","+_inlineValues+"]";
        }

        public int hashCode()
        {
            return _hashCode;
        }

        public boolean equals(Object o)
        {
            return (o instanceof FieldInfo) &&
                (((FieldInfo)o)._hashCode==_hashCode);
        }
        
    }

    /* ------------------------------------------------------------ */
    private static final StringMap __info = new StringMap(true);
    
    /* ------------------------------------------------------------ */
    static
    {
        // Initialize FieldInfo's with special values.
        // In order of most frequently used.
        new FieldInfo(__Host,true,false);
        
        new FieldInfo(__KeepAlive,false,false);
        new FieldInfo(__Connection,false,false);
        
        new FieldInfo(__Cookie,false,false);
        
        new FieldInfo(__Accept,false,false);
        new FieldInfo(__AcceptLanguage,false,false);
        new FieldInfo(__AcceptEncoding,false,false);
        new FieldInfo(__AcceptCharset,false,false);
        new FieldInfo(__CacheControl,false,false);
        new FieldInfo(__SetCookie,false,false);
        new FieldInfo(__SetCookie2,false,false);
        
        
        new FieldInfo(__Date,true,false);
        new FieldInfo(__TransferEncoding,false,true);
        new FieldInfo(__ContentEncoding,false,true);
        new FieldInfo(__ContentLength,true,false);
        new FieldInfo(__Expires,true,false);
        new FieldInfo(__Expect,true,false);
        
        new FieldInfo(__Referer,true,false);
        new FieldInfo(__TE,false,false);
        new FieldInfo(__UserAgent,true,false);
        
        new FieldInfo(__IfModifiedSince,true,false);
        new FieldInfo(__IfRange,true,false);
        new FieldInfo(__IfUnmodifiedSince,true,false);

        new FieldInfo(__Location,true,false);
        new FieldInfo(__Server,true,false);
        new FieldInfo(__ServletEngine,false,false);
        
        new FieldInfo(__AcceptRanges,false,false);
        new FieldInfo(__Range,true,false);
        new FieldInfo(__RequestRange,true,false);
        
        new FieldInfo(__ContentLocation,true,false);
        new FieldInfo(__ContentMD5,true,false);
        new FieldInfo(__ContentRange,true,false);
        new FieldInfo(__ContentType,true,false);
        new FieldInfo(__LastModified,true,false);
        new FieldInfo(__Authorization,true,false);
        new FieldInfo(__From,true,false);
        new FieldInfo(__MaxForwards,true,false);
        new FieldInfo(__ProxyAuthenticate,true,false);
        new FieldInfo(__Age,true,false);
        new FieldInfo(__ETag,true,false);
        new FieldInfo(__RetryAfter,true,false);
    };
    
    /* ------------------------------------------------------------ */
    private static FieldInfo getFieldInfo(String name)
    {
        FieldInfo info = (FieldInfo)__info.get(name);
        if (info==null)
            info = new FieldInfo(name,false,false);
        return info;
    }
    
    /* ------------------------------------------------------------ */
    private static FieldInfo getFieldInfo(char[] name,int offset,int length)
    {
        Map.Entry entry = __info.getEntry(name,offset,length);
        if (entry==null)
            return new FieldInfo(new String(name,offset,length),false,false);

        return (FieldInfo) entry.getValue();
    }
    
    /* ------------------------------------------------------------ */
    /** Fields Values.
     */    
    public final static String __Chunked = "chunked";
    public final static String __Close = "close";
    public final static String __TextHtml = "text/html";
    public final static String __MessageHttp = "message/http";
    public final static String __WwwFormUrlEncode =
        "application/x-www-form-urlencoded";
    public static final String __ExpectContinue="100-continue";
    
    /* ------------------------------------------------------------ */
    public final static String __separators = ", \t";    

    /* ------------------------------------------------------------ */
    public final static char[] __CRLF = {'\015','\012'};
    public final static char[] __COLON = {':',' '};

    /* -------------------------------------------------------------- */
    public final static DateCache __dateCache = 
        new DateCache("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                      Locale.US);
    public final static SimpleDateFormat __dateSend = 
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
                             Locale.US);

    /* ------------------------------------------------------------ */
    private final static String __dateReceiveFmt[] =
    {
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss",
        "EEE dd MMM yyyy HH:mm:ss zzz",
        "EEE dd MMM yyyy HH:mm:ss",
        "EEE MMM dd yyyy HH:mm:ss zzz",
        "EEE MMM dd yyyy HH:mm:ss",
        "EEE MMM-dd-yyyy HH:mm:ss zzz",
        "EEE MMM-dd-yyyy HH:mm:ss",
        "dd MMM yyyy HH:mm:ss zzz",
        "dd MMM yyyy HH:mm:ss",
        "dd-MMM-yy HH:mm:ss zzz",
        "dd-MMM-yy HH:mm:ss",
        "MMM dd HH:mm:ss yyyy zzz",
        "MMM dd HH:mm:ss yyyy",
        "EEE MMM dd HH:mm:ss yyyy zzz",
        "EEE MMM dd HH:mm:ss yyyy",
        "EEE, MMM dd HH:mm:ss yyyy zzz",
        "EEE, MMM dd HH:mm:ss yyyy",
        "EEE, dd-MMM-yy HH:mm:ss zzz",
        "EEE, dd-MMM-yy HH:mm:ss",
        "EEE dd-MMM-yy HH:mm:ss zzz",
        "EEE dd-MMM-yy HH:mm:ss",
    };
    public static SimpleDateFormat __dateReceive[];
    static
    {
        TimeZone tz = TimeZone.getTimeZone("GMT");
        tz.setID("GMT");
        __dateSend.setTimeZone(tz);
        __dateCache.setTimeZone(tz);
        
        __dateReceive = new SimpleDateFormat[__dateReceiveFmt.length];
        for(int i=0;i<__dateReceive.length;i++)
        {
            __dateReceive[i] =
                new SimpleDateFormat(__dateReceiveFmt[i],Locale.US);
            __dateReceive[i].setTimeZone(tz);
        }
    }

    /* ------------------------------------------------------------ */
    private static class Field
    {
        FieldInfo _info;
        String _value;
        Field _next;
        Field _prev;

        Field(FieldInfo info, String value)
        {
            _info=info;
            _value=value;
            _next=null;
            _prev=null;
        }
        
        public boolean equals(Object o)
        {
            return (o instanceof Field) && ((Field)o)._info==_info;
        }

        void clear()
        {
            _info=null;
            _value=null;
            _next=null;
            _prev=null;
        }

        public void write(Writer writer)
            throws IOException
        {
            if (_info==null)
                return;
            if (_info._inlineValues)
            {
                if (_prev!=null)
                    return;
                writer.write(_info._name);
                writer.write(__COLON);
                Field f=this;
                while (true)
                {
                    writer.write(QuotedStringTokenizer.quote(f._value,", \t"));
                    f=f._next;
                    if (f==null)
                        break;
                    writer.write(",");
                }
                writer.write(__CRLF);
            }
            else
            {
                writer.write(_info._name);
                writer.write(__COLON);
                writer.write(_value);
                writer.write(__CRLF);
            }
        }

        public String toString()
        {
            return (_prev==null?"[":("["+_prev._info._name+"<- "))+
                _info._name+__COLON+_value+
                (_next==null?"]":(" ->"+_next._info._name)+"]");
        }
    }
    
    /* ------------------------------------------------------------ */
    private static Float __one = new Float("1.0");
    
    /* -------------------------------------------------------------- */
    private ArrayList _fields=new ArrayList(15);
    private int[] _index=new int[2*FieldInfo.__hashCode];

    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public HttpFields()
    {
        Arrays.fill(_index,-1);
    }

    /* ------------------------------------------------------------ */
    public int size()
    {
        return _fields.size();
    }
    
    /* -------------------------------------------------------------- */
    /** Get enumeration of header _names.
     * Returns an enumeration of strings representing the header _names
     * for this request. 
     */
    public Enumeration getFieldNames()
    {
        return new Enumeration()
            {
                int i=0;
                Field f=null;

                public boolean hasMoreElements()
                {
                    if (f!=null)
                        return true;
                    while (i<_fields.size())
                    {
                        Field t=(Field)_fields.get(i++);
                        if (t!=null && t._prev==null)
                        {
                            f=t;
                            return true;
                        }
                    }
                    return false;
                }

                public Object nextElement()
                    throws NoSuchElementException
                {
                    if (f!=null || hasMoreElements())
                    {
                        String n=f._info._name;
                        f=null;
                        return n;
                    }
                    throw new NoSuchElementException();
                }
            };
    }
    
    /* ------------------------------------------------------------ */
    public boolean containsKey(String name)
    {
        FieldInfo info=getFieldInfo(name);
        return _index[info.hashCode()]>=0;        
    }
    
    /* -------------------------------------------------------------- */
    /**
     * @return the value of a field, or null if not found. For
     * multiple fields of the same name, only the first is returned.
     * @param name the case-insensitive field name
     */
    public String get(String name)
    {
        FieldInfo info=getFieldInfo(name);
        
        if (_index[info.hashCode()]>=0)
        {   
            Field field=(Field)_fields.get(_index[info.hashCode()]);
            return field._value;
        }
        return null;
    }
    
    /* -------------------------------------------------------------- */
    /** Get multi headers
     * @return Enumeration of the values, or null if no such header.
     * @param name the case-insensitive field name
     */
    public Enumeration getValues(String name)
    {
        FieldInfo info=getFieldInfo(name);
        if (_index[info.hashCode()]>=0)
        {
            final Field field=(Field)_fields.get(_index[info.hashCode()]);
            
            return new Enumeration()
                {
                    Field f=field;
                    
                    public boolean hasMoreElements()
                    {
                        return f!=null;
                    }
                        
                    public Object nextElement()
                        throws NoSuchElementException
                    {
                        if (f==null)
                            throw new NoSuchElementException();
                        Field n=f;
                        f=f._next;
                        return n._value;
                    }
                };
        }
        return null;
    }
    
    /* -------------------------------------------------------------- */
    /** Get multi field values with separator.
     * The multiple values can be represented as separate headers of
     * the same name, or by a single header using the separator(s), or
     * a combination of both. Separators may be quoted.
     * @param name the case-insensitive field name
     * @param separators String of separators.
     * @return Enumeration of the values, or null if no such header.
     */
    public Enumeration getValues(String name,final String separators)
    {
        final Enumeration e = getValues(name);
        if (e==null)
            return null;
        return new Enumeration()
            {
                QuotedStringTokenizer tok=null;
                public boolean hasMoreElements()
                {
                    if (tok!=null && tok.hasMoreElements())
                            return true;
                    while (e.hasMoreElements())
                    {
                        String value=(String)e.nextElement();
                        tok=new QuotedStringTokenizer(value,separators,false,false);
                        if (tok.hasMoreElements())
                            return true;
                    }
                    tok=null;
                    return false;
                }
                        
                public Object nextElement()
                    throws NoSuchElementException
                {
                    if (!hasMoreElements())
                        throw new NoSuchElementException();
                    return tok.nextElement();
                }
            };
    }
    
        
    /* -------------------------------------------------------------- */
    /** Set a field.
     * @param name the name of the field
     * @param value the value of the field. If null the field is cleared.
     */
    public String put(String name,String value)
    {
        if (value==null)
            return remove(name);
        
        FieldInfo info=getFieldInfo(name);
        
        // Look for value to replace.
        if (_index[info.hashCode()]>=0)
        {
            Field field=(Field)_fields.get(_index[info.hashCode()]);
            String old=field._value;
            field._value=value;

            Field last=field;
            field=last._next;
            last._next=null;
            while(field!=null)
            {
                last=field;
                field=field._next;
                last.clear();
            }
            return old;    
        }
        
        // new value;
        Field field=new Field(info,value);
        _index[info.hashCode()]=_fields.size();
        _fields.add(field);
        return null;
    }

    /* -------------------------------------------------------------- */
    /** Set a multi value field.
     * If the field is allowed to have multiple values.
     * @param name the name of the field
     * @param value the list of values of the field. If null the field is cleared.
     */
    public void put(String name,List value)
    {
        FieldInfo info=getFieldInfo(name);
        if (info._singleValued)
            throw new IllegalArgumentException("Field "+name+" must be single valued");

        if (_index[info.hashCode()]>=0)
        {
            Field field=(Field)_fields.get(_index[info.hashCode()]);
            while(field!=null)
            {
                Field last=field;
                field=field._next;
                last.clear();
            }
        }
        
        Field last=null;
        Iterator iter = value.iterator();
        while (iter.hasNext())
        {
            Field field=new Field(info,iter.next().toString());
            if (last==null)
                _index[info.hashCode()]=_fields.size();
            else
            {
                field._prev=last;
                last._next=field;
            }
            _fields.add(field);
            last=field;
        }        
    }
    
    /* -------------------------------------------------------------- */
    /** Add to or set a field.
     * If the field is allowed to have multiple values, add will add
     * multiple headers of the same name.
     * @param name the name of the field
     * @param value the value of the field.
     * @exception IllegalArgumentException If the name is a single
     *            valued field
     */
    public void add(String name,String value)
        throws IllegalArgumentException
    {
        if (value==null)
            throw new IllegalArgumentException("null value");
        
        FieldInfo info=getFieldInfo(name);
        Field last=null;
        if (_index[info.hashCode()]>=0)
        {
            if (info._singleValued)
                throw new IllegalArgumentException("Field "+name+" must be single valued");
            
            last=(Field)_fields.get(_index[info.hashCode()]);
            while(last._next!=null)
                last=last._next;
        }
        else
            _index[info.hashCode()]=_fields.size();
            
        // create the field
        Field field=new Field(info,value);
        _fields.add(field);
        
        // look for chain to add too
        if(last!=null)
        {
            field._prev=last;
            last._next=field;    
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Remove a field.
     * @param name 
     */
    public String remove(String name)
    {
        String old=null;
        FieldInfo info=getFieldInfo(name);

        if (_index[info.hashCode()]>=0)
        {
            Field field=(Field)_fields.get(_index[info.hashCode()]);
            _fields.set(_index[info.hashCode()],null);
            old=field._value;
            while(field!=null)
            {
                Field last=field;
                field=field._next;
                last.clear();
            }
            _index[info.hashCode()]=-1;
        }
        
        return old;
    }
   
    /* -------------------------------------------------------------- */
    /** Get a header as an integer value.
     * Returns the value of an integer field or -1 if not found.
     * The case of the field name is ignored.
     * @param name the case-insensitive field name
     * @exception NumberFormatException If bad integer found
     */
    public int getIntField(String name)
        throws NumberFormatException
    {
        String val = valueParameters(get(name),null);
        if (val!=null)
            return Integer.parseInt(val);
        return -1;
    }
    
    /* -------------------------------------------------------------- */
    /** Get a header as a date value.
     * Returns the value of a date field, or -1 if not found.
     * The case of the field name is ignored.
     * @param name the case-insensitive field name
     */
    public long getDateField(String name)
    {
        String val = valueParameters(get(name),null);
        if (val!=null)
        {
            for (int i=0;i<__dateReceive.length;i++)
            {
                try{
                    Date date=(Date)__dateReceive[i].parseObject(val);
                    return date.getTime();
                }
                catch(java.lang.Exception e)
                {Code.ignore(e);}
            }
            if (val.endsWith(" GMT"))
            {
                val=val.substring(0,val.length()-4);
                for (int i=0;i<__dateReceive.length;i++)
                {
                    try{
                        Code.debug("TRY ",val," against ",__dateReceive[i].toPattern());
                        Date date=(Date)__dateReceive[i].parseObject(val);
                        Code.debug("GOT ",date);
                        return date.getTime();
                    }
                    catch(java.lang.Exception e)
                    {
                        Code.ignore(e);
                    }
                }
            }
        }
        return -1;
    }
    
    /* -------------------------------------------------------------- */
    /**
     * Sets the value of an integer field.
     * @param name the field name
     * @param value the field integer value
     */
    public void putIntField(String name, int value)
    {
        put(name, Integer.toString(value));
    }

    /* -------------------------------------------------------------- */
    /**
     * Sets the value of a date field.
     * @param name the field name
     * @param value the field date value
     */
    public void putDateField(String name, Date date)
    {
        put(name, __dateSend.format(date));
    }
    
    /* -------------------------------------------------------------- */
    /**
     * Adds the value of a date field.
     * @param name the field name
     * @param value the field date value
     */
    public void addDateField(String name, Date date)
    {
        add(name, __dateSend.format(date));
    }
    
    /* -------------------------------------------------------------- */
    /**
     * Sets the value of a date field.
     * @param name the field name
     * @param value the field date value
     */
    public void putDateField(String name, long date)
    {
        put(name, __dateSend.format(new Date(date)));
    }

    /* -------------------------------------------------------------- */
    /** Read HttpHeaders from inputStream.
     */
    public void read(LineInput in)
        throws IOException
    {  
        Field last=null;
        char[] buf=null;
        int size=0;
        org.mortbay.util.LineInput.LineBuffer line_buffer;
        synchronized(in)
        {
            while ((line_buffer=in.readLineBuffer())!=null)
            {
                // check space in the lowercase buffer
                buf=line_buffer.buffer;
                size=line_buffer.size;
                if (size==0)
                    break;
                
                // setup loop state machine
                int state=0;
                int i1=-1;
                int i2=-1;
                int name_i=-1;
                int name_l=0;
                
                // loop for all chars in buffer
                for (int i=0;i<line_buffer.size;i++)
                {
                    char c=buf[i];
                    
                    switch(state)
                    {
                      case 0: // leading white
                          if (c==' ' || c=='\t')
                          {
                              // continuation line
                              state=2;
                              continue;
                          }
                          state=1;
                          i1=i;
                          i2=i-1;
                          // fall through to case 1...
                          
                      case 1: // reading name
                          if (c==':')
                          {
                              name_i=i1;
                              name_l=i2-i1+1;                              
                              state=2;
                              i1=i;i2=i-1;
                              continue;
                          }
                          if (c!=' '&&c!='\t')
                              i2=i;
                          continue;
                          
                      case 2: // skip whitespace after :
                          if (c==' ' || c=='\t')
                              continue;
                          state=3;
                          i1=i;
                          i2=i-1;
                          // fall through to case 3...
                          
                      case 3: // looking for last non-white
                          if (c!=' ' && c!='\t')
                              i2=i;
                    }
                    continue;
                }
                
                if (name_i<0 || name_l==0)
                {
                    if (state>=2 && last!=null)
                        // Continuation line
                        last._value=last._value+' '+new String(buf,i1,i2-i1+1);
                    continue;
                }

                // create the field.
                FieldInfo info = getFieldInfo(buf,name_i,name_l);
                Field field=new Field(info,new String(buf,i1,i2-i1+1));
                
                if (_index[info.hashCode()]<0)
                    _index[info.hashCode()]=_fields.size();
                else if (info._singleValued)
                {
                    Code.warning("Ignored duplicate single value header: "+field);
                    continue;
                }
                else
                {
                    Field link=(Field)_fields.get(_index[info.hashCode()]);
                    while(link._next!=null)
                        link=link._next;
                    field._prev=link;
                    link._next=field;
                }
                _fields.add(field);
                last=field;
            }
        }
    }

    
    /* -------------------------------------------------------------- */
    /* Write Extra HTTP headers.
     */
    public void write(Writer writer)
        throws IOException
    {
        synchronized(writer)
        {
            for (int i=0;i<_fields.size();i++)
            {
                Field field=(Field)_fields.get(i);
                if (field!=null)
                    field.write(writer);
            }
            writer.write(__CRLF);
        }
    }
    
    
    /* -------------------------------------------------------------- */
    public String toString()
    {
        try
        {
            StringWriter writer = new StringWriter();
            write(writer);
            return writer.toString();
        }
        catch(Exception e)
        {}
        return null;
    }

    /* ------------------------------------------------------------ */
    /** Clear the header.
     * Remove all entries.
     */
    public void clear()
    {        
        for (int i=_fields.size();i-->0;)
        {
            Field field=(Field)_fields.get(i);
            if (field!=null)
                field.clear();
        }
        _fields.clear();
        Arrays.fill(_index,-1);
    }
    
    /* ------------------------------------------------------------ */
    /** Destroy the header.
     * Help the garbage collector by null everything that we can.
     */
    public void destroy()
    {
        clear();
        _fields=null;
    }
    
    /* ------------------------------------------------------------ */
    /** Get field value parameters.
     * Some field values can have parameters.  This method separates
     * the value from the parameters and optionally populates a
     * map with the paramters. For example:<PRE>
     *   FieldName : Value ; param1=val1 ; param2=val2
     * </PRE>
     * @param value The Field value, possibly with parameteres.
     * @param parameters A map to populate with the parameters, or null
     * @return The value.
     */
    public static String valueParameters(String value, Map parameters)
    {
        if (value==null)
            return null;
        
        int i = value.indexOf(';');
        if (i<0)
            return value;
        if (parameters==null)
            return value.substring(0,i).trim();

        StringTokenizer tok1 =
            new QuotedStringTokenizer(value.substring(i),";",false,true);
        while(tok1.hasMoreTokens())
        {
            String token=tok1.nextToken();
            StringTokenizer tok2 =
                new QuotedStringTokenizer(token,"= ");
            if (tok2.hasMoreTokens())
            {
                String paramName=tok2.nextToken();
                String paramVal=null;
                if (tok2.hasMoreTokens())
                    paramVal=tok2.nextToken();
                parameters.put(paramName,paramVal);
            }
        }
        
        return value.substring(0,i).trim();
    }

    /* ------------------------------------------------------------ */
    public static Float getQuality(String value)
    {
        HashMap params = new HashMap(7);
        valueParameters(value,params);
        String qs=(String)params.get("q");
        Float q=__one;
        
        if (qs!=null)
        {
            try{q=new Float(qs);}
            catch(Exception e){q=__one;}
        }
        return q;
    }

    /* ------------------------------------------------------------ */
    /** List values in quality order.
     * @param enum Enumeration of values with quality parameters
     * @return values in quality order.
     */
    public static List qualityList(Enumeration enum)
    {
        if(enum==null || !enum.hasMoreElements())
            return Collections.EMPTY_LIST;

        String value=null;
        Float quality=null;
        LinkedList list=null;
        LinkedList qual=null;
        
        // Assume that we will only have zero or 1 elements1
        while(enum.hasMoreElements())
        {
            String v=enum.nextElement().toString();
            Float q=getQuality(v);
            
            if (q.floatValue()>=0.001)
            {
                // Is this the first OK value?
                if (value==null)
                {
                    value=v;
                    quality=q;
                }
                else
                {
                    // we have more than 1 OK value, so build list
                    list = new LinkedList();
                    qual = new LinkedList();
                    if (quality.floatValue()>q.floatValue())
                    {
                        list.add(value);
                        list.add(v);
                        qual.add(quality);
                        qual.add(q);
                    }
                    else
                    {
                        list.add(v);
                        list.add(value);
                        qual.add(q);
                        qual.add(quality);
                    }
                    break;
                }
            }
        }
        
        // Do we have any values?
        if (value==null)
            return Collections.EMPTY_LIST;

        // Do we have only 1 value?
        if (list==null)
            return SingletonList.newSingletonList(value);

        // Add remaining values
        while(enum.hasMoreElements())
        {
            String v=enum.nextElement().toString();
            Float q=getQuality(v);
            if (q.floatValue()<0.001)
                continue;

            ListIterator vi = list.listIterator();
            ListIterator qi = qual.listIterator();
        
            // Insert sort
            while(vi.hasNext())
            {
                String cv=(String)vi.next();
                Float cq=(Float)qi.next();
                if(cq.floatValue()<q.floatValue())
                {
                    qi.previous();
                    vi.previous();
                    break;
                }
            }
            vi.add(v);
            qi.add(q);
        }

        qual.clear();
        return list;
    }


    /* ------------------------------------------------------------ */
    /** Format a set cookie value
     * @param cookie The cookie.
     * @param cookie2 If true, use the alternate cookie 2 header
     */
    public void addSetCookie(Cookie cookie, boolean cookie2)
    {
        String name=cookie.getName();
        String value=cookie.getValue();
        int version=cookie.getVersion();
        
        // Check arguments
        if (name==null || name.length()==0)
            throw new IllegalArgumentException("Bad cookie name");

        // Format value and params
        StringBuffer buf = new StringBuffer(128);
        String name_value_params=null;
        synchronized(buf)
        {
            buf.append(name);
            if (value!=null && value.length()>0)
            {
                buf.append('=');
                buf.append(UrlEncoded.encodeString(value));
            }
            
            if (version>0)
            {
                buf.append(";Version=");
                buf.append(version);
                String comment=cookie.getComment();
                if (comment!=null && comment.length()>0)
                {
                    buf.append(";Comment=\"");
                    buf.append(comment);
                    buf.append('"');
                }
            }
            String path=cookie.getPath();
            if (path!=null && path.length()>0)
            {
                buf.append(";Path=");
                buf.append(path);
            }
            String domain=cookie.getDomain();
            if (domain!=null && domain.length()>0)
            {
                buf.append(";Domain=");
                buf.append(domain.toLowerCase());// lowercase for IE
            }
            long maxAge = cookie.getMaxAge();
            if (maxAge>=0)
            {
                if (version==0)
                {
                    buf.append(";Expires=");
                    buf.append(HttpFields.__dateSend.format(new Date(System.currentTimeMillis()+1000L*maxAge)));
                }
                else
                {
                    buf.append (";Max-Age=");
                    buf.append (cookie.getMaxAge());
                }
            }
            else if (version>0)
            {
                buf.append (";Discard");
            }
            if (cookie.getSecure())
            {
                buf.append(";secure");
            }
            name_value_params = buf.toString();
        }
        add(cookie2
            ?HttpFields.__SetCookie2:HttpFields.__SetCookie,
            name_value_params); 
    }
}

