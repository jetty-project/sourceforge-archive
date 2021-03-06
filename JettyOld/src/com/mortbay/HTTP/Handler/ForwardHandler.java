// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP.Handler;
import com.mortbay.Base.*;
import com.mortbay.HTTP.*;
import com.mortbay.Util.PropertyTree;
import com.mortbay.Util.*;
import java.io.*;
import java.net.*;
import java.util.*;


/* --------------------------------------------------------------------- */
/** Forward HttpHandler
 * This handler forwards a request onto another HttpServer.
 * It is configured with a PathMap of paths to URL instances.
 * The request is forwarded to the host and port of the URL with the matching
 * part of the request path translated to the path provided in the URL.
 * Forwarding is different to a redirect as the response is still passed
 * through this server and may be filtered or modified is some other fashion.
 * <p>Proxy HTTP servers are supported.
 * <h3>Notes</h3>
 * ForwardHandler should not be placed after the ParamHandler unless it is
 * known that no form or query content will be forwarded. As ParamHandler
 * moves all form content and cookies to the parameters, these will be
 * duplicated in the query content of the forwarded request.
 * @version $Id$
 * @author Greg Wilkins
 */
public class ForwardHandler extends NullHandler
{
    /* ----------------------------------------------------------------- */
    PathMap forwardMap = null;
    InetAddrPort proxy=null;
    
    /* ----------------------------------------------------------------- */
    /** Construct from properties.
     * @param properties Passed to setProperties
     */
    public ForwardHandler(Properties properties)
        throws IOException
    {
        setProperties(properties);
    }
    
    /* ----------------------------------------------------------------- */
    /** Construct a ForwardHandler
     * @param forwardMap Maps path to a URL
     */
    public ForwardHandler(PathMap forwardMap)
    {
        this.forwardMap = forwardMap;
        try{
            URL url=null;
            Enumeration k = forwardMap.keys();
            while(k.hasMoreElements())
                url = (URL)(forwardMap.get(k.nextElement()));
        }
        catch (Exception e){
            Code.fail("Can't configure ForwardHandler",e);
        }
    }
    
    /* ----------------------------------------------------------------- */
    /** Construct a ForwardHandler that uses a HTTP proxy.
     * @param forwardMap Maps path to a URL
     */
    public ForwardHandler(PathMap forwardMap,
                          InetAddrPort proxy)
    {
        this(forwardMap);       
        this.proxy=proxy;
    }


    /* ------------------------------------------------------------ */
    /** Configure from Properties.
     * Properties are assumed to be in the format of a PropertyTree
     * like:<PRE>
     * ProxyAddrPort        : 0.0.0.0:1234
     * FORWARD.name.PATHS   : /pathSpec;/list%
     * FORWARD.name.URL     : http:/forward/url
     *</PRE>
     * The Proxy address is optional.
     * @param properties Configuration.
     */
    public void setProperties(Properties properties)
        throws IOException
    {
        PropertyTree tree=null;
        if (properties instanceof PropertyTree)
            tree = (PropertyTree)properties;
        else
            tree = new PropertyTree(properties);
        Code.debug(tree);

        String proxyStr = tree.getProperty("ProxyAddrPort");
        if (proxyStr!=null && proxyStr.trim().length()>0)
            proxy=new InetAddrPort(proxyStr);
        
        forwardMap=new PathMap();
        Enumeration names = tree.getTree("FORWARD").getNodes();
        while (names.hasMoreElements())
        {
            String forwardName = names.nextElement().toString();
            Code.debug("Configuring forward "+forwardName);
            PropertyTree forwardTree = tree.getTree("FORWARD."+forwardName);
            URL url = new URL(forwardTree.getProperty("URL"));
            
            Vector paths = forwardTree.getVector("PATHS",",;");
            for (int f=paths.size();f-->0;)
            {
                String path=(String)paths.elementAt(f);
                Code.debug(path,"-->",url);
                forwardMap.put(path,url);
            }
            
        }    
    }
    
    /* ----------------------------------------------------------------- */
    /** Handle forward for requests.
     */
    public void handle(HttpRequest request,
                       HttpResponse response)
         throws IOException
    {
        String requestPath=request.getResourcePath();
        String path = forwardMap.longestMatch(requestPath);
        
        if (path!=null)
        {
            String match=PathMap.match(path,requestPath);
            URL root = (URL)forwardMap.get(path);
            String file = root.getFile();
            String newPath=null;
            URL url=root;
            if (match.startsWith("/"))
            {
                if (match.length()==requestPath.length())
                    newPath=file;
                else
                    newPath=file+requestPath.substring(match.length());
                Code.debug("Replace "+match +
                           " in " + requestPath +
                           " with " + file);
                url = new URL(root,newPath);
            }
 
            if (url != null)
            {
                Code.debug("Forward to "+url);
                Socket socket=null;
            
                if (proxy==null)
                {
                    int port = url.getPort() ;
                    socket= new Socket(url.getHost(),port<0?80:port);
                    if (match.startsWith("/"))
                        request.translateAddress(requestPath,
                                                 url.getFile(),
                                                 true);
                }
                else
                {
                    socket= new Socket(proxy.getInetAddress(),
                                       proxy.getPort());
                    request.translateAddress(requestPath,
                                             url.toString(),
                                             true);
                }

                try{
                    Code.debug("Forward to "+url+
                               "   resourcePath = "+request.getResourcePath());
                    request.setHeader(HttpHeader.Connection,null);
                    request.setHeader("Host",null);
                    request.setVersion(request.HTTP_1_0);
                    
                    request.write(socket.getOutputStream());
                    Code.debug("waiting for forward reply...");
                    response.writeInputStream(socket.getInputStream(),-1,true);
                }
                finally
                {
                    socket.close();
                }
            }
        }
    }    
}



