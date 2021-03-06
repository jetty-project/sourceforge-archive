// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.Servlets;
import com.mortbay.Base.*;
import com.mortbay.Util.*;
import com.mortbay.HTML.*;
import com.mortbay.HTTP.*;
import com.mortbay.HTTP.Handler.*;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * This is an example of a simple Servlet
 */
public class ConfigDump extends HttpServlet
{
    
    /* ------------------------------------------------------------ */
    String pageType;

    /* ------------------------------------------------------------ */
    public void init(ServletConfig config)
         throws ServletException
    {
        super.init(config);
        
        pageType = getInitParameter(Page.PageType);
        if (pageType ==null)
            pageType=Page.getDefaultPageType();
    }

    /* ------------------------------------------------------------ */
    public void service(HttpServletRequest request,
                        HttpServletResponse response) 
        throws ServletException, IOException
    {
        HttpServer server = (HttpServer) getServletContext();
        HttpConfiguration config = server.configuration();

        Page page = Page.getPage(pageType,request,response);

        page.title("Jetty Configuration Dump");

        Table table = new Table(0).newRow().newCell();
        table.width("80%");
        page.nest(table);

        page.add("<P><B>Connections:</B><BR><BLOCKQUOTE>");
        String s = "Listen on ";
        InetAddrPort[] addresses = config.addresses();
        for (int i=0; i<addresses.length; i++)
        {
            page.add(s);
            page.add(addresses[i]);
            s=", ";
        }

        page.add("</BLOCKQUOTE><P><B>RequestHandlers:</B><BR><BLOCKQUOTE>");
        PathMap pm = config.httpHandlersMap();
        Enumeration e = pm.keys();
        while(e.hasMoreElements())
        {
            String path = (String)e.nextElement();
            page.add("Stack at "+path);
            com.mortbay.HTML.List list = new com.mortbay.HTML.List(com.mortbay.HTML.List.Ordered);
            page.add(list);
            HttpHandler[] handlers = (HttpHandler[])pm.get(path);
            for (int h=0;h<handlers.length;h++)
            {
                if (handlers[h]==null)
                    list.add("null");
                else
                    list.add(handlers[h].toString());
            }
        }
        
        page.write(response.getWriter());
    }    
}

