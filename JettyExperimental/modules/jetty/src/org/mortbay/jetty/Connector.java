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

package org.mortbay.jetty;

import java.io.IOException;

import org.mortbay.io.Buffers;
import org.mortbay.jetty.util.Continuation;
import org.mortbay.thread.LifeCycle;
import org.mortbay.thread.ThreadPool;

/** HTTP Connector.
 * Implementations of this interface provide connectors for the HTTP protocol.
 * A connector receives requests (normally from a socket) and calls the 
 * handle method of the Handler object.  These operations are performed using
 * threads from the ThreadPool set on the connector.
 * 
 * When a connector is registered with an instance of Server, then the server
 * will set itself as both the ThreadPool and the Handler.  Note that a connector
 * can be used without a Server if a thread pool and handler are directly provided.
 * 
 * @author gregw
 *
 */
public interface Connector extends LifeCycle, Buffers
{ 
    public void open() throws IOException;
    public void close() throws IOException;
    
    public void setThreadPool(ThreadPool pool);
    public ThreadPool getThreadPool();
    
    public void setHandler(Handler handler);
    public Handler getHandler();
    

    /* ------------------------------------------------------------ */
    /**
     * @return Returns the headerBufferSize.
     */
    int getHeaderBufferSize();
    
    /* ------------------------------------------------------------ */
    /**
     * Set the size of the buffer to be used for request and response headers.
     * An idle connection will at most have one buffer of this size allocated.
     * @param headerBufferSize The headerBufferSize to set.
     */
    void setHeaderBufferSize(int headerBufferSize);
    
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the requestBufferSize.
     */
    int getRequestBufferSize();
    
    /* ------------------------------------------------------------ */
    /**
     * Set the size of the content buffer for receiving requests. 
     * These buffers are only used for active connections that have
     * requests with bodies that will not fit within the header buffer.
     * @param requestBufferSize The requestBufferSize to set.
     */
    void setRequestBufferSize(int requestBufferSize);
    
    /* ------------------------------------------------------------ */
    /**
     * @return Returns the responseBufferSize.
     */
    int getResponseBufferSize();
    
    /* ------------------------------------------------------------ */
    /**
     * Set the size of the content buffer for sending responses. 
     * These buffers are only used for active connections that are sending 
     * responses with bodies that will not fit within the header buffer.
     * @param responseBufferSize The responseBufferSize to set.
     */
    void setResponseBufferSize(int responseBufferSize);
    

    /* ------------------------------------------------------------ */
    int getConfidentialPort();
    String getConfidentialScheme();
    boolean isConfidential(Request request);
    
    Continuation newContinuation();
    
}