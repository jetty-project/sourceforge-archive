/*
 * jBoss, the OpenSource EJB server
 *
 * Distributable under GPL license.
 * See terms of license at gnu.org.
 */

// $Id$

package org.jboss.jetty.security;

import java.io.Serializable;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;

import org.jboss.jetty.JBossWebApplicationContext;
import org.jboss.logging.Logger;
import org.jboss.security.AuthenticationManager;
import org.jboss.security.RealmMapping;
import org.jboss.security.SecurityAssociation;
import org.jboss.security.SimplePrincipal;
import org.jboss.security.SubjectSecurityManager;
import org.mortbay.http.HashSSORealm;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.SSORealm;
import org.mortbay.http.UserRealm;
import org.mortbay.util.Credential;

/** An implementation of UserRealm that integrates with the JBossSX
 * security manager associted with the web application.
 * @author Scott_Stark@displayscape.com
 * @author Cert Auth by pdawes@users.sf.net
 * @author SSO Patch by steve.g@byu.edu
 * @version $Revision$
 */

public class JBossUserRealm
        implements UserRealm, SSORealm		// Jetty API
{
    private final Logger                 _log;
    private final String                 _realmName;
    private final String                 _subjAttrName;
    private final boolean                _useJAAS;
    private final HashMap                _users        =new HashMap();
    private       AuthenticationManager  _authMgr      =null;
    private       RealmMapping           _realmMapping =null;
    private       SubjectSecurityManager _subjSecMgr   =null;
    private       JBossWebApplicationContext _jbossWebAppContext = null;


    /* Since there is a seperate instance of JBossUserRealm per web-app regardless of whether the realm-name is the same,
       this creates an instance of HashSSORealm shared between all JBossUserRealms that have the same realm-name. */
    private final static HashMap _sharedHashSSORealms = new HashMap();
    private String _ssoRealmName=null;
    private HashSSORealm _ssoRealm = null;

    static class JBossUserPrincipal
        implements Principal,Serializable
    {
        protected transient Logger _logRef;
        protected transient RealmMapping _realmMappingRef;
        protected transient SubjectSecurityManager _subjSecMgrRef;
        protected transient JBossUserRealm _realm;

        final SimplePrincipal _principal;	// JBoss API
        private String _password;

        JBossUserPrincipal(String name, Logger _log)
        {
            _principal=new SimplePrincipal(name);
            this._logRef = _log;

            if (_log.isDebugEnabled()) _log.debug("created JBossUserRealm::JBossUserPrincipal: "+name);
        }

        void associateWithRealm(RealmMapping _realmMapping,
                                SubjectSecurityManager _subjSecMgr, JBossUserRealm _realm)
        {
            this._realmMappingRef = _realmMapping;
            this._subjSecMgrRef = _subjSecMgr;
            this._realm = _realm;
        }

        private boolean
            isAuthenticated(String password)
        {
            boolean authenticated = false;

            if (password==null)
                password="";

            char[] passwordChars = password.toCharArray();

            if (_logRef.isDebugEnabled())
                _logRef.debug("authenticating: Name:"+_principal+" Password:****"/*+password*/);

            Subject subjectCopy = new Subject();
            
            if(_subjSecMgrRef!=null &&_subjSecMgrRef.isValid(this._principal, passwordChars, subjectCopy))
                {
                    if (_logRef.isDebugEnabled())
                        _logRef.debug("authenticated: "+_principal);

                    // work around the fact that we are not serialisable - thanks Anatoly
                    //	SecurityAssociation.setPrincipal(this);
                    SecurityAssociation.setPrincipal(_principal);

                    SecurityAssociation.setCredential(passwordChars);
                    SecurityAssociation.setSubject(subjectCopy);
                    authenticated=true;
                }
            else
                {
                    _logRef.warn("authentication failure: "+_principal);
                }

            return authenticated;
        }

        public boolean
            equals(Object o)
        {
            if (o==this)
                return true;

            if (o==null)
                return false;

            if (getClass()!=o.getClass())
                return false;

            String myName  =this.getName();
            String yourName=((JBossUserPrincipal)o).getName();

            if (myName==null && yourName==null)
                return true;

            if (myName!=null && myName.equals(yourName))
                return true;

            return false;
        }

        //----------------------------------------
        // SimplePrincipal - for JBoss

        public String getName()
        {
            //return _principal.getName();
            return _realmMappingRef.getPrincipal(_principal).getName();
        }

        //----------------------------------------
        // UserPrincipal - for Jetty

        public boolean authenticate(String password, HttpRequest request)
        {
            _password=password;
            boolean authenticated=false;
            authenticated=isAuthenticated(_password);

            // This doesn't mean anything to Jetty - but may to some
            // Servlets - confirm later...
            if ( authenticated && _subjSecMgrRef!=null)
                {
                    Subject subject = _subjSecMgrRef.getActiveSubject();
                    if (_logRef.isDebugEnabled())
                        _logRef.debug("setting JAAS subjectAttributeName(j_subject) : "+subject);
                    request.setAttribute("j_subject", subject);
                }

            return authenticated;
        }

        public boolean isAuthenticated()
        {
            return isAuthenticated(_password);
        }

        private UserRealm getUserRealm()
        {
            return _realm;
        }

        public boolean isUserInRole(String role)
        {
            boolean isUserInRole = false;

            Set requiredRoles = Collections.singleton(new SimplePrincipal(role));
            if(_realmMappingRef!=null && _realmMappingRef.doesUserHaveRole(this._principal, requiredRoles))
                {
                    if (_logRef.isDebugEnabled())
                        _logRef.debug("JBossUserPrincipal: "+_principal+" is in Role: "+role);

                    isUserInRole = true;
                }
            else
                {
                    if (_logRef.isDebugEnabled())
                        _logRef.debug("JBossUserPrincipal: "+_principal+" is NOT in Role: "+role);
                }

            return isUserInRole;
        }

        public String
            toString()
        {
            return getName();
        }
    }


    // Represents a user which has been authenticated elsewhere
    //  (e.g. at the fronting server), and thus doesnt have credentials
    static class JBossCertificatePrincipal
        extends JBossUserPrincipal
    {
        private X509Certificate[] _certs;

        JBossCertificatePrincipal(String name, Logger log, X509Certificate[] certs)
        {
            super(name, log);

            _certs = certs;

            if (_logRef.isDebugEnabled())
                _logRef.debug("created JBossUserRealm::JBossCertificatePrincipal: "+name);
        }

        public boolean isAuthenticated()
        {

            // TODO I'm dubious if this is correct???
            _logRef.debug("JBossUserRealm::isAuthenticated called");
            return true;
        }

        public boolean authenticate()
        {
            boolean authenticated = false;

            if (_logRef.isDebugEnabled())
                _logRef.debug("authenticating: Name:"+_principal);

            // Authenticate using the cert as the credential
            Subject subjectCopy = new Subject();
            if(_subjSecMgrRef!=null &&_subjSecMgrRef.isValid(_principal,_certs,subjectCopy))
                {
                    if (_logRef.isDebugEnabled())
                        _logRef.debug("authenticated: "+_principal);

                    SecurityAssociation.setPrincipal(_principal);
                    SecurityAssociation.setCredential(_certs);
                    SecurityAssociation.setSubject(subjectCopy);
                    authenticated=true;
                }
            else
                {
                    _logRef.warn("authentication failure: "+_principal);
                }

            return authenticated;
        }
    }

    public JBossUserRealm(String realmName, String subjAttrName)
    {
        _realmName    = realmName;
        _log          = Logger.getLogger(JBossUserRealm.class.getName() + "#" + _realmName);
        _subjAttrName = subjAttrName;
        _useJAAS      = (_subjAttrName!=null);
    }

    public void init()
    {
        _log.debug("initialising...");
        try
            {
                // can I get away with just doing this lookup once per webapp ?
                InitialContext iniCtx = new InitialContext();
                // do we need the 'java:comp/env' prefix ? TODO
                Context securityCtx  =(Context) iniCtx.lookup("java:comp/env/security");
                _authMgr      =(AuthenticationManager) securityCtx.lookup("securityMgr");
                _realmMapping =(RealmMapping)          securityCtx.lookup("realmMapping");
                iniCtx=null;

                if (_authMgr instanceof SubjectSecurityManager)
                    _subjSecMgr = (SubjectSecurityManager) _authMgr;
            }
        catch (NamingException e)
            {
                _log.error("java:comp/env/security does not appear to be correctly set up", e);
            }
        _log.debug("...initialised");
    }

    // this is going to cause contention - TODO
    private synchronized JBossUserPrincipal ensureUser(String userName)
    {
        JBossUserPrincipal user = (JBossUserPrincipal)_users.get(userName);

        if (user==null)
            {
                user=new JBossUserPrincipal(userName, _log);
                user.associateWithRealm(_realmMapping, _subjSecMgr, this);
                _users.put(userName, user);
            }

        return user;
    }

    public Principal getPrincipal(String username)
    {
        return (Principal)_users.get(username);
    }

    /**
     * @deprecated
     */
    public Principal getUserPrincipal(String username)
    {
        return (Principal)_users.get(username);
    }
    
    
    public Principal authenticate(String userName, Object credential, HttpRequest request)
    {
        if (_log.isDebugEnabled()) _log.debug("JBossUserPrincipal: "+userName);

        // until we get DigestAuthentication sorted JBoss side...
        JBossUserPrincipal user = null;

        if (credential instanceof java.lang.String) // password
            {
                user = ensureUser(userName);
                if (!user.authenticate((String)credential,request)){
                    user = null;
                }
            }
        else if(credential instanceof X509Certificate[]) // certificate
            {
                X509Certificate[] certs = (X509Certificate[])credential;
                user = this.authenticateFromCertificates(certs);
            }

        if (user != null)
            {
                request.setAuthType(javax.servlet.http.HttpServletRequest.CLIENT_CERT_AUTH);
                request.setAuthUser(user.getName());
                request.setUserPrincipal(user);
            }

        return user;
    }

    public boolean reauthenticate(Principal user)
    {
        return ((JBossUserPrincipal)user).isAuthenticated();
    }

    /**
     * @deprecated Use reauthenticate
     */  
    public boolean isAuthenticated(Principal user)
    {
        return ((JBossUserPrincipal)user).isAuthenticated();
    }
    
    public boolean isUserInRole(Principal user, String role)
    {
        return ((JBossUserPrincipal)user).isUserInRole(role);  
    }
    
    
    public JBossUserPrincipal authenticateFromCertificates(X509Certificate[] certs)
    {
        JBossCertificatePrincipal user = (JBossCertificatePrincipal)_users.get(certs[0]);

        if (user == null)
            {
                user = new JBossCertificatePrincipal(getFilterFromCertificate(certs[0]), _log, certs);
                user.associateWithRealm(_realmMapping, _subjSecMgr, this);
                _users.put(certs[0], user);
            }

        if (user.authenticate())
            {
                _log.debug("authenticateFromCertificates - authenticated");
                return user;
            }

        _log.debug("authenticateFromCertificates - returning NULL");
        return null;
    }

    /**
     * Takes an X509Certificate object and extracts the certificate's
     * serial number and issuer in order to construct a unique string
     * representing that certificate.
     *
     * @param cert the user's certificate.
     * @return an LDAP filter for retrieving the user's entry.
     */
    private String getFilterFromCertificate(X509Certificate cert)
    {
        //StringBuffer    buff = new StringBuffer("certSerialAndIssuer=");
        StringBuffer    buff = new StringBuffer();
        String          serialNumber = cert.getSerialNumber().toString(16).toUpperCase();

        if (serialNumber.length() % 2 != 0)
            buff.append("0");

        buff.append(serialNumber);
        buff.append(" ");
        buff.append(cert.getIssuerDN().toString());
        String  filter = buff.toString();
        return filter;
    }

    public void disassociate(Principal user)
    {
        SecurityAssociation.clear();
    }

    public Principal pushRole(Principal user, String role)
    {
        // Not implemented.
        // need to return a new user with the role added.
        return user;
    }

    public Principal popRole(Principal user)
    {
        // Not implemented
        // need to return the original user with any new role associations
        // removed from this thread.
        return user;
    }
    
    public void logout(Principal user)
    {
    	// yukky hack to try and force JBoss to actually
    	// flush the user from the jaas security manager's cache therefore
    	// forcing logincontext.logout() to be called
    	
    	_log.info ("logout for "+user.getName());
    	
    	try
		{  	
    		Principal pUser = user;
    		if (user instanceof JBossUserPrincipal)
    			pUser = ((JBossUserPrincipal)user)._principal;
    		  		
    		java.util.ArrayList servers = MBeanServerFactory.findMBeanServer(null);
    		if (servers.size() != 1) 
    			_log.warn ("More than one MBeanServer found, choosing first");
    		MBeanServer server = (MBeanServer) servers.get(0);   	 
    		
    		server.invoke(new ObjectName("jboss.security:service=JaasSecurityManager"), 
    				"flushAuthenticationCache", 
    				new Object[]{getName(), pUser}, 
					new String[]{"java.lang.String", "java.security.Principal"});
		}
    	catch (Exception e)
		{
    		_log.error (e);
		}
    	catch (Error err)
		{
    		_log.error(err);
    	}
    }

    /**
     * @param name The name of a Single Sign On realm.  Realms that share a sso realm will
     * share authentication for users. Null if no SSO realm.
     */
    public void setSSORealmName(String name)
    {
        _ssoRealmName=name;
        _ssoRealm=null;
    }

    /**
     * @return The name of a Single Sign On realm.  Realms that share a sso realm will
     * share authentication for users. Null if no SSO realm.
     */
    public String getSSORealmName()
    {
        return _ssoRealmName;
    }


    public Credential getSingleSignOn(HttpRequest request,
                                      HttpResponse response) 
    {
        if (!isSSORealm())
            return null;
        Credential singleSignOnCredential = _ssoRealm.getSingleSignOn(request, response);
        if (_log.isDebugEnabled())
            _log.debug("getSingleSignOn principal="+request.getUserPrincipal()+
                       " credential="+singleSignOnCredential);
        return singleSignOnCredential;

    }

    public void setSingleSignOn(HttpRequest request,
                                HttpResponse response,
                                Principal principal,
                                Credential credential) 
    {
        if (!isSSORealm())
            return;
        if (_log.isDebugEnabled())
            _log.debug("setSingleSignOn called. principal="+principal+
                       " credential="+credential);
        _ssoRealm.setSingleSignOn(request, response, principal, credential);
    }

    public void clearSingleSignOn(String username) 
    {
        if (!isSSORealm())
            return;

        if (_log.isDebugEnabled())
            _log.debug("clearSingleSignOn called. username="+username);
        _ssoRealm.clearSingleSignOn(username);
        SecurityAssociation.setPrincipal(null);
        SecurityAssociation.setCredential(null);
    }

    private boolean isSSORealm() 
    {
        if (_ssoRealm==null && _ssoRealmName!=null)
        {
            synchronized(_sharedHashSSORealms)
            {
                _ssoRealm = (HashSSORealm) 
                    _sharedHashSSORealms.get(_ssoRealmName);
                if ( _ssoRealm == null ) 
                {
                    _log.debug("created SSORealm for "+_ssoRealmName);
                    _ssoRealm = new HashSSORealm();
                    _sharedHashSSORealms.put(_ssoRealmName, _ssoRealm);
                }
            }
        }
        return _ssoRealm!=null;
    }
    
    public String
        getName()
    {
        return _realmName;
    }
}
