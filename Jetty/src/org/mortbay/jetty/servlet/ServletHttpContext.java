// ========================================================================
// $Id$
// Copyright 2001-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.servlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

/* ------------------------------------------------------------ */
/** ServletHttpContext.
 * Extends HttpContext with conveniance methods for adding servlets.
 * Enforces a single ServletHandler per context.
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class ServletHttpContext extends HttpContext
{
    private HashMap _localeEncodingMap  = new HashMap();
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public ServletHttpContext()
    {
        super();
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return The ServletContext. 
     */
    public ServletContext getServletContext()
    {
        ServletHandler shandler=getServletHandler();
        if (shandler!=null)
            return shandler.getServletContext();
        return null;
    }
    
    /* ------------------------------------------------------------ */
    /** Get the context ServletHandler.
     * Conveniance method. If no ServletHandler exists, a new one is added to
     * the context.
     * @return ServletHandler
     */
    public synchronized ServletHandler getServletHandler()
    {
        ServletHandler shandler=(ServletHandler) getHandler(ServletHandler.class);
        if (shandler==null)
        {
            shandler=new ServletHandler();
            addHandler(shandler);
        }
        return shandler;
    }
    
    /* ------------------------------------------------------------ */
    /** Add a servlet to the context.
     * Conveniance method.
     * If no ServletHandler is found in the context, a new one is added.
     * @param pathSpec The pathspec within the context
     * @param className The classname of the servlet.
     * @return The ServletHolder.
     * @exception ClassNotFoundException 
     * @exception InstantiationException 
     * @exception IllegalAccessException 
     */
    public synchronized ServletHolder addServlet(String pathSpec,
                                                 String className)
        throws ClassNotFoundException,
               InstantiationException,
               IllegalAccessException
    {
        return addServlet(className,pathSpec,className);
    }
    
    /* ------------------------------------------------------------ */
    /** Add a servlet to the context.
     * If no ServletHandler is found in the context, a new one is added.
     * @param name The name of the servlet.
     * @param pathSpec The pathspec within the context
     * @param className The classname of the servlet.
     * @return The ServletHolder.
     * @exception ClassNotFoundException 
     * @exception InstantiationException 
     * @exception IllegalAccessException 
     */
    public synchronized ServletHolder addServlet(String name,
                                                 String pathSpec,
                                                 String className)
        throws ClassNotFoundException,
               InstantiationException,
               IllegalAccessException
    {
        return getServletHandler().addServlet(name,pathSpec,className,null);
    }

    /* ------------------------------------------------------------ */
    protected boolean jSecurityCheck(String pathInContext,
                                     HttpRequest request,
                                     HttpResponse response)
            throws IOException
    {
        if (getAuthenticator() instanceof FormAuthenticator &&
            pathInContext.endsWith(FormAuthenticator.__J_SECURITY_CHECK) &&
            getAuthenticator().authenticate(getRealm(),
                                                        pathInContext,
                                                        request,
                                                        response)==null)
            return false;
        return true;
    }
    
    /* ------------------------------------------------------------ */
    public boolean checkSecurityConstraints(String pathInContext,
                                            HttpRequest request,
                                            HttpResponse response)
            throws HttpException, IOException
    {
        if (!super.checkSecurityConstraints(pathInContext,request,response) ||
            ! jSecurityCheck(pathInContext,request,response))
            return false;
        
        return true;
    }
    
    /* ------------------------------------------------------------ */
    public void addLocaleEncoding(String locale,String encoding)
    {
        _localeEncodingMap.put(locale, encoding);
    }
    
    /* ------------------------------------------------------------ */
    /**
     * Get the character encoding for a locale. The full locale name is first
     * looked up in the map of encodings. If no encoding is found, then the
     * locale language is looked up. 
     *
     * @param locale a <code>Locale</code> value
     * @return a <code>String</code> representing the character encoding for
     * the locale or null if none found.
     */
    public String getLocaleEncoding(Locale locale)
    {
        String encoding = (String)_localeEncodingMap.get(locale.toString());
        if (encoding==null)
            encoding = (String)_localeEncodingMap.get(locale.getLanguage());
        return encoding;
    }
    
    /* ------------------------------------------------------------ */
    public String toString()
    {
        return "Servlet"+super.toString(); 
    }
    

    /* ------------------------------------------------------------ */
    /* send servlet response error
     * 
     */
    public void sendError(HttpResponse response,int code,String msg)
    	throws IOException
    {
        Object wrapper = response.getWrapper();
        if (wrapper!=null && wrapper instanceof HttpServletResponse)
            ((HttpServletResponse)wrapper).sendError(code,msg);
        else
            super.sendError(response,code,msg);
    }
}
