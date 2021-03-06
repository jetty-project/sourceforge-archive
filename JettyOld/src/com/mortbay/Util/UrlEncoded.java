// ===========================================================================
// $Id$
package com.mortbay.Util;

import com.mortbay.Base.*;

import java.io.*;
import java.util.*;
import java.net.*;

/* ------------------------------------------------------------ */
/** Handles coding of MIME  "x-www-form-urlencoded"
 *
 * <p><h4>Notes</h4>
 * The hashtable either contains String single values, vectors
 * of String or arrays of Strings.
 *
 * @see java.net.URLEncoder
 * @version 1.0 Fri Dec 12 1997
 * @author Greg Wilkins (gregw)
 */
public class UrlEncoded extends Hashtable
{
    /* -------------------------------------------------------------- */
    public static final String noValue="";

    /* ----------------------------------------------------------------- */
    public UrlEncoded()
    {
        super(10);
    }

    /* ----------------------------------------------------------------- */
    public UrlEncoded(String s)
        throws IOException
    {
        super(10);
        read(s);
    }

    /* ----------------------------------------------------------------- */
    public void read(String string_input)
         throws IOException
    {
        addParamsTo(string_input,this);
    }


    /* ------------------------------------------------------------ */
    /** Get value
     * Converts multiple values into coma separated list
     * @param key The parameter name
     * @return value
     */
    public Object get(Object key)
    {
        Object o = super.get(key);
        if (o!=null)
        {
            if (o instanceof Vector)
            {
                Vector v=(Vector)o;
                if (v.size()>0)
                {
                    String value=v.elementAt(0).toString();
                    for (int i=1; i<v.size(); i++)
                        value += ','+v.elementAt(i).toString();
                    return value;
                }
                return null;
            }

            if (o instanceof String[])
            {
                String[] a=(String[])o;
                if (a.length>0)
                {
                    StringBuffer buf = new StringBuffer(a[0]);
                    for (int i=1; i<a.length; i++)
                    {
                        buf.append(',');
                        buf.append(a[i]);
                    }
                    return buf.toString();
                }
                return null;
            }
        }
        return o;
    }

    /* ------------------------------------------------------------ */
    /** Get the value as an object
     * @param key The parameter name
     * @return Either a String value or Vector of String values
     */
    public Object getObject(Object key)
    {
        return super.get(key);
    }

    /* ------------------------------------------------------------ */
    /** Get multiple values as an array.
     * Multiple values must be specified as "N=A&N=B"
     * @param key The parameter name
     * @return array of values or null
     */
    public String[] getValues(String key)
    {
        Object o = super.get(key);
        if (o==null)
            return null;
        if (o instanceof String[])
            return (String[])o;
        if (o instanceof Vector)
        {
            Vector v = (Vector)o;
            String[] a = new String[v.size()];
            for (int i=v.size();i-->0;)
                a[i]=v.elementAt(i).toString();
            return a;
        }

        String[] a = new String[1];
        a[0]=o.toString();
        return a;
    }



    /* ------------------------------------------------------------ */
    /** Set a multi valued parameter
     * @param key The parameter name
     * @param values Array of string values
     */
    public void putValues(String key, String[] values)
    {
        super.put(key,values);
    }


    /* -------------------------------------------------------------- */
    /* Add encoded parameters to Dictionary.
     * @param content the string containing the encoded parameters
     * @param url The dictionary to add the parameters to
     */
    public static void addParamsTo(String content,UrlEncoded url)
    {
        String name;
        String value;

        StringTokenizer tokenizer = new StringTokenizer(content, "&", false);

        while ((tokenizer.hasMoreTokens()))
        {
            // take the first token string, which should be an assignment statement
            String substring = tokenizer.nextToken();

            // breaking it at the "=" sign
            int i = substring.indexOf('=');
            if (i<0)
            {
                name=decode(substring);
                value=noValue;
            }
            else
            {
                name  = decode(substring.substring(0,i++).trim());
                if (i>=substring.length())
                    value=noValue;
                else
                {
                    value =
                        substring.substring(i,substring.length()).trim();
                    value = decode(value);
                }
            }

            if (name.length() > 0)
            {
                Object o = url.getObject(name);
                if (o!=null)
                {
                    if (o instanceof Vector)
                        ((Vector)o).addElement(value);
                    else
                    {
                        Vector v = new Vector();
                        v.addElement(o);
                        v.addElement(value);
                        url.put(name,v);
                    }
                }
                else
                   url.put(name,value);
            }
        }
    }

    /* -------------------------------------------------------------- */
    /** Encode Hashtable with % encoding
     */
    public String encode()
    {
        return encode(false);
    }

    /* -------------------------------------------------------------- */
    /** Encode Hashtable with % encoding
     * @param equalsForNullValue if True, then an '=' is always used, even
     * for parameters without a value. e.g. "blah?a=&b=&c=".
     */
    public String encode(boolean equalsForNullValue)
    {
        Enumeration keys=keys();
        String separator="";
        StringBuffer result = new StringBuffer(128);
        while(keys.hasMoreElements())
        {
            String key = keys.nextElement().toString();
            String[] values = getValues(key);

            for (int v=0; v<values.length;v++)
            {
                result.append(separator);
                result.append(encode(key));

                if (values[v].length()>0)
                {
                    result.append('=');
                    result.append(encode(values[v]));
                }
                else if (equalsForNullValue)
                    result.append('=');

                separator="&";
            }
        }
        return result.toString();
    }

    /* ------------------------------------------------------------ */
    /** Perform URL encoding.
     * @param string
     * @return encoded string.
     */
    public static String encode(String string)
    {
        byte[] bytes=null;
        try
        {
            bytes=string.getBytes("ISO8859_1");
        }
        catch(UnsupportedEncodingException e)
        {
            Code.warning(e);
            bytes=string.getBytes();
        }

        int len=bytes.length;
        byte[] encoded= new byte[bytes.length*3];
        int n=0;
        boolean noEncode=true;

        for (int i=0;i<len;i++)
        {
            byte b = bytes[i];

            if (b==' ')
            {
                noEncode=false;
                encoded[n++]=(byte)'+';
            }
            else if (b>='a' && b<='z' ||
                     b>='A' && b<='Z' ||
                     b>='0' && b<='9')
            {
                encoded[n++]=b;
            }
            else
            {
                noEncode=false;
                encoded[n++]=(byte)'%';
                byte nibble= (byte) ((b&0xf0)>>4);
                if (nibble>=10)
                    encoded[n++]=(byte)('A'+nibble-10);
                else
                    encoded[n++]=(byte)('0'+nibble);
                nibble= (byte) (b&0xf);
                if (nibble>=10)
                    encoded[n++]=(byte)('A'+nibble-10);
                else
                    encoded[n++]=(byte)('0'+nibble);
            }
        }

        if (noEncode)
            return string;

        try
        {
            return new String(encoded,0,n,"ISO8859_1");
        }
        catch(UnsupportedEncodingException e)
        {
            Code.warning(e);
            return new String(encoded,0,n);
        }
    }

    /* -------------------------------------------------------------- */
    /** Decode String with % encoding
     */
    public static String decode(String encoded)
    {
        int len=encoded.length();
        char[] characters = encoded.toCharArray();
        byte[] bytes=new byte[len];
        int n=0;
        boolean noDecode=true;

        for (int i=0;i<len;i++)
        {
            char c = characters[i];
            if (c<0||c>0x7f)
                throw new IllegalArgumentException("Not encoded");

            byte b = (byte)(0x7f & c);

            if (c=='+')
            {
                noDecode=false;
                b=(byte)' ';
            }
            else if (c=='%' && (i+2)<len)
            {
                noDecode=false;
                b=(byte)(0xff&Integer.parseInt(encoded.substring(i+1,i+3),16));
                i+=2;
            }

            bytes[n++]=b;
        }

        if (noDecode)
            return encoded;

        try
        {
            return new String(bytes,0,n,"ISO8859_1");
        }
        catch(UnsupportedEncodingException e)
        {
            Code.warning(e);
            return new String(bytes,0,n);
        }

    }

}
