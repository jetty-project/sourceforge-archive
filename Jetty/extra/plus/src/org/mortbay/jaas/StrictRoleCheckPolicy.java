// ========================================================================
// $Id$
// Copyright 2003-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jaas;

import java.security.Principal;
import java.security.acl.Group;


/* ---------------------------------------------------- */
/** StrictRoleChecPolicy
 * <p>Enforces that if a runAsRole is present, then the
 * role to check must be the same as that runAsRole and
 * the set of static roles is ignored.
 *
 * <p><h4>Notes</h4>
 * <p>
 *
 * <p><h4>Usage</h4>
 * <pre>
 */
/*
 * </pre>
 *
 * @see
 * @version 1.0 Mon Apr 14 2003
 * @author Jan Bartel (janb)
 */
public class StrictRoleCheckPolicy implements RoleCheckPolicy
{

    public boolean checkRole (Principal role, Principal runAsRole, Group roles)
    {
        //check if this user has had any temporary role pushed onto
        //them. If so, then only check if the user has that role.
        if (runAsRole != null)
        {
            return (role.equals(runAsRole));
        }
        else
        {
            if (roles == null)
                return false;
            
            return roles.isMember (role);
        }
        
    }
    
}
