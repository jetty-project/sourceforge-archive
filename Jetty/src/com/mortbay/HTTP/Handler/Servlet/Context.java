// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP.Handler.Servlet;

import com.mortbay.HTTP.HandlerContext;
import com.mortbay.HTTP.Version;
import com.mortbay.Util.Code;
import com.mortbay.Util.Frame;
import com.mortbay.Util.Log;
import com.mortbay.Util.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;


/* --------------------------------------------------------------------- */
/** 
 *
 * @version $Id$
 * @author Greg Wilkins
 */
public class Context implements ServletContext, HttpSessionContext
{
    /* ------------------------------------------------------------ */
    private ServletHandler _handler;
    ServletHandler getHandler(){return _handler;}

    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param handler 
     */
    Context(ServletHandler handler)
    {
        _handler=handler;
    }
    
    /* ------------------------------------------------------------ */
    public String getContextPath()
    {
        return _handler.getHandlerContext().getContextPath();
    }
    
    /* ------------------------------------------------------------ */
    /**
     * Implemented by delegation to getAttribute.
     */
    public String getInitParameter(String param)
    {
        Object p = getAttribute(param);
        if (p!=null)
            return p.toString();
        return null;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * Implemented by delegation to getAttributeNames.
     */
    public Enumeration getInitParameterNames()
    {
        return getAttributeNames();
    }
    
    
    /* ------------------------------------------------------------ */
    /**
     * Returns a <code>ServletContext</code> object that 
     * corresponds to a specified URL on the server.
     *
     * <p>This method allows servlets to gain
     * access to the context for various parts of the server, and as
     * needed obtain {@link RequestDispatcher} objects from the context.
     * The given path must be absolute (beginning with "/") and is 
     * interpreted based on the server's document root. 
     * 
     * <p>In a security conscious environment, the servlet container may
     * return <code>null</code> for a given URL.
     *       
     * @param uripath 	a <code>String</code> specifying the absolute URL of 
     *			a resource on the server
     *
     * @return		the <code>ServletContext</code> object that
     *			corresponds to the named URL
     *
     * @see 		RequestDispatcher
     *
     */
    public ServletContext getContext(String uri)
    {
        HandlerContext context=
            _handler.getHandlerContext();

        ServletHandler handler=context.getHttpServer()
            .findServletHandler(uri,context.getHosts());

        if (handler!=null)
            return handler.getContext();
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
        return 1;
    }

    /* ------------------------------------------------------------ */
    public String getMimeType(String file)
    {
        return _handler.getHandlerContext().getMimeByExtension(file);
    }
    
    /* ------------------------------------------------------------ */
    public URL getResource(String uri)
        throws MalformedURLException
    {
        Resource resourceBase=_handler.getHandlerContext().getResourceBase();
        if (resourceBase==null)
            return null;

        try{
            Resource resource = resourceBase.addPath(uri);
            return resource.getURL();
        }
        catch(IOException e)
        {
            Code.warning(e);
            return null;
        }
    }
    
    /* ------------------------------------------------------------ */
    public InputStream getResourceAsStream(String uri)
    {
        try
        {
            URL url = getResource(uri);
            if (url!=null)
                return url.openStream();
        }
        catch(MalformedURLException e)
        {
            Code.ignore(e);
        }
        catch(IOException e)
        {
            Code.ignore(e);
        }
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
            if ((q=pathInContext.indexOf("?"))>0)
            {
                pathInContext=uriInContext.substring(0,q);
                query=uriInContext.substring(q+1);
            }
            
            return new Dispatcher(this,pathInContext,query);
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

        try
        {
            return new Dispatcher(this,name);
        }
        catch(Exception e)
        {
            Code.ignore(e);
            return null;
        }
    }


    /* ------------------------------------------------------------ */
    public Servlet getServlet(String name)
    {
        Code.warning("No longer supported");
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public Enumeration getServlets()
    {
        Code.warning("No longer supported");
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public Enumeration getServletNames()
    {
        Code.warning("No longer supported");
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public void log(String msg)
    {
        Log.message(Log.EVENT,msg,new Frame(2));
    }
    
    /* ------------------------------------------------------------ */
    public void log(Exception e, String msg)
    {
        Code.warning(msg,e);
    }
    
    /* ------------------------------------------------------------ */
    public void log(String msg, Throwable th)
    {
        Code.warning(msg,th);
    }
    
    /* ------------------------------------------------------------ */
    public String getRealPath(String path)
    {
        if(Code.debug())
            Code.debug("getRealPath of ",path," in ",this);
        
        Resource resourceBase=_handler.getHandlerContext().getResourceBase();
        if (resourceBase==null )
            return null;
        
        try{
            Resource resource = resourceBase.addPath(path);
            File file = resource.getFile();
            if (file==null)
                return null;
            return file.getAbsolutePath();
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
    /** Get context attribute.
     * Delegated to HandlerContext.
     */
    public Object getAttribute(String name)
    {
        if ("javax.servlet.context.tempdir".equals(name))
        {
            // Initialize temporary directory
            File tempDir=(File)_handler.getHandlerContext()
                .getAttribute("javax.servlet.context.tempdir");
            if (tempDir==null)
            {
                try{
                    tempDir=File.createTempFile("JettyContext",null);
                    if (tempDir.exists())
                        tempDir.delete();
                    tempDir.mkdir();
                    tempDir.deleteOnExit();
                    _handler.getHandlerContext()
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
        
        return _handler.getHandlerContext().getAttribute(name);
    }
    
    /* ------------------------------------------------------------ */
    /** Get context attribute names.
     * Delegated to HandlerContext.
     */
    public Enumeration getAttributeNames()
    {
        return _handler.getHandlerContext().getAttributeNames();
    }
    
    /* ------------------------------------------------------------ */
    /** Set context attribute names.
     * Delegated to HandlerContext.
     */
    public void setAttribute(String name, Object value)
    {
        if (name!=null &&
            ( name.startsWith("com.mortbay.") ||
              name.startsWith("java.") ||
              name.startsWith("javax.")))
            Code.warning("Servlet attempted to set com.mortbay.* attribute: "+
                         name);
        else
            _handler.getHandlerContext().setAttribute(name,value);
    }
    
    /* ------------------------------------------------------------ */
    /** Remove context attribute.
     * Delegated to HandlerContext.
     */
    public void removeAttribute(String name)
    {
        if (name!=null &&
            ( name.startsWith("com.mortbay.") ||
              name.startsWith("java.") ||
              name.startsWith("javax.")))
            Code.warning("Servlet removing com.mortbay.* attribute: "+
                         name);
        else
            _handler.getHandlerContext().removeAttribute(name);
    }


    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public final static String __SessionUrlPrefix = ";jsessionid=";
    public final static String __SessionId  = "jessionid";
    public final static int __distantFuture = 60*60*24*7*52*20;
    private static long __nextSessionId = System.currentTimeMillis();
    
    // Setting of max inactive interval for new sessions
    // -1 means no timeout
    private int _defaultMaxIdleTime = -1;
    private SessionScavenger _scavenger = null;
    private Map _sessions = new HashMap();

    /* ------------------------------------------------------------ */
    /**
     * @deprecated
     */   
    public Enumeration getIds()
    {
        return Collections.enumeration(_sessions.keySet());
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @deprecated
     */   
    public HttpSession getSession(String id)
    {
        HttpSession s = (HttpSession)_sessions.get(id);
        return s;
    }
    
    /* ------------------------------------------------------------ */
    public HttpSession newSession()
    {
        HttpSession session = new Session();
        session.setMaxInactiveInterval(_defaultMaxIdleTime);
        _sessions.put(session.getId(),session);
        return session;
    }

    /* ------------------------------------------------------------ */
    public static void access(HttpSession session)
    {
        ((Session)session).accessed();
    }
    
    /* ------------------------------------------------------------ */
    public static boolean isValid(HttpSession session)
    {
        return !(((Session)session).invalid);
    }
    
    /* -------------------------------------------------------------- */
    /** Set the default session timeout.
     *  @param  default The timeout in minutes
     */
    public void setSessionTimeout(int timeoutMinutes)
    {   
        _defaultMaxIdleTime = timeoutMinutes*60;;
        
        // Start the session scavenger if we haven't already
        if (_scavenger == null)
            _scavenger = new SessionScavenger();
    }

    /* -------------------------------------------------------------- */
    /** Find sessions that have timed out and invalidate them. 
     *  This runs in the SessionScavenger thread.
     */
    private synchronized void scavenge()
    {
        // Set our priority high while we have the sessions locked
        int oldPriority = Thread.currentThread().getPriority();
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                
        long now = System.currentTimeMillis();
                
        // Since Hashtable enumeration is not safe over deletes,
        // we build a list of stale sessions, then go back and invalidate them
        ArrayList staleSessions = null;
                
        // For each session
        for (Iterator i = _sessions.values().iterator(); i.hasNext(); )
        {
            Session session = (Session)i.next();
            long idleTime = session.maxIdleTime;
            if (idleTime > 0 && session.accessed + idleTime < now) {
                // Found a stale session, add it to the list
                if (staleSessions == null)
                    staleSessions = new ArrayList(5);
                staleSessions.add(session);
            }
        }
                
        // Remove the stale sessions
        if (staleSessions != null)
        {
            for (int i = staleSessions.size() - 1; i >= 0; --i) {
                ((Session)staleSessions.get(i)).invalidate();
            }
        }
                
        Thread.currentThread().setPriority(oldPriority);
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    public String toString()
    {
        return "Servlet"+_handler.getHandlerContext().toString();
    }
    
    
    // how often to check - XXX - make this configurable
    final static int scavengeDelay = 30000;
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* -------------------------------------------------------------- */
    /** SessionScavenger is a background thread that kills off old sessions */
    class SessionScavenger extends Thread
    {
        public void run() {
            while (true) {
                try {
                    sleep(scavengeDelay); 
                } catch (InterruptedException ex) {}
                Context.this.scavenge();
            }
        }

        SessionScavenger() {
            super("SessionScavenger");
            setDaemon(true);
            this.start();
        }
        
    }   // SessionScavenger    


    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    class Session implements HttpSession
    {
        HashMap _values = new HashMap(11);
        boolean invalid=false;
        boolean newSession=true;
        long created=System.currentTimeMillis();
        long accessed=created;
        long maxIdleTime = -1;
        String id=null;

        /* ------------------------------------------------------------- */
        Session()
        {
            synchronized(com.mortbay.HTTP.Handler.Servlet.Context.class)
            {
                long idtmp = __nextSessionId;
                __nextSessionId+=created%4096;
                this.id=Long.toString(idtmp,30+(int)(created%7));
            }
            if (_defaultMaxIdleTime>=0)
                maxIdleTime=_defaultMaxIdleTime*1000;
        }
        
        /* ------------------------------------------------------------- */
        void accessed()
        {
            newSession=false;
            accessed=System.currentTimeMillis();
        }
        
        /* ------------------------------------------------------------- */
        public String getId()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            return id;
        }
        
        /* ------------------------------------------------------------- */
        public long getCreationTime()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            return created;
        }
        
        /* ------------------------------------------------------------- */
        public long getLastAccessedTime()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            return accessed;
        }
        
        /* ------------------------------------------------------------- */
        public int getMaxInactiveInterval()
        {
            return (int)(maxIdleTime / 1000);
        }
        
        /* ------------------------------------------------------------- */
        /**
         * @deprecated
         */   
        public HttpSessionContext getSessionContext()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            return Context.this;
        }
        
        /* ------------------------------------------------------------- */
        public void setMaxInactiveInterval(int i)
        {
            maxIdleTime = (long)i * 1000;
        }
        
        /* ------------------------------------------------------------- */
        public synchronized void invalidate()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            
            // Call valueUnbound on all the HttpSessionBindingListeners
            // To avoid iterator problems, don't actually remove them
            Iterator iter = _sessions.keySet().iterator();
            while (iter.hasNext())
            {
                String key = (String)iter.next();
                Object value = _values.get(key);
                unbindValue(key, value);
            }
            Context.this._sessions.remove(id);
            invalid=true;
        }
        
        /* ------------------------------------------------------------- */
        public boolean isNew()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            return newSession;
        }
        

        /* ------------------------------------------------------------ */
        public Object getAttribute(String name)
        {
            if (invalid) throw new IllegalStateException();
            return _values.get(name);
        }

        /* ------------------------------------------------------------ */
        public Enumeration getAttributeNames()
        {
            if (invalid) throw new IllegalStateException();
            return Collections.enumeration(_values.keySet());
        }
        
        /* ------------------------------------------------------------ */
        public void setAttribute(String name, Object value)
        {
            if (invalid) throw new IllegalStateException();
            Object oldValue = _values.put(name,value);

            if (value != oldValue)
            {
                unbindValue(name, oldValue);
                bindValue(name, value);
            }
        }
        
        /* ------------------------------------------------------------ */
        public void removeAttribute(String name)
        {
            if (invalid) throw new IllegalStateException();
            Object value=_values.remove(name);
            unbindValue(name, value);
        }
        
        /* ------------------------------------------------------------- */
        /**
         * @deprecated 	As of Version 2.2, this method is
         * 		replaced by {@link #getAttribute}
         */
        public Object getValue(String name)
            throws IllegalStateException
        {
            return getAttribute(name);
        }
        
        /* ------------------------------------------------------------- */
        /**
         * @deprecated 	As of Version 2.2, this method is
         * 		replaced by {@link #getAttributeNames}
         */
        public synchronized String[] getValueNames()
            throws IllegalStateException
        {
            if (invalid) throw new IllegalStateException();
            String[] a = new String[_values.size()];
            return (String[])_values.keySet().toArray(a);
        }
        
        /* ------------------------------------------------------------- */
        /**
         * @deprecated 	As of Version 2.2, this method is
         * 		replaced by {@link #setAttribute}
         */
        public void putValue(java.lang.String name,
                             java.lang.Object value)
            throws IllegalStateException
        {
            setAttribute(name,value);
        }
        
        /* ------------------------------------------------------------- */
        /**
         * @deprecated 	As of Version 2.2, this method is
         * 		replaced by {@link #removeAttribute}
         */
        public void removeValue(java.lang.String name)
            throws IllegalStateException
        {
            removeAttribute(name);
        }

        /* ------------------------------------------------------------- */
        /** If value implements HttpSessionBindingListener, call valueBound() */
        private void bindValue(java.lang.String name, Object value)
        {
            if (value!=null && value instanceof HttpSessionBindingListener)
                ((HttpSessionBindingListener)value)
                    .valueBound(new HttpSessionBindingEvent(this,name));
        }

        /* ------------------------------------------------------------- */
        /** If value implements HttpSessionBindingListener, call valueUnbound() */
        private void unbindValue(java.lang.String name, Object value)
        {
            if (value!=null && value instanceof HttpSessionBindingListener)
                ((HttpSessionBindingListener)value)
                    .valueUnbound(new HttpSessionBindingEvent(this,name));
        }        
    }    
}
