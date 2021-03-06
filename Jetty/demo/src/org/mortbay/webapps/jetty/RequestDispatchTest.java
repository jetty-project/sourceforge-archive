// ========================================================================
// $Id$
// Copyright 1996-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.webapps.jetty;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.util.StringUtil;

/* ------------------------------------------------------------ */
/** Test Servlet RequestDispatcher.
 * 
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class RequestDispatchTest extends HttpServlet
{
    private static Log log= LogFactory.getLog(RequestDispatchTest.class);

    /* ------------------------------------------------------------ */
    String pageType;

    /* ------------------------------------------------------------ */
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
    }

    /* ------------------------------------------------------------ */
    public void doPost(HttpServletRequest sreq, HttpServletResponse sres) throws ServletException, IOException
    {
        doGet(sreq, sres);
    }

    /* ------------------------------------------------------------ */
    public void doGet(HttpServletRequest sreq, HttpServletResponse sres) throws ServletException, IOException
    {
        if (sreq.getParameter("wrap") != null)
        {
            sreq= new HttpServletRequestWrapper(sreq);
            sres= new HttpServletResponseWrapper(sres);
        }

        String prefix=
            sreq.getContextPath() != null ? sreq.getContextPath() + sreq.getServletPath() : sreq.getServletPath();

        String info;

        if (sreq.getAttribute("javax.servlet.include.servlet_path") != null)
            info= (String)sreq.getAttribute("javax.servlet.include.path_info");
        else
            info= sreq.getPathInfo();

        if (info == null)
            info= "NULL";

        if (info.startsWith("/include/"))
        {
            sres.setContentType("text/html");
            info= info.substring(8);
            if (info.indexOf('?') < 0)
                info += "?Dispatch=include";
            else
                info += "&Dispatch=include";

            if (System.currentTimeMillis() % 2 == 0)
            {
                PrintWriter pout= null;
                pout= sres.getWriter();
                pout.write("<H1>Include: " + info + "</H1><HR>");

                RequestDispatcher dispatch= getServletContext().getRequestDispatcher(info);
                if (dispatch == null)
                {
                    pout= sres.getWriter();
                    pout.write("<H1>Null dispatcher</H1>");
                }
                else
                    dispatch.include(sreq, sres);

                pout.write("<HR><H1>-- Included (writer)</H1>");
            }
            else
            {
                OutputStream out= null;
                out= sres.getOutputStream();
                out.write(("<H1>Include: " + info + "</H1><HR>").getBytes(StringUtil.__ISO_8859_1));

                RequestDispatcher dispatch= getServletContext().getRequestDispatcher(info);
                if (dispatch == null)
                {
                    out= sres.getOutputStream();
                    out.write("<H1>Null dispatcher</H1>".getBytes(StringUtil.__ISO_8859_1));
                }
                else
                    dispatch.include(sreq, sres);

                out.write("<HR><H1>-- Included (outputstream)</H1>".getBytes(StringUtil.__ISO_8859_1));
            }
        }
        else if (info.startsWith("/forward/"))
        {
            info= info.substring(8);
            if (info.indexOf('?') < 0)
                info += "?Dispatch=forward";
            else
                info += "&Dispatch=forward";
            RequestDispatcher dispatch= getServletContext().getRequestDispatcher(info);
            if (dispatch != null)
                dispatch.forward(sreq, sres);
            else
            {
                sres.setContentType("text/html");
                PrintWriter pout= sres.getWriter();
                pout.write("<H1>No dispatcher for: " + info + "</H1><HR>");
                pout.flush();
            }
        }
        else if (info.startsWith("/forwardC/"))
        {
            info= info.substring(9);
            if (info.indexOf('?') < 0)
                info += "?Dispatch=forward";
            else
                info += "&Dispatch=forward";
            
            String cpath= info.substring(0, info.indexOf('/', 1));
            info= info.substring(cpath.length());
            
            ServletContext context= getServletContext().getContext(cpath);
            RequestDispatcher dispatch= context.getRequestDispatcher(info);
            
            if (dispatch != null)
            {
                dispatch.forward(sreq, sres);
            }
            else
            {
                sres.setContentType("text/html");
                PrintWriter pout= sres.getWriter();
                pout.write("<H1>No dispatcher for: " + cpath + "/" + info + "</H1><HR>");
                pout.flush();
            }
        }
        else if (info.startsWith("/forwardSC/"))
        {
            sreq.getSession(true);
            info= info.substring(10);
            if (info.indexOf('?') < 0)
                info += "?Dispatch=forward";
            else
                info += "&Dispatch=forward";
            
            String cpath= info.substring(0, info.indexOf('/', 1));
            info= info.substring(cpath.length());
            
            ServletContext context= getServletContext().getContext(cpath);
            RequestDispatcher dispatch= context.getRequestDispatcher(info);
            
            if (dispatch != null)
            {
                dispatch.forward(sreq, sres);
            }
            else
            {
                sres.setContentType("text/html");
                PrintWriter pout= sres.getWriter();
                pout.write("<H1>No dispatcher for: " + cpath + "/" + info + "</H1><HR>");
                pout.flush();
            }
        }
        else if (info.startsWith("/includeN/"))
        {
            sres.setContentType("text/html");
            info= info.substring(10);
            if (info.indexOf("/") >= 0)
                info= info.substring(0, info.indexOf("/"));
            
            PrintWriter pout;
            if (info.startsWith("/null"))
                info= info.substring(5);
            else
            {
                pout= sres.getWriter();
                pout.write("<H1>Include named: " + info + "</H1><HR>");
            }
            
            RequestDispatcher dispatch= getServletContext().getNamedDispatcher(info);
            if (dispatch != null)
                dispatch.include(sreq, sres);
            else
            {
                pout= sres.getWriter();
                pout.write("<H1>No servlet named: " + info + "</H1>");
            }
            
            pout= sres.getWriter();
            pout.write("<HR><H1>Included ");
        }
        else if (info.startsWith("/forwardN/"))
        {
            info= info.substring(10);
            if (info.indexOf("/") >= 0)
                info= info.substring(0, info.indexOf("/"));
            RequestDispatcher dispatch= getServletContext().getNamedDispatcher(info);
            if (dispatch != null)
                dispatch.forward(sreq, sres);
            else
            {
                sres.setContentType("text/html");
                PrintWriter pout= sres.getWriter();
                pout.write("<H1>No servlet named: " + info + "</H1>");
                pout.flush();
            }
        }
        else
        {
            sres.setContentType("text/html");
            PrintWriter pout= sres.getWriter();
            pout.write(
                    "<H1>Dispatch URL must be of the form: </H1>"
                    + "<PRE>"
                    + prefix
                    + "/include/path\n"
                    + prefix
                    + "/forward/path\n"
                    + prefix
                    + "/includeN/name\n"
                    + prefix
                    + "/forwardC/_context/path\n"
                    + prefix
                    + "/forwardSC/_context/path</PRE>");
            pout.flush();
        }
    }

    /* ------------------------------------------------------------ */
    public String getServletInfo()
    {
        return "Include Servlet";
    }

    /* ------------------------------------------------------------ */
    public synchronized void destroy()
    {
        log.debug("Destroyed");
    }

}
