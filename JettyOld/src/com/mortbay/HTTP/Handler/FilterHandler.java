// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP.Handler;
import com.mortbay.Base.*;
import com.mortbay.HTTP.*;
import com.mortbay.Util.PropertyTree;
import java.io.*;
import java.util.*;


/* --------------------------------------------------------------------- */
/** Filter HttpHandler<p>
 * Filter handler can be used to insert filters into the outputStream
 * of the response generated by subsequent handlers.
 * It is intended to be allow modifications of responses such as: <ul>
 * <li>Wrapping another reply in a look and feel.
 * <li>Expanding tags from a file or forward generated response to add
 * more dynamic content (See HtmlFilter).
 * <li>Modify link paths
 * <li>etc.
 * <ul>
 * It is configured with a PathMap of paths to HttpFilter class names
 * or Properties instance containing a similar mapping.
 * <P>
 * If the path matches a request, new filter instances are added to the
 * response. These will only be activated if their content types match
 * that of the response (which may be set later).  If the filter class
 * has a constructor that take a HttpRequest then the request is passed,
 * otherwise the default constructor is used.
 * 
 * @version $Id$
 * @author Greg Wilkins
 */
public class FilterHandler extends NullHandler
{
    /* ----------------------------------------------------------------- */
    static Class[] __requestArg = {com.mortbay.HTTP.HttpRequest.class};
        
    /* ----------------------------------------------------------------- */
    PathMap filterMap = null;
    
    /* ----------------------------------------------------------------- */
    /** Construct basic auth handler.
     * @param properties Passed to setProperties
     */
    public FilterHandler(Properties properties)
        throws IOException
    {
        setProperties(properties);
    }
    
    /* ----------------------------------------------------------------- */
    /** Construct a FilterHandler
     * @param filterMap A PathMap mapping paths to be filtered to either
     * the class  name of a HttpFilter or a Vector of class names.
     * If the a request path maps to 1 or more filters, these are added
     * to the HttpResponse in order.
     */
    public FilterHandler(PathMap filterMap)
    {
        this.filterMap = filterMap;

        // check the filters exists
        try{
            Enumeration k = filterMap.keys();
            while(k.hasMoreElements())
            {
                Object filter = filterMap.get(k.nextElement());
                
                if (filter instanceof Vector)
                {
                    Enumeration f = ((Vector)filter).elements();
                    while (f.hasMoreElements())
                        newFilter(f.nextElement().toString(),null);
                }
                else
                    newFilter(filter.toString(),null);
            }
        }
        catch (Exception e){
            Code.fail("Can't instantiate HttpFilter",e);
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Configure from Properties.
     * Properties are assumed to be in the format of a PropertyTree
     * like:<PRE>
     * name.CLASS : filterClassName
     * name.PATHS : /list/of/paths
     *</PRE>
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
        
        filterMap = new PathMap();

        Enumeration names = tree.getRealNodes();
        while (names.hasMoreElements())
        {
            String filterName = names.nextElement().toString();
            if ("*".equals(filterName))
                continue;
            Code.debug("Configuring filter "+filterName);
            Vector paths = tree.getVector(filterName+".PATHS",",;");
            for (int r=paths.size();r-->0;)
                filterMap.put(paths.elementAt(r),
                              tree.getProperty(filterName+".CLASS"));
        }
    }
    
    /* ----------------------------------------------------------------- */
    /** Handle filters for requests.
     * Add a HttpFilter instance to the response for each matching filter
     */
    public void handle(HttpRequest request,
                       HttpResponse response)
         throws Exception
    {
        Object filter =
            filterMap.getLongestMatch(request.getResourcePath());

        if (filter!=null)
        {    
            if (filter instanceof Vector)
            {
                Enumeration f = ((Vector)filter).elements();
                while (f.hasMoreElements())
                    response.addObserver(newFilter(f.nextElement().toString(),
                                                   request));
            }
            else
                response.addObserver(newFilter(filter.toString(),
                                               request));
        }
    }
    
    /* ----------------------------------------------------------------- */
    HttpFilter newFilter(String className,HttpRequest request)
        throws ClassNotFoundException,
               ClassCastException,
               IllegalAccessException,
               InstantiationException
    {
        Class filterClass = Class.forName(className);
        
        HttpFilter filter = null;
        try
        {
            java.lang.reflect.Constructor c =
                filterClass.getConstructor(__requestArg);
            Object[] args= {request};
            filter=(HttpFilter)c.newInstance(args);
        }
        catch(Exception e)
        {
            Code.debug(e);
            filter=(HttpFilter)filterClass.newInstance();
        }
        
        return filter;
    }
}


