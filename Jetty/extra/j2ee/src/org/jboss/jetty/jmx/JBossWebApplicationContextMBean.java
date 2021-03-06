// ========================================================================
// $Id$
// Copyright 1999-2004 Mort Bay Consulting Pty. Ltd.
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

package org.jboss.jetty.jmx;

import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.jboss.jetty.JBossWebApplicationContext;
import org.jboss.jetty.JettyMBean;
import org.mortbay.jetty.servlet.jmx.WebApplicationContextMBean;

/* ------------------------------------------------------------ */
/** JBoss Web Application MBean.
 *
 * @version $Revision$
 * @author Jules Gosnell (jules)
 */
public class
  JBossWebApplicationContextMBean
  extends WebApplicationContextMBean
{
    
    private JBossWebApplicationContext _jbwac = null;
    
  /* ------------------------------------------------------------ */
  /** Constructor.
   * @exception MBeanException
   * @exception InstanceNotFoundException
   */
  public
    JBossWebApplicationContextMBean()
    throws MBeanException
    {
    }

  /* ------------------------------------------------------------ */
  protected void
    defineManagedResource()
    {
      super.defineManagedResource();

      //         defineAttribute("displayName",false);
      //         defineAttribute("defaultsDescriptor",true);
      //         defineAttribute("deploymentDescriptor",false);
      //         defineAttribute("WAR",true);
      //         defineAttribute("extractWAR",true);
    }

   public void setManagedResource(Object proxyObject, String type)
     throws MBeanException,
     RuntimeOperationsException,
     InstanceNotFoundException,
     InvalidTargetObjectTypeException
     {
       super.setManagedResource(proxyObject, type);
       _jbwac=(JBossWebApplicationContext)proxyObject;
       _jbwac.setMBeanPeer(this);
     }

   public ObjectName[]
     getComponentMBeans(Object[] components, Map map)
     {
       return super.getComponentMBeans(components, map);
     }
   
   public Set getJsr77ObjectNames ()
   throws Exception
   {
       String webModuleName = _jbwac.getContextPath();
       ObjectName jettyJsr77Query = new ObjectName (JettyMBean.JBOSS_DOMAIN+":J2EEServer=null,J2EEApplication=null,J2EEWebModule="+(webModuleName.length()==0?"/":webModuleName)+",j2EEType=Servlet,*");
       return getMBeanServer().queryNames (jettyJsr77Query, null);
   }
}
