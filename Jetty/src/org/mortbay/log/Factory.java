/*
 * Created on Jun 4, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.mortbay.log;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;


/**
 * Commons Logging Factory for org.mortbay.log
 * Returns a static default log, unless an alternate Log implementation has been set as
 * an attribute keyed by the classname or other name of the log.  If the name of the attibute ends 
 * with '.*' it is assumed to be a name prefix match.
 * Attributes with string values are treated as references to other attributes.
 * 
 * If you do not want to use Jetty logging, you can revert to the commons logging log factory
 * by setting the system property:
 *   -Dorg.apache.commons.logging.LogFactory=org.apache.commons.logging.impl.LogFactoryImpl
 */
public class Factory extends LogFactory
{
     static LogImpl log = new LogImpl();
     static HashMap attributes = new HashMap();
     static ArrayList prefixes = new ArrayList();
    
    /**
     * 
     */
    public Factory()
    {
        super();
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#getAttribute(java.lang.String)
     */
    public Object getAttribute(String n)
    {
        return attributes.get(n);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#getAttributeNames()
     */
    public String[] getAttributeNames()
    {
        return (String[]) attributes.keySet().toArray(new String[attributes.size()]);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#getInstance(java.lang.Class)
     */
    public Log getInstance(Class c) throws LogConfigurationException
    {
        if (c!=null)
            return getInstance(c.getName());
        return getInstance((String)null);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#getInstance(java.lang.String)
     */
    public Log getInstance(String name) throws LogConfigurationException
    {
        String match="";
        for (int i=prefixes.size();name!=null && i-->0;)
        {
            String prefix=(String)prefixes.get(i);
            if (name.startsWith(prefix) && prefix.length()>match.length())
                match=prefix;
        }
        if (match.length()>0)
            name=match+".*";
        
        // Get value
        Object o = attributes.get(name);
        
        // Dereference string attributes
        while(o!=null && o instanceof String)
            o=attributes.get(o);
        
        // return actual log.
        if (o instanceof Log)
            return (Log)o;
        return log;
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#release()
     */
    public void release()
    {
        log.reset();
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#removeAttribute(java.lang.String)
     */
    public void removeAttribute(String n)
    {
        attributes.remove(n);
        if (n.endsWith(".*"))
            prefixes.remove(n.substring(0,n.length()-2));
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.LogFactory#setAttribute(java.lang.String, java.lang.Object)
     */
    public void setAttribute(String n, Object v)
    {
        attributes.put(n,v);
        if (n.endsWith(".*") && v instanceof Log)
            prefixes.add(n.substring(0,n.length()-2));
    }

}
