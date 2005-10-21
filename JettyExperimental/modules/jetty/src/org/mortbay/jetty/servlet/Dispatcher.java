// ========================================================================
// $Id$
// Copyright 199-2004 Mort Bay Consulting Pty. Ltd.
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.util.Attributes;
import org.mortbay.util.LazyList;
import org.mortbay.util.MultiMap;
import org.mortbay.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* ------------------------------------------------------------ */
/** Servlet RequestDispatcher.
 * 
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class Dispatcher implements RequestDispatcher
{
    private static Logger log = LoggerFactory.getLogger(DefaultServlet.class);
    
    /** Dispatch include attribute names */
    public final static String __INCLUDE_REQUEST_URI= "javax.servlet.include.request_uri";
    public final static String __INCLUDE_CONTEXT_PATH= "javax.servlet.include.context_path";
    public final static String __INCLUDE_SERVLET_PATH= "javax.servlet.include.servlet_path";
    public final static String __INCLUDE_PATH_INFO= "javax.servlet.include.path_info";
    public final static String __INCLUDE_QUERY_STRING= "javax.servlet.include.query_string";

    /** Dispatch include attribute names */
    public final static String __FORWARD_REQUEST_URI= "javax.servlet.forward.request_uri";
    public final static String __FORWARD_CONTEXT_PATH= "javax.servlet.forward.context_path";
    public final static String __FORWARD_SERVLET_PATH= "javax.servlet.forward.servlet_path";
    public final static String __FORWARD_PATH_INFO= "javax.servlet.forward.path_info";
    public final static String __FORWARD_QUERY_STRING= "javax.servlet.forward.query_string";


    /* ------------------------------------------------------------ */
    /** Dispatch type from name
     */
    public static int type(String type)
    {
        if ("request".equalsIgnoreCase(type))
            return Handler.REQUEST;
        if ("forward".equalsIgnoreCase(type))
            return Handler.FORWARD;
        if ("include".equalsIgnoreCase(type))
            return Handler.INCLUDE;
        if ("error".equalsIgnoreCase(type))
            return Handler.ERROR;
        throw new IllegalArgumentException(type);
    }


    /* ------------------------------------------------------------ */
    private ContextHandler _contextHandler;
    private String _uri;
    private String _path;
    private String _query;
    private String _named;
    
    /* ------------------------------------------------------------ */
    /**
     * @param contextHandler
     * @param uriInContext
     * @param pathInContext
     * @param query
     */
    public Dispatcher(ContextHandler contextHandler, String uri, String pathInContext, String query)
    {
        _contextHandler=contextHandler;
        _uri=uri;
        _path=pathInContext;
        _query=query;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.RequestDispatcher#forward(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void forward(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        dispatch(request, response, Handler.FORWARD);
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.RequestDispatcher#forward(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void error(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        dispatch(request, response, Handler.ERROR);
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.RequestDispatcher#forward(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    protected void dispatch(ServletRequest request, ServletResponse response, int dispatch) throws ServletException, IOException
    {
        response.reset(); 
        
        Request base_request=(request instanceof Request)?((Request)request):HttpConnection.getCurrentConnection().getRequest();
        
        String old_uri=base_request.getRequestURI();
        String old_context_path=base_request.getContextPath();
        String old_query=base_request.getQueryString();
        Attributes old_attr=base_request.getAttributes();
        MultiMap old_params=base_request.getParameters();
        try
        {
            // TODO parameters and query
            String query=_query;
            
            if (query!=null)
            {
                MultiMap parameters=new MultiMap();
                UrlEncoded.decodeTo(query,parameters,request.getCharacterEncoding());
                
                if (old_params!=null && old_params.size()>0)
                {
                    // Merge parameters.
                    Iterator iter = old_params.entrySet().iterator();
                    while (iter.hasNext())
                    {
                        Map.Entry entry = (Map.Entry)iter.next();
                        String name=(String)entry.getKey();
                        Object values=entry.getValue();
                        for (int i=0;i<LazyList.size(values);i++)
                            parameters.add(name, LazyList.get(values, i));
                    }
                    query=UrlEncoded.encode(parameters,null,false);
                }
                base_request.setParameters(parameters);
                base_request.setQueryString(query);
            }
            
            ForwardAttributes attr = new ForwardAttributes(old_attr); 
            
            attr._requestURI=base_request.getRequestURI();
            attr._contextPath=base_request.getContextPath();
            attr._servletPath=base_request.getServletPath();
            attr._pathInfo=base_request.getPathInfo();
            attr._query=base_request.getQueryString();

            base_request.setRequestURI(_uri);
            base_request.setContextPath(_contextHandler.getContextPath());
            base_request.setAttributes(attr);
            base_request.setQueryString(query);
            
            _contextHandler.handle(_path, (HttpServletRequest)request, (HttpServletResponse)response, dispatch);
        }
        finally
        {
            base_request.setRequestURI(old_uri);
            base_request.setContextPath(old_context_path);
            base_request.setAttributes(old_attr);
            base_request.setParameters(old_params);
            base_request.setQueryString(old_query);
        }
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see javax.servlet.RequestDispatcher#include(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    public void include(ServletRequest request, ServletResponse response) throws ServletException, IOException
    {
        Request base_request=(request instanceof Request)?((Request)request):HttpConnection.getCurrentConnection().getRequest();
        
        Attributes old_attr=base_request.getAttributes();
        try
        {
            // TODO parameters and query
            String query=_query;
            
            IncludeAttributes attr = new IncludeAttributes(old_attr); 
            
            attr._requestURI=_uri;
            attr._contextPath=_contextHandler.getContextPath();
            attr._query=query;
            
            base_request.setAttributes(attr);
            
            _contextHandler.handle(_path, (HttpServletRequest)request, (HttpServletResponse)response, Handler.INCLUDE);
        }
        finally
        {
            base_request.setAttributes(old_attr);
        }
    }


    private class ForwardAttributes implements Attributes
    {
        Attributes _attr;
        
        String _requestURI;
        String _contextPath;
        String _servletPath;
        String _pathInfo;
        String _query;
        
        ForwardAttributes(Attributes attributes)
        {
            _attr=attributes;
        }
        
        /* ------------------------------------------------------------ */
        public Object getAttribute(String key)
        {
            if (Dispatcher.this._named==null)
            {
                if (key.equals(__FORWARD_PATH_INFO))    return _pathInfo;
                if (key.equals(__FORWARD_REQUEST_URI))  return _requestURI;
                if (key.equals(__FORWARD_SERVLET_PATH)) return _servletPath;
                if (key.equals(__FORWARD_CONTEXT_PATH)) return _contextPath;
                if (key.equals(__FORWARD_QUERY_STRING)) return _query;
            }
            
            return _attr.getAttribute(key);
        }
        
        /* ------------------------------------------------------------ */
        public Enumeration getAttributeNames()
        {
            HashSet set=new HashSet();
            Enumeration e=_attr.getAttributeNames();
            while(e.hasMoreElements())
                set.add(e.nextElement());
            
            if (_named==null)
            {
                set.add(__FORWARD_PATH_INFO);
                set.add(__FORWARD_REQUEST_URI);
                set.add(__FORWARD_SERVLET_PATH);
                set.add(__FORWARD_CONTEXT_PATH);
                set.add(__FORWARD_QUERY_STRING);
            }
            
            return Collections.enumeration(set);
        }
        
        /* ------------------------------------------------------------ */
        public void setAttribute(String key, Object value)
        {

            if (_named==null && ((String)key).startsWith("javax.servlet."))
            {
                if (key.equals(__FORWARD_PATH_INFO))         _pathInfo=(String)value;
                else if (key.equals(__FORWARD_REQUEST_URI))  _requestURI=(String)value;
                else if (key.equals(__FORWARD_SERVLET_PATH)) _servletPath=(String)value;
                else if (key.equals(__FORWARD_CONTEXT_PATH)) _contextPath=(String)value;
                else if (key.equals(__FORWARD_QUERY_STRING)) _query=(String)value;
                else if (value==null)
                    _attr.removeAttribute(key);
                else
                    _attr.setAttribute(key,value); // TODO ???
            }
            else if (value==null)
                _attr.removeAttribute(key);
            else
                _attr.setAttribute(key,value);
        }
        
        /* ------------------------------------------------------------ */
        public String toString() 
        {
            return "FORWARD+"+_attr.toString();
        }

        /* ------------------------------------------------------------ */
        public void clearAttributes()
        {
            throw new IllegalStateException();
        }

        /* ------------------------------------------------------------ */
        public void removeAttribute(String name)
        {
            setAttribute(name,null);
        }
    }
    
    private class IncludeAttributes implements Attributes
    {
        Attributes _attr;
        
        String _requestURI;
        String _contextPath;
        String _servletPath;
        String _pathInfo;
        String _query;
        
        IncludeAttributes(Attributes attributes)
        {
            _attr=attributes;
        }
        
        /* ------------------------------------------------------------ */
        public Object getAttribute(String key)
        {
            if (Dispatcher.this._named==null)
            {
                if (key.equals(__INCLUDE_PATH_INFO))    return _pathInfo;
                if (key.equals(__INCLUDE_SERVLET_PATH)) return _servletPath;
                if (key.equals(__INCLUDE_CONTEXT_PATH)) return _contextPath;
                if (key.equals(__INCLUDE_QUERY_STRING)) return _query;
                if (key.equals(__INCLUDE_REQUEST_URI))  return _requestURI;
            }
            else
            {
                if (key.equals(__INCLUDE_PATH_INFO))    return null;
                if (key.equals(__INCLUDE_REQUEST_URI))  return null;
                if (key.equals(__INCLUDE_SERVLET_PATH)) return null;
                if (key.equals(__INCLUDE_CONTEXT_PATH)) return null;
                if (key.equals(__INCLUDE_QUERY_STRING)) return null;
            }

            return _attr.getAttribute(key);
        }
        
        /* ------------------------------------------------------------ */
        public Enumeration getAttributeNames()
        {
            HashSet set=new HashSet();
            Enumeration e=_attr.getAttributeNames();
            while(e.hasMoreElements())
                set.add(e.nextElement());
            
            if (_named==null)
            {
                set.add(__INCLUDE_PATH_INFO);
                set.add(__INCLUDE_REQUEST_URI);
                set.add(__INCLUDE_SERVLET_PATH);
                set.add(__INCLUDE_CONTEXT_PATH);
                set.add(__INCLUDE_QUERY_STRING);
            }
            else
            {
                set.remove(__INCLUDE_PATH_INFO);
                set.remove(__INCLUDE_REQUEST_URI);
                set.remove(__INCLUDE_SERVLET_PATH);
                set.remove(__INCLUDE_CONTEXT_PATH);
                set.remove(__INCLUDE_QUERY_STRING);
            }
            
            return Collections.enumeration(set);
        }
        
        /* ------------------------------------------------------------ */
        public void setAttribute(String key, Object value)
        {

            if (_named==null && ((String)key).startsWith("javax.servlet."))
            {
                if (key.equals(__INCLUDE_PATH_INFO))         _pathInfo=(String)value;
                else if (key.equals(__INCLUDE_REQUEST_URI))  _requestURI=(String)value;
                else if (key.equals(__INCLUDE_SERVLET_PATH)) _servletPath=(String)value;
                else if (key.equals(__INCLUDE_CONTEXT_PATH)) _contextPath=(String)value;
                else if (key.equals(__INCLUDE_QUERY_STRING)) _query=(String)value;
                else if (value==null)
                    _attr.removeAttribute(key);
                else
                    _attr.setAttribute(key,value); // TODO ???
            }
            else if (value==null)
                _attr.removeAttribute(key);
            else
                _attr.setAttribute(key,value);
        }
        
        /* ------------------------------------------------------------ */
        public String toString() 
        {
            return "INCLUDE+"+_attr.toString();
        }

        /* ------------------------------------------------------------ */
        public void clearAttributes()
        {
            throw new IllegalStateException();
        }

        /* ------------------------------------------------------------ */
        public void removeAttribute(String name)
        {
            setAttribute(name,null);
        }
    }
};
