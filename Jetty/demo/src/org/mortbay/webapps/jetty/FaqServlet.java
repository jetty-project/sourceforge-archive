// ===========================================================================
// Copyright (c) 2003 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.webapps.jetty;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.html.*;

/* ------------------------------------------------------------ */
/** Faq Servlet 
 * 
 * A servlet to render an FAQ. The servlet reads in faq entries
 * in individual files and generates an index page and each faq
 * as a separate page.
 *
 * The servlet is configured by
 * servlet init-params:
 * <pre>
 *   <init-param>
 *       <param-name>srcDir</param-name>
 *	<param-value>faq/src</param-value>
 *   </init-param>
 *   <init-param>
 *       <param-name>refreshMSec</param-name>
 *	<param-value>120000</param-value>
 *   </init-param>
 * </pre> 
 *
 * <b>srcDir</b> is the directory containing the faq files
 * <b>refreshMSec</b> is the interval in milliseconds during
 * which the servlet will not reprocess the faq files and
 * regenrate the pages.
 * 
 */
public class FaqServlet extends HttpServlet
{
    private static Log log = LogFactory.getLog(JettyServlet.class);
    public static final String GENERAL_SECTION_NAME = "General";
   
    private TreeMap sectionMap;
    private String faqSrcDir;
    private long refreshInterval;
    private long timestamp = 0;

    /**
     * FaqEntry
     *
     * A data object representing one faq entry.
     *
     *
     */
    public class FaqEntry
    {
        private String section;
        private String title;
        private String question;
        private StringBuffer body;
        private String fileName;

        public FaqEntry ()
        {
            body = new StringBuffer();
        }
        
        public FaqEntry (String filename)
        {
            this ();
            setFileName (filename);
        }

        public void setFileName (String txt)
        {
            fileName = txt;
        }

        public String getFileName ()
        {
            return fileName;
        }

        public void setSection (String txt)
        {
            section = txt;
        }

        public String getSection ()
        {
            return section;
        }

        public void setTitle (String txt)
        {
            title = txt;
        }

        public String getTitle ()
        {
            return title;
        }

        public void setQuestion (String txt)
        {
            question = txt;
        }
        
        public String getQuestion ()
        {
            return question;
        }

        public void appendBody (String txt)
        {
            body.append(txt);
        }

        public String getBody ()
        {
            return body.toString();
        }

        public String toString ()
        {
            return "SECTION: "+section+" TITLE: "+title+" QUESTION: "+question+" "+getBody();
        }
    }


    /**
     * Initialise the servlet
     *
     */
    public void init ()
        throws ServletException
    {
        faqSrcDir = getInitParameter("srcDir");
        if ((faqSrcDir == null) || (faqSrcDir.trim().equals("")))
            faqSrcDir = "faq";


        try
        {
            String tmp = getInitParameter ("refreshMSec");
            refreshInterval = Long.valueOf (tmp).longValue();
        }
        catch (NumberFormatException e)
        {
            throw new ServletException (e);
        }
    }




    /**
     * handle GET
     *
     * @param request servlet request
     * @param response servlet response
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException
    {
        long now = System.currentTimeMillis();

        if ((timestamp == 0) || ((now - timestamp) >= refreshInterval))
            refreshMap();

       
        String contextPath = request.getContextPath();
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        
        log.info ("contextPath="+contextPath);
        log.info ("servletPath="+servletPath);
        log.info ("pathInfo="+pathInfo);

        Page page = null;

        if (servletPath.equals("/faq") && (request.getQueryString() == null))
        {
            if ((pathInfo == null) 
                || 
                (pathInfo.equals("/")) 
                || 
                (pathInfo.equals("/index.html")))
            {
                page = generateFaqIndex(contextPath, servletPath);
            }
        }
        else
        {
            String section = request.getParameter ("s");
            String title = request.getParameter ("t");
            if ((section == null) || (section.trim().equals("")))
                throw new ServletException ("No section specified");
            if ((title == null) || (title.trim().equals("")))
                throw new ServletException ("No title specified");

            log.info ("section="+section+" title="+title);
            TreeMap entries = (TreeMap)sectionMap.get(section);
            if (entries == null)
                throw new ServletException ("No matching entries for section="+section);
            FaqEntry entry = (FaqEntry)entries.get (title);
            if (entry == null)
                throw new ServletException ("No matching entry for title="+title);

            page = generateFaqEntry (contextPath, servletPath, entry);
        }

        response.setContentType("text/html");
        Writer out = response.getWriter();
        page.write(out);
    }



    /**
     * Return servlet info
     *
     * @return servlet description
     */
    public String getServletInfo()
    {
        return "Faq Servlet";
    }    


    /**
     * Re-process all of the faq files to build
     * in-memory structure
     *
     *
     */
    public void refreshMap ()
        throws IOException
    {
        //time consuming: read all files in the faq directory

        log.info ("Refreshing faq");

        clearFaqMap ();

        Set fileNameSet = getServletConfig().getServletContext().getResourcePaths ("/"+faqSrcDir);
        
        if ( (fileNameSet == null)  || (fileNameSet.size() == 0) )
            throw new IOException ("No files in faq directory");

        Iterator itor = fileNameSet.iterator();
        while (itor.hasNext())
        {
            String fileName = (String)itor.next();
            log.info ("Handling file "+fileName);

            if (fileName.endsWith("FAQ.html"))
            {
                log.info ("Skipping FAQ.html");
                continue;
            }
            
            
            InputStream is = getServletConfig().getServletContext().getResourceAsStream(fileName);
            BufferedReader reader = new BufferedReader (new InputStreamReader(is));
            boolean moreLines = true;
            FaqEntry entry = new FaqEntry();
            while (moreLines)
            {
                String line = reader.readLine();
                if (line == null)
                    moreLines = false;
                else
                {
                    String key;
                    if (line.length() >= 8)
                    {
                        key = line.substring(0,8);
                        if (key.equalsIgnoreCase("SECTION:"))
                        {
                            entry.setSection (line.substring (8).trim());
                            continue;
                        }
                    }
                    
                    if (line.length() >= 6)
                    {
                        key = line.substring (0,6);
                        if (key.equalsIgnoreCase("TITLE:"))
                        {
                            entry.setTitle (line.substring (6).trim());
                            continue;
                        }
                    }

                    if (line.length() >= 9)
                    {
                        key = line.substring (0,9);
                        
                        if (key.equalsIgnoreCase("QUESTION:"))
                        {
                            entry.setQuestion (line.substring(9).trim());
                            continue;
                        }
                    }

                    entry.appendBody (line+"\n");
                }
            }

            //if there is no title, use the filename instead
            if (entry.getTitle() == null)
            {
                int fileSepIndex = fileName.lastIndexOf ("/");
                int dotIndex = fileName.lastIndexOf(".");
                String tmp = (fileSepIndex >=0?fileName.substring(fileSepIndex): fileName);
                tmp = (dotIndex >= 0?tmp.substring(0, dotIndex): tmp);
                entry.setTitle (tmp);
            }

            
            insertFaqEntry (entry);
        }

        timestamp = System.currentTimeMillis();
    }


    /**
     * Empty the faq structure
     */
    private synchronized void clearFaqMap ()
    {
        sectionMap = new TreeMap();
    }


    /**
     * Insert a new faq entry into the in-memory structure
     *
     * The structure is a sorted map keyed by section name,
     * each value of which is a sorted map of faq entries
     * keyed by title.
     *
     * @param entry the faq entry
     */
    private synchronized void insertFaqEntry (FaqEntry entry)
    {
        if (entry == null)
            return;

        //log.info ("inserting faq entry: "+entry);

        String sectionName = entry.getSection();
        if (sectionName == null)
            sectionName = GENERAL_SECTION_NAME;

        TreeMap entryMap = (TreeMap)sectionMap.get(sectionName);
        if (entryMap == null)
        {
            entryMap = new TreeMap();
            sectionMap.put (sectionName, entryMap);
        }
        
        String titleName = entry.getTitle();
        if ((titleName == null) || (titleName.equals("")))
            titleName = String.valueOf (System.currentTimeMillis());

        entryMap.put (titleName, entry);
    }


    /**
     * Generate the index page for the FAQ
     *
     *@param contextPath the servlet context path
     *@param servletPath the servlet path
     */
    private  Page generateFaqIndex (String contextPath, String servletPath)
        throws IOException
    {
                
        JettyPage page = new JettyPage (contextPath, servletPath);
        page.title ("Jetty FAQ");
        page.add(new Heading (1, "Jetty FAQ &nbsp; <IMG SRC=\"/jetty/images/info.gif\" ALIGN=\"MIDDLE\" BORDER=\"0\">"));

        page.add(new Text ("These frequently asked questions have been contributed by Jetty users.  If you have a question and/or answer that is not here, email <A HREF=\"http:///lists.sourceforge.net/lists/listinfo/jetty-discuss/\">Jetty Discuss</A>."));


        Iterator itor = sectionMap.keySet().iterator();
        while (itor.hasNext())
        {
            String section = (String)itor.next();
            page.add(new Heading (1, section));
            
            TreeMap entryMap = (TreeMap)sectionMap.get(section);
            if (entryMap == null)
                continue;
            
            Iterator entries = entryMap.entrySet().iterator();
            while (entries.hasNext())
            {
                FaqEntry entry = (FaqEntry)((Map.Entry)entries.next()).getValue();
                page.add (new Link (contextPath+servletPath+"?s="+section+"&t="+entry.getTitle(), entry.getQuestion()));
                page.add (new Text("<P>"));
            }
            
        }
        return page;
    }


   /**
    * Generate the page for a particular faq entry
    * @param contextPath  the servlet context path
    * @param servletPath the servlet servlet path
    * @param entry the FaqEntry requested
    *
    */
    private Page generateFaqEntry (String contextPath, String servletPath, FaqEntry entry)
    {        
        JettyPage page = new JettyPage (contextPath, servletPath);
        page.title ("Jetty FAQ");
        
        page.add (new Heading (1, entry.getQuestion()));

        page.add (new Text (entry.getBody()));
        page.add (new Text ("<P>"));
        page.add (new Link (contextPath+servletPath+"/index.html","<IMG SRC=\"/jetty/images/info_sm.gif\" BORDER=\"0\"><BR CLEAR=\"bottom\"> JettyFaq"));
        page.add (new Text ("<BR><BR>"));
        
        return page;

    }
}
            
    
