// ======================================================================
//  Copyright (C) 2002 by Mortbay Consulting Ltd
// $Id$ 
// ======================================================================

package org.mortbay.jaas;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.security.Principal;
import java.security.acl.Group;
import org.mortbay.util.Code;


public class JAASGroup implements Group 
{
    public static final String ROLES = "roles";
    
    private String name = null;
    private HashSet members = null;
    
    
   
    public JAASGroup(String n)
    {
        this.name = n;
        this.members = new HashSet();
    }
   
    /* ------------------------------------------------------------ */
    /**
     *
     * @param param1 <description>
     * @return <description>
     */
    public synchronized boolean addMember(Principal principal)
    {
        return members.add(principal);
    }

    /**
     *
     * @param param1 <description>
     * @return <description>
     */
    public synchronized boolean removeMember(Principal principal)
    {
        return members.remove(principal);
    }

    /**
     *
     * @param param1 <description>
     * @return <description>
     */
    public boolean isMember(Principal principal)
    {
        return members.contains(principal);
    }


    
    /**
     *
     * @return <description>
     */
    public Enumeration members()
    {

        class MembersEnumeration implements Enumeration
        {
            private Iterator itor;
            
            public MembersEnumeration (Iterator itor)
            {
                this.itor = itor;
            }
            
            public boolean hasMoreElements ()
            {
                return this.itor.hasNext();
            }


            public Object nextElement ()
            {
                return this.itor.next();
            }
            
        }

        return new MembersEnumeration (members.iterator());
    }


    /**
     *
     * @return <description>
     */
    public int hashCode()
    {
        return getName().hashCode();
    }


    
    /**
     *
     * @param object <description>
          * @return <description>
     */
    public boolean equals(Object object)
    {
        if (! (object instanceof JAASGroup))
            return false;

        return ((JAASGroup)object).getName().equals(getName());
    }

    /**
     *
     * @return <description>
     */
    public String toString()
    {
        return getName();
    }

    /**
     *
     * @return <description>
     */
    public String getName()
    {
        
        return name;
    }

}