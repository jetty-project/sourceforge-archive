// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.jetty.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.mortbay.http.ChunkableOutputStream;
import org.mortbay.http.HttpConnection;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpMessage;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.PathMap;
import org.mortbay.http.handler.NullHandler;
import org.mortbay.http.handler.ResourceHandler;
import org.mortbay.util.Code;
import org.mortbay.util.Log;
import org.mortbay.util.MultiMap;
import org.mortbay.util.Resource;
import org.mortbay.util.UrlEncoded;
import org.mortbay.util.URI;
import org.mortbay.util.WriterOutputStream;


/* ------------------------------------------------------------ */
/** Servlet RequestDispatcher.
 * 
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class Dispatcher implements RequestDispatcher
{
    public final static String __REQUEST_URI= "javax.servlet.include.request_uri";
    public final static String __CONTEXT_PATH= "javax.servlet.include.context_path";
    public final static String __SERVLET_PATH= "javax.servlet.include.servlet_path";
    public final static String __PATH_INFO= "javax.servlet.include.path_info";
    public final static String __QUERY_STRING= "javax.servlet.include.query_string";
    
    ServletHandler _servletHandler;
    ServletHolder _holder=null;
    String _pathSpec;
    String _uriInContext;
    String _path;
    String _query;
    Resource _resource;
    ResourceHandler _resourceHandler;
    WebApplicationHandler _webAppHandler;
    boolean _include;
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
    /** Constructor. 
     * @param servletHandler 
     * @param resourceHandler 
     * @param uriInContext Encoded pathInContext
     * @param query 
     * @exception IllegalStateException 
     */
    Dispatcher(ServletHandler servletHandler,
               ResourceHandler resourceHandler,
               String uriInContext,
               String query)
        throws IllegalStateException
    {
        Code.debug("Dispatcher for ",servletHandler,",",uriInContext,",",query);

        _uriInContext=uriInContext;
        String pathInContext=URI.decodePath(_uriInContext);
        
        _servletHandler=servletHandler;
        _resourceHandler=resourceHandler;
        if (_servletHandler instanceof WebApplicationHandler)
        {
            _webAppHandler=(WebApplicationHandler)_servletHandler;
            _resourceHandler=null;
        }
        
        _path=URI.canonicalPath(pathInContext);
        _query=query;

        Map.Entry entry=_servletHandler.getHolderEntry(_path);
        if(entry!=null)
        {
            _pathSpec=(String)entry.getKey();
            _holder = (ServletHolder)entry.getValue();
        }
        else if (_resourceHandler!=null)
        {            
            // Look for a static resource
            try{
                Resource resource=
                    _resourceHandler.getHttpContext().getResource(pathInContext);
                if (resource.exists())
                {
                    _resource=resource;
                    Code.debug("Dispatcher for resource ",_resource);
                }
            }
            catch(IOException e){Code.ignore(e);}
        }
        else if (_webAppHandler!=null)
        {            
            // Look for a static resource
            try{
                Resource resource=
                    _webAppHandler.getHttpContext().getResource(pathInContext);
                if (resource.exists())
                {
                    _resource=resource;
                    Code.debug("Dispatcher for resource ",_resource);
                }
            }
            catch(IOException e){Code.ignore(e);}
        }

        // if no servlet and no resource
        if (_holder==null && _resource==null)
            throw new IllegalStateException("No servlet handlers in context");
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param server 
     * @param URL 
     */
    Dispatcher(ServletHandler servletHandler,String name)
        throws IllegalStateException
    {
        _servletHandler=servletHandler;
        _holder=_servletHandler.getServletHolder(name);
        if (_holder==null)
            throw new IllegalStateException("No named servlet handler in context");
    }

    /* ------------------------------------------------------------ */
    public boolean isNamed()
    {
        return _path==null;
    }

    /* ------------------------------------------------------------ */
    public boolean isResource()
    {
        return _resource!=null;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param request 
     * @param response 
     * @exception ServletException 
     * @exception IOException 
     */
    public void include(ServletRequest servletRequest,
                        ServletResponse servletResponse)
        throws ServletException, IOException     
    {
        _include=true;
        forward(servletRequest,servletResponse);
    }
    
    /* ------------------------------------------------------------ */
    public void forward(ServletRequest servletRequest,
                        ServletResponse servletResponse)
        throws ServletException,IOException
    {
        HttpServletRequest httpServletRequest=(HttpServletRequest)servletRequest;
        HttpServletResponse httpServletResponse=(HttpServletResponse)servletResponse;

        HttpConnection httpConnection=
            ((ServletHttpContext)_servletHandler.getHttpContext()).getHttpConnection();
        ServletHttpRequest servletHttpRequest= (httpConnection!=null)
            ?(ServletHttpRequest)httpConnection.getRequest().getWrapper()
            :ServletHttpRequest.unwrap(servletRequest);
        ServletHttpResponse servletHttpResponse=
            servletHttpRequest.getServletHttpResponse();

        // wrap the request and response
        DispatcherRequest request = new DispatcherRequest(httpServletRequest);
        DispatcherResponse response = new DispatcherResponse(httpServletResponse);
        
        if (!_include)
            servletResponse.resetBuffer();
        
        // Merge parameters
        String query=_query;
        MultiMap parameters=null;
        if (query!=null)
        {
            // Add the parameters
            parameters=new MultiMap();
            UrlEncoded.decodeTo(query,parameters);
            request.addParameters(parameters);
        }
        
        if (isNamed())
        {
            // No further modifications required.
            _holder.handle(request,response);
        }
        else if (isResource())
        {
            if (_include)
            {
                OutputStream out=response.getOutputStream();
                _resource.writeTo(out,0,-1);
            }
            else
            {
                if (_webAppHandler!=null)
                    _webAppHandler.handleGet(_path,_resource,request,response,true);
                else
                {
                    HttpRequest httpRequest=servletHttpRequest.getHttpRequest();
                    HttpResponse httpResponse=httpRequest.getHttpResponse();
                    _resourceHandler.handle(_path,null,httpRequest,httpResponse);
                }
            }
        }
        else
        {
            // merge query string
            String oldQ=httpServletRequest.getQueryString();
            if (oldQ!=null && oldQ.length()>0 && parameters!=null)
            {
                UrlEncoded encoded = new UrlEncoded(oldQ);
                encoded.putAll(parameters);
                query=encoded.encode();
            }
            else
                query=oldQ;
            
            // Adjust servlet paths
            servletHttpRequest.setServletHandler(_servletHandler);
            request.setPaths(_servletHandler.getHttpContext().getContextPath(),
                             PathMap.pathMatch(_pathSpec,_path),
                             PathMap.pathInfo(_pathSpec,_path),
                             query);
            _holder.handle(request,response);
            
            if (!_include)
                response.close();
            else if (response.isFlushNeeded())
                response.flushBuffer();
        }
    }

    /* ------------------------------------------------------------ */
    public String toString()
    {
        return "Dispatcher["+_pathSpec+","+_holder+"]";
    }
        

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class DispatcherRequest extends HttpServletRequestWrapper
    {
        String _contextPath;
        String _servletPath;
        String _pathInfo;
        String _query;
        MultiMap _parameters;
        
        /* ------------------------------------------------------------ */
        DispatcherRequest(HttpServletRequest request)
        {
            super(request);
        }


        /* ------------------------------------------------------------ */
        public String getRequestURI()
        {
            if (_include || isNamed())
                return super.getRequestURI();
            return URI.addPaths(_contextPath,_uriInContext);
        }

        
        /* ------------------------------------------------------------ */
        void setPaths(String cp,String sp, String pi, String qs)
        {
            _contextPath = (cp.length()==1 && cp.charAt(0)=='/')?"":cp;
            _servletPath=sp;
            _pathInfo=pi;
            _query=qs;
        }
        
        /* ------------------------------------------------------------ */
        public String getContextPath()
        {
            return(_include||isNamed())?super.getContextPath():_contextPath;
        }
        
        /* ------------------------------------------------------------ */
        public String getServletPath()
        {
            return(_include||isNamed())?super.getServletPath():_servletPath;
        }
        
        /* ------------------------------------------------------------ */
        public String getPathInfo()
        {
            return(_include||isNamed())?super.getPathInfo():_pathInfo;
        }
        
        /* ------------------------------------------------------------ */
        public String getQueryString()
        {
            return(_include||isNamed())?super.getQueryString():_query;
        }
        

        /* ------------------------------------------------------------ */
        void addParameters(MultiMap parameters)
        {
            _parameters=parameters;
        }
        
        /* -------------------------------------------------------------- */
        public Enumeration getParameterNames()
        {
            if (_parameters==null)
                return super.getParameterNames();
            
            HashSet set = new HashSet(_parameters.keySet());
            Enumeration e = super.getParameterNames();
            while (e.hasMoreElements())
                set.add(e.nextElement());

            return Collections.enumeration(set);
        }
        
        /* -------------------------------------------------------------- */
        public String getParameter(String name)
        {
            if (_parameters==null)
                return super.getParameter(name);
            String value=_parameters.getString(name);
            if (value!=null)
                return value;
            return super.getParameter(name);
        }
        
        /* -------------------------------------------------------------- */
        public String[] getParameterValues(String name)
        {
            if (_parameters==null)
                return super.getParameterValues(name);
            List values=_parameters.getValues(name);
            if (values!=null)
            {
                String[]a=new String[values.size()];
                return (String[])values.toArray(a);
            }
            return super.getParameterValues(name);
        }
        
        /* -------------------------------------------------------------- */
        public Map getParameterMap()
        {
            if (_parameters==null)
                return super.getParameterMap();
            Map m0 = _parameters.toStringArrayMap();
            Map m1 = super.getParameterMap();
            
            Iterator i = m1.entrySet().iterator();
            while(i.hasNext())
            {
                Map.Entry entry = (Map.Entry)i.next();
                if (!m0.containsKey(entry.getKey()))
                    m0.put(entry.getKey(),entry.getValue());
            }
            return m0;
        }

        /* ------------------------------------------------------------ */
        public Object getAttribute(String name)
        {
            if (_include && !isNamed())
            {
                if (name.equals(__PATH_INFO))    return _pathInfo;
                if (name.equals(__REQUEST_URI))  return URI.addPaths(_contextPath,_uriInContext);
                if (name.equals(__SERVLET_PATH)) return _servletPath;
                if (name.equals(__CONTEXT_PATH)) return _contextPath;
                if (name.equals(__QUERY_STRING)) return _query;
            }
            else
            {
                if (name.equals(__PATH_INFO))    return null;
                if (name.equals(__REQUEST_URI))  return null;
                if (name.equals(__SERVLET_PATH)) return null;
                if (name.equals(__CONTEXT_PATH)) return null;
                if (name.equals(__QUERY_STRING)) return null;
            }
            
            return super.getAttribute(name);
        }
        
        /* ------------------------------------------------------------ */
        public Enumeration getAttributeNames()
        {
            HashSet set=new HashSet();
            Enumeration e=super.getAttributeNames();
            while (e.hasMoreElements())
                set.add(e.nextElement());
                
            if (_include && !isNamed())
            {
                set.add(__PATH_INFO);
                set.add(__REQUEST_URI);
                set.add(__SERVLET_PATH);
                set.add(__CONTEXT_PATH);
                set.add(__QUERY_STRING);
            }
            else
            {
                set.remove(__PATH_INFO);
                set.remove(__REQUEST_URI);
                set.remove(__SERVLET_PATH);
                set.remove(__CONTEXT_PATH);
                set.remove(__QUERY_STRING);
            }
            
            return Collections.enumeration(set);
        }
    }
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class DispatcherResponse extends HttpServletResponseWrapper
    {
        private ServletOutputStream _out=null;
        private PrintWriter _writer=null;
        private boolean _flushNeeded=false;
        
        /* ------------------------------------------------------------ */
        DispatcherResponse(HttpServletResponse response)
        {
            super(response);
        }

        /* ------------------------------------------------------------ */
        public ServletOutputStream getOutputStream()
            throws IOException
        {
            if (_writer!=null)
                throw new IllegalStateException("getWriter called");

            if (_out==null)
            {
                try {_out=super.getOutputStream();}
                catch(IllegalStateException e)
                {
                    Code.ignore(e);
                    _flushNeeded=true;
                    _out=new ServletOut(new WriterOutputStream(super.getWriter()));
                }
            }

            if (_include)
                _out=new DontCloseServletOut(_out);
            
            return _out;
        }  
      
        /* ------------------------------------------------------------ */
        public PrintWriter getWriter()
            throws IOException
        {
            if (_out!=null)
                throw new IllegalStateException("getOutputStream called");

            if (_writer==null)
            {
                try{_writer=super.getWriter();}
                catch(IllegalStateException e)
                {
                    if (Code.debug()) Code.warning(e);
                    _flushNeeded=true;
                    _writer = new ServletWriter(super.getOutputStream(),
                                                getCharacterEncoding());
                }
            }

            if (_include)
                _writer=new DontCloseWriter(_writer);
            return _writer;
        }

        /* ------------------------------------------------------------ */
        boolean isFlushNeeded()
        {
            return _flushNeeded;
        }
        
        /* ------------------------------------------------------------ */
        public void flushBuffer()
            throws IOException
        {
            if (_writer!=null)
                _writer.flush();
            if (_out!=null)
                _out.flush();
            super.flushBuffer();
        }
        
        /* ------------------------------------------------------------ */
        public void close()
            throws IOException
        {
            if (_writer!=null)
                _writer.close();
            if (_out!=null)
                _out.close();
        }
        
        /* ------------------------------------------------------------ */
        public void setLocale(Locale locale)
        {
            if (!_include) super.setLocale(locale);
        }
        
        /* ------------------------------------------------------------ */
        public void sendError(int status, String message)
            throws IOException
        {
            if (!_include) super.sendError(status,message);
        }
        
        /* ------------------------------------------------------------ */
        public void sendError(int status)
            throws IOException
        {
            if (!_include) super.sendError(status);
        }
        
        /* ------------------------------------------------------------ */
        public void sendRedirect(String url)
            throws IOException
        {
            if (!_include) super.sendRedirect(url);
        }
        
        /* ------------------------------------------------------------ */
        public void setDateHeader(String name, long value)
        {
            if (!_include) super.setDateHeader(name,value);
        }
        
        /* ------------------------------------------------------------ */
        public void setHeader(String name, String value)
        {
            if (!_include) super.setHeader(name,value);
        }
        
        /* ------------------------------------------------------------ */
        public void setIntHeader(String name, int value)
        {
            if (!_include) super.setIntHeader(name,value);
        }
        
        /* ------------------------------------------------------------ */
        public void addHeader(String name, String value)
        {
            if (!_include) super.addHeader(name,value);
        }
        
        /* ------------------------------------------------------------ */
        public void addDateHeader(String name, long value)
        {
            if (!_include) super.addDateHeader(name,value);
        }
        
        /* ------------------------------------------------------------ */
        public void addIntHeader(String name, int value)
        {
            if (!_include) super.addIntHeader(name,value);
        }
        
        /* ------------------------------------------------------------ */
        public void setStatus(int status)
        {
            if (!_include) super.setStatus(status);
        }
        
        /* ------------------------------------------------------------ */
        public void setStatus(int status, String message)
        {
            if (!_include) super.setStatus(status,message);
        }
        
        /* ------------------------------------------------------------ */
        public void setContentLength(int len)
        {
            if (!_include) super.setContentLength(len);
        }
        
        /* ------------------------------------------------------------ */
        public void setContentType(String contentType)
        {
            if (!_include) super.setContentType(contentType);
        }
    }


    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class DontCloseWriter extends PrintWriter
    {
        DontCloseWriter(PrintWriter writer)
        {
            super(writer);
        }

        public void close()
        {}
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class DontCloseServletOut extends ServletOut
    {
        DontCloseServletOut(ServletOutputStream output)
        {
            super(output);
        }

        public void close()
            throws IOException
        {}
    }
};



