// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.Util;
import java.util.Vector;
import com.mortbay.Base.Code;

/* ------------------------------------------------------------ */
/** Blocking queue
 *
 * XXX temp implementation while waiting for java2 containers.
 * Implemented as circular buffer in a Vector. Synchronization is on the
 * vector to avoid double synchronization.
 *
 * @version 1.0 Fri May 28 1999
 * @author Greg Wilkins (gregw)
 */
public class BlockingQueue
{
    Object[] elements;
    int maxSize;
    int size=0;
    int head=0;
    int tail=0;

    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public BlockingQueue(int maxSize)
    {
        this.maxSize=maxSize;
        if (maxSize==0)
            this.maxSize=255;
        elements = new Object[this.maxSize];
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    public int size()
    {
        return size;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @return 
     */
    public int maxSize()
    {
        return maxSize;
    }
    
  
    /* ------------------------------------------------------------ */
    /** Put object in queue.
     * @param o Object
     */
    public void put(Object o)
        throws InterruptedException
    {
        synchronized(elements)
        {
            while (size==maxSize)
                elements.wait();

            elements[tail]=o;
            if(++tail==maxSize)
                tail=0;
            size++;
            elements.notify();
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Put object in queue.
     * @param timeout If timeout expires, throw InterruptedException
     * @param o Object
     * @exception InterruptedException Timeout expired or otherwise interrupted
     */
    public void put(Object o, int timeout)
        throws InterruptedException
    {
        synchronized(elements)
        {
            if (size==maxSize)
            {
                elements.wait(timeout);
                if (size==maxSize)
                    throw new InterruptedException("Timed out");
            }
            
            elements[tail]=o;
            if(++tail==maxSize)
                tail=0;
            size++;
            elements.notify();
        }
    }

    /* ------------------------------------------------------------ */
    /** Get object from queue.
     * Block if there are no objects to get.
     * @return The next object in the queue.
     */
    public Object get()
        throws InterruptedException
    {
        synchronized(elements)
        {
            while (size==0)
                elements.wait();
            
            Object o = elements[head];
            if(++head==maxSize)
                head=0;
            if (size==maxSize)
                elements.notifyAll();
            size--;
            return o;
        }
    }
    
        
    /* ------------------------------------------------------------ */
    /** Get from queue.
     * Block for timeout if there are no objects to get.
     * @param timeout the time to wait for a job
     * @return The next object in the queue, or null if timedout.
     */
    public Object get(long timeout)
        throws InterruptedException
    {   
        synchronized(elements)
        {
            if (size==0)
                elements.wait(timeout);
            
            if (size==0)
                return null;
            
            Object o = elements[head];
            if(++head==maxSize)
                head=0;

            if (size==maxSize)
                elements.notifyAll();
            size--;
            
            return o;
        }
    }
}








