// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package com.mortbay.HTTP.Handler;

import com.mortbay.HTTP.HttpException;
import com.mortbay.HTTP.HttpFields;
import com.mortbay.HTTP.HttpRequest;
import com.mortbay.HTTP.HttpResponse;
import com.mortbay.HTTP.PathMap;
import com.mortbay.HTTP.SecurityConstraint;
import com.mortbay.Util.B64Code;
import com.mortbay.Util.Code;
import com.mortbay.Util.Password;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/* ------------------------------------------------------------ */
/** 
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class SecurityHandler extends NullHandler
{
    public final static String __BASIC_AUTH="BASIC";
    
    PathMap _constraintMap=new PathMap();
    String _authMethod=__BASIC_AUTH;
    Map _authRealmMap;
    String _authRealm ;
    
    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    public String getAuthRealm()
    {
        return _authRealm;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param authRealm 
     */
    public void setAuthRealm(String authRealm)
    {
        _authRealm = authRealm;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    public String getAuthMethod()
    {
        return _authMethod;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param authRealm 
     */
    public void setAuthMethod(String method)
    {
        if (!__BASIC_AUTH.equals(method))
            throw new IllegalArgumentException("Not supported: "+method);
        _authMethod = method;
    }

    /* ------------------------------------------------------------ */
    public void addSecurityConstraint(String pathSpec,
                                      SecurityConstraint sc)
    {
        List scs = (List)_constraintMap.get(pathSpec);
        if (scs==null)
        {
            scs=new ArrayList(2);
            _constraintMap.put(pathSpec,scs);
        }
        scs.add(sc);
        
        Code.debug("add",pathSpec," : ",sc);
    }
    
    /* ------------------------------------------------------------ */
    public void handle(String contextPath,
                       String pathInContext,
                       HttpRequest request,
                       HttpResponse response)
        throws HttpException, IOException
    {
        if (!isStarted())
            return;

        // Get all path matches
        List scss =_constraintMap.getMatches(request.getPath());
        if (scss!=null)
        {
            // for each path match 
            for (int m=0;m<scss.size();m++)
            {
                // Get all constraints
                Map.Entry entry=(Map.Entry)scss.get(m);
                List scs = (List)entry.getValue();
                // for each constraint
                for (int c=0;c<scs.size();c++)
                {
                    SecurityConstraint sc=(SecurityConstraint)scs.get(c);

                    // Check the method applies
                    if (!sc.forMethod(request.getMethod()))
                        continue;

                    // Check roles
                    if (sc.isAuthenticated())
                    {
                        if (!authenticated(request,response,sc.roles()))
                            return;
                    }

                    // Check data
                    if (sc.getDataConstraint()!=SecurityConstraint.DC_NONE &&
                        !"https".equals(request.getScheme()))
                    {
                        response.sendError(HttpResponse.__403_Forbidden);
                        return;
                    }
                }
            }
        }
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    private boolean authenticated(HttpRequest request,
                                  HttpResponse response,
                                  Iterator roles)
        throws IOException
    {
        boolean userAuth=false;
        
        if (__BASIC_AUTH.equals(_authMethod))
            userAuth=basicAuthenticated(request,response);
        else
            response.sendError(HttpResponse.__401_Unauthorized);

        if (!userAuth)
            return false;
        
        boolean inRole=false;
        while(roles.hasNext())
        {
            String role=roles.next().toString();
            if (request.isUserInRole(role))
            {
                inRole=true;
                break;
            }
        }

        if (!inRole)
            response.sendError(HttpResponse.__403_Forbidden);
        
        return userAuth && inRole;
    }
    

    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    private boolean basicAuthenticated(HttpRequest request,
                                       HttpResponse response)
        throws IOException
    {
        String credentials =
            request.getField(HttpFields.__Authorization);
        
        if (credentials!=null )
        {
            Code.debug("Credentials: "+credentials);
            credentials =
                credentials.substring(credentials.indexOf(' ')+1);
            credentials = B64Code.decode(credentials,"ISO-8859-1");
            int i = credentials.indexOf(':');
            String user = credentials.substring(0,i);
            String password = credentials.substring(i+1);
            
            request.setAttribute(HttpRequest.__AuthUser,user);
            request.setAttribute(HttpRequest.__AuthType,"BASIC");

            if (_authRealmMap!=null)
            {
                String pw=(String)_authRealmMap.get(user);
                if (pw!=null)
                {
                    Password dpw=new Password(user,pw);
                    if (password.equals(dpw.toString()))
                        return true;
                }
                    
                if (Code.debug())
                    Code.warning("'"+pw+"'!='"+password+"'");
            }
        }
        
        Code.debug("Unauthorized in "+_authRealm);
        response.setField(HttpFields.__ContentType,"text/html");
        response.setStatus(HttpResponse.__401_Unauthorized);
        response.setField(HttpFields.__WwwAuthenticate,
                          "basic realm=\""+_authRealm+'"');
        OutputStream out = response.getOutputStream();
        out.write("<HTML><BODY><H1>Authentication Failed</H1></BODY></HTML>"
                  .getBytes());
        out.flush();
        response.commit();
        request.setHandled(true);
        return false;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @param username 
     * @param password 
     */
    public synchronized void addUser(String username, String password)
    {
        if (_authRealmMap==null)
            _authRealmMap=new HashMap();
        _authRealmMap.put(username,password);
    }
    
}




