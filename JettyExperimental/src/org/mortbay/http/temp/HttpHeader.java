// ========================================================================
// $Id$
// Copyright 2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.mortbay.http.temp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.mortbay.http.HttpHeaderValues;
import org.mortbay.http.HttpHeaders;
import org.mortbay.http.HttpMethods;
import org.mortbay.http.HttpStatus;
import org.mortbay.http.HttpVersions;
import org.mortbay.io.Buffer;
import org.mortbay.io.BufferUtil;
import org.mortbay.io.ByteArrayBuffer;
import org.mortbay.io.Portable;
import org.mortbay.util.DateCache;
import org.mortbay.util.LazyList;
import org.mortbay.util.QuotedStringTokenizer;
import org.mortbay.util.StringMap;
import org.mortbay.util.StringUtil;

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
public class HttpHeader
{
    /* ------------------------------------------------------------ */
    public final static String __separators = ", \t";    

    /* ------------------------------------------------------------ */
    private static String[] DAYS= { "Sat","Sun","Mon","Tue","Wed","Thu","Fri","Sat" };
    private static String[] MONTHS= { "Jan","Feb","Mar","Apr","May","Jun",
                                      "Jul","Aug","Sep","Oct","Nov","Dec","Jan" };

    /* ------------------------------------------------------------ */
    /** Format HTTP date
     * "EEE, dd MMM yyyy HH:mm:ss 'GMT'" or 
     * "EEE, dd-MMM-yy HH:mm:ss 'GMT'"for cookies
     */
    public static String formatDate(long date, boolean cookie)
    {
        StringBuffer buf = new StringBuffer(32);
        GregorianCalendar gc = new GregorianCalendar(__GMT);
        gc.setTimeInMillis(date);
        formatDate(buf,gc,cookie);
        return buf.toString();
    } 

    /* ------------------------------------------------------------ */
    /** Format HTTP date
     * "EEE, dd MMM yyyy HH:mm:ss 'GMT'" or 
     * "EEE, dd-MMM-yy HH:mm:ss 'GMT'"for cookies
     */
    public static String formatDate(Calendar calendar, boolean cookie)
    {
        StringBuffer buf = new StringBuffer(32);
        formatDate(buf,calendar,cookie);
        return buf.toString();
    }

    /* ------------------------------------------------------------ */
    /** Format HTTP date
     * "EEE, dd MMM yyyy HH:mm:ss 'GMT'" or 
     * "EEE, dd-MMM-yy HH:mm:ss 'GMT'"for cookies
     */
    public static String formatDate(StringBuffer buf, long date, boolean cookie)
    {
        GregorianCalendar gc = new GregorianCalendar(__GMT);
        gc.setTimeInMillis(date);
        formatDate(buf,gc,cookie);
        return buf.toString();
    } 

    /* ------------------------------------------------------------ */
    /** Format HTTP date
     * "EEE, dd MMM yyyy HH:mm:ss 'GMT'" or 
     * "EEE, dd-MMM-yy HH:mm:ss 'GMT'"for cookies
     */
    public static void formatDate(StringBuffer buf,Calendar calendar, boolean cookie)
    {
        // "EEE, dd MMM yyyy HH:mm:ss 'GMT'"
        // "EEE, dd-MMM-yy HH:mm:ss 'GMT'",     cookie
        
        int day_of_week  = calendar.get(Calendar.DAY_OF_WEEK);
        int day_of_month = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        int century = year/100;
        year=year%100;
        
        int epoch=(int)((calendar.getTimeInMillis()/1000) % (60*60*24));
        int seconds=epoch%60;
        epoch=epoch/60;
        int minutes=epoch%60;
        int hours=epoch/60;
        
        buf.append(DAYS[day_of_week]);
        buf.append(',');
        buf.append(' ');
        StringUtil.append2digits(buf,day_of_month);
        
        if (cookie)
        {
            buf.append('-');
            buf.append(MONTHS[month]);
            buf.append('-');
            StringUtil.append2digits(buf,year);
        }
        else
        {
            buf.append(' ');
            buf.append(MONTHS[month]);
            buf.append(' ');
            StringUtil.append2digits(buf,century);
            StringUtil.append2digits(buf,year);
        }
        buf.append(' ');
        StringUtil.append2digits(buf,hours);
        buf.append(':');
        StringUtil.append2digits(buf,minutes);
        buf.append(':');
        StringUtil.append2digits(buf,seconds);
        buf.append(" GMT");
    }    

    /* -------------------------------------------------------------- */
    private static TimeZone __GMT = TimeZone.getTimeZone("GMT");
    public final static DateCache __dateCache = 
        new DateCache("EEE, dd MMM yyyy HH:mm:ss 'GMT'",
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
        __GMT.setID("GMT");
        __dateCache.setTimeZone(__GMT); 
        __dateReceive = new SimpleDateFormat[__dateReceiveFmt.length];
        for(int i=0;i<__dateReceive.length;i++)
        {
            __dateReceive[i] =
                new SimpleDateFormat(__dateReceiveFmt[i],Locale.US);
            __dateReceive[i].setTimeZone(__GMT);
        }
    }                 
    public final static String __01Jan1970=formatDate(0,false);
    public final static Buffer __01Jan1970_BUFFER=new ByteArrayBuffer(__01Jan1970);
    
    /* -------------------------------------------------------------- */
    protected Buffer _method;
    protected Buffer _uri;
    protected Buffer _version;
    protected int _status=0;
    protected Buffer _reason;   
    protected ArrayList _fields=new ArrayList(20);
    protected int _revision;
    protected HashMap _bufferMap=new HashMap(32);
    protected SimpleDateFormat _dateReceive[]=new SimpleDateFormat[__dateReceive.length];
    private StringBuffer _dateBuffer;
    private Calendar _calendar;

    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public HttpHeader()
    {}

    
    /**
     * @return
     */
    public Buffer getMethod()
    {
        return _method;
    }

    /**
     * @return
     */
    public Buffer getReason()
    {
        return _reason;
    }

    /**
     * @return
     */
    public int getStatus()
    {
        return _status;
    }

    /**
     * @return
     */
    public Buffer getUri()
    {
        return _uri;
    }

    /**
     * @return HttpVersions cache Buffer
     */
    public Buffer getVersion()
    {
        return _version;
    }
    
    /**
     * @return HttpVersions cache ordinal
     */
    public int getVersionOrdinal()
    {
        return HttpVersions.CACHE.getOrdinal(_version);
    }

    /**
     * @param buffer
     */
    public void setMethod(Buffer buffer)
    {
        _method= buffer;
    }

    /**
     * @param method
     */
    public void setMethod(String method)
    {
        _method= HttpMethods.CACHE.get(method);
    }

    /**
     * @param ordinal
     */
    public void setMethod(int ordinal)
    {
        _method= HttpMethods.CACHE.get(ordinal);
    }

    /**
     * @param buffer
     */
    public void setReason(Buffer buffer)
    {
        _reason= buffer;
    }

    /**
     * @param i
     */
    public void setStatus(int i)
    {
        _status= i;
    }

    /**
     * @param buffer
     */
    public void setURI(Buffer buffer)
    {
        _uri= buffer;
    }

    /**
     * @param buffer
     */
    public void setURI(String uri)
    {
        _uri= new ByteArrayBuffer(uri);
    }

    /**
     * @param buffer
     */
    public void setVersion(Buffer buffer)
    {
        _version= buffer;
    }

    /**
     * @param buffer
     */
    public void setVersion(String version)
    {
        _version= HttpVersions.CACHE.lookup(version);
    }

    /**
     * @param buffer
     */
    public void setVersion(int ordinal)
    {
        _version= HttpVersions.CACHE.get(ordinal);
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
                Field field=null;

                public boolean hasMoreElements()
                {
                    if (field!=null)
                        return true;
                    while (i<_fields.size())
                    {
                        Field f=(Field)_fields.get(i++);
                        if (f!=null &&  f._prev==null && f._revision==_revision)
                        {
                            field=f;
                            return true;
                        }
                    }
                    return false;
                }

                public Object nextElement()
                    throws NoSuchElementException
                {
                    if (field!=null || hasMoreElements())
                    {
                        String n=field._name.toString();
                        field=null;
                        return n;
                    }
                    throw new NoSuchElementException();
                }
            };
    }
    
    /* -------------------------------------------------------------- */
    /** Get enumeration of header _names.
     * Returns an enumeration of strings representing the header _names
     * for this request. 
     */
    public Enumeration getFieldNameBuffers()
    {
        return new Enumeration()
        {
            int i= 0;
            Field field= null;

            public boolean hasMoreElements()
            {
                if (field != null)
                    return true;
                while (i < _fields.size())
                {
                    Field f= (Field)_fields.get(i++);
                    if (f != null && f._prev == null && f._revision == _revision)
                    {
                        field= f;
                        return true;
                    }
                }
                return false;
            }

            public Object nextElement() throws NoSuchElementException
            {
                if (field != null || hasMoreElements())
                {
                    Buffer n= field._name;
                    field= null;
                    return n;
                }
                throw new NoSuchElementException();
            }
        };
    }
    /* ------------------------------------------------------------ */
    public Field getField(String name)
    {       
        return (Field)_bufferMap.get(HttpHeaders.CACHE.lookup(name));
    }
    
    /* ------------------------------------------------------------ */
    public Field getField(Buffer name)
    {       
        return (Field)_bufferMap.get(name);
    }
    
    /* ------------------------------------------------------------ */
    public boolean containsKey(String name)
    {
        return _bufferMap.containsKey(HttpHeaders.CACHE.lookup(name));
    }
    
    /* -------------------------------------------------------------- */
    /**
     * @return the value of a field, or null if not found. For
     * multiple fields of the same name, only the first is returned.
     * @param name the case-insensitive field name
     */
    public String get(String name)
    {
        Field field=getField(name);
        if (field!=null && field._revision==_revision)
            return field._value.toString();
        return null;
    }
    
    /* -------------------------------------------------------------- */
    /**
     * @return the value of a field, or null if not found. For
     * multiple fields of the same name, only the first is returned.
     * @param name the case-insensitive field name
     */
    public Buffer get(Buffer name)
    {
        Field field=getField(name);
        if (field!=null && field._revision==_revision)
            return field._value;
        return null;
    }
    
    /* -------------------------------------------------------------- */
    /** Get multi headers
     * @return Enumeration of the values, or null if no such header.
     * @param name the case-insensitive field name
     */
    public Enumeration getValues(String name)
    {
        final Field field= getField(name);
        if (field == null)
            return null;

        return new Enumeration()
        {
            Field f= field;
            public boolean hasMoreElements()
            {
                while (f != null && f._revision != _revision)
                    f= f._next;
                return f != null;
            }
            public Object nextElement() throws NoSuchElementException
            {
                if (f == null)
                    throw new NoSuchElementException();
                Field n= f;
                do f= f._next;
                while (f != null && f._revision != _revision);
                return n._value.toString();
            }
        };
    }
    
    /* -------------------------------------------------------------- */
    /** Get multi headers
     * @return Enumeration of the value Buffers, or null if no such header.
     * @param name the case-insensitive field name
     */
    public Enumeration getValues(Buffer name)
    {
        final Field field= getField(name);

        if (field == null)
            return null;

        return new Enumeration()
        {
            Field f= field;
            public boolean hasMoreElements()
            {
                while (f != null && f._revision != _revision)
                    f= f._next;
                return f != null;
            }
            public Object nextElement() throws NoSuchElementException
            {
                if (f == null)
                    throw new NoSuchElementException();
                Field n= f;
                do f= f._next;
                while (f != null && f._revision != _revision);
                return n._value;
            }
        };
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
                    String next=(String) tok.nextElement();
		    if (next!=null)next=next.trim();
		    return next;
                }
            };
    }
    
    /* -------------------------------------------------------------- */
    /** Set a field.
     * @param name the name of the field
     * @param value the value of the field. If null the field is cleared.
     */
    public void put(String name,String value)
    {
        Buffer n=HttpHeaders.CACHE.lookup(name);
        Buffer v=HttpHeaderValues.CACHE.lookup(value);
        put(n,v);
    }

    /* -------------------------------------------------------------- */
    /** Set a field.
     * @param name the name of the field
     * @param value the value of the field. If null the field is cleared.
     */
    public void put(Buffer name,Buffer value)
    {
        if (value==null)
            {remove(name);return;} 
        
        Field field=(Field)_bufferMap.get(name);
        
        // Look for value to replace.
        if (field!=null)
        {
            field.reset(value,_revision);
            field=field._next;
            while(field!=null)
            {
                field.clear();
                field=field._next;
            }
            return;    
        }
        else
        {
            // new value;
            field=new Field(name,value,_revision);
            _fields.add(field);
            _bufferMap.put(name,field);
        }
    }
    
        
    /* -------------------------------------------------------------- */
    /** Set a field.
     * @param name the name of the field
     * @param list the List value of the field. If null the field is cleared.
     */
    public void put(String name,List list)
    {
        if (list==null || list.size()==0)
        {
            remove(name);
            return;
        }
        Buffer n = HttpHeaders.CACHE.lookup(name);
        
        Object v=list.get(0);
        if (v!=null)
            put(n,HttpHeaderValues.CACHE.lookup(v.toString()));
        else
            remove(n);
        
        if (list.size()>1)
        {    
            java.util.Iterator iter = list.iterator();
            iter.next();
            while(iter.hasNext())
            {
                v=iter.next();
                if (v!=null)
                    put(n,HttpHeaderValues.CACHE.lookup(v.toString()));
            }
        }
    }

    
    /* -------------------------------------------------------------- */
    /** Add to or set a field.
     * If the field is allowed to have multiple values, add will add
     * multiple headers of the same name.
     * @param name the name of the field
     * @param value the value of the field.
     * @exception IllegalArgumentException If the name is a single
     *            valued field and already has a value.
     */
    public void add(String name,String value)
        throws IllegalArgumentException
    {
        Buffer n=HttpHeaders.CACHE.lookup(name);
        Buffer v=HttpHeaderValues.CACHE.lookup(value);
        add(n,v);
    }

    /* -------------------------------------------------------------- */
    /** Add to or set a field.
     * If the field is allowed to have multiple values, add will add
     * multiple headers of the same name.
     * @param name the name of the field
     * @param value the value of the field.
     * @exception IllegalArgumentException If the name is a single
     *            valued field and already has a value.
     */
    public void add(Buffer name,Buffer value)
        throws IllegalArgumentException
    {
    
        if (value==null)
            throw new IllegalArgumentException("null value");
        
        Field field=(Field)_bufferMap.get(name);
        Field last=null;
        if (field!=null)
        {
            while(field!=null && field._revision==_revision)
            {
                last=field;
                field=field._next;
            }
        }

        if (field!=null)    
            field.reset(value,_revision);
        else
        {
            // create the field
            field=new Field(name,value,_revision);
            
            // look for chain to add too
            if(last!=null)
            {
                field._prev=last;
                last._next=field;    
            }
            else
                _bufferMap.put(name, field);
                
            _fields.add(field);
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Remove a field.
     * @param name 
     */
    public void remove(String name)
    {
        remove (HttpHeaders.CACHE.lookup(name));
    }

    /* ------------------------------------------------------------ */
    /** Remove a field.
     * @param name 
     */
    public void remove(Buffer name)
    {
        Field field=(Field)_bufferMap.get(name);

        if (field!=null)
        {
            while(field!=null)
            {
                field.clear();
                field=field._next;
            }
        }     
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
        Field field = getField(name);
        if (field!=null && field._revision==_revision)
            return BufferUtil.toInt(field._value);
        
        return -1;
    }
   
    /* -------------------------------------------------------------- */
    /** Get a header as an integer value.
     * Returns the value of an integer field or -1 if not found.
     * The case of the field name is ignored.
     * @param name the case-insensitive field name
     * @exception NumberFormatException If bad integer found
     */
    public int getIntField(Buffer name)
        throws NumberFormatException
    {
        Field field = getField(name);
        if (field!=null && field._revision==_revision)
            return BufferUtil.toInt(field._value);
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
        if (val==null)
            return -1;

        for (int i=0;i<_dateReceive.length;i++)
        {
            if (_dateReceive[i]==null)
                _dateReceive[i]=(SimpleDateFormat)__dateReceive[i].clone();
                
            try{
                Date date=(Date)_dateReceive[i].parseObject(val);
                return date.getTime();
            }
            catch(java.lang.Exception e)
            {}
        }
        if (val.endsWith(" GMT"))
        {
            val=val.substring(0,val.length()-4);
            for (int i=0;i<_dateReceive.length;i++)
            {
                try{
                    Date date=(Date)_dateReceive[i].parseObject(val);
                    return date.getTime();
                }
                catch(java.lang.Exception e)
                {}
            }
        }

        throw new IllegalArgumentException("Cannot convert date: "+val);
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
     * Adds the value of a date field.
     * @param name the field name
     * @param date the field date value
     */
    public void addDateField(String name, long date)
    {
        if (_dateBuffer==null)
        {
            _dateBuffer=new StringBuffer(32);
            _calendar=new GregorianCalendar(__GMT);
        }
        _dateBuffer.setLength(0);
        _calendar.setTimeInMillis(date);
        formatDate(_dateBuffer, _calendar, false);
        add(name, _dateBuffer.toString());
    }
    
    /* -------------------------------------------------------------- */
    /**
     * Sets the value of a date field.
     * @param name the field name
     * @param date the field date value
     */
    public void putDateField(String name, long date)
    {
        if (_dateBuffer==null)
        {
            _dateBuffer=new StringBuffer(32);
            _calendar=new GregorianCalendar(__GMT);
        }
        _dateBuffer.setLength(0);
        _calendar.setTimeInMillis(date);
        formatDate(_dateBuffer, _calendar, false);
        put(name, _dateBuffer.toString());
    }

    /* ------------------------------------------------------------ */
    /** Format a set cookie value
     * @param cookie The cookie.
     * @param cookie2 If true, use the alternate cookie 2 header
     */
    public void addSetCookie(
        String name,
        String value,
        String path,
        String domain, 
        long maxAge,
        int version,
        String extraParams)
    {
        
        // Check arguments
        if (name==null || name.length()==0)
            throw new IllegalArgumentException("Bad cookie name");

        // Format value and params
        StringBuffer buf = new StringBuffer(128);
        String name_value_params=null;
        synchronized(buf)
        {
            buf.append(name);
            buf.append('=');
            if (value!=null && value.length()>0)
            {
                if (version==0)
                {
                    // TODO - better than this
                    try{buf.append(URLEncoder.encode(value,StringUtil.__ISO_8859_1));}
                    catch(UnsupportedEncodingException e){e.printStackTrace();}
                }
                else
                    QuotedStringTokenizer.quote(buf,value);
            }

            if (version>0)
            {
                buf.append(";version=");
                buf.append(version);
            }
            if (path!=null && path.length()>0)
            {
                buf.append(";path=");
                buf.append(path);
            }
            if (domain!=null && domain.length()>0)
            {
                buf.append(";domain=");
                buf.append(domain.toLowerCase());// lowercase for IE
            }
            
            if (maxAge>=0)
            {
                if (version==0)
                {
                    buf.append(";expires=");
                    if (maxAge==0)
                        buf.append(__01Jan1970);
                    else
                        formatDate(buf,System.currentTimeMillis()+1000L*maxAge,true);
                }
                else
                {
                    buf.append (";max-age=");
                    buf.append (maxAge);
                }
            }
            else if (version>0)
            {
                buf.append (";discard");
            }
            if (extraParams!=null)
            {
                buf.append(extraParams);
            }
            
            // TODO - straight to Buffer?
            name_value_params = buf.toString();
        }
        put(HttpHeaders.EXPIRES_BUFFER,__01Jan1970_BUFFER);
        add(HttpHeaders.SET_COOKIE_BUFFER,new ByteArrayBuffer(name_value_params)); 
    }

    
    /* -------------------------------------------------------------- */
    public void write(Writer writer)
        throws IOException
    {
        synchronized(writer)
        {
            if (_status>0)
            {
                if (_method!=null)
                    Portable.throwIllegalState("status and method");
                if (_version==null)
                    _version=HttpVersions.CACHE.get(HttpVersions.HTTP_1_1_ORDINAL);
                writer.write(_version.toString());
                writer.write(' ');
                writer.write(_status);
                writer.write(' ');
                if (_reason==null)
                {
                    _reason=HttpStatus.CACHE.get(_status);
                    if (_reason==null)
                        _reason=HttpStatus.CACHE.get(HttpStatus.ORDINAL_999_Unknown);
                }
                writer.write(_reason.toString());
            }
            else
            {
                if (_method==null)
                    Portable.throwIllegalState("no status or method");
                writer.write(_method.toString());
                writer.write(' ');
                writer.write(_uri.toString());
                if (_version!=null)
                {
                    writer.write(' ');
                    writer.write(_version.toString());
                }
            }
            writer.write(StringUtil.CRLF);
            for (int i=0;i<_fields.size();i++)
            {
                Field field=(Field)_fields.get(i);
                if (field!=null && field._revision==_revision)
                    field.write(writer);
            }
            writer.write(StringUtil.CRLF);
        }
    }
    
    /* -------------------------------------------------------------- */
    public void put(Buffer buffer) throws IOException
    {
        if (_status>0)
        {
            if (_method!=null)
                Portable.throwIllegalState("status and method");
            if (_version==null)
                _version=HttpVersions.CACHE.get(HttpVersions.HTTP_1_1_ORDINAL);
            buffer.put(_version);
            buffer.put((byte)' ');
            BufferUtil.putDecInt(buffer, _status);
            buffer.put((byte)' ');
            if (_reason==null)
            {
                _reason=HttpStatus.CACHE.get(_status);
                if (_reason==null)
                    _reason=HttpStatus.CACHE.get(HttpStatus.ORDINAL_999_Unknown);
            }
            buffer.put(_reason);
        }
        else
        {
            if (_method==null)
                Portable.throwIllegalState("no status or method");
            buffer.put(_method);
            buffer.put((byte)' ');
            buffer.put(_uri);
            if (_version!=null)
            {
                buffer.put((byte)' ');
                buffer.put(_version);
            }
        }
        BufferUtil.putCRLF(buffer);
        
        for (int i= 0; i < _fields.size(); i++)
        {
            Field field= (Field)_fields.get(i);
            if (field != null && field._revision == _revision)
                field.put(buffer);
        }
        BufferUtil.putCRLF(buffer);
    }
       
    /* -------------------------------------------------------------- */
    public String toString()
    {
        try
        {
            ByteArrayBuffer buffer = new ByteArrayBuffer(4096);
            put(buffer);
            return buffer.toString();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        
        return null;
    }

    /* ------------------------------------------------------------ */
    /** Clear the header.
     */
    public void clear()
    {
        _method=null;
        _uri=null;
        _version=null;
        _status=0;
        _reason=null;
        
        _revision++;
        if (_revision>1000000)
        {
            _revision=0;
            for (int i=_fields.size();i-->0;)
            {
                Field field=(Field)_fields.get(i);
                if (field!=null)
                    field.clear();
            }
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Destroy the header.
     * Help the garbage collector by null everything that we can.
     */
    public void destroy()
    {   
        if (_fields!=null)
        {
            for (int i=_fields.size();i-->0;)
            {
                Field field=(Field)_fields.get(i);
                if (field!=null)
                    field.destroy();
            }
        }
        _fields=null;
        _dateBuffer=null;
        _calendar=null;
        _dateReceive=null;
    }
    
    
    /* ------------------------------------------------------------ */
    /** Add fields from another HttpFields instance.
     * Single valued fields are replaced, while all others are added.
     * @param fields 
     */
    public void add(HttpHeader fields)
    {
        if (fields==null)
            return;

        Enumeration enum = fields.getFieldNames();
        while( enum.hasMoreElements() )
        {
            String name = (String)enum.nextElement();
            Enumeration values = fields.getValues(name);
            while(values.hasMoreElements())
                add(name,(String)values.nextElement());
        }
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
    private static Float __one = new Float("1.0");
    private static Float __zero = new Float("0.0");
    private static StringMap __qualities=new StringMap();
    static
    {
        __qualities.put(null,__one);
        __qualities.put("1.0",__one);
        __qualities.put("1",__one);
        __qualities.put("0.9",new Float("0.9"));
        __qualities.put("0.8",new Float("0.8"));
        __qualities.put("0.7",new Float("0.7"));
        __qualities.put("0.66",new Float("0.66"));
        __qualities.put("0.6",new Float("0.6"));
        __qualities.put("0.5",new Float("0.5"));
        __qualities.put("0.4",new Float("0.4"));
        __qualities.put("0.33",new Float("0.33"));
        __qualities.put("0.3",new Float("0.3"));
        __qualities.put("0.2",new Float("0.2"));
        __qualities.put("0.1",new Float("0.1"));
        __qualities.put("0",__zero);
        __qualities.put("0.0",__zero);
    }

    /* ------------------------------------------------------------ */
    public static Float getQuality(String value)
    {
        if (value==null)
            return __zero;
        
        int qe=value.indexOf(";");
        if (qe++<0 || qe==value.length())
            return __one;
        
        if (value.charAt(qe++)=='q');
        {
            qe++;
            Map.Entry entry=__qualities.getEntry(value,qe,value.length()-qe);
            if (entry!=null)
                return (Float)entry.getValue();
        }
        
        HashMap params = new HashMap(3);
        valueParameters(value,params);
        String qs=(String)params.get("q");
        Float q=(Float)__qualities.get(qs);
        if (q==null)
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

        Object list=null;
        Object qual=null;

        // Assume list will be well ordered and just add nonzero
        while(enum.hasMoreElements())
        {
            String v=enum.nextElement().toString();
            Float q=getQuality(v);

            if (q.floatValue()>=0.001)
            {
                list=LazyList.add(list,v);
                qual=LazyList.add(qual,q);
            }
        }

        List vl=LazyList.getList(list,false);
        if (vl.size()<2)
            return vl;

        List ql=LazyList.getList(qual,false);

        // sort list with swaps
        Float last=__zero;
        for (int i=vl.size();i-->0;)
        {
            Float q = (Float)ql.get(i);
            if (last.compareTo(q)>0)
            {
                Object tmp=vl.get(i);
                vl.set(i,vl.get(i+1));
                vl.set(i+1,tmp);
                ql.set(i,ql.get(i+1));
                ql.set(i+1,q);
                last=__zero;
                i=vl.size();
                continue;
            }
            last=q;
        }
        ql.clear();
        return vl;
    }
    

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private static final class Field
    {
        Buffer _name;
        Buffer _value;
        Field _next;
        Field _prev;
        int _revision;

        /* ------------------------------------------------------------ */
        Field(Buffer name, Buffer value, int revision)
        {
            _name=name.asReadOnlyBuffer();
            _value=value.asReadOnlyBuffer();
            _next=null;
            _prev=null;
            _revision=revision;
        }
        
        void clear()
        {
            _revision=-1;
        }
        
        /* ------------------------------------------------------------ */
        void destroy()
        {
            _name=null;
            _value=null;
            _next=null;
            _prev=null;
        }
        
        /* ------------------------------------------------------------ */
        /** Reassign a value to this field.
         * Checks if the value is the same as that in the char array, if so
         * then just reuse existing value.
         */
        void reset(Buffer value, int revision)
        {  
            if (_value==null || !_value.equals(value))
                _value=value.asReadOnlyBuffer();
            _revision=revision;
        }
        
        /* ------------------------------------------------------------ */
        void write(Writer writer)
            throws IOException
        {
            writer.write(_name.toString());
            writer.write(":");
            writer.write(_value.toString());
            writer.write(StringUtil.CRLF);  
        }
        
        /* ------------------------------------------------------------ */
        void put(Buffer buffer)
            throws IOException
        {
           buffer.put(_name);
           buffer.put((byte)':');
           buffer.put((byte)' ');
           buffer.put(_value);
           BufferUtil.putCRLF(buffer);
        }

        /* ------------------------------------------------------------ */
        public String getDisplayName()
        {
            return _name.toString();
        }
        
        /* ------------------------------------------------------------ */
        public String toString()
        {
            return ("["+
                (_prev==null?"":"<-")+
                getDisplayName()+"="+_value+
                (_next==null?"":"->")+
                "]");
        }
    }


}