// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP;
import com.mortbay.Base.*;
import com.mortbay.Util.*;
import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.http.Cookie;


/* ------------------------------------------------------------ */
/** Hashtable of cookies
 * <p>
 *
 * @version $Revision$ $Date$
 * @author Greg Wilkins (gregw)
 */
public class Cookies
{
    /* -------------------------------------------------------------- */
    private Hashtable cookies = new Hashtable(10);
    
    /* -------------------------------------------------------------- */
    public final static SimpleDateFormat __dateSend = 
        new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss 'GMT'");
    static
    {
        TimeZone tz = TimeZone.getTimeZone("GMT");
        tz.setID("GMT");
        __dateSend.setTimeZone(tz);
    }
    
    /* -------------------------------------------------------------- */
    /** Set a cookie.
     */
    public void setCookie(Cookie cookie)
    {
        String key = StringUtil
            .asciiToLowerCase((cookie.getName()+';'+
                               cookie.getPath()+';'+
                               cookie.getDomain()));
	cookies.put(key,cookie);
    }
    
    /* -------------------------------------------------------------- */
    /** Set a cookie.
     * Default values used except path which is set to "/".
     * Cookie setting are unique for a given name & path pair.
     */
    public void setCookie(String name,
                          String value)
    {
        setCookie(name,value,null,"/",-1,false);
    }
    
    /* -------------------------------------------------------------- */
    /** Set Cookie
     * Cookie setting are unique for a given name & path pair.
     * @param name The cookie name
     * @param value The cookie value which must not contain ';'.
     * @param domain The domain, which must be a subdomain of the server.
     *        If null is passed the default of this domain is used.
     * @param path The path the cookie applies to.
     *        If null is passed the default of "/" is used.
     * @param maxAge the seconds until the cookie expires. A negative value
     *        implies browser session and zero causes the cookie to be 
     *        deleted.
     * @param secure if true the cookie is flagged secure.
     */
    public void setCookie(String name,
                          String value,
                          String domain,
                          String path,
                          int maxAge,
                          boolean secure)
    {
        Code.assert(name!=null && name.length()>0,
                    "Bad cookie name passed");
        Code.assert(value!=null && value.length()>0,
                    "Bad cookie value passed");
        
        if (path==null)
            path="/";
        
        String key = StringUtil.asciiToLowerCase(name+';'+path+';'+domain);

        Cookie cookie = new Cookie(name,value);
        cookie.setPath(path);
        if (domain!=null)
            cookie.setDomain(domain);
        
	cookie.setMaxAge(maxAge);
	cookies.put(key,cookie);
    }

    /* -------------------------------------------------------------- */
    public String toString()
    {
        StringBuffer buf = new StringBuffer(128);
        Enumeration e = cookies.elements();
        String s = "";
        
        while (e.hasMoreElements())
        {
            buf.append(s);
            s=HttpHeader.CRLF;

            Cookie cookie = (Cookie)e.nextElement();
            buf.append(HttpHeader.SetCookie);
            buf.append(": ");
            buf.append(toString(cookie));
        }
        return buf.toString();
    }

    /* -------------------------------------------------------------- */
    public static String toString(Cookie cookie)
    {
        StringBuffer buf = new StringBuffer(128);
        buf.append(cookie.getName());
        buf.append('=');
        buf.append(cookie.getValue());

        String s = cookie.getPath();
        if (s!=null && s.length()>0)
        {
            buf.append("; path=");
            buf.append(s);
        }

        s = cookie.getDomain();
        if (s!=null && s.length()>0)
        {
            buf.append("; domain=");
            buf.append(s);
        }

        int maxAge = cookie.getMaxAge();
        if (maxAge>=0)
        {
            buf.append("; expires=");
            Date date = new Date(System.currentTimeMillis()+1000L*maxAge);
            buf.append(__dateSend.format(date));
        }
        
        return buf.toString();
    }
    
    /* -------------------------------------------------------------- */
    /** Decode received cookies.
     * @param buffer Contains encoded cookies
     * @return Array of Cookies.
     */
    public static Cookie[] decode(String buffer)
    {
        if (buffer!=null)
        {
            Vector cv = new Vector();
            StringTokenizer tok = new StringTokenizer(buffer,";");
            while (tok.hasMoreTokens())
            {
                String c = tok.nextToken();
                int e = c.indexOf("=");
                String n;
                String v;
                if (e>0)
                {
                    n=c.substring(0,e).trim();
                    v=c.substring(e+1).trim();
                }
                else
                {
                    n=c.trim();
                    v=UrlEncoded.noValue;
                }
                v=UrlEncoded.decode(v);
                cv.addElement(new Cookie(n,v));
            }
            Cookie[] cookies = new Cookie[cv.size()];
            cv.copyInto(cookies);
            return cookies;
        }
        return null;
    }    
}


