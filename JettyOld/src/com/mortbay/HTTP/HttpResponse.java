// ========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ------------------------------------------------------------------------

package com.mortbay.HTTP;

import com.mortbay.Base.*;
import com.mortbay.Util.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;


/* ------------------------------------------------------------------ */
/** Hnadling of a HTTP response
 * <p> Implements and extends the javax.servlet.http.HttpServletResponse
 * interface.
 * The extensions are for HttpHandler instances that need to modify
 * the response or have better access to the IO.
 *
 * <p><h4>Note</h4>
 * By default, responses will only use chunking if requested by the
 * by setting the transfer encoding header.  However, if
 * the java property CHUNK_BY_DEFAULT is set, then chunking is
 * used if no content length is set. The chunking default may also
 * be set on a per servlet holder basis.

 * @see com.mortbay.HTTP.HttpServer
 * @version $Id$
 * @author Greg Wilkins
*/
public class HttpResponse extends HttpHeader implements HttpServletResponse
{
    /* ---------------------------------------------------------------- */
    public final static boolean chunkByDefaultDefault =
	System.getProperty("CHUNK_BY_DEFAULT")!=null;
    
    /* -------------------------------------------------------------- */
    public final static String MIME_Version ="MIME-Version"   ;
    public final static String Server ="Server"   ;
    public final static String Expires ="Expires"   ;
    public final static String Location ="Location"   ;
    
    /* -------------------------------------------------------------- */
    private String version;
    private String status;
    private String reason;
    private HttpOutputStream httpOut;
    private OutputStream out;
    private PrintWriter writer;
    private boolean headersWritten=false;   
    private Cookies cookies = null;
    private Vector filters=null;
    private HttpRequest request=null;
    private Observable observable=null;
    private boolean chunkByDefault=chunkByDefaultDefault;
    private boolean doNotClose=false;
    
    /* -------------------------------------------------------------- */
    /** Construct a response
     * @param out The output stream that the response will be written to.
     * @param request The HttpRequest that this response is to.
     */
    public HttpResponse(OutputStream out, HttpRequest request)
    {
	this.out=out;
	this.httpOut=new HttpOutputStream(out,this);
	this.request=request;
	request.response=this;
	version = HttpHeader.HTTP_1_0;
	status = Integer.toString(SC_OK);
	reason = "OK";
	setContentType("text/html");
	setHeader(MIME_Version,"1.0");
	setHeader(Server,"MortBay-Jetty-2 ");
	setDateHeader(Date,System.currentTimeMillis());

	if (HttpHeader.Close.equals(request.getHeader(HttpHeader.Connection)))
	    setHeader(HttpHeader.Connection,HttpHeader.Close);
    }
    
    /* -------------------------------------------------------------- */
    public String getVersion()
    {
	return version;
    }
    
    /* -------------------------------------------------------------- */
    public void setVersion(String version)
    {
	this.version=version;
    }
    
    /* -------------------------------------------------------------- */
    public String getStatus()
    {
	return status;
    }
    
    /* -------------------------------------------------------------- */
    public String getReason()
    {
	return reason;
    }
    
    /* -------------------------------------------------------------- */
    /** Get the HttpRequest for this response
     */
    public HttpRequest getRequest()
    {
	return request;
    }
    
    /* -------------------------------------------------------------- */
    public String getResponseLine()
    {
	return version + " "+status + " " + reason;
    }

    /* -------------------------------------------------------------- */
    public void setChunkByDefault(boolean chunk)
    {
	chunkByDefault=chunk;
    }
    
    /* -------------------------------------------------------------- */
    /** Add an observer to the response.
     * Observers are notified when the HTTP headers are complete
     * and before any output has been written.  Thus observers may
     * examine or modify the headers and activate filters.
     * Notify is called with the request as the argument object
     */
    public void addObserver(Observer o)
    {
	if (observable==null)
	    observable=new Observed();
	Code.debug("Added Observer "+o);
	observable.addObserver(o);
    }
    
    /* -------------------------------------------------------------- */
    public void deleteObserver(Observer o)
    {
	if (observable!=null)
	    observable.deleteObserver(o);
    }

    /* -------------------------------------------------------------- */
    /** Complete the response.
     * Will conditionally call close to indicate the end
     * of the response
     */
    public void complete()
	throws IOException
    {
	if (!doNotClose)
	    httpOut.close();
	doNotClose=false;
    }
    
    /* -------------------------------------------------------------- */
    /** Return true if the headers have already been written for this
     * response
     */
    public boolean headersWritten()
    {
	return headersWritten;
    }
    
    /* -------------------------------------------------------------- */
    /** If the headers have not alrady been written, write them.
     * If any HttpFilters have been added activate them before writing.
     */
    public void writeHeaders() 
	throws IOException
    {
	if (!headersWritten)
	{
	    // Add Date if not already there
	    if (getHeader(HttpHeader.Date)==null)
	    {
		int s = Integer.parseInt(status);
		if (s<100 || s>199)
		    setDateHeader(HttpHeader.Date,new Date().getTime());
	    }

	    
	    // Tell anybody who wants to not headers are complete
	    // (eg. to activate filters by content-type)
	    if (observable!=null)
	    {
		Code.debug("notify Observers");
		observable.notifyObservers(this);
	    }
	    
	    // Should we chunk or close
	    if (HttpHeader.HTTP_1_1.equals(version))
	    {
		String encoding = getHeader(HttpHeader.TransferEncoding);
		String connection =getHeader(Connection);
		String length = getHeader(HttpHeader.ContentLength);
		    
		// chunk if we are told to
		if (encoding!=null && encoding.equals(HttpHeader.Chunked))
		{
		    httpOut.setChunking(true);
		}
		// if we hve no content length then ...
		else if (length==null)
		{
		    // if not closing and chunk by default
		    if (!(HttpHeader.Close.equals(connection))
			&& chunkByDefault)
		    {
			// need to chunk
			setHeader(HttpHeader.TransferEncoding,
				  HttpHeader.Chunked);
			httpOut.setChunking(true);
		    }
		    else
		    {
			// have to close to mark the end
			setHeader(Connection,HttpHeader.Close);
		    }
		}
		else
		{
		    // We have a content length, so we will not be
		    // chunking, but we can be persistent, so we must
		    // hide the next close.
		    doNotClose=true;
		}
	    }
	    else
	    {
		setHeader(Connection,HttpHeader.Close);
	    }
	    
	    
	    // Write the headers
	    Code.debug("Write Headers");
	    headersWritten=true;
	    out.write(getResponseLine().getBytes());
	    out.write(__CRLF);
	    if (cookies!=null)
		super.write(out,cookies.toString());
	    else
		super.write(out);

	    // Handle HEAD
	    if (request.getMethod().equals("HEAD"))
		// Fake a break in the HttpOutputStream
		throw HeadException.instance;
	    
	}
    }
    
    /* ------------------------------------------------------------- */
    /** Copy all data from an input stream to the HttpResponse.
     * This method assumes that the input stream does not include HTTP
     * headers.
     * @param stream the InputStream to read
     * @param length If greater than 0, this is the number of bytes
     *        to read and write
     */
    public void writeInputStream(InputStream stream,long length)
	throws IOException
    {
	writeInputStream(stream,length,false);
    }
    
    /* ------------------------------------------------------------- */
    /** Copy all data from an input stream to the HttpResponse.
     * @param stream the InputStream to read
     * @param length If greater than 0, this is the number of bytes
     *        to read and write
     * @param streamIncludesHeaders True when the input stream includes
     *        HTTP headers which replace those set in this HttpResponse.
     */
    public void writeInputStream(InputStream stream,
				 long length,
				 boolean streamIncludesHeaders)
	throws IOException
    {
	Code.debug("writeInputStream:"+length);
	if (streamIncludesHeaders)
	{
	    Code.assert(!headersWritten(),"Headers already written");
	    headersWritten=true;
	    if (observable!=null)
		observable.notifyObservers(this);
	}
	
	if (stream!=null)
	    IO.copy(stream,getOutputStream(),length);
    }
    
    
    /* -------------------------------------------------------------- */
    /** Return the Cookies instance
     * These are the "Set-Cookies" that will be sent with this response.
     * Cookies may be added, modified or deleted from the Cookies instance.
     * @deprecated Use addCookie()
     */
    public Cookies getCookies()
    {
	if (cookies==null)
	    cookies=new Cookies();
	return cookies;
    }
    

    /* -------------------------------------------------------------- */
    /* ServletResponse methods -------------------------------------- */
    /* -------------------------------------------------------------- */

    /* ------------------------------------------------------------- */
    /** Set the content length of the response
     */
    public void setContentLength(int len)
    {
	setHeader(ContentLength,Integer.toString(len));
    }

    /* ------------------------------------------------------------- */
    /** Set the content type of the response
     */
    public void setContentType(String type)
    {
	setHeader(ContentType,type);
    }


    /* ------------------------------------------------------------- */
    /** Get the OutputStream of the response.
     * The first write to this stream will trigger writing of the
     * HTTP filters and potential activation of any HttpFilters.
     */
    public ServletOutputStream getOutputStream()
    {
	return httpOut;
    }
    
    /* -------------------------------------------------------------- */
    /* HttpServletResponse methods ---------------------------------- */
    /* -------------------------------------------------------------- */
    
    /* ------------------------------------------------------------- */
    /**
     * Sets the status code and message for this response.
     * @param code the status code
     * @param msg the status message
     */
    public void setStatus(int code,String msg)
    {
	status=Integer.toString(code);
	reason=msg;
    }

    /* ------------------------------------------------------------- */
    /**
     * Sets the status code and a default message for this response.
     * @param code the status code
     */
    public void setStatus(int code)
    {
	status=Integer.toString(code);
	reason=status;
    }
      
    /* ------------------------------------------------------------- */
    /**
     * Sends an error response to the client using the specified status
     * code and detail message.
     * @param code the status code
     * @param msg the detail message
     * @exception IOException If an I/O error has occurred.
     */
    public void sendError(int code,String msg) 
	throws IOException
    {
	setStatus(code,msg);
	writeHeaders();
    }
      
    /* ------------------------------------------------------------- */
    /**
     * Sends an error response to the client using the specified status
     * code and no default message.
     * @param code the status code
     * @exception IOException If an I/O error has occurred.
     */
    public void sendError(int code) 
	throws IOException
    {
	setStatus(code);
	writeHeaders();
    }  
    
    
    /* ------------------------------------------------------------- */
    /**
     * Sends a redirect response to the client using the specified redirect
     * location URL.
     * @param location the redirect location URL
     * @exception IOException If an I/O error has occurred.
     */
    public void sendRedirect(String location)
	throws IOException
    {
	setHeader(Location,location);
	setStatus(SC_MOVED_TEMPORARILY);
	writeHeaders();
    }
    
    /* -------------------------------------------------------------- */
    public boolean containsHeader(String headerKey)
    {
	return getHeader(headerKey)!=null;
    }

    /* -------------------------------------------------------------- */
    public void addCookie(javax.servlet.http.Cookie cookie)
    {
	if (cookies==null)
	    cookies=new Cookies();
	cookies.setCookie(cookie);
    }
	
    /* -------------------------------------------------------------- */
    public java.lang.String encodeRedirectUrl(java.lang.String url)
    {
	//XXX - Dont support rewriting
	return url;
    }
    
    /* -------------------------------------------------------------- */
    public java.lang.String encodeUrl(java.lang.String url)
    {
	//XXX - Don't support rewriting
	return url;
    }
    
    /* -------------------------------------------------------------- */
    public java.io.PrintWriter getWriter()
    {
	if (writer==null)
	    writer=new PrintWriter(new OutputStreamWriter(getOutputStream()));
	return writer;
    }

    /* -------------------------------------------------------------- */
    public String getCharacterEncoding()
    {
	return "8859_1";
    }
    

}








