// ========================================================================
// $Id$
// Copyright 2002-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.j2ee.session;

//----------------------------------------
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.util.Map;

import org.jboss.logging.Logger;
//----------------------------------------

// at a later date, this needs to be able to batch up changes and
// flush to JG on various events e.g. immediately (no batching),
// end-of-request, idle, stop (migration)...

public class PublishingInterceptor
  extends StateInterceptor
{
  protected static final Logger _log=Logger.getLogger(PublishingInterceptor.class);

  protected AbstractReplicatedStore
    getStore()
  {
    AbstractReplicatedStore store=null;
    try
    {
      store=(AbstractReplicatedStore)getManager().getStore();
    }
    catch (Exception e)
    {
      _log.error("could not get AbstractReplicatedStore");
    }

    return store;
  }

  // by using a Proxy, we can avoid having to allocate/deallocate lots
  // of Class[]/Object[]s... Later it could be intelligent and not
  // pass the methodName and Class[] across the wire...
  public class PublishingInvocationHandler
    implements InvocationHandler
  {
    public Object
      invoke(Object proxy, Method method, Object[] args)
      throws RemoteException
    {
      if (!AbstractReplicatedStore.getReplicating())
      {
      	AbstractReplicatedStore store = getStore();
      	if (store != null)
          store.publish(getId(), method, args);
      }

      return null;
    }
  }

  public void
    start()
  {
    _publisher=createProxy();
  }

  public void
    stop()
  {
  	_publisher=null;
  }
  
  public State
    createProxy()
  {
  	AbstractReplicatedStore store=getStore();
  	if (store==null)
  	  return null;
    InvocationHandler handler = new PublishingInvocationHandler();
    return (State) Proxy.newProxyInstance(store.getLoader(), new Class[] { State.class }, handler);
  }

  protected State _publisher;

  //----------------------------------------
  // writers - wrap-publish-n-delegate - these should be moved into a
  // ReplicatingInterceptor...

  public void
    setLastAccessedTime(long time)
    throws RemoteException
  {
    if (_publisher!=null) _publisher.setLastAccessedTime(time);
    super.setLastAccessedTime(time);
  }

  public void
    setMaxInactiveInterval(int interval)
    throws RemoteException
  {
  	if (_publisher!=null) _publisher.setMaxInactiveInterval(interval);
    super.setMaxInactiveInterval(interval);
  }

  public Object
    setAttribute(String name, Object value, boolean returnValue)
    throws RemoteException
  {
  	if (_publisher!=null) _publisher.setAttribute(name, value, returnValue);
    return super.setAttribute(name, value, returnValue);
  }

  public void
    setAttributes(Map attributes)
    throws RemoteException
  {
  	if (_publisher!=null) _publisher.setAttributes(attributes);
    super.setAttributes(attributes);
  }

  public Object
    removeAttribute(String name, boolean returnValue)
    throws RemoteException
  {
  	if (_publisher!=null) _publisher.removeAttribute(name, returnValue);
    return super.removeAttribute(name, returnValue);
  }
}
