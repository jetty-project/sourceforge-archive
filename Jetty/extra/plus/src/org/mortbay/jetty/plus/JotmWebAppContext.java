package org.mortbay.jetty.plus;

import java.io.IOException;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.jetty.servlet.WebApplicationContext;
import org.mortbay.xml.XmlParser;
import org.mortbay.util.Code;
import org.mortbay.util.Log;
import org.mortbay.util.TypeUtil;

/* ------------------------------------------------------------ */
public class JotmWebAppContext extends WebApplicationContext
{
//    MemoryContext _root;
    Context _rootComp;
    Context _rootCompEnv;
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @exception IOException 
     */
    public JotmWebAppContext(
    )
    {
       super();
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param webApp The Web application directory or WAR file.
     * @exception IOException 
     */
    public JotmWebAppContext(
       String webApp
    )
    {
        super(webApp);
    }

    /* ------------------------------------------------------------ */
    public void start()
        throws Exception
    {
        // Rebind the global UserTransaction for this object
        bindToGlobal("javax.transaction.UserTransaction", "java:comp/UserTransaction");

        super.start();
    }
        
    /* ------------------------------------------------------------ */
    protected void initWebXmlElement(String element, XmlParser.Node node)
        throws Exception
    {
        // this is ugly - should be dispatched through a hash-table or introspection...

        // these are handled by AbstractWebContainer
        if ("env-entry".equals(element))
        {
            String name=node.getString("env-entry-name",false,true);
            Object value= TypeUtil.valueOf(node.getString("env-entry-type",false,true),
                                           node.getString("env-entry-value",false,true));

            bind("java:comp/env/" + name, value);
        }
        else if ("resource-ref".equals(element))
        {
            // Lookup the name in the global environment, if found
            // bind it to the local context
            String name=node.getString("res-ref-name",false,true);

            bindToGlobal(name, "java:comp/env/" + name);
        }
        else if ("resource-env-ref".equals(element))
        {
            // Lookup the name in the global environment, if found
            // bind it to the local context
            String name=node.getString("resource-env-ref-name",false,true);

            bindToGlobal(name, "java:comp/env/" + name);
        }
        else if ("ejb-ref".equals(element) ||
                 "ejb-local-ref".equals(element) ||
                 "security-domain".equals(element))
        {
           Code.warning("Entry " + element+" => "+node+" is not supported yet");
        }
        else
        {
            super.initWebXmlElement(element, node);
        }
    }

    
    /* ------------------------------------------------------------ */
    public boolean handle(HttpRequest request,
                          HttpResponse response)
        throws HttpException, IOException
    {
        return super.handle(request,response);
    }    


    /* ------------------------------------------------------------ */
    private void bind(String name, Object value)
        throws NamingException
    {
        // Now add the data source to the JNDI tree so we can retrieve it 
        // in contexts
        Context ctx = null;

        try 
        {
            ctx = new InitialContext();
        } 
        catch (NamingException e) 
        {
            Code.warning(e);
            throw e;
        }

        Code.debug("bind in ",ctx," ",name,"=",value);
        ctx.rebind(name,value);
        Code.debug("Entry bound in JNDI with name " + name);
    }
    
    /* ------------------------------------------------------------ */
    private void bindToGlobal(String strGlobalName, String name)
        throws NamingException
    {
        // Now add the data source to the JNDI tree so we can retrieve it 
        // in contexts
        Context ctx = null;

        try 
        {
            ctx = new InitialContext();
        } 
        catch (NamingException e) 
        {
            Code.warning(e);
            throw e;
        }

        Object objValue;

        Code.debug("Searching for global entry in JNDI with name " + strGlobalName);
        objValue = ctx.lookup(strGlobalName);
        Code.debug("Found global entry in JNDI with name " + strGlobalName);

        Code.debug("bind in ",ctx," ",name,"=",objValue);
        ctx.rebind(name,objValue);
        Code.debug("Entry bound in JNDI with name " + name);
    }
}