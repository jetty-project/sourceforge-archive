// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.jetty.jmx;

import java.util.Arrays;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import javax.management.loading.MLet;
import javax.management.JMException;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.RequiredModelMBean;
import javax.management.MBeanInfo;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanRegistration;
import javax.management.Descriptor;
import javax.management.NotificationListener;
import javax.management.ListenerNotFoundException;
import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;

import javax.management.modelmbean.DescriptorSupport;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

import org.mortbay.http.HttpServer;
import org.mortbay.util.Code;
import org.mortbay.util.Log;
import org.mortbay.util.Primitive;


/* ------------------------------------------------------------ */
/** Model MBean Implementation.
 * This implementation of the JMX Model MBean API is designed to allow
 * easy creation of Model MBeans. From minimal descriptions of
 * operations and attributes, reflection is used to determine the full
 * signature and ResourceBundles are used to determine other meta data.
 *
 * This class is normally used in one of the following patterns:<UL>
 * <LI>As a base class for a real MBean that contains the actual
 * attributes and operations of the MBean.  Such an Object is only
 * usable in a JMX environment.
 * <LI>As a proxy MBean to another non-JMX object. The attributes and
 * operations of the proxied object are defined in the MBean.  This
 * pattern is used when an existing non-JMX objects API is to be
 * exposed as an MBean.
 * <LI>As a base class for a proxy MBean. The attributes and oepration
 * of the MBean are implemented by the derived class but delegate to
 * one or more other objects. This pattern is used if existing objects
 * are to be managed by JMX, but a new management API needs to be
 * defined.
 * </UL>
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
public class ModelMBeanImpl
    implements ModelMBean,
               MBeanRegistration
{
    public final static int IMPACT_ACTION = MBeanOperationInfo.ACTION;
    public final static int IMPACT_ACTION_INFO = MBeanOperationInfo.ACTION_INFO;
    public final static int IMPACT_INFO = MBeanOperationInfo.INFO;
    public final static int IMPACT_UNKOWN = MBeanOperationInfo.UNKNOWN;

    public final static String STRING="java.lang.String";
    public final static String OBJECT="java.lang.Object";
    public final static String INT="int";
    
    public final static String[] NO_PARAMS=new String[0];
    
    private static HashMap __objectId = new HashMap();

    private static String __jettyDomain="org.mortbay.jetty";
    
    protected ModelMBeanInfoSupport _beanInfo;
    private MBeanServer _mBeanServer;
    private Object _object;
    private ObjectName _objectName;
    
    private boolean _dirty=false;
    private HashMap _getter = new HashMap();
    private HashMap _setter = new HashMap();
    private HashMap _method = new HashMap();
    private ArrayList _attributes = new ArrayList();
    private ArrayList _operations = new ArrayList();
    private ArrayList _notifications = new ArrayList();
    
    /* ------------------------------------------------------------ */
    /** Proxy MBean Constructor. 
     * @param proxyObject The actual object on which attributes and
     * operations are to be defined and called. 
     */
    public ModelMBeanImpl(Object proxyObject)
    {
        try
        {
            setManagedResource(proxyObject,"objectReference");
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }
    }
    
    /* ------------------------------------------------------------ */
    /** MBean Constructor.
     * No proxy object is defined.  Attributes and operations are
     * defined on this instance. 
     */
    public ModelMBeanImpl()
    {
        try
        {
            setManagedResource(this,"objectReference");
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }
    }
    
    /* ------------------------------------------------------------ */
    public static String getJettyDomain()    { return __jettyDomain; }
    
    /* ------------------------------------------------------------ */
    public static void setJettyDomain(String d)   { __jettyDomain=d; }
    
    /* ------------------------------------------------------------ */
    public MBeanServer getMBeanServer()       { return _mBeanServer; }

    /* ------------------------------------------------------------ */
    public ObjectName getObjectName()          { return _objectName; }
    
    /* ------------------------------------------------------------ */
    public Object getManagedResource()             { return _object; }
  
    /* ------------------------------------------------------------ */
    public void setManagedResource(Object proxyObject, String type)
        throws MBeanException,
               RuntimeOperationsException,
               InstanceNotFoundException,
               InvalidTargetObjectTypeException
    {
        if (proxyObject==null)
        {
            proxyObject=null;
            return;
        }
        
        Code.debug("setManagedResource");
        if (!"objectreference".equalsIgnoreCase(type))
            throw new InvalidTargetObjectTypeException(type);

        if (_object==null)
        {
            // first set so define attributes etc.
            _object=proxyObject;
            
            defineManagedResource();
        }
        else
            _object=proxyObject;    
    }

    /* ------------------------------------------------------------ */
    /** Define the Managed Resource.
     * This method is called the first time setManagedResource is
     * called with a non-null object. It should be implemented by a
     * derived ModelMBean to define the attributes and operations
     * after an initial object has been set.
     */
    protected void defineManagedResource()
    {}
    
    /* ------------------------------------------------------------ */
    /** Not Supported.
     * Use RequiredModelMBean for this style of MBean creation.
     */
    public void setModelMBeanInfo(ModelMBeanInfo info)
        throws MBeanException,
               RuntimeOperationsException
    {
        throw new Error("setModelMBeanInfo not supported");
    }
    
    /* ------------------------------------------------------------ */
    /** Define an attribute on the managed object.
     * The meta data is defined by looking for standard getter and
     * setter methods. Descriptions are obtained with a call to
     * findDescription with the attribute name.
     * @param name The name of the attribute. Normal java bean
     * capitlization is enforced on this name.
     */
    public synchronized void defineAttribute(String name)
    {
        defineAttribute(name,true);
    }
    
    /* ------------------------------------------------------------ */
    /** Define an attribute on the managed object.
     * The meta data is defined by looking for standard getter and
     * setter methods. Descriptions are obtained with a call to
     * findDescription with the attribute name.
     * @param name The name of the attribute. Normal java bean
     * capitlization is enforced on this name.
     * @param writable If false, do not look for a setter.
     */
    public synchronized void defineAttribute(String name, boolean writable)
    {
        _dirty=true;
        
        String uName=name.substring(0,1).toUpperCase()+name.substring(1);
        name=java.beans.Introspector.decapitalize(name);
        Class oClass=_object.getClass();

        Class type=null;
        Method getter=null;
        Method setter=null;
        Method[] methods=oClass.getMethods();
        for (int m=0;m<methods.length;m++)
        {
            if ((methods[m].getModifiers()&Modifier.PUBLIC)==0)
                continue;

            // Look for a getter
            if (methods[m].getName().equals("get"+uName) &&
                methods[m].getParameterTypes().length==0)
            {
                if (getter!=null)
                    throw new IllegalArgumentException("Multiple getters for attr "+name);
                getter=methods[m];
                if (type!=null &&
                    !type.equals(methods[m].getReturnType()))
                    throw new IllegalArgumentException("Type conflict for attr "+name);
                type=methods[m].getReturnType();
            }

            // Look for an is getter
            if (methods[m].getName().equals("is"+uName) &&
                methods[m].getParameterTypes().length==0)
            {
                if (getter!=null)
                    throw new IllegalArgumentException("Multiple getters for attr "+name);
                getter=methods[m];
                if (type!=null &&
                    !type.equals(methods[m].getReturnType()))
                    throw new IllegalArgumentException("Type conflict for attr "+name);
                type=methods[m].getReturnType();
            }

            // look for a setter
            if (writable &&
                methods[m].getName().equals("set"+uName) &&
                methods[m].getParameterTypes().length==1)
            {
                if (setter!=null)
                    throw new IllegalArgumentException("Multiple setters for attr "+name);
                setter=methods[m];
                if (type!=null &&
                    !type.equals(methods[m].getParameterTypes()[0]))
                    throw new IllegalArgumentException("Type conflict for attr "+name);
                type=methods[m].getParameterTypes()[0];
            }
        }

        if (getter==null && setter==null)
            throw new IllegalArgumentException("No getter or setters found for "+name);
        
        try
        {
            // Remember the methods
            _getter.put(name,getter);
            _setter.put(name,setter);
            // create and add the info
            _attributes.add(new ModelMBeanAttributeInfo(name,
                                                        findDescription(name),
                                                        getter,
                                                        setter));
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }
    }

    /* ------------------------------------------------------------ */
    /** Define an attribute.
     * Explicit definition of an attribute. Reflection is used to
     * locate the actual getter and setter methods.
     * @param attrInfo ModelMBeanAttributeInfo.
     */
    public synchronized void defineAttribute(ModelMBeanAttributeInfo attrInfo)
    {
        _dirty=true;
        
        String name=attrInfo.getName();
        String uName=name.substring(0,1).toUpperCase()+name.substring(1);
        Class oClass=_object.getClass();

        try
        {
            Class type=Primitive.fromName(attrInfo.getType());
            if (type==null)
                type=Thread.currentThread().getContextClassLoader().loadClass(attrInfo.getType());
        
            Method getter=null;
            Method setter=null;
            
            if (attrInfo.isReadable())
                getter=oClass.getMethod((attrInfo.isIs()?"is":"get")+uName,null);
            
            if (attrInfo.isWritable())
                setter=oClass.getMethod("set"+uName,new Class[] {type});
            
            _getter.put(name,getter);
            _setter.put(name,setter);
            _attributes.add(attrInfo);
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Define an operation on the managed object.
     * Defines an operation with no parameters. Refection is used to
     * determine the return type and the description is found with a
     * call to findDescription on "name()".
     * @param name  Name of the method call
     * @param impact Impact as defined in MBeanOperationInfo
     */
    public synchronized void defineOperation(String name,int impact)
    {
        defineOperation(name,null,impact);
    }
    
    /* ------------------------------------------------------------ */
    /** Define an operation on the managed object.
     * Defines an operation with parameters. Refection is used to
     * determine find the method and it's return type. The description
     * of the method is found with a call to findDescription on
     * "name(signature)". The name and description of each parameter
     * is found with a call to findDescription with
     * "name(partialSignature", the returned description is for the
     * last parameter of the partial signature and is assumed to start
     * with the parameter name, followed by a colon.
     * @param name The name of the method call.
     * @param signature The types of the operation parameters.
     * @param impact Impact as defined in MBeanOperationInfo
     */
    public synchronized void defineOperation(String name,
                                             String[] signature,
                                             int impact)
    {
        _dirty=true;        
        Class oClass=_object.getClass();
        if (signature==null) signature=new String[0];

        try
        {
            Class[] types = new Class[signature.length];
            MBeanParameterInfo[] pInfo = new
                MBeanParameterInfo[signature.length];

            // Check types and build methodKey
            String methodKey=name+"(";
            for (int i=0;i<signature.length;i++)
            {
                Class type=Primitive.fromName(signature[i]);
                if (type==null)
                    type=Thread.currentThread().getContextClassLoader().loadClass(signature[i]);
                types[i]=type;
                signature[i]=type.isPrimitive()?Primitive.toName(type):signature[i];
                methodKey+=(i>0?",":"")+signature[i];
            }
            methodKey+=")";

            // Build param infos
            for (int i=0;i<signature.length;i++)
            {
                String description=findDescription(methodKey+"["+i+"]");
                int colon=description.indexOf(":");
                if (colon<0)
                {
                    description="param"+i+":"+description;
                    colon=description.indexOf(":");
                }
                pInfo[i]=new
                    MBeanParameterInfo(description.substring(0,colon).trim(),
                                       signature[i],
                                       description.substring(colon+1).trim());
            }

            // build the operation info
            Method method=oClass.getMethod(name,types);
            Class returnClass=method.getReturnType();
            _method.put(methodKey,method);
            _operations.add(new ModelMBeanOperationInfo
                (name,
                 findDescription(methodKey),
                 pInfo,
                 returnClass.isPrimitive()?Primitive.toName(returnClass):(returnClass.getName()),
                 impact));
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }
        
    }
    
    /* ------------------------------------------------------------ */
    /** Define an operation.
     * Explicit definition of an operation. Reflection is used to
     * locate method called.
     * @param opInfo 
     */
    public synchronized void defineOperation(ModelMBeanOperationInfo opInfo)
    {
        _dirty=true;        
        Class oClass=_object.getClass();
        
        try
        {
            MBeanParameterInfo[] pInfo = opInfo.getSignature();
            
            Class[] types = new Class[pInfo.length];
            String method=opInfo.getName()+"(";
            for (int i=0;i<pInfo.length;i++)
            {
                Class type=Primitive.fromName(pInfo[i].getType());
                if (type==null)
                    type=Thread.currentThread().getContextClassLoader().loadClass(pInfo[i].getType());
                types[i]=type;
                method+=(i>0?",":"")+pInfo[i].getType();
            }
            method+=")";

            _method.put(method,oClass.getMethod(opInfo.getName(),types));
            _operations.add(opInfo);
        }
        catch(Exception e)
        {
            Code.warning(e);
            throw new IllegalArgumentException(e.toString());
        }   
    }
    
    /* ------------------------------------------------------------ */
    public synchronized MBeanInfo getMBeanInfo()
    {
        Code.debug("getMBeanInfo");

        if (_dirty)
        {
            _dirty=false;
            ModelMBeanAttributeInfo[] attributes = (ModelMBeanAttributeInfo[])
                _attributes.toArray(new ModelMBeanAttributeInfo[0]);
            ModelMBeanOperationInfo[] operations = (ModelMBeanOperationInfo[])
                _operations.toArray(new ModelMBeanOperationInfo[0]);
            ModelMBeanNotificationInfo[] notifications =(ModelMBeanNotificationInfo[])
                _notifications.toArray(new ModelMBeanNotificationInfo[0]);

            _beanInfo =
                new ModelMBeanInfoSupport(_object.getClass().getName(),
                                          findDescription(null),
                                          attributes,
                                          null,
                                          operations,
                                          notifications);            
        }
            
        return _beanInfo;
    }
  
    /* ------------------------------------------------------------ */
    public Object getAttribute(String name)
        throws AttributeNotFoundException,
               MBeanException,
               ReflectionException
    {
        Code.debug("getAttribute ",name);
        Method getter = (Method)_getter.get(name);
        if (getter==null)
            throw new AttributeNotFoundException(name);
        try
        {
            return getter.invoke(_object,null);
        }
        catch(IllegalAccessException e)
        {
            Code.warning(e);
            throw new AttributeNotFoundException(e.toString());
        }
        catch(InvocationTargetException e)
        {
            Code.warning(e);
            throw new ReflectionException((Exception)e.getTargetException());
        }
    }
    
    /* ------------------------------------------------------------ */
    public AttributeList getAttributes(String[] names)
    {
        Code.debug("getAttributes");
        AttributeList results=new AttributeList(names.length);
        for (int i=0;i<names.length;i++)
        {
            try
            {
                results.add(new Attribute(names[i],
                                          getAttribute(names[i])));
            }
            catch(Exception e)
            {
                Code.warning(e);
            }
        }
        return results;
    }
    
    
    /* ------------------------------------------------------------ */
    public void setAttribute(Attribute attr)
        throws AttributeNotFoundException,
               InvalidAttributeValueException,
               MBeanException,
               ReflectionException
    {
        Code.debug("setAttribute ",attr.getName(),"=",attr.getValue());
        Method setter = (Method)_setter.get(attr.getName());
        if (setter==null)
            throw new AttributeNotFoundException(attr.getName());
        try
        {
            setter.invoke(_object,new Object[]{attr.getValue()});
        }
        catch(IllegalAccessException e)
        {
            Code.warning(e);
            throw new AttributeNotFoundException(e.toString());
        }
        catch(InvocationTargetException e)
        {
            Code.warning(e);
            throw new ReflectionException((Exception)e.getTargetException());
        }
    }
    
    /* ------------------------------------------------------------ */
    public AttributeList setAttributes(AttributeList attrs)
    {
        Code.debug("setAttributes");

        AttributeList results=new AttributeList(attrs.size());
        Iterator iter = attrs.iterator();
        while(iter.hasNext())
        {
            try
            {
                Attribute attr=(Attribute)iter.next();
                setAttribute(attr);
                results.add(new Attribute(attr.getName(),
                                          getAttribute(attr.getName())));
            }
            catch(Exception e)
            {
                Code.warning(e);
            }
        }
        return results;
    }

    /* ------------------------------------------------------------ */
    public Object invoke(String name, Object[] params, String[] signature)
        throws MBeanException,
               ReflectionException
    {
        Code.debug("invoke ",name);

        String methodKey=name+"(";
        for (int i=0;i<signature.length;i++)
            methodKey+=(i>0?",":"")+signature[i];
        methodKey+=")";

        try
        {
            Method method = (Method)_method.get(methodKey);
            if (method==null)
                throw new NoSuchMethodException(methodKey);

            return method.invoke(_object,params);
        }
        catch(NoSuchMethodException e)
        {
            Code.warning(e);
            throw new ReflectionException(e);
        }
        catch(IllegalAccessException e)
        {
            Code.warning(e);
            throw new MBeanException(e);
        }
        catch(InvocationTargetException e)
        {
            Code.warning(e);
            throw new ReflectionException((Exception)e.getTargetException());
        }
        
    }
    
    /* ------------------------------------------------------------ */
    public void load()
        throws MBeanException,
               RuntimeOperationsException,
               InstanceNotFoundException
    {
        Code.debug("load");
    }
    
    /* ------------------------------------------------------------ */
    public void store()
        throws MBeanException,
               RuntimeOperationsException,
               InstanceNotFoundException
    {
        Code.debug("store");
    }

    /* ------------------------------------------------------------ */
    public void addNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws IllegalArgumentException
    {
        Code.debug("addNotificationListener");
    }
    
    /* ------------------------------------------------------------ */
    public MBeanNotificationInfo[] getNotificationInfo()
    {
        Code.debug("getNotificationInfo");
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException
    {
        Code.debug("removeNotificationListener");
    }

    /* ------------------------------------------------------------ */
    public void addAttributeChangeNotificationListener(NotificationListener listener,
                                               String name,
                                               Object handback)
        throws MBeanException,
               RuntimeOperationsException,
               IllegalArgumentException
    {
        Code.debug("addAttributeChangeNotificationListener");
    }
    
    /* ------------------------------------------------------------ */
    public void removeAttributeChangeNotificationListener(NotificationListener listener,
                                                          String name)
        throws MBeanException,
               RuntimeOperationsException,
               ListenerNotFoundException
    {
        Code.debug("removeAttributeChangeNotificationListener");
    }
    
    /* ------------------------------------------------------------ */
    public void sendAttributeChangeNotification(Attribute oldAttr,
                                                Attribute newAttr)
        throws MBeanException,
               RuntimeOperationsException
    {
        Code.debug("sendAttributeChangeNotification");
    }
    
    /* ------------------------------------------------------------ */
    public void sendAttributeChangeNotification(AttributeChangeNotification notify)
        throws MBeanException,
               RuntimeOperationsException
    {
        Code.debug("sendAttributeChangeNotification");
    }
    
    /* ------------------------------------------------------------ */
    public void sendNotification(String notify)
        throws MBeanException,
               RuntimeOperationsException
    {
        Code.debug("sendNotification");
    }
    
    /* ------------------------------------------------------------ */
    public void sendNotification(Notification notify)
        throws MBeanException,
               RuntimeOperationsException
    {
        Code.debug("sendNotification");
    }

    /* ------------------------------------------------------------ */
    /* Find MBean descriptions.
     * MBean descriptions are searched for in ResourceBundles. Bundles
     * are looked for in a mbean.property files within each package of
     * the MBean class inheritance hierachy.
     * Once a bundle is found, the key is added to object names in the
     * following order: fully qualied managed resource class name, tail
     * managed resource class name, tail mbean class name. The string
     * "MBean" is stripped from the tail of any name.
     * <P>For example, if the class a.b.C is managed by a MBean
     * p.q.RMBean which is derived from p.SMBean, then the seach order
     * for a key x is as follows:<PRE>
     *   bundle: p.q.mbean    name: a.b.C.x
     *   bundle: p.q.mbean    name: C.x
     *   bundle: p.q.mbean    name: R.x
     *   bundle: p.mbean      name: a.b.C.x
     *   bundle: p.mbean      name: C.x
     *   bundle: p.mbean      name: S.x
     * </PRE>
     * <P>The convention used for keys passed to this method are:<PRE>
     *   null or empty         - Object description
     *   xxx                   - Attribute xxx description
     *   xxx()                 - Simple operation xxx description
     *   xxx(type,..)          - Operation xxx with signature desciption
     *   xxx(type,..)[n]       - Param n of operation xxx description
     * </PRE>
     * @param key 
     * @return Description string.
     */
    private String findDescription(String key)
    {
        Class lookIn = this.getClass();

        // Array of possible objectNames
        String[] objectNames=new String[3];
        objectNames[0]=_object.getClass().getName();
        if (objectNames[0].indexOf(".")>=0)
            objectNames[1]=objectNames[0].substring(objectNames[0].lastIndexOf(".")+1);

        while(lookIn!=null)
        {
            String pkg=lookIn.getName();
            int lastDot= pkg.lastIndexOf(".");
            if (lastDot>0)
            {
                objectNames[2]=pkg.substring(lastDot+1);
                pkg=pkg.substring(0,lastDot);
            }
            else
            {
                objectNames[2]=pkg;
                pkg=null;
            }

            String resource=(pkg==null?"mbean":(pkg.replace('.','/')+"/mbean"));
            Code.debug("Look for: ",resource);

            try
            {
                ResourceBundle bundle=
                    ResourceBundle.getBundle(resource,
                                             Locale.getDefault(),
                                             _object.getClass().getClassLoader());
            
                Code.debug("Bundle ",resource);
                
                for (int i=0;i<objectNames.length;i++)
                {
                    String name=objectNames[i];
                    
                    if (name==null)
                        continue;
                    if (name.endsWith("MBean"))
                        name=name.substring(0,name.length()-5);
                    if (key!=null && key.length()>0)
                        name+="."+key;

                    try{
                        String description=bundle.getString(name);
                        if (description!=null && description.length()>0)
                            return description;
                    }
                    catch(Exception e) { Code.ignore(e); }
                }
            }
            catch(Exception e) { Code.ignore(e); }

            lookIn=lookIn.getSuperclass();
        }

        if (key==null || key.length()==0)
            return objectNames[0];
        
        return key;
    }

    
    /* ------------------------------------------------------------ */
    /** Create a new ObjectName.
     * Return a new object name. The default implementation is
     * to return the results of defaultDomain+":" passed
     * to uniqueObjectName method.
     * @return 
     */
    protected String newObjectName(MBeanServer server)
    {
        // Create own ObjectName of the form:
        // package:class=id
        return uniqueObjectName(server,getJettyDomain()+":");
    }
    
    /* ------------------------------------------------------------ */
    /** Pre registration notification.
     * If this method is specialized by a derived class that may set
     * the objectName, then it should call this implementation with
     * the new objectName.
     * @param server 
     * @param oName 
     * @return 
     */
    public synchronized ObjectName preRegister(MBeanServer server, ObjectName oName)
    {
        _mBeanServer=server;
        _objectName=oName;
        if (_objectName==null)
        {
            try{oName=new ObjectName(newObjectName(server));}
            catch(Exception e){Code.warning(e);}
        }
        Code.debug("preRegister ",_objectName," -> ",oName);
        _objectName=oName;
        return _objectName;
    }
    

    /* ------------------------------------------------------------ */
    public void postRegister(Boolean ok)
    {
        if (ok.booleanValue())
            Log.event("Registered "+_objectName);
        else
        {
            _mBeanServer=null;
            _objectName=null;
        }
    }

    /* ------------------------------------------------------------ */
    public void preDeregister()
    {
        Log.event("Deregister "+_objectName);
    }
    
    /* ------------------------------------------------------------ */
    /** Post Deregister.
     * This implementation destroys this MBean and it cannot be used again.
     */
    public void postDeregister()
    {
        _beanInfo=null;
        _mBeanServer=null;
        _object=null;
        _objectName=null;
        if (_getter!=null)
            _getter.clear();
        _getter=null;
        if (_setter!=null)
            _setter.clear();
        _setter=null;
        if (_method!=null)
            _method.clear();
        _method=null;
        if (_attributes!=null)
            _attributes.clear();
        _attributes=null;
        if (_operations!=null)
            _operations.clear();
        _operations=null;
        if (_notifications!=null)
            _notifications.clear();
        _notifications=null;
    }

    /* ------------------------------------------------------------ */
    /** Add an id clause to a JMX object name.
     * Used to make unique objectnames when there are no other
     * distinguishing attributes.
     * If the passed object name ends with '=', just a unique ID is
     * added.  Otherwise and classname= clause is added.
     * @param objectName 
     * @return objectName with id= class.
     */
    public synchronized String uniqueObjectName(MBeanServer server,String objectName)
    {        
        if (!objectName.endsWith("="))
        {
            String className = _object.getClass().getName();
            if (className.indexOf(".")>0)
                className=className.substring(className.lastIndexOf(".")+1);
            if (className.endsWith("MBean"))
                className=className.substring(0,className.length()-5);
            if (!objectName.endsWith(":"))
                objectName+=",";
            objectName+=className+"=";
        }
        
        String newName=null;
        while(true)
        {
            Integer id=(Integer)__objectId.get(objectName);
            if (id==null)
                id=new Integer(0);
            newName=objectName+id;
            id=new Integer(id.intValue()+1);
            __objectId.put(objectName,id);
            
            // If no server, this must be unique
            if (server==null)
                break;

            // Otherwise let's check it is unique
            try
            {
                // if not found then it is unique
                if (!server.isRegistered(new ObjectName(newName)))
                    break;
            }
            catch(Exception e)
            {
                Log.warning(e);
                break;
            }
        }
        return newName;
    }
    
}
