// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.jetty.jmx;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanOperationInfo;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

import org.mortbay.util.Code;
import org.mortbay.util.Log;
import org.mortbay.util.LogSink;
import org.mortbay.util.LifeCycle;

import java.beans.beancontext.BeanContextMembershipListener;
import java.beans.beancontext.BeanContextMembershipEvent;


/* ------------------------------------------------------------ */
/** 
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
public class LogSinkMBean extends LifeCycleMBean
{
    private LogSink _logSink;

    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @exception MBeanException 
     * @exception InstanceNotFoundException 
     */
    public LogSinkMBean(LogSink sink)
        throws MBeanException, InstanceNotFoundException
    {
        super(sink);
        _logSink=sink;
    }

    /* ------------------------------------------------------------ */
    protected void defineManagedResource()
    {
        super.defineManagedResource();
        defineOperation("log",
                        new String[]{STRING},
                        IMPACT_ACTION);
    }
    
    /* ------------------------------------------------------------ */
    protected String newObjectName(MBeanServer server)
    {
        return uniqueObjectName(server,getJettyDomain()+":name=Log");
    }
    
    /* ------------------------------------------------------------ */
    public void postDeregister()
    {
        _logSink=null;
        super.postDeregister();
    }
}
