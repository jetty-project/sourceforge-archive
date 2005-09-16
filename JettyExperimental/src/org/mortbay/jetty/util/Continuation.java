//========================================================================
//$Id$
//Copyright 2004 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.jetty.util;


/* ------------------------------------------------------------ */
/** Continuation.
 * 
 * A continuation is a mechanism by which a HTTP Request can be 
 * suspended and retried after a timeout or an asynchronous event
 * has occured.
 * With the appropriate HTTP Connector, this allows threadless waiting
 * for events.
 * 
 * @author gregw
 *
 */
public interface Continuation
{
    
    /* ------------------------------------------------------------ */
    /** Resume.
     * Resume a suspended request.  The passed object will be returned in the getObject method.
     * @param object
     */
    public void resume(Object object);
    
    /* ------------------------------------------------------------ */
    /** Get object.
     * This method will suspend the request for the timeout or until resume is
     * called.
     * @param timeout
     * @return The object passed to resume or null if timeout.
     */
    public Object getObject(long timeout);
    
    /* ------------------------------------------------------------ */
    /**
     * @return True if the continuation has just been created and has not yet suspended the request.
     */
    public boolean isNew();
    
    /* ------------------------------------------------------------ */
    /**
     * @return True if neither resume has been called or the continuation has timed out.
     */
    public boolean isPending();
}