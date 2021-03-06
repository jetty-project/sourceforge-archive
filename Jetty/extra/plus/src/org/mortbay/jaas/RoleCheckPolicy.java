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


public interface RoleCheckPolicy 
{
    /* ------------------------------------------------ */
    /** Check if a role is either a runAsRole or in a set of roles
     * @param role the role to check
     * @param runAsRole a pushed role (can be null)
     * @param roles a Group whose Principals are role names
     * @return 
     */
    public boolean checkRole (Principal role, Principal runAsRole, Group roles);
    
}
