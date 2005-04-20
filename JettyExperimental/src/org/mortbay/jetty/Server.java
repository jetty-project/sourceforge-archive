// ========================================================================
// $Id$
// Copyright 2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.mortbay.thread.AbstractLifeCycle;
import org.mortbay.thread.BoundedThreadPool;
import org.mortbay.thread.ThreadPool;
import org.mortbay.util.MultiException;

public class Server extends AbstractLifeCycle implements Handler, ThreadPool
{
    private ThreadPool _threadPool;
    private Connector[] _connectors;
    private Handler[] _handlers;
    

    /* ------------------------------------------------------------ */
    public Server()
    	throws Exception
    {
    }

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the connectors.
     */
    public Connector[] getConnectors()
    {
        return _connectors;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param connectors The connectors to set.
     */
    public void setConnectors(Connector[] connectors)
    {
        if (_connectors!=null)
        {
            for (int i=0;i<_connectors.length;i++)
            {
                if (_connectors[i].getThreadPool()==this)
                    _connectors[i].setThreadPool(null);
                if (_connectors[i].getHandler()==this)
                    _connectors[i].setHandler(null);
            }
        }
        _connectors = connectors;
        if (_connectors!=null)
        {
            for (int i=0;i<_connectors.length;i++)
            {
                if (_connectors[i].getThreadPool()==null)
                    _connectors[i].setThreadPool(this);
                if (_connectors[i].getHandler()==null)
                    _connectors[i].setHandler(this);
            }
        }
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the handlers.
     */
    public Handler[] getHandlers()
    {
        return _handlers;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param handlers The handlers to set.
     */
    public void setHandlers(Handler[] handlers)
    {
        _handlers = handlers;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the threadPool.
     */
    public ThreadPool getThreadPool()
    {
        return _threadPool;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param threadPool The threadPool to set.
     */
    public void setThreadPool(ThreadPool threadPool)
    {
        _threadPool = threadPool;
    }
    
    /* ------------------------------------------------------------ */
    protected void doStart() throws Exception
    {
        MultiException mex=new MultiException();
        
        if (_threadPool==null)
        {
            BoundedThreadPool btp=new BoundedThreadPool();
            btp.setQueue(true);
            _threadPool=btp;
        }
        
        try{_threadPool.start();}catch(Throwable e){mex.add(e);}
        
        if (_handlers!=null)
        {
            for (int i=0;i<_handlers.length;i++)
                try{_handlers[i].start();}catch(Throwable e){mex.add(e);}
        }
        if (_connectors!=null)
        {
            for (int i=0;i<_connectors.length;i++)
                try{_connectors[i].start();}catch(Throwable e){mex.add(e);}
        }

        mex.ifExceptionThrow();
    }

    /* ------------------------------------------------------------ */
    protected void doStop() throws Exception
    {
        MultiException mex=new MultiException();
        
        if (_connectors!=null)
        {
            for (int i=_connectors.length;i-->0;)
                try{_connectors[i].stop();}catch(Throwable e){mex.add(e);}
        }
        if (_handlers!=null)
        {
            for (int i=_handlers.length;i-->0;)
                try{_handlers[i].stop();}catch(Throwable e){mex.add(e);}
        }
        try{_threadPool.stop();}catch(Throwable e){mex.add(e);}
        
        mex.ifExceptionThrow();
    }

    /* ------------------------------------------------------------ */
    public boolean dispatch(Runnable job)
    {
        if (isRunning())
            return _threadPool.dispatch(job);
        return false;
    }
    
    public String toString()
    {
        return "Server";
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.jetty.EventHandler#handle(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public boolean handle(HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException
    {
        if (_handlers==null || _handlers.length==0)
        {
            response.sendError(500);
            return true;
        }
        else
        {
            for (int i=0;i<_handlers.length;i++)
            {
                if (_handlers[i].handle(request,response, dispatch))
                    return true;
            }
        }    
        return false;
    }
    

}