// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.j2ee.session;

//----------------------------------------

import java.util.Timer;
import java.util.TimerTask;
import org.apache.log4j.Category;

//----------------------------------------

public abstract class
  AbstractStore
  implements Store
{
  protected final Category _log=Category.getInstance(getClass().getName());

  // distributed scavenging

  /**
   * The period between scavenges
   */
  protected int _scavengerPeriod=60*30;	// 1/2 an hour
  /**
   * The extra time we wait before tidying up a CMPState to ensure
   * that if it loaded locally it will be scavenged locally first...
   */
  protected int _scavengerExtraTime=60*30; // 1/2 an hour
  /**
   * A maxInactiveInterval of -1 means never scavenge. The DB would
   * fill up very quickly - so we can override -1 with a real value
   * here.
   */
  protected int _actualMaxInactiveInterval=60*60*24*28;	// 28 days

  public void setScavengerPeriod(int secs) {_scavengerPeriod=secs;}
  public void setScavengerExtraTime(int secs) {_scavengerExtraTime=secs;}
  public void setActualMaxInactiveInterval(int secs) {_actualMaxInactiveInterval=secs;}

  protected static Timer _scavenger;
  protected static int   _scavengerCount=0;

  // Store LifeCycle
  public void
    start()
    throws Exception
    {
      synchronized (getClass())
      {
	if (_scavengerCount++==0)
	{
	  boolean isDaemon=true;
	  _scavenger=new Timer(isDaemon);
	  long delay=_scavengerPeriod+Math.round(Math.random()*_scavengerPeriod);
	  _log.debug("local scavenge delay is: "+delay+" seconds");
	  _scavenger.scheduleAtFixedRate(new Scavenger(), delay*1000, _scavengerPeriod*1000);
	  _log.debug("started local scavenger");
	}
      }
    }

  public void
    stop()
    {
      synchronized (getClass())
      {
	if (--_scavengerCount==0)
	{
	  _scavenger.cancel();
	  _scavenger=null;
	  _log.debug("stopped local scavenger");
	}
      }
    }

  public void
    destroy()
    {
      _guidGenerator=null;
    }

  class Scavenger
    extends TimerTask
  {
    public void
      run()
    {
      try
      {
	scavenge();
      }
      catch (Exception e)
      {
	_log.warn("could not scavenge distributed sessions", e);
      }
    }
  }

  // ID allocation

  protected GUIDGenerator _guidGenerator=new GUIDGenerator();

  public String
    allocateId()
  {
    return _guidGenerator.generateSessionId();
  }

  public void
    deallocateId(String id)
  {
    // these ids are disposable
  }

  // still abstract...

  //   // Store LifeCycle
  //   void destroy();	// corresponds to ctor
  //
  //   boolean isDistributed();
  //
  //   // State LifeCycle
  //   State newState(String id, int maxInactiveInterval) throws Exception;
  //   State loadState(String id) throws Exception;
  //   void  storeState(State state) throws Exception;
  //   void  removeState(State state) throws Exception;
  //
  //   // Store misc
  //   void scavenge() throws Exception;
  //   void passivateSession(StateAdaptor sa);
}
