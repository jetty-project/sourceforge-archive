// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP;

import com.mortbay.Base.*;
import com.mortbay.Util.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Constructor;
import javax.servlet.http.*;
import javax.servlet.*;
import java.util.*;


/* ------------------------------------------------------------------------ */
/** HTTP Server.
 * This is the core class for the Jetty HTTP server.
 * The server takes it's configuration from an instance of a class
 * derived from HttpConfiguration. It then creates a HttpListener for
 * each host and port described in the configuration.
 * Requests received on those ports are converted into HttpRequests
 * and a HttpResponse is instantiated.
 *
 * The request path and server configuration are used to select a
 * handler stack (actually an array of HttpHandler instances). The request
 * and response are passed to each handler in the stack until the
 * request is handled (indicated by the response header fields having
 * been written).  HttpHandlers may choose to ignore, modify or completely
 * handle a request given to them. They also may modify the response without
 * completely handling the request.
 *
 * A typical stack of HttpHandlers would include:<UL>
 * <LI>Basic Authentication Handler - Checks username and password on
 * selected paths.
 * <LI>Parameter Handler - Reads form content and adds it to the request
 * parameters. It can also optionally read cookies and add then to the
 * request parameters.
 * <LI>Servlet Handler - Searches for a javax.servlet.http.HttpServlet to
 * handle the request.
 * <LI>File Handler - Searches for a file to be served in reply to the
 * request.
 * <LI>Not Found Handler - Generates the not found reply for requests that
 * fall through to here.
 * </UL>
 * There are other handlers for things like response filtering, request
 * forwarding, session management defined in the com.mortbay.HTTP.Handler
 * package.  These may be configured in or the user may develop their
 * own handlers. However, servlets should be used for user handling unless
 * the user requires the additional functionality of HttpRequest (typically
 * for better control of input/output of modification of the request).
 *
 * Exceptions raised by a Handler are handled themselves by a similar
 * stack of ExceptionHandlers selected by request path and configuration.
 *
 * The configuration of the server is done via an abstract class to
 * decouple the server from any particular configuration technology and
 * to allow lightweight installations.
 *
 * <p><h4>Notes</h4>
 * The server understands HTTP/1.1 and HTTP/1.0.
 * <p><h4>Usage</h4><PRE>
 * java com.mortbay.HTTP.HttpServer [ HttpConfigurationClass ]
 * </PRE>
 * @version $Id$
 * @author Greg Wilkins
 */
public class HttpServer implements ServletContext
{
    /* -------------------------------------------------------------------- */
    private static Vector __servers = new Vector();
    
    /* -------------------------------------------------------------------- */
    private HttpConfiguration config;

    private HttpListener[] listeners;
    private PathMap httpHandlersMap;
    private PathMap exceptionHandlersMap;
    private Hashtable servletHandlerMap = new Hashtable(11);
    private String resourceBase=null;

    boolean http1_0_KeepAlive=true;
    
    /* -------------------------------------------------------------------- */
    /** Construct, must be configured later
     */
    public HttpServer()
    {
    }
    
    /* -------------------------------------------------------------------- */
    /** Construct and configure
     */
    public HttpServer(HttpConfiguration config)
         throws Exception
    {
        configure(config);
    }
    
    /* -------------------------------------------------------------------- */
    /** Configure and start the server
     * Defined server properties are:<PRE>
     * SessionMaxInactiveInterval - Max idle time Ms before session death
     * MinListenerThreads         - Min listener threads per listener
     * MaxListenerThreads         - Max listener threads per listener
     * MaxListenerThreadIdleMs    - Max idle time Ms before listen thread
     *                              death
     * HTTP1_0_KeepAlive          - Boolean for using Keep-Alive with HTTP/1.0
     * MimeMap		          - Property file of MIME mappings
     * </PRE>
     */
    public synchronized void configure(HttpConfiguration config)
         throws Exception
    {
        close();
        
        this.config=config;
        exceptionHandlersMap=config.exceptionHandlersMap();
        httpHandlersMap=config.httpHandlersMap();

        Hashtable handlerSet = new Hashtable(20);

        // for all handler stacks
        Enumeration e = httpHandlersMap.keys();
        while (e.hasMoreElements())
        {
            String handlersPath = (String) e.nextElement();
            Code.debug("Check configured handlers for path ",handlersPath);
            HttpHandler[] httpHandlers = null;
            try
            {
                httpHandlers =
                    (HttpHandler[]) httpHandlersMap.get(handlersPath);
            }
            catch (ClassCastException cce)
            {
                Code.fail("httpHandlersMap does not map to HttpHandlers[] for "+
                          handlersPath,
                          cce);
            }

            // For all handlers
            for (int h=httpHandlers.length;h-->0;)
            {
                HttpHandler handler = httpHandlers[h];
                if (handler==null)
                    continue;
                
                if (handlerSet.put(handler,handler)==null)
                {
                    // First time this handler has been seen so ...
                    // set the server
                    try{
                        httpHandlers[h].setServer(this);
                    }
                    catch (java.io.IOException ioe){
                        Code.fail("HttpHander "+httpHandlers[h]+
                                  " could not setServer",ioe);
                        throw ioe;
                    }

                    // Setup servletHandlerMap
                    Enumeration s = handler.servletNames();
                    while (s!=null && s.hasMoreElements())
                        servletHandlerMap.put(s.nextElement(),handler);
                }
            }
            
        }

        Code.assert(httpHandlersMap.get("/")!=null,
                    "No mapping for / in httpHandlersMap");
        
        Code.assert(exceptionHandlersMap.get("/")!=null,
                    "No mapping for / in exceptionHandlersMap");

        Properties p = config.getProperties();
        
        
        int minThreads =
            Integer.parseInt(p.getProperty(HttpConfiguration
                                           .MinListenerThreads,"0"));
        int maxThreads =
            Integer.parseInt(p.getProperty(HttpConfiguration
                                           .MaxListenerThreads,"0"));
        int maxThreadIdle =
            Integer.parseInt(p.getProperty(HttpConfiguration
                                           .MaxListenerThreadIdleMs,"0"));

        String keepAlive10 = p.getProperty(HttpConfiguration
                                           .HTTP1_0_KeepAlive,"True");
        if (keepAlive10.length()>0)
        {
            char c = keepAlive10.charAt(0);
            http1_0_KeepAlive = c=='t'||c=='T'||c=='0';
        }
        
        InetAddrPort[] addresses = config.addresses();
        Class[] classes = config.listenerClasses();
        listeners = new HttpListener[addresses.length];
        for (int a=addresses.length; a-->0; )
        {
            try{
                Constructor c = classes[a].getConstructor(HttpListener.ConstructArgs);
                Object[] args = {
                    addresses[a],
                    this,
                    new Integer(minThreads),
                    new Integer(maxThreads),
                    new Integer(maxThreadIdle),
                };
                listeners[a] = (HttpListener) c.newInstance(args);
                listeners[a].start();
            }
            catch (java.io.IOException ioe){
                Code.warning("HttpServer couldn't listen on "+addresses[a],
                             ioe);
                throw ioe;
            }
        }
        
        int maxSessionIdle = 
            Integer.parseInt(p.getProperty(HttpConfiguration.SessionMaxInactiveInterval,"0"));
        if (maxSessionIdle > 0)
            HttpRequest.setSessionMaxInactiveInterval(maxSessionIdle);
        
        resourceBase=p.getProperty(HttpConfiguration.ResourceBase);
        if (resourceBase!=null && resourceBase.trim().length()==0)
            resourceBase=null;

        __servers.addElement(this);

    }

    /* -------------------------------------------------------------------- */
    /** Get the Configuration
     */
    public HttpConfiguration configuration()
    {
        return config;
    }
        
    /* ------------------------------------------------------------------- */
    /** Close the HttpServer and all its listeners.
     * @deprecated Use stop
     */
    public void close()
    {
        stop();
    }
    
    /* ------------------------------------------------------------------- */
    /** Stop the HttpServer.
     * Configuration is cleared and the server needs to be reconfigured
     * to be restarted. This needs to be improved.
     */
    public synchronized void stop()
    {
        __servers.removeElement(this);
        servletHandlerMap.clear();

        if (listeners!=null)
        {
            for (int c=listeners.length;c-->0;)
                listeners[c].stop();
            listeners=null;
        }

        // Destroy handlers
        if (httpHandlersMap!=null)
        {
            Enumeration e = httpHandlersMap.elements();
            while (e.hasMoreElements())
            {
                HttpHandler[] stack = (HttpHandler[])e.nextElement();
                for (int i=0;i<stack.length;i++)
                    if (stack[i]!=null)
                        stack[i].destroy();
            }
        }
            
        httpHandlersMap=null;
        exceptionHandlersMap=null;

        config=null;
    }

    /* ------------------------------------------------------------------- */
    /** Stop all instances of HttpServer.
     */
    public static void stopAll()
    {
        HttpServer[] servers=null;
        synchronized(__servers)
        {
            servers=new HttpServer[__servers.size()];
            __servers.copyInto(servers);
        }

        for(int i=0;i<servers.length;i++)
            servers[i].stop();
    }
    
    
    /* ------------------------------------------------------------------- */
    /** join the HttpServer and all its listeners.
     */
    public synchronized void join()
         throws InterruptedException
    {
        if (listeners!=null)
        {
            for (int c=listeners.length;c-->0;)
                listeners[c].join();
        }
    }

    /* ------------------------------------------------------------------- */
    /** Handle a HTTP request.
     * Called by the HttpListener that received the request
     */
    void handle(HttpRequest request, HttpResponse response)
    {
        try {
            
            if (request.getProtocol().equals(HttpHeader.HTTP_1_1))
            {
                // Set response version.
                response.setVersion(HttpHeader.HTTP_1_1);

                // insist on Host header
                if (request.getHeader(HttpHeader.Host)==null)
                {
                    response.sendError(HttpResponse.SC_BAD_REQUEST);
                    return;
                }
            }

            // Give request to handlers
            String resourcePath = request.getResourcePath();
            Exception exception=null;
            boolean handled=false;
            int h = 0;
            try{
                // Select request handler stack by path
                HttpHandler[] httpHandlers = (HttpHandler[])
                    httpHandlersMap.match(resourcePath);

                // Try all handlers in the stack
                while (httpHandlers!=null &&
                       !handled && h<httpHandlers.length)
                {
                    HttpHandler handler=httpHandlers[h++];
                    if (handler==null)
                        continue;
                   
                    if (Code.verbose())
                        Code.debug("Handler: ",handler);
                    handler.handle(request,response);
                    handled=response.requestHandled();
                    if (Code.verbose())
                    {
                        if (handled)
                            Code.debug("Request was handled by ",handler);
                        else
                            Code.debug("Request NOT handled by ",handler);
                    }
                }
                if (handled)
                    Code.debug("Handled by ",httpHandlers[h-1]);
            }
            catch (HeadException e)
            {
                handled=true;
                Code.ignore(e);
            }
            catch (Exception e1)
            {
                exception=e1;
                Code.debug("Exception in HttpHandler ",
			   new Integer(h),
			   exception);

                // Select exception handler stack by path
                ExceptionHandler[] exceptionHandlers = (ExceptionHandler[])
                    exceptionHandlersMap.match(resourcePath);
                
                // try all handlers in the stack
                int e=0;
                while(exceptionHandlers!=null &&
                      !handled && e<exceptionHandlers.length)
                {
                    Code.debug("Try ExceptionHandler "+e);
                    try {
                        exceptionHandlers[e++].handle(request,response,exception);
                        handled=response.headersWritten();
                    }
                    catch (IOException e2){
                        Code.debug("IO problem");
                        throw e2;
                    }   
                    catch (Exception e2){
                        exception=e2;
                        Code.debug("HttpHandler "+h,e2);
                    }
                }
            }

            if (handled)
            {
                response.flush();
            }
            else
            {
                if (exception!=null)
                    Code.warning("request exception not handled: "+request,
                                 exception);
                else
                {
                    Code.warning("request not handled: "+request);
                    response.setStatus(response.SC_NOT_FOUND);
                }
            }
        }
        catch (IOException e){
            Code.debug("request aborted: "+request,e);
        }
    }
    

    
    /* ---------------------------------------------------------------- */
    /* ServletContext Methods ----------------------------------------- */
    /* ---------------------------------------------------------------- */

    /* ---------------------------------------------------------------- */
    /**
     * Returns the servlet for the specified name.
     * @param name the name of the servlet
     * @return the Servlet, or null if not found
     * @exception ServletException if the servlet could not be initialized
     * @deprecated
     */
    public Servlet getServlet(String name)
    {
        return null;
    }

    /* ---------------------------------------------------------------- */
    /**
     * Enumerates the servlets in this context (server). Only servlets
     * that are accessible will be returned. The enumeration always
     * includes the servlet itself.
     * @deprecated Use getServletNames & getServlet
     */
    public Enumeration getServlets()
    {
        return null;
    }
    
    /* ---------------------------------------------------------------- */
    /**
     * @deprecated
     */
    public Enumeration getServletNames()
    {
        return null;
    }

    /* ---------------------------------------------------------------- */
    public ServletContext getContext(String url)
    {
        return this;
    }

    /* ---------------------------------------------------------------- */
    public int getMajorVersion()
    {
        return 2;
    }
    
    /* ---------------------------------------------------------------- */
    public int getMinorVersion()
    {
        return 1;
    }
    
    /* ------------------------------------------------------------ */
    /** Get a resource.
     * This is implemented with a standard URL instance that will make
     * a socket connection back to the server. This is less than
     * efficient and a better implementation is provided for
     * getResourceAsStream.
     * @param path URL path of resource
     * @return null
     * @exception MalformedURLException 
     */
    public URL getResource(String path)
        throws MalformedURLException
    {
        if (resourceBase==null)
        {
            String host=null;
            if (listeners[0]!=null &&
                listeners[0].getAddress()!=null &&
                listeners[0].getAddress().getInetAddress()!=null)
                host=listeners[0].getAddress().getInetAddress().getHostAddress();
            if (host==null)
                host="127.0.0.1";
            // Loop request back to this server
            return new URL("http",host,
                           listeners[0].getPort(),
                           path);
        }
        else
            return new URL(resourceBase+path);
    }

    /* ------------------------------------------------------------ */
    /** Get a resource as a Stream.
     * Creates a simulated request to the local server and returns
     * the content of the simulated response.
     * @see getResource(String path)
     * @param path URL path of resource
     * @return InputStream
     */
    public InputStream getResourceAsStream(String path)
    {
        try {
            if (resourceBase==null)
            {
                // Get request from this context
                HttpRequest request =
                    new HttpRequest(this,HttpRequest.GET,path);
                return request.handleRequestLocally();
            }
            else
            {
                URL url = getResource(path);
                return url.openStream();
            }
        }
        catch (Exception e)
        {
            Code.warning(e);
        }
        return null;
    }
    
    /* ------------------------------------------------------------ */
    /** Get a RequestDispatcher.
     * @param path URL path of resource 
     * @return null
     */
    public RequestDispatcher getRequestDispatcher(String path)
    {
        return new HttpRequestDispatcher(this,path);
    }
    
    /* ---------------------------------------------------------------- */
    /**
     * Writes a message to the servlet log file.
     * @param message the message to be written
     */
    public void log(String message)
    {
        config.log(message);
    }
    
    /* ---------------------------------------------------------------- */
    /**
     * Writes a message to the servlet log file.
     * @param message the message to be written
     * @param th Throwable 
     */
    public void log(String message, Throwable th)
    {
        Code.warning(message,th);
    }
    
    /* ---------------------------------------------------------------- */
    /**
     * Writes an exception & message to the servlet log file.
     * @param message the message to be written
     * @deprecated
     */
    public void log(Exception e, String message)
    {
        Code.warning(message,e);
    }

    /* ---------------------------------------------------------------- */
    /**
     * Applies alias rules to the specified virtual path and returns the
     * corresponding real path. Returns null if the translation could not
     * be performed.
     * @param path the real path to be translated
     */
    public String getRealPath(String path)
    {
        Code.debug("Translate: ",path);
        String realPath = path;
        // Select request handler stack by path
        HttpHandler[] httpHandlers =
            (HttpHandler[])httpHandlersMap.match(realPath);
        if (httpHandlers != null)
        {
            for (int i = 0; i < httpHandlers.length; i++)
            {   
                if (httpHandlers[i] != null)
                {
                    realPath = httpHandlers[i].translate(realPath);
                    Code.debug("translate with ",httpHandlers[i],
                               " to ",realPath);
                }
            }
        }
        
        return realPath.replace('/', File.separatorChar);
    }
    

    /* ---------------------------------------------------------------- */
    /**
     * Returns the mime type of the specified file, or null if not known.
     * @param file file name whose mime type is required
     */
    public String getMimeType(String file)
    {
        return config.getMimeType(file);
    }


    /* ---------------------------------------------------------------- */
    /**
     * Returns the name and version of the Web server under which the
     * servlet is running. Same as the CGI variable SERVER_SOFTWARE.
     */
    public String getServerInfo()
    {
        return Version.__jetty;
    }

    /* ---------------------------------------------------------------- */
    /**
     * Returns an attribute of the server given the specified key name.
     * This allows access to additional information about the server not
     * already provided by the other methods in this interface. Attribute
     * names should follow the same convention as package names, and those
     * beginning with 'com.sun.*' are reserved for use by Sun Microsystems.
     *
     * This implementation maps the attribute requests to properties of
     * HttpConfiguration.  The Jetty configuration does not adhere to
     * the naming conventions described above.
     * @param name the attribute key name
     * @return the value of the attribute, or null if not defined
     */
    public Object getAttribute(String name)
    {
        return config.getProperties().get(name);
    }
    
    /* ---------------------------------------------------------------- */
    /** .
     * This implementation maps the attribute requests to properties of
     * HttpConfiguration.  The Jetty configuration does not adhere to
     * the naming conventions described above.
     */
    public Enumeration getAttributeNames()
    {
        return config.getProperties().keys();
    }
    
    /* ---------------------------------------------------------------- */
    /** .
     * This implementation maps the attribute requests to properties of
     * HttpConfiguration.  The Jetty configuration does not adhere to
     * the naming conventions described above.
     */
    public void setAttribute(String name, Object value)
    {
        config.getProperties().put(name,value);
    }
    
    /* ---------------------------------------------------------------- */
    /** .
     * This implementation maps the attribute requests to properties of
     * HttpConfiguration.  The Jetty configuration does not adhere to
     * the naming conventions described above.
     */
    public void removeAttribute(String name)
    {
        config.getProperties().remove(name);
    }
    
    /* -------------------------------------------------------------------- */
    /** Main
     */
    public static void main(String args[])
    {
        String configName = null;
        if (args.length!=1)
        {
            System.err.println("Usage - java com.mortbay.HTTP.HttpServer [configClassName]");
            configName="com.mortbay.Jetty.Demo";
            System.err.println("Using "+configName);
        }
        else
            configName=args[0];
        
        Code.debug("Running HttpServer with "+configName);
        
        try{        
            HttpConfiguration config = (HttpConfiguration)
                Class.forName(configName).newInstance();
            HttpServer httpServer = new HttpServer(config);
            httpServer.join();
        }
        catch(Exception e){
            Code.warning("Demo Failed",e);
        }
    }   
}










