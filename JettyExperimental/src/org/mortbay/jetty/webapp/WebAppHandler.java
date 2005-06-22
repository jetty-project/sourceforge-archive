//========================================================================
//$Id$
//Copyright 2004 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.jetty.webapp;

import java.io.File;
import java.io.IOException;
import java.util.EventListener;

import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionListener;

import org.mortbay.io.IO;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.SessionHandler;
import org.mortbay.resource.JarResource;
import org.mortbay.resource.Resource;
import org.mortbay.util.LazyList;
import org.mortbay.util.Loader;
import org.mortbay.util.LogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* ------------------------------------------------------------ */
/** WebApplicationHandler.
 * @author gregw
 *
 */
public class WebAppHandler extends ContextHandler
{

    /* ------------------------------------------------------------ */
    private static Logger log= LoggerFactory.getLogger(WebAppHandler.class);
    
    private SessionHandler _sessionHandler;
    private ServletHandler _servletHandler;
    private String[] _configurationClasses;
    private Configuration[] _configurations;
    private File _tmpDir;
    private Object _contextListeners;
    private String _war;
    private boolean _extractWAR=true;
    private String _defaultsDescriptor="org/mortbay/jetty/webapp/webdefault.xml";
    private boolean distributable=false;
    
    private transient Resource _webApp;
    private transient Resource _webInf;

    /* ------------------------------------------------------------ */
    public WebAppHandler()
    {
        _servletHandler = new ServletHandler();
        
        _sessionHandler = new SessionHandler();
        _sessionHandler.setHandler(_servletHandler);
        
        setHandler(_sessionHandler);
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the war as a file or URL string (Resource)
     */
    public String getWar()
    {
        return _war;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param war The war to set as a file name or URL
     */
    public void setWar(String war)
    {
        _war = war;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the configurations.
     */
    public String[] getConfigurationClasses()
    {
        return _configurationClasses;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param configurations The configuration class names.  If setConfigurations is not called
     * these classes are used to create a configurations array.
     */
    public void setConfigurationClasses(String[] configurations)
    {
        _configurationClasses = configurations==null?null:(String[])configurations.clone();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the configurations.
     */
    public Configuration[] getConfigurations()
    {
        return _configurations;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param configurations The configurations to set.
     */
    public void setConfigurations(Configuration[] configurations)
    {
        _configurations = configurations==null?null:(Configuration[])configurations.clone();
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the servletHandler.
     */
    public ServletHandler getServletHandler()
    {
        return _servletHandler;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param servletHandler The servletHandler to set.
     */
    public void setServletHandler(ServletHandler servletHandler)
    {
        _servletHandler = servletHandler;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the sessionHandler.
     */
    public SessionHandler getSessionHandler()
    {
        return _sessionHandler;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param sessionHandler The sessionHandler to set.
     */
    public void setSessionHandler(SessionHandler sessionHandler)
    {
        _sessionHandler = sessionHandler;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the defaultsDescriptor.
     */
    public String getDefaultsDescriptor()
    {
        return _defaultsDescriptor;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param defaultsDescriptor The defaultsDescriptor to set.
     */
    public void setDefaultsDescriptor(String defaultsDescriptor)
    {
        _defaultsDescriptor = defaultsDescriptor;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the extractWAR.
     */
    public boolean isExtractWAR()
    {
        return _extractWAR;
    }
    /* ------------------------------------------------------------ */
    /**
     * @param extractWAR The extractWAR to set.
     */
    public void setExtractWAR(boolean extractWAR)
    {
        _extractWAR = extractWAR;
    }
    

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the distributable.
     */
    public boolean isDistributable()
    {
        return distributable;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param distributable The distributable to set.
     */
    public void setDistributable(boolean distributable)
    {
        this.distributable = distributable;
    }
    
    /* ------------------------------------------------------------ */
    public synchronized void addEventListener(EventListener listener) throws IllegalArgumentException
    {
        // TODO - make sure all events handling is actually implemented....
        
        if (listener instanceof ServletContextListener)
        {
            _contextListeners= LazyList.add(_contextListeners, listener);
        }


        if ((listener instanceof HttpSessionActivationListener)
            || (listener instanceof HttpSessionAttributeListener)
            || (listener instanceof HttpSessionBindingListener)
            || (listener instanceof HttpSessionListener))
        {
            if (_sessionHandler!=null)
                _sessionHandler.addEventListener(listener);
        }

        if (listener instanceof ServletRequestListener ||
            listener instanceof ServletRequestAttributeListener ||
            listener instanceof ServletContextAttributeListener ||
            listener instanceof ServletRequestAttributeListener ||
            listener instanceof ServletContextAttributeListener)
        {
            _servletHandler.addEventListener(listener);
        }


    }
    
    
    /* ------------------------------------------------------------ */
    protected void resolveWebApp() throws IOException
    {
        if (_webApp == null)
        {
            if (_war==null || _war.length()==0)
                _war=getResourceBase();
            
            // Set dir or WAR
            _webApp= Resource.newResource(_war);

            // Accept aliases for WAR files
            if (_webApp.getAlias() != null)
            {
                log.info(_webApp + " anti-aliased to " + _webApp.getAlias());
                _webApp= Resource.newResource(_webApp.getAlias());
            }

            if (log.isDebugEnabled())
                log.debug("Try webapp=" + _webApp + ", exists=" + _webApp.exists() + ", directory=" + _webApp.isDirectory());

            // Is the WAR usable directly?
            if (_webApp.exists() && !_webApp.isDirectory() && !_webApp.toString().startsWith("jar:"))
            {
                // No - then lets see if it can be turned into a jar URL.
                Resource jarWebApp= Resource.newResource("jar:" + _webApp + "!/");
                if (jarWebApp.exists() && jarWebApp.isDirectory())
                {
                    _webApp= jarWebApp;
                    _war= _webApp.toString();
                    if (log.isDebugEnabled())
                        log.debug(
                            "Try webapp="
                                + _webApp
                                + ", exists="
                                + _webApp.exists()
                                + ", directory="
                                + _webApp.isDirectory());
                }
            }

            // If we should extract or the URL is still not usable
            if (_webApp.exists()
                && (!_webApp.isDirectory()
                    || (_extractWAR && _webApp.getFile() == null)
                    || (_extractWAR && _webApp.getFile() != null && !_webApp.getFile().isDirectory())))
            {
                // Then extract it.
                File tempDir= new File(getTempDirectory(), "webapp");
                if (tempDir.exists())
                    tempDir.delete();
                tempDir.mkdir();
                tempDir.deleteOnExit();
                log.info("Extract " + _war + " to " + tempDir);
                JarResource.extract(_webApp, tempDir, true);
                _webApp= Resource.newResource(tempDir.getCanonicalPath());

                if (log.isDebugEnabled())
                    log.debug(
                        "Try webapp="
                            + _webApp
                            + ", exists="
                            + _webApp.exists()
                            + ", directory="
                            + _webApp.isDirectory());
            }

            // Now do we have something usable?
            if (!_webApp.exists() || !_webApp.isDirectory())
            {
                log.warn("Web application not found " + _war);
                throw new java.io.FileNotFoundException(_war);
            }

            if (log.isDebugEnabled())
                log.debug("webapp=" + _webApp);

            // Iw there a WEB-INF directory?
            _webInf= _webApp.addPath("WEB-INF/");
            if (!_webInf.exists() || !_webInf.isDirectory())
                _webInf= null;
            else
            {
                // Is there a WEB-INF work directory
                Resource work= _webInf.addPath("work");
                if (work.exists()
                    && work.isDirectory()
                    && work.getFile() != null
                    && work.getFile().canWrite()
                    && getAttribute("javax.servlet.context.tempdir") == null)
                    setAttribute("javax.servlet.context.tempdir", work.getFile());
            }

            // ResourcePath
            super.setBaseResource(_webApp);
        }
    }


    /* ------------------------------------------------------------ */
    public Resource getWebInf() throws IOException
    {
        if (_webInf==null)
            resolveWebApp();
        return _webInf;
    }
    
    
    /* ------------------------------------------------------------ */
    /** Set temporary directory for context.
     * The javax.servlet.context.tempdir attribute is also set.
     * @param dir Writable temporary directory.
     */
    public void setTempDirectory(File dir)
    {
        if (isStarted())
            throw new IllegalStateException("Started");

        if (dir!=null)
        {
            try{dir=new File(dir.getCanonicalPath());}
            catch (IOException e){log.warn(LogSupport.EXCEPTION,e);}
        }

        if (dir!=null && !dir.exists())
        {
            dir.mkdir();
            dir.deleteOnExit();
        }

        if (dir!=null && ( !dir.exists() || !dir.isDirectory() || !dir.canWrite()))
            throw new IllegalArgumentException("Bad temp directory: "+dir);

        _tmpDir=dir;
        setAttribute("javax.servlet.context.tempdir",_tmpDir);
    }

    /* ------------------------------------------------------------ */
    public File getTempDirectory()
    {
        if (_tmpDir!=null)
            return _tmpDir;

        // Initialize temporary directory
        //
        // I'm afraid that this is very much black magic.
        // but if you can think of better....
        Object t = getAttribute("javax.servlet.context.tempdir");

        if (t!=null && (t instanceof File))
        {
            _tmpDir=(File)t;
            if (_tmpDir.isDirectory() && _tmpDir.canWrite())
                return _tmpDir;
        }

        if (t!=null && (t instanceof String))
        {
            try
            {
                _tmpDir=new File((String)t);

                if (_tmpDir.isDirectory() && _tmpDir.canWrite())
                {
                    if(log.isDebugEnabled())log.debug("Converted to File "+_tmpDir+" for "+this);
                    setAttribute("javax.servlet.context.tempdir",_tmpDir);
                    return _tmpDir;
                }
            }
            catch(Exception e)
            {
                log.warn(LogSupport.EXCEPTION,e);
            }
        }

        // No tempdir so look for a WEB-INF/work directory to use as tempDir base
        File work=null;
        try
        {
            work=new File(System.getProperty("jetty.home"),"work");
            if (!work.exists() || !work.canWrite() || !work.isDirectory())
                work=null;
        }
        catch(Exception e)
        {
            LogSupport.ignore(log,e);
        }

        // No tempdir set so make one!
        try
        {
            String temp="Jetty_"+getContextPath();
            temp=temp.replace('/','_');
            temp=temp.replace('.','_');
            temp=temp.replace('\\','_');

            
            if (work!=null)
                _tmpDir=new File(work,temp);
            else
            {
                _tmpDir=new File(System.getProperty("java.io.tmpdir"),temp);
                
                if (_tmpDir.exists())
                {
                    if(log.isDebugEnabled())log.debug("Delete existing temp dir "+_tmpDir+" for "+this);
                    if (!IO.delete(_tmpDir))
                    {
                        if(log.isDebugEnabled())log.debug("Failed to delete temp dir "+_tmpDir);
                    }
                
                    if (_tmpDir.exists())
                    {
                        String old=_tmpDir.toString();
                        _tmpDir=File.createTempFile(temp+"_","");
                        if (_tmpDir.exists())
                            _tmpDir.delete();
                        log.warn("Can't reuse "+old+", using "+_tmpDir);
                    }
                }
            }

            if (!_tmpDir.exists())
                _tmpDir.mkdir();
            if (work==null)
                _tmpDir.deleteOnExit();
            if(log.isDebugEnabled())log.debug("Created temp dir "+_tmpDir+" for "+this);
        }
        catch(Exception e)
        {
            _tmpDir=null;
            LogSupport.ignore(log,e);
        }

        if (_tmpDir==null)
        {
            try{
                // that didn't work, so try something simpler (ish)
                _tmpDir=File.createTempFile("JettyContext","");
                if (_tmpDir.exists())
                    _tmpDir.delete();
                _tmpDir.mkdir();
                _tmpDir.deleteOnExit();
                if(log.isDebugEnabled())log.debug("Created temp dir "+_tmpDir+" for "+this);
            }
            catch(IOException e)
            {
                log.warn("tmpdir",e); System.exit(1);
            }
        }

        setAttribute("javax.servlet.context.tempdir",_tmpDir);
        return _tmpDir;
    }

    /* ------------------------------------------------------------ */
    protected void loadConfigurations() 
    	throws Exception
    {
        if (_configurations!=null)
            return;
        if (_configurationClasses==null)
            _configurationClasses=new String[] { "org.mortbay.jetty.webapp.WebXmlConfiguration", "org.mortbay.jetty.webapp.JettyWebXmlConfiguration" } ;
        
        _configurations = new Configuration[_configurationClasses.length];
        for (int i=0;i<_configurations.length;i++)
        {
            _configurations[i]=(Configuration)Loader.loadClass(this.getClass(), _configurationClasses[i]).newInstance();
        }
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.thread.AbstractLifeCycle#doStart()
     */
    protected void doStart() throws Exception
    {
        // Setup configurations 
        loadConfigurations();
        for (int i=0;i<_configurations.length;i++)
            _configurations[i].setWebAppHandler(this);
        
        
        // Configure classloader
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent==null)
            parent=this.getClass().getClassLoader();
        if (parent==null)
            parent=ClassLoader.getSystemClassLoader();
        
        WebAppClassLoader classLoader = new WebAppClassLoader(parent);
        classLoader.setTempDirectory(getTempDirectory());
        setClassLoader(classLoader);
        for (int i=0;i<_configurations.length;i++)
            _configurations[i].configureClassLoader();

        super.doStart();

    }
    

    /* ------------------------------------------------------------ */
    protected void startContext()
    throws Exception
    {
        // Configure defaults
        for (int i=0;i<_configurations.length;i++)
            _configurations[i].configureDefaults();
        
        // Configure webapp
        for (int i=0;i<_configurations.length;i++)
            _configurations[i].configureWebApp();
        
        
        _servletHandler.setInitializeAtStart(false);
        
        super.startContext();

        // Context listeners
        if (_contextListeners != null && _servletHandler != null)
        {
            ServletContextEvent event= new ServletContextEvent(_servletHandler.getServletContext());
            for (int i= 0; i < LazyList.size(_contextListeners); i++)
            {
                ((ServletContextListener)LazyList.get(_contextListeners, i)).contextInitialized(event);
            }
        }

        // OK to Initialize servlet handler now
        if (_servletHandler != null && _servletHandler.isStarted())
        {
            _servletHandler.initialize();
        }
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.thread.AbstractLifeCycle#doStop()
     */
    protected void doStop() throws Exception
    {
        super.doStop();

        try
        {
            // Configure classloader
            for (int i=_configurations.length;i-->0;)
                _configurations[i].deconfigureWebApp();
        }
        finally
        {
            setClassLoader(null);
        }
    }

    
}