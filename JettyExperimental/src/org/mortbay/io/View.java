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

package org.mortbay.io;

/**
 * A View on another buffer.  Allows operations that do not change the content or
 * indexes of the backing buffer.
 * 
 * @author gregw
 * 
 */
public class View extends AbstractBuffer
{
    Buffer _buffer;

    /**
     * @param buffer
     * @param mark
     * @param get
     * @param put
     * @param readonly
     * @param b
     */
    public View(Buffer buffer, int mark, int get, int put,int access)
    {
        super(READWRITE,!buffer.isImmutable());
        _buffer=buffer.buffer();
        setPutIndex(put);
        setGetIndex(get);
        setMarkIndex(mark);
        _access=access;
    }
    
    public View(Buffer buffer)
    {
        super(READWRITE,!buffer.isImmutable());
        _buffer=buffer.buffer();
        setPutIndex(buffer.putIndex());
        setGetIndex(buffer.getIndex());
        setMarkIndex(buffer.markIndex());
        _access=buffer.isReadOnly()?READONLY:READWRITE;
    }
    
    public void update(Buffer buffer)
    {
        _access=READWRITE;
        _buffer=buffer.buffer();
        setPutIndex(buffer.putIndex());
        setGetIndex(buffer.getIndex());
        setMarkIndex(buffer.markIndex());
        _access=buffer.isReadOnly()?READONLY:READWRITE;
    }

    public void update(int get, int put)
    {
        int a=_access;
        _access=READWRITE;
        setPutIndex(put);
        setGetIndex(get);
        setMarkIndex(-1);
        _access=a;
    }

    /**
     * @return
     */
    public byte[] array()
    {
        return _buffer.array();
    }

    /**
     * @return
     */
    public Buffer buffer()
    {
        return _buffer.buffer();
    }

    /**
     * @return
     */
    public int capacity()
    {
        return _buffer.capacity();
    }

    /**
     *  
     */
    public void clear()
    {
        setMarkIndex(-1);
        setGetIndex(_buffer.getIndex());
        setPutIndex(_buffer.getIndex());
    }

    /**
     *  
     */
    public void compact()
    {
        // TODO
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object arg0)
    {
        return  this==arg0 || super.equals(arg0);
    }

    /**
     * @return
     */
    public boolean isReadOnly()
    {
        return _buffer.isReadOnly();
    }

    /**
     * @return
     */
    public boolean isVolatile()
    {
        return true;
    }

    /**
     * @param index
     * @return
     */
    public byte peek(int index)
    {
        return _buffer.peek(index);
    }

    /**
     * @param index
     * @param length
     * @return
     */
    public int peek(int index, byte[] b, int offset, int length)
    {
        return _buffer.peek(index,b,offset,length);
    }

    /**
     * @param index
     * @param length
     * @return
     */
    public Buffer peek(int index, int length)
    {
        return _buffer.peek(index, length);
    }
    
    /**
     * @param index
     * @param src
     */
    public void poke(int index, Buffer src)
    {
        _buffer.poke(index,src); 
    }

    /**
     * @param index
     * @param b
     */
    public void poke(int index, byte b)
    {
        _buffer.poke(index,b);
    }

    /**
     * @param index
     * @param b
     * @param offset
     * @param length
     */
    public void poke(int index, byte[] b, int offset, int length)
    {
        _buffer.poke(index,b,offset,length);
    }
}
