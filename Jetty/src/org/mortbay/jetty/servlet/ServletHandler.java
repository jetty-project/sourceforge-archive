// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.jetty.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionListener;
import org.mortbay.http.HandlerContext;
import org.mortbay.http.HttpConnection;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpMessage;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.HttpServer;
import org.mortbay.http.PathMap;
import org.mortbay.http.UserPrincipal;
import org.mortbay.http.UserRealm;
import org.mortbay.http.Version;
import org.mortbay.http.handler.NullHandler;
import org.mortbay.http.handler.SecurityHandler;
import org.mortbay.util.Code;
import org.mortbay.util.Frame;
import org.mortbay.util.LifeCycle;
import org.mortbay.util.Log;
import org.mortbay.util.LogSink;
import org.mortbay.util.MultiException;
import org.mortbay.util.Resource;
import org.mortbay.util.URI;



/* --------------------------------------------------------------------- */
/** Servlet HttpHandler.
 * This handler maps requests to servlets that implement the
 * javax.servlet.http.HttpServlet API.
 *
 * @version $Id$
 * @author Greg Wilkins
 */
public class ServletHandler
    extends NullHandler
    implements SecurityHandler.FormAuthenticator
{
    /* ------------------------------------------------------------ */
    private static final boolean __Slosh2Slash=File.separatorChar=='\\';

    /* ------------------------------------------------------------ */
    public final static String __SERVLET_REQUEST="org.mortbay.jetty.Request";
    public final static String __SERVLET_HOLDER="org.mortbay.jetty.Holder";
    public final static String __J_URI="org.mortbay.jetty.URI";
    public final static String __J_AUTHENTICATED="org.mortbay.jetty.Auth";
    
    /* ------------------------------------------------------------ */
    private PathMap _servletMap=new PathMap();
    private Map _nameMap=new HashMap();
    private HandlerContext _handlerContext;
    private Context _context;
    private ClassLoader _loader;
    private String _dynamicServletPathSpec;
    private Map _dynamicInitParams ;
    private boolean _serveDynamicSystemServlets=false;
    private boolean _usingCookies=true;
    private LogSink _logSink;
    private SessionManager _sessionManager;
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public ServletHandler()
    {
        _context=new Context();
        _sessionManager = new HashSessionManager(this);
    }
    
    /* ------------------------------------------------------------ */
    public ServletContext getServletContext() { return _context; }

    /* ------------------------------------------------------------ */
    public PathMap getServletMap() { return _servletMap; }
    
    /* ------------------------------------------------------------ */
    public boolean isAutoReload() { return false; }
    
    /* ------------------------------------------------------------ */
    public String getDynamicServletPathSpec() { return _dynamicServletPathSpec; }

    /* ------------------------------------------------------------ */
    public Map getDynamicInitParams() { return _dynamicInitParams; }

    /* ------------------------------------------------------------ */
    public boolean isUsingCookies() { return _usingCookies; }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return True if dynamic servlets can be on the non-context classpath
     */
    public boolean getServeDynamicSystemServlets()
    { return _serveDynamicSystemServlets; }
    
    /* ------------------------------------------------------------ */
    /** Set the dynamic servlet path.
     * If set, the ServletHandler will dynamically load servlet
     * classes that have their class names as the path info after the
     * set path sepcification.
     * @param dynamicServletPathSpec The path within the context at which
     * dynamic servlets are launched. eg /servlet/*
     */
    public void setDynamicServletPathSpec(String dynamicServletPathSpec)
    {
        if (dynamicServletPathSpec!=null &&
            !dynamicServletPathSpec.equals("/") &&
            !dynamicServletPathSpec.endsWith("/*"))
            throw new IllegalArgumentException("dynamicServletPathSpec must end with /*");
            
        _dynamicServletPathSpec=dynamicServletPathSpec;
    }
    
    /* ------------------------------------------------------------ */
    /** Set dynamic servlet initial parameters.
     * @param initParams Map passed as initParams to newly created
     * dynamic servlets.
     */
    public void setDynamicInitParams(Map initParams)
    {
        _dynamicInitParams = initParams;
    }

    /* ------------------------------------------------------------ */
    /** Set serving dynamic system servlets.
     * This is a security option so that you can control what servlets
     * can be loaded with dynamic discovery.
     * @param b If set to false, the dynamic servlets must be loaded
     * by the context classloader.  
     */
    public void setServeDynamicSystemServlets(boolean b)
    {
        _serveDynamicSystemServlets=b;
    }
    
    /* ------------------------------------------------------------ */
    public ClassLoader getClassLoader()
    {
        return _loader;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @param sc If true, cookies are used for sessions
     */
    public void setUsingCookies(boolean uc)
    {
        _usingCookies=uc;
    }

    /* ------------------------------------------------------------ */
    public void setLogSink(LogSink logSink)
    {
        _logSink=logSink;
    }
    
    /* ------------------------------------------------------------ */
    public LogSink getLogSink()
    {
        return _logSink;
    }
    
    /* ------------------------------------------------------------ */
    public synchronized void addEventListener(EventListener listener)
        throws IllegalArgumentException
    {
        if ((listener instanceof HttpSessionActivationListener) ||
            (listener instanceof HttpSessionAttributeListener) ||
            (listener instanceof HttpSessionBindingListener) ||
            (listener instanceof HttpSessionListener))
            _sessionManager.addEventListener(listener);
        else 
            throw new IllegalArgumentException(listener.toString());
    }
    
    /* ------------------------------------------------------------ */
    public synchronized void removeEventListener(EventListener listener)
    {
        if ((listener instanceof HttpSessionActivationListener) ||
            (listener instanceof HttpSessionAttributeListener) ||
            (listener instanceof HttpSessionBindingListener) ||
            (listener instanceof HttpSessionListener))
            _sessionManager.removeEventListener(listener);
    }
    
    /* ------------------------------------------------------------ */
    public synchronized boolean isStarted()
    {
        return super.isStarted();
    }
    
    /* ----------------------------------------------------------------- */
    public synchronized void start()
        throws Exception
    {
        _handlerContext=getHandlerContext();
        
        _sessionManager.start();
        
        // Initialize classloader
        _loader=getHandlerContext().getClassLoader();

        // start the handler - protected by synchronization until
        // end of the call.
        super.start();

        MultiException mx = new MultiException();
        
        // Sort and Initialize servlets
        ServletHolder holders [] = (ServletHolder [])
            (new HashSet(_servletMap.values ())).toArray(new ServletHolder [0]);
        java.util.Arrays.sort (holders);        
        for (int i=0; i<holders.length; i++)
        {
            ServletHolder holder = holders [i];
            
            try{holder.start();}
            catch(Exception e)
            {
                Code.debug(e);
                mx.add(e);
            }
        } 
        mx.ifExceptionThrow();       
    }   
    
    /* ----------------------------------------------------------------- */
    public synchronized void stop()
        throws InterruptedException
    {
        // Stop servlets
        Iterator i = _servletMap.values().iterator();
        while (i.hasNext())
        {
            ServletHolder holder = (ServletHolder)i.next();
            holder.stop();
        }      
        _sessionManager.stop();
        _loader=null;
        _context=null;
        _handlerContext=null;
        super.stop();
    }
    
    /* ----------------------------------------------------------------- */
    public synchronized void destroy()
    {
        // Destroy servlets
        Iterator i = _servletMap.values().iterator();
        while (i.hasNext())
        {
            ServletHolder holder = (ServletHolder)i.next();
            holder.destroy();
        }

        _sessionManager.destroy();
        _sessionManager=null;
        _loader=null;
        _context=null;
        _handlerContext=null;
        super.destroy();
    }
    
    
    /* ------------------------------------------------------------ */
    public ServletHolder addServlet(String name,
                                    String pathSpec,
                                    String servletClass,
                                    String path)
    {
        try
        {
            ServletHolder holder = new ServletHolder(this,name,servletClass,null);
            addHolder(pathSpec,holder);
            return holder;
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }
    }
    
    /* ------------------------------------------------------------ */
    public ServletHolder addServlet(String name,
                                    String pathSpec,
                                    String servletClass)
    {
        return addServlet(name,pathSpec,servletClass,null);
    }

    /* ------------------------------------------------------------ */
    public ServletHolder addServlet(String pathSpec,
                                    String servletClass)
    {
        return addServlet(servletClass,pathSpec,servletClass,null);
    }

    /* ------------------------------------------------------------ */
    /** Get servlet by name.
     * @param name 
     * @return 
     */
    public ServletHolder getServletHolder(String name)
    {
        return (ServletHolder)_nameMap.get(name);
    }
    
    /* ------------------------------------------------------------ */
    public void addHolder(String pathSpec, ServletHolder holder)
    {
        try
        {
            if (isStarted() && !holder.isStarted())
                holder.start();
            _servletMap.put(pathSpec,holder);
            _nameMap.put(holder.getName(),holder);
        }
        catch(Exception e)
        {
            Code.warning(e);
        }
    }

    /* ------------------------------------------------------------ */
    /** Get or create a ServletRequest.
     * Create a new or retrieve a previously created servlet request
     * that wraps the http request. Note that the ServletResponse is
     * also created and can be retrieved from the ServletRequest.
     * @param httpRequest 
     * @return ServletRequest wrapping the passed HttpRequest.
     */
    public ServletRequest getServletRequest(HttpRequest httpRequest,
                                            HttpResponse httpResponse)
    {
        // Look for a previously built servlet request.
        ServletRequest servletRequest = (ServletRequest)
            httpRequest.getAttribute(ServletHandler.__SERVLET_REQUEST);
        
        if (servletRequest==null)
        {
            servletRequest  = new ServletRequest(this,httpRequest);
            httpRequest.setAttribute(ServletHandler.__SERVLET_REQUEST,servletRequest);
            ServletResponse servletResponse =
                new ServletResponse(servletRequest,httpResponse);
        }
        return servletRequest;
    }
    

    /* ------------------------------------------------------------ */
    HttpSession getHttpSession(String id)
    {
        return _sessionManager.getHttpSession(id);
    }
    
    /* ------------------------------------------------------------ */
    HttpSession newHttpSession()
    {
        return _sessionManager.newHttpSession();
    }

    /* ------------------------------------------------------------ */
    void setSessionTimeout(int timeoutMinutes)
    {
        _sessionManager.setSessionTimeout(timeoutMinutes);
    }

    /* ------------------------------------------------------------ */
    /** Strip session from path.
     * Strip the session ID from a request path.  The session is
     * accessed in this process.
     * @param pathInContext The path which may contain the session ID
     * @param request The request made on the path.
     * @return The path in the context, stripped of any session ID.
     */
    public void setSessionId(String pathParams,
                             ServletRequest request)
    {
        request.setSessionId(pathParams);
        HttpSession session=request.getSession(false);
        if (session!=null)
            ((SessionManager.Session)session).access();
    }
    

    /* ----------------------------------------------------------------- */
    /** Handle request.
     * @param contextPath 
     * @param pathInContext 
     * @param httpRequest 
     * @param httpResponse 
     * @exception IOException 
     */
    public void handle(String pathInContext,
                       String pathParams,
                       HttpRequest httpRequest,
                       HttpResponse httpResponse)
         throws IOException
    {
        try
        {
            ServletRequest request=null;
            ServletResponse response=null;
            
            // handle
            Code.debug("Looking for servlet at ",pathInContext);
            
            ServletHolder holder =(ServletHolder)
                httpRequest.getAttribute(ServletHandler.__SERVLET_HOLDER);
            if (holder!=null)
            {
                request = getServletRequest(httpRequest,httpResponse);
                response = request.getServletResponse();
                setSessionId(pathParams,request);
            }
            else
            {
                Map.Entry entry=getHolderEntry(pathInContext);
                if (entry!=null)
                {
                    request = getServletRequest(httpRequest,httpResponse);
                    response = request.getServletResponse();
                    setSessionId(pathParams,request);
                    String servletPathSpec=(String)entry.getKey();
                    holder = (ServletHolder)entry.getValue();
                    
                    Code.debug("Pass request to servlet at ",entry);
                    request.setPaths(PathMap.pathMatch(servletPathSpec,
                                                       pathInContext),
                                     PathMap.pathInfo(servletPathSpec,
                                                      pathInContext));
                }
            }
            
            if (holder!=null)
            {
                // service request
                holder.handle(request,response);
                response.setOutputState(0);
                Code.debug("Handled by ",holder);
                if (!httpResponse.isCommitted())
                    httpResponse.commit();  
            }
            
        }
        catch(Exception e)
        {
            Code.debug(e);
            
            Throwable th=e;
            if (e instanceof ServletException)
            {
                if (((ServletException)e).getRootCause()!=null)
                {
                    Code.debug("Extracting root cause from ",e);
                    th=((ServletException)e).getRootCause();
                }
            }
            
            if (th instanceof HttpException)
                throw (HttpException)th;
            if (th instanceof IOException)
                throw (IOException)th;
            
            Code.warning("Servlet Exception for "+httpRequest.getURI(),th);
            Code.debug(httpRequest);
            
            httpResponse.getHttpConnection().forceClose();
            if (!httpResponse.isCommitted())
                httpResponse.sendError(503,th);
            else
                Code.debug("Response already committed for handling ",th);
        }
        catch(Error e)
        {
            Code.warning("Servlet Error for "+httpRequest.getURI(),e);
            Code.debug(httpRequest);
            httpResponse.getHttpConnection().forceClose();
            if (!httpResponse.isCommitted())
                httpResponse.sendError(503,e);
            else
                Code.debug("Response already committed for handling ",e);
        }
    }

    
    /* ------------------------------------------------------------ */
    /** ServletHolder matching path.
     * In a separate method so that dynamic servlet loading can be
     * implemented by derived handlers.
     * @param pathInContext Path within context.
     * @return PathMap Entries pathspec to ServletHolder
     */
    public Map.Entry getHolderEntry(String pathInContext)
    {
        Map.Entry entry =_servletMap.getMatch(pathInContext);

        String servletClass=null;
        if (_dynamicServletPathSpec!=null)
            servletClass=PathMap.pathInfo(_dynamicServletPathSpec,pathInContext);
        
        // Do we have a match and no chance of a new
        // dynamci servlet
        if (entry!=null && servletClass==null)
            return entry;

        // If it could be a dynamic servlet
        synchronized(this)
        {
            // sychronize and try again.
            entry =_servletMap.getMatch(pathInContext);
            if (entry!=null && servletClass==null)
                return entry;
            
            if (servletClass!=null && servletClass.length()>2 &&
                (entry==null||!PathMap.match(_dynamicServletPathSpec,(String)entry.getKey())))
            {
                try
                {
                    // OK lets look for a dynamic servlet.
                    String path=pathInContext;
                    Code.debug("looking for ",servletClass," in ",
                               getHandlerContext().getClassPath());
                
                    // remove prefix
                    servletClass=servletClass.substring(1);
                
                    // remove suffix
                    int slash=servletClass.indexOf('/');
                    if (slash>=0)
                        servletClass=servletClass.substring(0,slash);            
                    if (servletClass.endsWith(".class"))
                        servletClass=servletClass.substring(0,servletClass.length()-6);
                
                    // work out the actual servlet path
                    if ("/".equals(_dynamicServletPathSpec))
                        path='/'+servletClass;
                    else
                        path=PathMap.pathMatch(_dynamicServletPathSpec,path)+'/'+servletClass;
                
                    Code.debug("Dynamic path=",path);
                
                    // make a holder
                    ServletHolder holder=new ServletHolder(this,servletClass,servletClass);
                    
                    // Set params
                    Map params=getDynamicInitParams();
                    if (params!=null)
                        holder.putAll(params);
                    holder.start();
                    Object servlet=holder.getServlet();

                    // Check that the class was intended as a dynamic
                    // servlet
                    if (!_serveDynamicSystemServlets &&
                        _loader!=null &&
                        _loader!=this.getClass().getClassLoader())
                    {
                        // This context has a specific class loader.
                        if (servlet.getClass().getClassLoader()!=_loader)
                        {
                            holder.destroy();
                            String msg = "Dynamic servlet "+
                                servletClass+
                                " is not loaded from context: "+
                                getHandlerContext().getContextPath();
                        
                            Code.warning(msg);
                            throw new UnavailableException(msg);
                        }
                    }
                
                    Log.event("Dynamic load '"+servletClass+"' at "+path);
                    addHolder(path+"/*",holder);
                    addHolder(path+".class/*",holder);
                    
                    entry=_servletMap.getMatch(pathInContext);
                }
                catch(Exception e)
                {
                    Code.ignore(e);
                }
            }
        }
        
        return entry;
    }
    


    /* ------------------------------------------------------------ */
    void mapHolder(String name,ServletHolder holder, String oldName)
    {
        synchronized(_nameMap)
        {
            if (oldName!=null)
                _nameMap.remove(oldName);
            _nameMap.put(name,holder);
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Perform form authentication.
     * Called from SecurityHandler.
     * @return true if authenticated.
     */
    public boolean formAuthenticated(SecurityHandler shandler,
                                     String pathInContext,
                                     String pathParams,
                                     HttpRequest httpRequest,
                                     HttpResponse httpResponse)
        throws IOException
    {
        ServletRequest request = getServletRequest(httpRequest,httpResponse);
        ServletResponse response = request.getServletResponse();

        // Handle paths
        request.setSessionId(pathParams); 
        String uri = pathInContext;
        
        // Setup session 
        HttpSession session=request.getSession(true);             
        
        // Handle a request for authentication.
        if ( uri.substring(uri.lastIndexOf("/")+1).startsWith(__J_SECURITY_CHECK) )
        {
            // Check the session object for login info. 
            String username = request.getParameter(__J_USERNAME);
            String password = request.getParameter(__J_PASSWORD);
            
            UserPrincipal user =
                shandler.getUserRealm().getUser(username);
            if (user!=null && user.authenticate(password,httpRequest))
            {
                Code.debug("Form authentication OK for ",username);
                httpRequest.setAttribute(HttpRequest.__AuthType,"FORM");
                httpRequest.setAttribute(HttpRequest.__AuthUser,username);
                httpRequest.setAttribute(UserPrincipal.__ATTR,user);
                session.setAttribute(__J_AUTHENTICATED,username);
                String nuri=(String)session.getAttribute(__J_URI);
                if (nuri==null)
                    response.sendRedirect(URI.addPaths(request.getContextPath(),
                                                       shandler.getErrorPage()));
                else
                    response.sendRedirect(nuri);
            }
            else
            {
                Code.debug("Form authentication FAILED for ",username);
                response.sendRedirect(URI.addPaths(request.getContextPath(),
                                                   shandler.getErrorPage()));
            }
            
            // Security check is always false, only true after final redirection.
            return false;
        }

        // Check if the session is already authenticated.
        if (session.getAttribute(__J_AUTHENTICATED) != null)
        {
            String username=(String)session.getAttribute(__J_AUTHENTICATED);
            UserPrincipal user =
                shandler.getUserRealm().getUser(username);
            Code.debug("FORM Authenticated for ",username);
            httpRequest.setAttribute(HttpRequest.__AuthType,"FORM");
            httpRequest.setAttribute(HttpRequest.__AuthUser,username);
            httpRequest.setAttribute(UserPrincipal.__ATTR,user);
            return true;
        }
        
        // redirect to login page
        if (httpRequest.getQuery()!=null)
            uri+="?"+httpRequest.getQuery();
        session.setAttribute(__J_URI, URI.addPaths(request.getContextPath(),uri));
        response.sendRedirect(URI.addPaths(request.getContextPath(),
                                           shandler.getLoginPage()));
        return false;
    }


    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class Context implements ServletContext
    {
        /* ------------------------------------------------------------ */
        public ServletContext getContext(String uri)
        {        
            ServletHandler handler= (ServletHandler)
                _handlerContext.getHttpServer()
                .findHandler(org.mortbay.jetty.servlet.ServletHandler.class,
                             uri,
                             _handlerContext.getHosts());
            if (handler!=null)
                return handler.getServletContext();
            return null;
        }

        /* ------------------------------------------------------------ */
        public int getMajorVersion()
        {
            return 2;
        }

        /* ------------------------------------------------------------ */
        public int getMinorVersion()
        {
            return 3;
        }

        /* ------------------------------------------------------------ */
        public String getMimeType(String file)
        {
            return _handlerContext.getMimeByExtension(file);
        }

        /* ------------------------------------------------------------ */
        public Set getResourcePaths(String uriInContext)
        {
            Resource baseResource=_handlerContext.getBaseResource();
            uriInContext=Resource.canonicalPath(uriInContext);
            if (baseResource==null || uriInContext==null)
                return Collections.EMPTY_SET;

            try
            {
                Resource resource = baseResource.addPath(uriInContext);
                String[] contents=resource.list();
                if (contents==null || contents.length==0)
                    return Collections.EMPTY_SET;
                HashSet set = new HashSet(contents.length*2);
                for (int i=0;i<contents.length;i++)
                    set.add(URI.addPaths(uriInContext,contents[i]));
                return set;
            }
            catch(Exception e)
            {
                Code.ignore(e);
            }
        
            return Collections.EMPTY_SET;
        }
    

        /* ------------------------------------------------------------ */
        /** Get a Resource.
         * If no resource is found, resource aliases are tried.
         * @param uriInContext 
         * @return 
         * @exception MalformedURLException 
         */
        public URL getResource(String uriInContext)
            throws MalformedURLException
        {
            Resource baseResource=_handlerContext.getBaseResource();
            uriInContext=Resource.canonicalPath(uriInContext);
            if (baseResource==null || uriInContext==null)
                return null;
        
            try{
                Resource resource = baseResource.addPath(uriInContext);
                if (resource.exists())
                    return resource.getURL();

                String aliasedUri=_handlerContext.getResourceAlias(uriInContext);
                if (aliasedUri!=null)
                    return getResource(aliasedUri);
            }
            catch(IllegalArgumentException e)
            {
                Code.ignore(e);
            }
            catch(MalformedURLException e)
            {
                throw e;
            }
            catch(IOException e)
            {
                Code.warning(e);
            }
            return null;
        }

        /* ------------------------------------------------------------ */
        public InputStream getResourceAsStream(String uriInContext)
        {
            try
            {
                URL url = getResource(uriInContext);
                if (url!=null)
                    return url.openStream();
            }
            catch(MalformedURLException e) {Code.ignore(e);}
            catch(IOException e) {Code.ignore(e);}
            return null;
        }


        /* ------------------------------------------------------------ */
        public RequestDispatcher getRequestDispatcher(String uriInContext)
        {
        
            if (uriInContext == null || !uriInContext.startsWith("/"))
                return null;

            try
            {
                String pathInContext=uriInContext;
                String query=null;
                int q=0;
                if ((q=pathInContext.indexOf('?'))>0)
                {
                    pathInContext=uriInContext.substring(0,q);
                    query=uriInContext.substring(q+1);
                }

                return new Dispatcher(ServletHandler.this,pathInContext,query);
            }
            catch(Exception e)
            {
                Code.ignore(e);
                return null;
            }
        }

        /* ------------------------------------------------------------ */
        public RequestDispatcher getNamedDispatcher(String name)
        {
            if (name == null || name.length()==0)
                return null;

            try { return new Dispatcher(ServletHandler.this,name); }
            catch(Exception e) {Code.ignore(e);}
        
            return null;
        }
    
        /* ------------------------------------------------------------ */
        /**
         * @deprecated 
         */
        public Servlet getServlet(String name)
        {
            return null;
        }

        /* ------------------------------------------------------------ */
        /**
         * @deprecated 
         */
        public Enumeration getServlets()
        {
            return Collections.enumeration(Collections.EMPTY_LIST);
        }

        /* ------------------------------------------------------------ */
        /**
         * @deprecated 
         */
        public Enumeration getServletNames()
        {
            return Collections.enumeration(Collections.EMPTY_LIST);
        }


    
        /* ------------------------------------------------------------ */
        /** Servlet Log.
         * Log message to servlet log. Use either the system log or a
         * LogSinkset via the context attribute
         * org.mortbay.jetty.servlet.Context.LogSink
         * @param msg 
         */
        public void log(String msg)
        {
            if (_logSink!=null)
                _logSink.log(Log.EVENT,msg,new
                    Frame(2),System.currentTimeMillis());
            else
                Log.message(Log.EVENT,msg,new Frame(2));
        }

        /* ------------------------------------------------------------ */
        public void log(Exception e, String msg)
        {
            Code.warning(msg,e);
            log(msg+": "+e.toString());
        }

        /* ------------------------------------------------------------ */
        public void log(String msg, Throwable th)
        {
            Code.warning(msg,th);
            log(msg+": "+th.toString());
        }

        /* ------------------------------------------------------------ */
        public String getRealPath(String path)
        {
            if(Code.debug())
                Code.debug("getRealPath of ",path," in ",this);

            if (__Slosh2Slash)
                path=path.replace('\\','/');
        
            Resource baseResource=_handlerContext.getBaseResource();
            if (baseResource==null )
                return null;

            try{
                Resource resource = baseResource.addPath(path);
                File file = resource.getFile();

                return (file==null)
                    ?"null"
                    :(file.getAbsolutePath());
            }
            catch(IOException e)
            {
                Code.warning(e);
                return null;
            }
        }

        /* ------------------------------------------------------------ */
        public String getServerInfo()
        {
            return Version.__Version;
        }


        /* ------------------------------------------------------------ */
        /** Get context init parameter.
         * Delegated to HandlerContext.
         * @param param param name
         * @return param value or null
         */
        public String getInitParameter(String param)
        {
            return _handlerContext.getInitParameter(param);
        }

        /* ------------------------------------------------------------ */
        /** Get context init parameter names.
         * Delegated to HandlerContext.
         * @return Enumeration of names
         */
        public Enumeration getInitParameterNames()
        {
            return _handlerContext.getInitParameterNames();
        }

    
        /* ------------------------------------------------------------ */
        /** Get context attribute.
         * Delegated to HandlerContext.
         * @param name attribute name.
         * @return attribute
         */
        public Object getAttribute(String name)
        {
            if ("javax.servlet.context.tempdir".equals(name))
            {
                // Initialize temporary directory
                File tempDir=(File)_handlerContext
                    .getAttribute("javax.servlet.context.tempdir");
                if (tempDir==null)
                {
                    try{
                        tempDir=File.createTempFile("JettyContext",null);
                        if (tempDir.exists())
                            tempDir.delete();
                        tempDir.mkdir();
                        tempDir.deleteOnExit();
                        _handlerContext
                            .setAttribute("javax.servlet.context.tempdir",
                                          tempDir);
                    }
                    catch(Exception e)
                    {
                        Code.warning(e);
                    }
                }
                Code.debug("TempDir=",tempDir);
            }

            return _handlerContext.getAttribute(name);
        }

        /* ------------------------------------------------------------ */
        /** Get context attribute names.
         * Delegated to HandlerContext.
         */
        public Enumeration getAttributeNames()
        {
            return _handlerContext.getAttributeNames();
        }

        /* ------------------------------------------------------------ */
        /** Set context attribute names.
         * Delegated to HandlerContext.
         * @param name attribute name.
         * @param value attribute value
         */
        public void setAttribute(String name, Object value)
        {
            if (name.startsWith("org.mortbay.http"))
            {
                Code.warning("Servlet attempted update of "+name);
                return;
            }
            _handlerContext.setAttribute(name,value);
        }

        /* ------------------------------------------------------------ */
        /** Remove context attribute.
         * Delegated to HandlerContext.
         * @param name attribute name.
         */
        public void removeAttribute(String name)
        {
            if (name.startsWith("org.mortbay.http"))
            {
                Code.warning("Servlet attempted update of "+name);
                return;
            }
            _handlerContext.removeAttribute(name);
        }
    
        /* ------------------------------------------------------------ */
        public String getServletContextName()
        {
            if (_handlerContext instanceof WebApplicationContext)
                return ((WebApplicationContext)_handlerContext).getDisplayName();
            return null;
        }
    }


    
}
