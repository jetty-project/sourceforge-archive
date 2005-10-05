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
 
package org.mortbay.jetty.nio;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;

import org.mortbay.io.Buffer;
import org.mortbay.io.nio.ChannelEndPoint;
import org.mortbay.io.nio.NIOBuffer;
import org.mortbay.jetty.AbstractConnector;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.RetryRequest;
import org.mortbay.jetty.util.Continuation;
import org.mortbay.thread.Timeout;
import org.mortbay.util.LogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* ------------------------------------------------------------------------------- */
/** Selecting NIO connector.
 * <p>This connector uses efficient NIO buffers with a non blocking threading model.
 * Direct NIO buffers are used and threads are only allocated to connections with
 * requests. Synchronization is used to simulate blocking for the servlet API, and
 * any unflushed content at the end of request handling is written asynchronously.
 * </p>
 * <p>
 * This connector is best used when there are a many connections that have idle periods.
 * </p>
 * <p>
 * When used with {@link org.mortbay.jetty.util.Continuation}, threadless waits are
 * supported.  When a filter or servlet calls getEvent on a Continuation, a
 * {@link org.mortbay.jetty.RetryRequest} runtime exception is thrown to allow the
 * thread to exit the current request handling.  Jetty will catch this exception and will not
 * send a response to the client.  Instead the thread is released and the Continuation is 
 * placed on the timer queue.  If the Continuation timeout expires, or it's resume method
 * is called, then the request is again allocated a thread and the request is retied. 
 * </p>
 * 
 * @version $Revision$
 * @author gregw
 */
public class SelectChannelConnector extends AbstractConnector
{
    private static Logger log= LoggerFactory.getLogger(SelectChannelConnector.class);
    
    private transient Timeout _idleTimeout;
    private transient Timeout _retryTimeout;
    private transient ServerSocketChannel _acceptChannel;
    private transient SelectionKey _acceptKey;
    private transient Selector _selector;
    private transient ArrayList _keyChanges=new ArrayList();

    
    /* ------------------------------------------------------------------------------- */
    /** Constructor.
     * 
     */
    public SelectChannelConnector()
    {
    }
    

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.jetty.AbstractConnector#doStart()
     */
    protected void doStart() throws Exception
    {
        _idleTimeout=new Timeout();
        _idleTimeout.setDuration(getMaxIdleTime());
        _retryTimeout=new Timeout();
        _retryTimeout.setDuration(0L);
        super.doStart();
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.jetty.AbstractConnector#doStop()
     */
    protected void doStop() throws Exception
    {
        // TODO Auto-generated method stub
        super.doStop();
        _idleTimeout.cancelAll();
        _idleTimeout=null;
        _retryTimeout.cancelAll();
        _retryTimeout=null;
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.jetty.AbstractConnector#setMaxIdleTime(long)
     */
    public void setMaxIdleTime(long maxIdleTime)
    {
        super.setMaxIdleTime(maxIdleTime);
        if (_idleTimeout!=null)
            _idleTimeout.setDuration(maxIdleTime);
    }


    /* ------------------------------------------------------------ */
    public void open() throws IOException
    {
        if (_acceptChannel==null)
        {
            // Create a new server socket and set to non blocking mode
            _acceptChannel= ServerSocketChannel.open();
            _acceptChannel.configureBlocking(false);

            // Bind the server socket to the local host and port
            _acceptChannel.socket().bind(getAddress());

            // create a selector;
            _selector= Selector.open();

            // Register accepts on the server socket with the selector.
            _acceptKey=_acceptChannel.register(_selector, SelectionKey.OP_ACCEPT);
        }
    }

    /* ------------------------------------------------------------ */
    public void close() throws IOException
    {
        if (_acceptChannel != null)
            _acceptChannel.close();
        _acceptChannel=null;

        try
        {
            if (_selector != null)
                _selector.close();
        }
        catch (IOException e)
        {
            LogSupport.ignore(log, e);
        }

        _selector= null;
    }

    /* ------------------------------------------------------------ */
    public void accept()
    	throws IOException
    {      
        // Make any key changes required
        synchronized(_keyChanges)
        {
            for (int i=0;i<_keyChanges.size();i++)
            {
                try
                {
                    HttpEndPoint c = (HttpEndPoint)_keyChanges.get(i);
                    if (c._interestOps>=0 && c._key!=null && c._key.isValid())
                    {
                       c._key.interestOps(c._interestOps);
                    }
                    else 
                    {
                        if (c._key!=null && c._key.isValid())
                            c._key.cancel();
                        c._key=null;
                    }
                }
                catch(CancelledKeyException e)
                {
                    log.warn("???",e);
                }
            }
            _keyChanges.clear();
        }
 
        // workout how low to wait in select
        long wait = getMaxIdleTime();
        long to_next = _idleTimeout.getTimeToNext();
        if (wait<0 || to_next >=0 && wait>to_next)
            wait=to_next;
        to_next = _retryTimeout.getTimeToNext();
        if (wait<0 || to_next >=0 && wait>to_next)
            wait=to_next;
             
        // Do the select.
        if (wait>0)
            _selector.select(wait); 
        else if (wait==0)
            _selector.selectNow();
        else
            _selector.select();
        
        
        // update the timers for task schedule in this loop
        long now = System.currentTimeMillis();
        _idleTimeout.setNow(now);
        _retryTimeout.setNow(now);
        
        // Look for things to do
        Iterator iter= _selector.selectedKeys().iterator();
        while (iter.hasNext())
        {
            SelectionKey key= (SelectionKey)iter.next();
            iter.remove();
                        
            try
            {
                if (!key.isValid())
                {
                    key.cancel();
                    HttpEndPoint connection = (HttpEndPoint)key.attachment();
                    if (connection!=null)
                        connection._key=null;
                    continue;
                }
                
                if (key.equals(_acceptKey))
                {
                    if (key.isAcceptable())
                    {
                        SocketChannel channel = _acceptChannel.accept();
                        channel.configureBlocking(false);
                        Socket socket=channel.socket();
                        configure(socket);
                        SelectionKey cKey = channel.register(_selector, SelectionKey.OP_READ);
                        HttpEndPoint connection=new HttpEndPoint(channel);
                        connection.setKey(cKey);
                        
                        // assume something to do
                        connection.dispatch();
                    }
                }
                else
                {
                    HttpEndPoint connection = (HttpEndPoint)key.attachment();
                    if (connection!=null)
                        connection.dispatch(); 
                }
                
                key= null;
            }
            catch (Exception e)
            {
                if (isRunning())
                    log.warn("selector", e);
                if (key != null && key!=_acceptKey)
                    key.interestOps(0);
            }
        }
        
        // tick over the timer
        now=System.currentTimeMillis();
        _retryTimeout.setNow(now);
        _retryTimeout.tick();
        _idleTimeout.setNow(now);
        _idleTimeout.tick();
        
    }

    /* ------------------------------------------------------------------------------- */
    protected Buffer newBuffer(int size)
    {
        return new NIOBuffer(size, true);
    }

    /* ------------------------------------------------------------------------------- */
    /* ------------------------------------------------------------------------------- */
    /* ------------------------------------------------------------------------------- */
    private class HttpEndPoint extends ChannelEndPoint implements Runnable
    {
        boolean _dispatched=false;
        boolean _writable=true;  // TODO - get rid of this bad side effect
        SelectionKey _key;
        HttpConnection _connection;
        int _interestOps;
        int _readBlocked;
        int _writeBlocked;
        
        IdleTask _timeoutTask = new IdleTask();
    
        /* ------------------------------------------------------------ */
        HttpEndPoint(SocketChannel channel) 
        {
            super(channel);
            _connection = new HttpConnection(SelectChannelConnector.this,this, getHandler());
            _timeoutTask.schedule(_idleTimeout);
        }

        /* ------------------------------------------------------------ */
        void setKey(SelectionKey key)
        {
            _key=key;
            _key.attach(this);
        }

        /* ------------------------------------------------------------ */
        /** Dispatch the endpoint by arranging for a thread to service it.
         * Either a blocked thread is woken up or the endpoint is passed to the server job queue.
         * If the thread is dispatched and then the selection key 
         * is modified  so that it is no longer selected.
         */
        void dispatch() 
            throws IOException
        {
            synchronized(this)
            {
                _timeoutTask.reschedule();
                
                // If threads are blocked on this
                if (_readBlocked>0 || _writeBlocked>0)
                {
                    // wake them up is as good as a dispatched.
                    this.notifyAll();

                    // we are not interested in further selecting
                    _key.interestOps(0);
                    return;
                }
                
                // Otherwise if we are still dispatched
                if (_dispatched)
                {
                    // we are not interested in further selecting 
                    _key.interestOps(0);
                    return;
                }

                // Remove writeable op
                if ((_key.readyOps()|SelectionKey.OP_WRITE)!=0 &&
                    (_key.interestOps()|SelectionKey.OP_WRITE)!=0)
                    // Remove writeable op
                    _key.interestOps(_interestOps=_key.interestOps()&(-1^SelectionKey.OP_WRITE));
                
                _dispatched=true;
            }
            
            boolean dispatch_done=false;
            try
            {
                dispatch_done=getThreadPool().dispatch(this);
            }
            finally
            {
                if (!dispatch_done)
                {
                    log.warn("dispatch failed");
                    undispatch();
                }
            }
        }

        /* ------------------------------------------------------------ */
        /** Called when a dispatched thread is no longer handling the endpoint.
         * The selection key operations are updated.
         */
        private void undispatch()
        {
            try
            {
                _dispatched=false;
                
                if (getChannel().isOpen())
                    updateKey();
            }
            catch(Exception e)
            {
                log.error("???",e);
                _interestOps=-1;
                synchronized(_keyChanges)
                {
                    _keyChanges.add(this);
                }
            }
        }

        /* ------------------------------------------------------------ */
        /* 
         */
        public int fill(Buffer buffer) throws IOException
        {
            int l = super.fill(buffer);
            if (l<0)
                getChannel().close();
            return l;
        }

        /* ------------------------------------------------------------ */
        /*
         */
        public int flush(Buffer header, Buffer buffer, Buffer trailer) throws IOException
        {
            int l = super.flush(header, buffer, trailer);
            _writable=l>0;
            return l;
        }

        /* ------------------------------------------------------------ */
        /*
         */
        public int flush(Buffer buffer) throws IOException
        {
            int l = super.flush(buffer);
            _writable=l>0;
            return l;
        }
        
        /* ------------------------------------------------------------ */
        /* Allows thread to block waiting for further events.
         */
        public void blockReadable(long timeoutMs)
        {
            synchronized(this)
            {
                if (getChannel().isOpen() && _key.isValid())
                {
                    try
                    {
                        _readBlocked++;
                        updateKey();
                        this.wait(timeoutMs);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    finally
                    {
                        _readBlocked--;
                    }
                }
            }
        }

        /* ------------------------------------------------------------ */
        /* Allows thread to block waiting for further events.
         */
        public void blockWritable(long timeoutMs)
        {
            synchronized(this)
            {
                if (getChannel().isOpen() && _key.isValid())
                {
                    try
                    {
                        _writeBlocked++;
                        updateKey();
                        this.wait(timeoutMs);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    finally
                    {
                        _writeBlocked--;
                    }
                }
            }
        }

        /* ------------------------------------------------------------ */
        /** Updates selection key.
         * Adds operations types to the selection key as needed.
         * No operations are removed as this is only done during dispatch
         */
        private void updateKey()
        {
            synchronized(this)
            {
                int ops = _key==null?0:_key.interestOps();
                _interestOps=ops | 
                ((!_dispatched||_readBlocked>0)?SelectionKey.OP_READ:0) | 
                ((!_writable||_writeBlocked>0)?SelectionKey.OP_WRITE:0);
                _writable=true; // Once writable is in ops, only removed with dispatch.
                                
                if (_interestOps!=ops)
                {
                    synchronized(_keyChanges)
                    {
                        _keyChanges.add(this);
                    }
                    _selector.wakeup();
                }
            }
        }
        
        /* ------------------------------------------------------------ */
        /* 
         */
        public void run()
        {
            try
            {
                _connection.handle();
            }
            catch(ClosedChannelException e)
            {
                log.debug("handle",e);
            }
            catch(IOException e)
            {
                // TODO - better than this
                if ("BAD".equals(e.getMessage()))
                {
                    log.warn("BAD Request");
                    log.debug("BAD",e);
                }
                else if ("EOF".equals(e.getMessage()))
                    log.debug("EOF",e);
                else
                    log.warn("IO",e);
                if (_key!=null)
                    _key.cancel();
                _key=null;
                try{close();}
                catch(IOException e2){LogSupport.ignore(log, e2);}
            }
            catch(Throwable e)
            {
                log.warn("handle failed",e);
                if (_key!=null)
                    _key.cancel();
                _key=null;
                try{close();}
                catch(IOException e2){LogSupport.ignore(log, e2);}
            }
            finally
            {
                synchronized(this)
                {
                    RetryContinuation continuation = (RetryContinuation)_connection.getRequest().getContinuation();
                    if (continuation!=null && continuation.isPending())
                    {
                        // We have a continuation
                        log.debug("continuation {}",continuation);
                        long timeout=continuation.getTimeout();
                        continuation.setEndPoint(this);
                        _retryTimeout.schedule(continuation,timeout);
                        _selector.wakeup();
                    }
                    else
                        undispatch();
                }
            }
        }
        
        public String toString()
        {
            return "HEP[d="+_dispatched+",io="+_interestOps+",w="+_writable+",b="+_readBlocked+"|"+_writeBlocked+"]";
        }
        


        /* ------------------------------------------------------------ */
        /* ------------------------------------------------------------ */
        /* ------------------------------------------------------------ */
        private class IdleTask extends Timeout.Task
        {
            /* ------------------------------------------------------------ */
            /* @see org.mortbay.thread.Timeout.Task#expire()
             */
            public void expire()
            {
                try{close();}
                catch(IOException e) { LogSupport.ignore(log, e); }
            }
            
            public String toString()
            {
                return "TimeoutTask:"+HttpEndPoint.this.toString();
            }
            
        }
        
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.jetty.Connector#newContinuation()
     */
    public Continuation newContinuation()
    {
        return new RetryContinuation();
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private class RetryContinuation extends Timeout.Task implements Continuation
    {
        Object _event;
        Object _object;
        HttpEndPoint _endPoint;
        long _timeout;
        boolean _new=true;
        boolean _pending=true;
        
        
        void setEndPoint(HttpEndPoint ep)
        {
            synchronized (this)
            {
                _endPoint=ep;
            
                if (_event!=null)
                    redispatch();
            }
        }

        long getTimeout()
        {
            return _timeout;
        }

        public boolean isNew()
        {
            return _new;
        }

        public boolean isPending()
        {
            return _pending;
        }
        
        public void expire()
        {
            synchronized (this)
            {
                if (_pending)
                    redispatch();
                _pending=false;
            }
        }
        
        public Object getEvent(long timeout)
        {
            synchronized (this)
            {
                _new=false;
                if (!isExpired() && _event==null && timeout>0)
                {
                    if (_endPoint!=null)
                    {
                        throw new IllegalStateException();
                    }
                    _timeout=timeout;
                    throw new RetryRequest();
                }
            }   
            
            return _event;
        }
        
        public void resume(Object object)
        {
            synchronized (this)
            {
                if (isExpired())
                    return;

                _pending=false;
                this.cancel();
                _event=object==null?this:object;
            
                if (_endPoint!=null)
                    redispatch();
            }
        }

        private void redispatch()
        {
            boolean dispatch_done=false;
            try
            {
                dispatch_done=getThreadPool().dispatch(_endPoint);
            }
            finally
            {
                if (!dispatch_done)
                {
                    log.warn("dispatch failed");
                    _endPoint.undispatch();
                }
            }
        }
        
        public Object getObject()
        {
            return _object;
        }

        public void setObject(Object object)
        {
            _object = object;
        }
        
    }
}