// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.jetty.servlet.jmx;

import java.util.Iterator;
import java.util.List;
import javax.management.MBeanException;
import org.mortbay.jetty.servlet.WebApplicationHandler;
import javax.management.ObjectName;
import java.util.HashMap;
import java.util.Map;


/* ------------------------------------------------------------ */
/** 
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
public class WebApplicationHandlerMBean extends ServletHandlerMBean
{
    /* ------------------------------------------------------------ */
    private WebApplicationHandler _webappHandler;
    private Map _filters = new HashMap();
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @exception MBeanException 
     */
    public WebApplicationHandlerMBean()
        throws MBeanException
    {}
    
    /* ------------------------------------------------------------ */
    protected void defineManagedResource()
    {
        super.defineManagedResource();
        defineAttribute("acceptRanges"); 
        defineAttribute("filters",READ_ONLY,ON_MBEAN);
        _webappHandler=(WebApplicationHandler)getManagedResource();
    }

    /* ------------------------------------------------------------ */
    public ObjectName[] getFilters()
    {
        List l=_webappHandler.getFilters();
        return getComponentMBeans(l.toArray(),_filters);  
    }
}