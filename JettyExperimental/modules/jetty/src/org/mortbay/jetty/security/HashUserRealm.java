// ========================================================================
// $Id$
// Copyright 1996-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.security;

import java.io.IOException;
import java.io.PrintStream;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.mortbay.jetty.Request;
import org.mortbay.jetty.Response;
import org.mortbay.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* ------------------------------------------------------------ */
/** HashMapped User Realm.
 *
 * An implementation of UserRealm that stores users and roles in-memory in
 * HashMaps.
 * <P>
 * Typically these maps are populated by calling the load() method or passing
 * a properties resource to the constructor. The format of the properties
 * file is: <PRE>
 *  username: password [,rolename ...]
 * </PRE>
 * Passwords may be clear text, obfuscated or checksummed.  The class 
 * com.mortbay.Util.Password should be used to generate obfuscated
 * passwords or password checksums.
 * 
 * If DIGEST Authentication is used, the password must be in a recoverable
 * format, either plain text or OBF:.
 *
 * The HashUserRealm also implements SSORealm but provides no implementation
 * of SSORealm. Instead setSSORealm may be used to provide a delegate
 * SSORealm implementation. 
 *
 * @see Password
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class HashUserRealm implements UserRealm, SSORealm
{
    private static Logger log = LoggerFactory.getLogger(HashUserRealm.class);

    /** HttpContext Attribute to set to activate SSO.
     */
    public static final String __SSO = "org.mortbay.http.SSO";
    
    /* ------------------------------------------------------------ */
    private String _realmName;
    private String _config;
    private HashMap _users=new HashMap();
    protected HashMap _roles=new HashMap(7);
    private SSORealm _ssoRealm;
    
    

    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public HashUserRealm()
    {}
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param name Realm Name
     */
    public HashUserRealm(String name)
    {
        _realmName=name;
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param name Realm name
     * @param config Filename or url of user properties file.
     */
    public HashUserRealm(String name, String config)
        throws IOException
    {
        _realmName=name;
        setConfig(config);
    }
    
    public String getConfig()
    {
        return _config;
    }

    /* ------------------------------------------------------------ */
    /** Load realm users from properties file.
     * The property file maps usernames to password specs followed by
     * an optional comma separated list of role names.
     *
     * @param config Filename or url of user properties file.
     * @exception IOException 
     */
    public void setConfig(String config)
        throws IOException
    {
        _config=config;
        _users.clear();
        _roles.clear();
        
        if(log.isDebugEnabled())log.debug("Load "+this+" from "+config);
        Properties properties = new Properties();
        Resource resource=Resource.newResource(config);
        properties.load(resource.getInputStream());

        Iterator iter = properties.entrySet().iterator();
        while(iter.hasNext())
        {
            Map.Entry entry = (Map.Entry)iter.next();

            String username=entry.getKey().toString().trim();
            String credentials=entry.getValue().toString().trim();
            String roles=null;
            int c=credentials.indexOf(',');
            if (c>0)
            {
                roles=credentials.substring(c+1).trim();
                credentials=credentials.substring(0,c).trim();
            }

            if (username!=null && username.length()>0 &&
                credentials!=null && credentials.length()>0)
            {
                put(username,credentials);
                if(roles!=null && roles.length()>0)
                {
                    StringTokenizer tok = new StringTokenizer(roles,", ");
                    while (tok.hasMoreTokens())
                        addUserToRole(username,tok.nextToken());
                }
            }
        }
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param name The realm name 
     */
    public void setName(String name)
    {
        _realmName=name;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return The realm name. 
     */
    public String getName()
    {
        return _realmName;
    }

    /* ------------------------------------------------------------ */
    public Principal getPrincipal(String username)
    {
        return (Principal)_users.get(username);
    }
    
    /* ------------------------------------------------------------ */
    public Principal authenticate(String username,Object credentials,Request request)
    {
        KnownUser user;
        synchronized (this)
        {
            user = (KnownUser)_users.get(username);
        }
        if (user==null)
            return null;

        if (user.authenticate(credentials))
            return user;
        
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public void disassociate(Principal user)
    {
    }
    
    /* ------------------------------------------------------------ */
    public Principal pushRole(Principal user, String role)
    {
        if (user==null)
            user=new User();
        
        return new WrappedUser(user,role);
    }

    /* ------------------------------------------------------------ */
    public Principal popRole(Principal user)
    {
        WrappedUser wu = (WrappedUser)user;
        return wu.getUserPrincipal();
    }
    
    /* ------------------------------------------------------------ */
    /** Put user into realm.
     * @param name User name
     * @param credentials String password, Password or UserPrinciple
     *                    instance. 
     * @return Old UserPrinciple value or null
     */
    public synchronized Object put(Object name, Object credentials)
    {
        if (credentials instanceof Principal)
            return _users.put(name.toString(),credentials);
        
        if (credentials instanceof Password)
            return _users.put(name,new KnownUser(name.toString(),(Password)credentials));
        if (credentials != null)
            return _users.put(name,new KnownUser(name.toString(),Credential.getCredential(credentials.toString())));
        return null;
    }

    /* ------------------------------------------------------------ */
    /** Add a user to a role.
     * @param userName 
     * @param roleName 
     */
    public synchronized void addUserToRole(String userName, String roleName)
    {
        HashSet userSet = (HashSet)_roles.get(roleName);
        if (userSet==null)
        {
            userSet=new HashSet(11);
            _roles.put(roleName,userSet);
        }
        userSet.add(userName);
    }
    
    /* -------------------------------------------------------- */
    public boolean reauthenticate(Principal user)
    {
        return ((User)user).isAuthenticated();
    }
    
    /* ------------------------------------------------------------ */
    /** Check if a user is in a role.
     * @param user The user, which must be from this realm 
     * @param roleName 
     * @return True if the user can act in the role.
     */
    public synchronized boolean isUserInRole(Principal user, String roleName)
    {
        if (user instanceof WrappedUser)
            return ((WrappedUser)user).isUserInRole(roleName);
         
        if (user==null || ((User)user).getUserRealm()!=this)
            return false;
        
        HashSet userSet = (HashSet)_roles.get(roleName);
        return userSet!=null && userSet.contains(user.getName());
    }

    /* ------------------------------------------------------------ */
    public void logout(Principal user)
    {}
    
    /* ------------------------------------------------------------ */
    public String toString()
    {
        return "Realm["+_realmName+"]=="+_users.keySet();
    }
    
    /* ------------------------------------------------------------ */
    public void dump(PrintStream out)
    {
        out.println(this+":");
        out.println(super.toString());
        out.println(_roles);
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return The SSORealm to delegate single sign on requests to.
     */
    public SSORealm getSSORealm()
    {
        return _ssoRealm;
    }
    
    /* ------------------------------------------------------------ */
    /** Set the SSORealm.
     * A SSORealm implementation may be set to enable support for SSO.
     * @param ssoRealm The SSORealm to delegate single sign on requests to.
     */
    public void setSSORealm(SSORealm ssoRealm)
    {
        _ssoRealm = ssoRealm;
    }
    
    /* ------------------------------------------------------------ */
    public Credential getSingleSignOn(Request request,Response response)
    {
        if (_ssoRealm!=null)
            return _ssoRealm.getSingleSignOn(request,response);
        return null;
    }
    
    /* ------------------------------------------------------------ */
    public void setSingleSignOn(Request request,Response response,Principal principal,Credential credential)
    {
        if (_ssoRealm!=null)
            _ssoRealm.setSingleSignOn(request,response,principal,credential);
    }
    
    /* ------------------------------------------------------------ */
    public void clearSingleSignOn(String username)
    {
        if (_ssoRealm!=null)
            _ssoRealm.clearSingleSignOn(username);
    }
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class User implements Principal
    {
        List roles=null;

        /* ------------------------------------------------------------ */
        private UserRealm getUserRealm()
        {
            return HashUserRealm.this;
        }
        
        public String getName()
        {
            return "Anonymous";
        }
                
        public boolean isAuthenticated()
        {
            return false;
        }
        
        public String toString()
        {
            return getName();
        }        
    }
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class KnownUser extends User
    {
        private String _userName;
        private Credential _cred;
        
        /* -------------------------------------------------------- */
        KnownUser(String name,Credential credential)
        {
            _userName=name;
            _cred=credential;
        }
        
        /* -------------------------------------------------------- */
        boolean authenticate(Object credentials)
        {
            return _cred!=null && _cred.check(credentials);
        }
        
        /* ------------------------------------------------------------ */
        public String getName()
        {
            return _userName;
        }
        
        /* -------------------------------------------------------- */
        public boolean isAuthenticated()
        {
            return true;
        }
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class WrappedUser extends User
    {   
        private Principal user;
        private String role;

        WrappedUser(Principal user, String role)
        {
            this.user=user;
            this.role=role;
        }

        Principal getUserPrincipal()
        {
            return user;    
        }

        public String getName()
        {
            return "role:"+role;
        }
                
        public boolean isAuthenticated()
        {
            return true;
        }
        
        public boolean isUserInRole(String role)
        {
            return this.role.equals(role);
        }
    }
}