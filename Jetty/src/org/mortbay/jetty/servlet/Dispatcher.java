// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.jetty.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.mortbay.http.ChunkableOutputStream;
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


/* ------------------------------------------------------------ */
/** Servlet RequestDispatcher.
 * 
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class Dispatcher implements RequestDispatcher
{
    public final static String __REQUEST_URI= "javax.servlet.include.request_uri";
    public final static String __SERVLET_PATH= "javax.servlet.include.servlet_path";
    public final static String __CONTEXT_PATH= "javax.servlet.include.context_path";
    public final static String __QUERY_STRING= "javax.servlet.include.query_string";
    public final static String __PATH_INFO= "javax.servlet.include.path_info";
    
    ServletHandler _servletHandler;
    ServletHolder _holder=null;
    String _pathSpec;
    String _path;
    String _query;
    Resource _resource;
    ResourceHandler _resourceHandler;
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param server 
     * @param URL 
     */
    Dispatcher(ServletHandler servletHandler, String pathInContext, String query)
        throws IllegalStateException
    {
        Code.debug("Dispatcher for ",servletHandler,",",pathInContext,",",query);
        
        _servletHandler=servletHandler;
        _path=Resource.canonicalPath(pathInContext);
        _query=query;

        Map.Entry entry=_servletHandler.getHolderEntry(_path);
        if(entry!=null)
        {
            _pathSpec=(String)entry.getKey();
            _holder = (ServletHolder)entry.getValue();
        }
        else
            _resourceHandler=(ResourceHandler)
                _servletHandler.getHttpContext().getHandler(ResourceHandler.class);
        
        // If no servlet found
        if (_holder==null && _resourceHandler!=null)
        {
            // Look for a static resource
            try{
                Resource resource= _servletHandler.getHttpContext().getBaseResource();
                if (resource!=null)
                    resource = resource.addPath(_path);
                if (resource.exists() && !resource.isDirectory())
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
    Dispatcher(ServletHandler servletHandler, String name)
        throws IllegalStateException
    {
        _servletHandler=servletHandler;
        _holder=_servletHandler.getServletHolder(name);
        if (_holder==null)
            throw new IllegalStateException("No named servlet handler in context");
    }

    
    /* ------------------------------------------------------------ */
    /** 
     * @param request 
     * @param response 
     * @exception ServletException 
     * @exception IOException 
     */
    public void forward(ServletRequest request,
                        ServletResponse response)
        throws ServletException,IOException
    {
        ServletHttpRequest servletHttpRequest=(ServletHttpRequest)request;
        HttpRequest httpRequest=servletHttpRequest.getHttpRequest();
        ServletHttpResponse servletHttpResponse=(ServletHttpResponse)response;
        HttpResponse httpResponse=servletHttpResponse.getHttpResponse();
            
        if (servletHttpRequest.getHttpRequest().isCommitted())
            throw new IllegalStateException("Request is committed");
        servletHttpResponse.resetBuffer();
        servletHttpResponse.setOutputState(-1);
        
        // Remove any evidence of previous include
        httpRequest.removeAttribute(__REQUEST_URI);
        httpRequest.removeAttribute(__SERVLET_PATH);
        httpRequest.removeAttribute(__CONTEXT_PATH);
        httpRequest.removeAttribute(__QUERY_STRING);
        httpRequest.removeAttribute(__PATH_INFO);
        
        // merge query params
        if (_query!=null && _query.length()>0)
        {
            MultiMap parameters=new MultiMap();
            UrlEncoded.decodeTo(_query,parameters);
            servletHttpRequest.pushParameters(parameters);
            
            String oldQ=servletHttpRequest.getQueryString();
            if (oldQ!=null && oldQ.length()>0)
            {
                UrlEncoded encoded = new UrlEncoded(oldQ);
                Iterator iter = parameters.entrySet().iterator();
                while(iter.hasNext())
                {
                    Map.Entry entry = (Map.Entry)iter.next();
                    encoded.put(entry.getKey(),entry.getValue());
                }
                _query=encoded.encode(false);
            }
        }
        
        if (_path==null)
        {
            // go direct to named servlet
            _holder.handle(servletHttpRequest,servletHttpResponse);
        }
        else
        {
            // The path of the new request is the forward path
            // context must be the same, info is recalculate.
            if (_pathSpec!=null)
            {
                Code.debug("Forward request to ",_holder,
                           " at ",_pathSpec);
                servletHttpRequest.setForwardPaths(_servletHandler,
                                               PathMap.pathMatch(_pathSpec,_path),
                                               PathMap.pathInfo(_pathSpec,_path),
                                               _query);
            }
            
            // Forward request
            httpRequest.setAttribute(ServletHandler.__SERVLET_HOLDER,_holder);
            _servletHandler.getHttpContext().handle(0,_path,null,httpRequest,httpResponse);
        }
    }
        
        
    /* ------------------------------------------------------------ */
    /** 
     * @param request 
     * @param response 
     * @exception ServletException 
     * @exception IOException 
     */
    public void include(ServletRequest request,
                        ServletResponse response)
        throws ServletException, IOException     
    {
        ServletHttpRequest servletHttpRequest=(ServletHttpRequest)request;
        HttpRequest httpRequest=servletHttpRequest.getHttpRequest();
        ServletHttpResponse servletHttpResponse=(ServletHttpResponse)response;
        HttpResponse httpResponse=servletHttpResponse.getHttpResponse();
           
        // Need to ensure that there is no change to the
        // response other than write
        boolean old_locked = servletHttpResponse.getLocked();
        servletHttpResponse.setLocked(true);
        int old_output_state = servletHttpResponse.getOutputState();
        servletHttpResponse.setOutputState(0);

        // handle static resource
        if (_resource!=null)
        {
            Code.debug("Include resource ",_resource);
            // just call it with existing request/response
            InputStream in = _resource.getInputStream();
            try
            {
                int len = (int)_resource.length();
                httpResponse.getOutputStream().write(in,len);
                return;
            }
            finally
            {
                try{in.close();}catch(IOException e){Code.ignore(e);}
                servletHttpResponse.setLocked(old_locked);
                servletHttpResponse.setOutputState(old_output_state);
            }
        }
        
        // handle named servlet
        if (_pathSpec==null)
        {
            Code.debug("Include named ",_holder);
            // just call it with existing request/response
            try
            {
                _holder.handle(servletHttpRequest,servletHttpResponse);
                return;
            }
            finally
            {
                servletHttpResponse.setLocked(old_locked);
                servletHttpResponse.setOutputState(old_output_state);
            }
        }
        
        // merge query string
        if (_query!=null && _query.length()>0)
        {
            MultiMap parameters=new MultiMap();
            UrlEncoded.decodeTo(_query,parameters);
            servletHttpRequest.pushParameters(parameters);
        }
        
        // Request has all original path and info etc.
        // New path is in attributes - whose values are
        // saved to handle chains of includes.
        
        // javax.servlet.include.request_uri
        Object old_request_uri =
            request.getAttribute(__REQUEST_URI);
        httpRequest.setAttribute(__REQUEST_URI,
                                 servletHttpRequest.getRequestURI());
        
        // javax.servlet.include.context_path
        Object old_context_path =
            request.getAttribute(__CONTEXT_PATH);
        httpRequest.setAttribute(__CONTEXT_PATH,
                                 servletHttpRequest.getContextPath());
        
        // javax.servlet.include.query_string
        Object old_query_string =
            request.getAttribute(__QUERY_STRING);
        httpRequest.setAttribute(__QUERY_STRING,
                                 _query);
        
        // javax.servlet.include.servlet_path
        Object old_servlet_path =
            request.getAttribute(__SERVLET_PATH);
        
        // javax.servlet.include.path_info
        Object old_path_info =
            request.getAttribute(__PATH_INFO);

        // Try each holder until handled.
        try
        {
            // The path of the new request is the forward path
            // context must be the same, info is recalculate.
            Code.debug("Include request to ",_holder,
                       " at ",_pathSpec);
            httpRequest.setAttribute(__SERVLET_PATH,
                                 PathMap.pathMatch(_pathSpec,_path));
            httpRequest.setAttribute(__PATH_INFO,
                                 PathMap.pathInfo(_pathSpec,_path));
                
            // try service request
            _holder.handle(servletHttpRequest,servletHttpResponse);
        }
        finally
        {
            // revert request back to it's old self.
            servletHttpResponse.setLocked(old_locked);
            servletHttpResponse.setOutputState(old_output_state);
            if (_query!=null && _query.length()>0)
                servletHttpRequest.popParameters();
            httpRequest.setAttribute(__REQUEST_URI,old_request_uri);
            httpRequest.setAttribute(__CONTEXT_PATH,old_context_path);
            httpRequest.setAttribute(__QUERY_STRING,old_query_string);
            httpRequest.setAttribute(__SERVLET_PATH,old_servlet_path);
            httpRequest.setAttribute(__PATH_INFO,old_path_info);
        }
    }
};



