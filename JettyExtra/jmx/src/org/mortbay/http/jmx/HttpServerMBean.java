// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.http.jmx;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.MBeanOperationInfo;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.ModelMBean;

import org.mortbay.http.HttpServer;
import org.mortbay.http.HttpServer.ComponentEvent;
import org.mortbay.http.HttpServer.ComponentEventListener;
import org.mortbay.http.HttpListener;
import org.mortbay.http.SocketListener;
import org.mortbay.http.HttpContext;
import org.mortbay.util.Code;
import org.mortbay.util.Log;
import org.mortbay.util.LogSink;
import org.mortbay.util.LifeCycle;
import org.mortbay.util.OutputStreamLogSink;
import org.mortbay.util.jmx.LifeCycleMBean;
import org.mortbay.util.jmx.ModelMBeanImpl;

import java.util.Iterator;
import java.util.HashMap;

import java.io.IOException;

/* ------------------------------------------------------------ */
/** HttpServer MBean.
 * This Model MBean class provides the mapping for HttpServer
 * management methods. It also registers itself as a membership
 * listener of the HttpServer, so it can create and destroy MBean
 * wrappers for listeners and contexts.
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
public class HttpServerMBean extends LifeCycleMBean
    implements HttpServer.ComponentEventListener
{
    private HttpServer _httpServer;
    private HashMap _mbeanMap = new HashMap();
    private String _configuration;

    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @exception MBeanException 
     * @exception InstanceNotFoundException 
     */
    protected HttpServerMBean(HttpServer httpServer)
        throws MBeanException, InstanceNotFoundException
    {
        _httpServer=httpServer;
        _httpServer.addEventListener(this);
        try{super.setManagedResource(_httpServer,"objectReference");}
        catch(InvalidTargetObjectTypeException e){Code.warning(e);}
    }

    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @exception MBeanException 
     * @exception InstanceNotFoundException 
     */
    public HttpServerMBean()
        throws MBeanException, InstanceNotFoundException
    {
        this(new HttpServer());
    }
    
    /* ------------------------------------------------------------ */
    public void setManagedResource(Object o,String s)
        throws MBeanException, InstanceNotFoundException, InvalidTargetObjectTypeException
    {
        if (o!=null)
            ((HttpServer)o).addEventListener(this);
        super.setManagedResource(o,s);
    }

    /* ------------------------------------------------------------ */
    protected void defineManagedResource()
    {
        super.defineManagedResource();
        
        defineAttribute("requestLog");
        
        defineOperation("addListener",
                        new String[]{"java.lang.String"},
                        IMPACT_ACTION);
        defineOperation("addListener",
                        new String[]{"org.mortbay.http.HttpListener"},
                        IMPACT_ACTION);
        defineOperation("addContext",
                        new String[]{"java.lang.String","java.lang.String"},
                        IMPACT_ACTION);
        defineOperation("addHostAlias",
                        new String[]{"java.lang.String","java.lang.String"},
                        IMPACT_ACTION);
        
        defineAttribute("statsOn");
        defineOperation("statsReset",IMPACT_ACTION);
        defineAttribute("connections");
        defineAttribute("connectionsOpen");
        defineAttribute("connectionsOpenMax");
        defineAttribute("connectionsDurationAve");
        defineAttribute("connectionsDurationMax");
        defineAttribute("connectionsRequestsAve");
        defineAttribute("connectionsRequestsMax");
        defineAttribute("errors");
        defineAttribute("requests");
        defineAttribute("requestsActive");
        defineAttribute("requestsActiveMax");
        defineAttribute("requestsDurationAve");
        defineAttribute("requestsDurationMax");
        defineOperation("destroy",IMPACT_ACTION);
    }
    
    /* ------------------------------------------------------------ */
    public synchronized void addComponent(ComponentEvent event)
    {
        try
        {
            Code.debug("Component added ",event);
            Object o=event.getComponent();
            
            ModelMBean mbean=ModelMBeanImpl.mbeanFor(o);
            if (mbean==null)
                Code.warning("No MBean for "+o);
            else
            {
                if (mbean instanceof ModelMBeanImpl)
                    ((ModelMBeanImpl)mbean).setBaseObjectName(getObjectName().toString());
                ObjectName oName=
                    getMBeanServer().registerMBean(mbean,null).getObjectName();
                Holder holder = new Holder(oName,mbean);
                _mbeanMap.put(o,holder);
            }
        }
        catch(Exception e)
        {
            Code.warning(e);
        }
    }
    
    /* ------------------------------------------------------------ */
    public synchronized void removeComponent(ComponentEvent event)
    {
        Code.debug("Component removed ",event);
        
        try
        {
            Object o=event.getComponent();
            Holder holder=(Holder)_mbeanMap.remove(o);
            if (holder!=null)
                getMBeanServer().unregisterMBean(holder.oName);
            else if (o==_httpServer)
                getMBeanServer().unregisterMBean(this.getObjectName());
        }
        catch(Exception e)
        {
            Code.warning(e);
        }
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param ok 
     */
    public void postRegister(Boolean ok)
    {
        super.postRegister(ok);
    }
    
    /* ------------------------------------------------------------ */
    public void postDeregister()
    {
        _httpServer.removeEventListener(this);
        _httpServer=null;
        if (_mbeanMap!=null)
            _mbeanMap.clear();
        _mbeanMap=null;
        
        super.postDeregister();
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private static class Holder
    {
        Holder(ObjectName oName,Object mbean){ this.oName=oName; this.mbean=mbean;}
        ObjectName oName;
        Object mbean;
    }

    
}
