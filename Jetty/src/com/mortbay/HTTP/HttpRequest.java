// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP;
import com.mortbay.Util.*;
import com.sun.java.util.collections.*;

import java.io.*;
import java.net.*;
import java.util.*;


/* ------------------------------------------------------------ */
/** HTTP Request.
 * This class manages the headers, trailers and content streams
 * of a HTTP request. It can be used for receiving or generating
 * requests.
 *
 * @see HttpResponse
 * @version 1.0 Thu Oct 5 1999
 * @author Greg Wilkins (gregw)
 */
public class HttpRequest extends HttpMessage
{
    /* ------------------------------------------------------------ */
    /** Request METHODS
     */
    public static final String
        __OPTIONS="OPTIONS",
        __GET="GET",
        __HEAD="HEAD",
        __POST="POST",
        __PUT="PUT",
        __DELETE="DELETE",
        __TRACE="TRACE",
        __CONNECT="CONNECT",
        __MOVE="MOVE";

    /* ------------------------------------------------------------ */
    private String _method=null;
    private URI _uri=null;
    private String _host;
    private int _port;
    private List _te;
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public HttpRequest()
    {}
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param connection 
     */
    public HttpRequest(HttpConnection connection)
    {
        super(connection);
    }

    /* ------------------------------------------------------------ */
    /** Get the HTTP Response.
     * Get the HTTP Response associated with this request.
     * @return associated response
     */
    public HttpResponse getResponse()
    {
        if (_connection==null)
            return null;
        return _connection.getResponse();
    }
    
    /* ------------------------------------------------------------ */
    /** XXX
     * @param in 
     * @exception IOException 
     */
    public synchronized void readHeader(ChunkableInputStream in)
        throws IOException
    {
        _state=__MSG_BAD;
        
        // Get start line
        com.mortbay.Util.LineInput$LineBuffer line_buffer
                =in.readLine(in.__maxLineLength);
        if (line_buffer==null)
            throw new IOException("EOF");
        if (line_buffer.size==in.__maxLineLength)
            throw new HttpException(HttpResponse.__414_Request_URI_Too_Large);
        decodeRequestLine(line_buffer.buffer,line_buffer.size);
        
        // Read headers
        _header.read((LineInput)in.getRawStream());

        // Handle version
        if (__HTTP_1_1.equals(_version))
            _version=__HTTP_1_1;
        else if (__HTTP_1_0.equals(_version))
            _version=__HTTP_1_0;
        else if (__HTTP_0_9.equals(_version))
            _version=__HTTP_0_9;
        
        _state=__MSG_RECEIVED;
    }
    
    /* -------------------------------------------------------------- */
    /** Write the request header.
     * Places the message in __MSG_SENDING state.
     * @param out Chunkable output stream
     * @exception IOException IO problem
     */
    public synchronized void writeHeader(OutputStream out)
        throws IOException
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not MSG_EDITABLE");
        
        _state=__MSG_BAD;
        synchronized(out)
        {
            out.write(_method.getBytes());
            out.write(' ');
            out.write(_uri.toString().getBytes());
            out.write(' ');
            out.write(_version.getBytes());
            out.write(HttpFields.__CRLF_B);
            _header.write(out);
            out.flush();
        }
        _state=__MSG_SENDING;
    }

    /* -------------------------------------------------------------- */
    /** Return the HTTP request line as it was received
     */
    public String getRequestLine()
    {
        return _method+" "+_uri+" "+_version;
    }
    
    /* -------------------------------------------------------------- */
    /** Get the HTTP method for this request.
     * Returns the method with which the request was made. The returned
     * value can be "GET", "HEAD", "POST", or an extension method. Same
     * as the CGI variable REQUEST_METHOD.
     * @return The method
     */
    public String getMethod()
    {
        return _method;
    }
    
    /* -------------------------------------------------------------- */
    /** Set the HTTP method for this request.
     * @param method the method
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setMethod(String method)
        throws IllegalStateException
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        _method=method;
    }
    
    /* -------------------------------------------------------------- */
    /** Get the full URI.
     * @return A cloned copy of the request URI
     */
    public URI getURI()
    {
        return (URI)_uri.clone();
    }
    
    /* -------------------------------------------------------------- */
    /** Set the HTTP URI
     * @param uri the uri
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setURI (URI uri)
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        _uri=uri;
    }
    
    /* ------------------------------------------------------------ */
    /** Get the request protocol.
     * The protocol is obtained from an absolute URI.  If the URI in
     * the request is not absolute, "http" is returned.
     * NB. This is different to the getProtocol method of a
     * HttpServletRequest, which returns the protocol version.
     * @return The request protocol
     */
    public String getProtocol()
    {
        String protocol=_uri.getProtocol();
        return protocol==null?"http":protocol;
    }
    
    /* ------------------------------------------------------------ */
    /** Set the request protocol.
     * If the URI was not previously an absolute URI, the URI host and
     * port are also set from the HTTP Host Header field.
     * @param protocol The protocol
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setProtocol(String protocol)
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        
        if (!_uri.isAbsolute())
        {
            _uri.setHost(getHost());
            _uri.setPort(getPort());
        }
        _uri.setProtocol(protocol);
    }
    
    /* ------------------------------------------------------------ */
    /** Get the request host.
     * The host is obtained from an absolute URI, the HTTP header field,
     * the requests connection or the local host name.
     * @return 
     */
    public String getHost()
    {
        // Return already determined host
        if (_host!=null)
            return _host;

        // Return host from absolute URI
        _host=_uri.getHost();
        _port=_uri.getPort();
        if (_host!=null)
            return _host;

        // Return host from header field
        _host=_header.get(HttpFields.__Host);
        _port=0;
        if (_host!=null)
        {
            int colon=_host.indexOf(':');
            if (colon>=0)
            {
                if (colon<_host.length())
                {
                    try{
                        _port=Integer
                            .parseInt(_host.substring(colon+1));
                    }
                    catch(Exception e)
                    {Code.ignore(e);}
                }
                _host=_host.substring(0,colon);
            }

            return _host;
        }

        // Return host from connection
        if (_connection!=null)
        {
            _host=_connection.getHost();
            _port=_connection.getPort();
            return _host;
        }

        // Return the local host
        try {_host=InetAddress.getLocalHost().getHostName();}
        catch(java.net.UnknownHostException e){Code.ignore(e);}
        return _host;
    }
    
    /* ------------------------------------------------------------ */
    /** Set the request host.
     * If the current uri is absolute, then the URI is updated.
     * The HTTP Host header field is always updated.
     * @param host The host
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setHost(String host)
        throws IllegalStateException
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        
        _host=host;
        if (_uri.isAbsolute())
            _uri.setHost(host);
        
        getPort();
        if (_port>0)
            _header.put(HttpFields.__Host,host+":"+_port);
        else
            _header.put(HttpFields.__Host,host);
    }
    
    /* ------------------------------------------------------------ */
    /** Get the request port.
     * The port is obtained either from an absolute URI, the HTTP
     * Host header field, the connection or the default.
     * @return The port.  0 should be interpreted as the default port.
     */
    public int getPort()
    {
        if (_port>0)
            return _port;
        if (_uri.isAbsolute())
            _port=_uri.getPort();
        else if (_connection!=null)
            _port=_connection.getPort();
        return _port;    
    }
    
    /* ------------------------------------------------------------ */
    /** Set the request port.
     * If the current uri is absolute, then the URI is updated.
     * The HTTP Host header field is always updated.
     * @param port The port
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setPort(int port)
        throws IllegalStateException
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        
        _port=port;
        if (_uri.isAbsolute())
            _uri.setPort(port);
        
        getHost();
        if (_port>0)
            _header.put(HttpFields.__Host,_host+":"+_port);
        else
            _header.put(HttpFields.__Host,_host);
    }
    
    /* ------------------------------------------------------------ */
    /** Get the request path
     * @return 
     */
    public String getPath()
    {
        return _uri.getPath();
    }
    
    /* ------------------------------------------------------------ */
    /** Set the request path
     * @param path The path
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setPath(String path)
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        
        _uri.setPath(path);
    }
    
    /* ------------------------------------------------------------ */
    /** Get the request query
     * @return 
     */
    public String getQuery()
    {
        return _uri.getQuery();
    }
    
    /* ------------------------------------------------------------ */
    /** Set the request query
     * @param query The query
     * @exception IllegalStateException Request is not EDITABLE
     */
    public void setQuery(String query)
    {
        if (_state!=__MSG_EDITABLE)
            throw new IllegalStateException("Not EDITABLE");
        
        _uri.setQuery(query);
    }

    /* ------------------------------------------------------------ */
    /** Decode HTTP request line
     * @param buf Character buffer
     * @param len Length of line in buffer.
     * @exception IOException 
     */
    void decodeRequestLine(char[] buf,int len)
        throws IOException
    {        
        // Search for first space separated chunk
        int s1=-1,s2=-1,s3=-1;
        int state=0;
    startloop:
        for (int i=0;i<len;i++)
        {
            char c=buf[i];
            switch(state)
            {
              case 0: // leading white
                  if (c==' ')
                      continue;
                  state=1;
                  s1=i;
                  
              case 1: // reading method
                  if (c==' ')
                      state=2;
                  else
                  {
                      s2=i;
                      if (c>='a'&&c<='z')
                          buf[i]=(char)(c-'a'+'A');
                  }
                  continue;
                  
              case 2: // skip whitespace after method
                  s3=i;
                  if (c!=' ')
                      break startloop;
            }
        }

        // Search for first space separated chunk
        int e1=-1,e2=-1,e3=-1;
        state=0;
    endloop:
        for (int i=len;i-->0;)
        {
            char c=buf[i];
            switch(state)
            {
              case 0: // trailing white
                  if (c==' ')
                      continue;
                  state=1;
                  e1=i;
                  
              case 1: // reading Version
                  if (c==' ')
                      state=2;
                  else
                      e2=i;
                  continue;
                  
              case 2: // skip whitespace after method
                  e3=i;
                  if (c!=' ')
                      break endloop;
            }
        }
        
        // Check sufficient params
        if (s3<0 || e1<0 || e3<s2 )
            throw new HttpException(400);

        // get method
        _method=new String(buf,s1,s2-s1+1);
        
        // get version as uppercase
        if (s2!=e3 || s3!=e2)
        {
            for (int i=e2;i<=e1;i++)
                if (buf[i]>='a'&&buf[i]<='z')
                    buf[i]=(char)(buf[i]-'a'+'A');
            _version=new String(buf,e2,e1-e2+1);
        }
        else
        {
            // missing version
            _version=__HTTP_1_0;
            e3=e1;
        }

        // handle URI
        _uri= new URI(new String(buf,s3,e3-s3+1));
        
    }
    
    /* ------------------------------------------------------------ */
    /** Force a removeField.
     * This call ignores the message state and forces a field
     * to be removed from the request.  It is required for the
     * handling of the Connection field.
     * @param name The field name
     * @return The old value or null.
     */
    public synchronized Object forceRemoveField(String name)
    {
        Code.debug("force remove ",name);
        int saved_state=_state;
        try{
            _state=__MSG_EDITABLE;
            return removeField(name);
        }
        finally
        {
            _state=saved_state;
        }
    }


    /* ------------------------------------------------------------ */
    /** Get the acceptable transfer encodings.
     * The TE field is used to construct a list of acceptable
     * extension transfer codings in quality order.
     * An empty list implies that only "chunked" is acceptable.
     * A null list implies that no transfer coding can be applied.
     *
     * If the "trailer" coding is found in the TE field, then
     * message trailers are enabled in any linked response.
     * @return List of codings.
     */
    public List getAcceptableTransferCodings()
    {
        if (_version.equals(__HTTP_1_0))
            return null;
        if (_te!=null)
            return _te;
        
        // Decode any TE field
        List te = getFieldValues(HttpFields.__TE);
        if (te!=null && te.size()>0)
        {
            // Sort the list
            te=HttpFields.qualityList(te);

            // remove trailer and chunked items.
            ListIterator iter = te.listIterator();
            while(iter.hasNext())
            {
                String coding= StringUtil.asciiToLowerCase
                    (HttpFields.valueParameters(iter.next().toString(),null));
                
                iter.set(coding);
                if ("trailer".equals(coding))
                {
                    // Allow trailers in the response
                    HttpResponse response=getResponse();
                    if (response!=null)
                        response.setAcceptTrailer(true);
                    iter.remove();
                }
                else if (HttpFields.__Chunked.equals(coding))
                    iter.remove();
            }
            _te=te;
        }
        else
            _te=Collections.EMPTY_LIST;

        return _te;
    }
    
    
    /* ------------------------------------------------------------ */
    /** Destroy the request.
     * Help the garbage collector by null everything that we can.
     */
    public void destroy()
    {
        _method=null;
        _uri=null;
        _version=null;
        super.destroy();
    }
}

           

