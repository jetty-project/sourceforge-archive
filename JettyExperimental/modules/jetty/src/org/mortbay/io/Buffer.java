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

package org.mortbay.io;

import java.io.IOException;
import java.io.OutputStream;


/**
 * Byte Buffer interface.
 * 
 * This is a byte buffer that is designed to work like a FIFO for bytes. Puts and Gets operate on different
 * pointers into the buffer and the valid _content of the buffer is always between the getIndex and the putIndex.
 * 
 * This buffer interface is designed to be similar, but not dependant on the java.nio buffers, which may
 * be used to back an implementation of this Buffer. The main difference is that NIO buffer after a put have 
 * their valid _content before the position and a flip is required to access that data.
 *
 * For this buffer it is always true that:
 *  markValue <= getIndex <= putIndex <= capacity
 *  @author gregw
 *
 * @version 1.0
 */
public interface Buffer extends Cloneable
{
    public final static int 
      IMMUTABLE=0,
      READONLY=1,
      READWRITE=2;
    public final boolean VOLATILE=true;
    public final boolean NON_VOLATILE=false;

    /**
     *  Get the underlying array, if one exists.
     * @return a <code>byte[]</code> backing this buffer or null if none exists.
     */
    byte[] array();
    
    /**
     * 
     * @return a <code>byte[]</code> value of the bytes from the getIndex to the putIndex.
     */
    byte[] asArray();
    
    /** 
     * Get the unerlying buffer. If this buffer wraps a backing buffer.
     * @return The root backing buffer or this if there is no backing buffer;
     */
    Buffer buffer();
    
    /**
     * 
     * @return a non volitile version of this <code>Buffer</code> value
     */
    Buffer asNonVolatileBuffer();

    /**
     *
     * @return a readonly version of this <code>Buffer</code>.
     */
    Buffer asReadOnlyBuffer();

    /**
     *
     * @return an immutable version of this <code>Buffer</code>.
     */
    Buffer asImmutableBuffer();

    /**
     *
     * @return an immutable version of this <code>Buffer</code>.
     */
    Buffer asMutableBuffer();
    
    /**
     * 
     * The capacity of the buffer. This is the maximum putIndex that may be set.
     * @return an <code>int</code> value
     */
    int capacity();
    
    /**
     * the space remaining in the buffer.
     * @return capacity - putIndex
     */
    int space();
    
    /**
     * Clear the buffer. getIndex=0, putIndex=0.
     */
    void clear();

    /**
     * Compact the buffer by discarding bytes before the postion (or mark if set).
     * Bytes from the getIndex (or mark) to the putIndex are moved to the beginning of 
     * the buffer and the values adjusted accordingly.
     */
    void compact();
    
    /**
     * Get the byte at the current getIndex and increment it.
     * @return The <code>byte</code> value from the current getIndex.
     */
    byte get();
    
    /**
     * Get bytes from the current postion and put them into the passed byte array.
     * The getIndex is incremented by the number of bytes copied into the array.
     * @param b The byte array to fill.
     * @param offset Offset in the array.
     * @param length The max number of bytes to read.
     * @return The number of bytes actually read.
     */
    int get(byte[] b, int offset, int length);

    /**
     * 
     * @param length an <code>int</code> value
     * @return a <code>Buffer</code> value
     */
    Buffer get(int length);

    /**
     * The index within the buffer that will next be read or written.
     * @return an <code>int</code> value >=0 <= putIndex()
     */
    int getIndex();
    
    /**
     * @return true of putIndex > getIndex
     */
    boolean hasContent();
    
    /**
     * 
     * @return a <code>boolean</code> value true if case sensitive comparison on this buffer
     */
    boolean equalsIgnoreCase(Buffer buffer);


    /**
     * 
     * @return a <code>boolean</code> value true if the buffer is immutable and that neither
     * the buffer contents nor the indexes may be changed.
     */
    boolean isImmutable();
    
    /**
     * 
     * @return a <code>boolean</code> value true if the buffer is readonly. The buffer indexes may
     * be modified, but the buffer contents may not. For example a View onto an immutable Buffer will be
     * read only.
     */
    boolean isReadOnly();
    
    /**
     * 
     * @return a <code>boolean</code> value true if the buffer contents may change 
     * via alternate paths than this buffer.  If the contents of this buffer are to be used outside of the
     * current context, then a copy must be made.
     */
    boolean isVolatile();

    /**
     * The number of bytes from the getIndex to the putIndex
     * @return an <code>int</code> == putIndex()-getIndex()
     */
    int length();
    
    /**
     * Set the mark to the current getIndex.
     */
    void mark();
    
    /**
     * Set the mark relative to the current getIndex
     * @param offset an <code>int</code> value to add to the current getIndex to obtain the mark value.
     */
    void mark(int offset);

    /**
     * The current index of the mark.
     * @return an <code>int</code> index in the buffer or -1 if the mark is not set.
     */
    int markIndex();

    /**
     * Get the byte at the current getIndex without incrementing the getIndex.
     * @return The <code>byte</code> value from the current getIndex.
     */
    byte peek();
  
    /**
     * Get the byte at a specific index in the buffer.
     * @param index an <code>int</code> value
     * @return a <code>byte</code> value
     */
    byte peek(int index);

    /**
     * 
     * @param index an <code>int</code> value
     * @param length an <code>int</code> value
     * @return The <code>Buffer</code> value from the requested getIndex.
     */
    Buffer peek(int index, int length);

    /**
     * 
     * @param index an <code>int</code> value
     * @param b The byte array to peek into
     * @param offset The offset into the array to start peeking
     * @param length an <code>int</code> value
     * @return The number of bytes actually peeked
     */
    int peek(int index, byte[] b, int offset, int length);
    
    /**
     * Put the contents of the buffer at the specific index.
     * @param index an <code>int</code> value
     * @param src a <code>Buffer</code>. If the source buffer is immutable, it is not modified, otherwise it's getIndex is updated.
    
     * @return The number of bytes actually poked
     */
    int poke(int index, Buffer src);
    
    /**
     * Put a specific byte to a specific getIndex.
     * @param index an <code>int</code> value
     * @param b a <code>byte</code> value
     * @return The number of bytes actually poked
     */
    void poke(int index, byte b);
    
    /**
     * Put a specific byte to a specific getIndex.
     * @param index an <code>int</code> value
     * @param b a <code>byte array</code> value
     * @return The number of bytes actually poked
     */
    int poke(int index, byte b[], int offset, int length);
    
    /**
     * Write the bytes from the source buffer to the current getIndex.
     * @param src The source <code>Buffer</code>. If the source buffer is immutable, it is not modified, otherwise it's getIndex is updated.
     * @return The number of bytes actually poked
     */
    int put(Buffer src);

    /**
     * Put a byte to the current getIndex and increment the getIndex.
     * @param b a <code>byte</code> value
     * @return The number of bytes actually poked
     */
    void put(byte b);
    
    /**
     * Put a byte to the current getIndex and increment the getIndex.
     * @param b a <code>byte</code> value
     * @return The number of bytes actually poked
     */
    int put(byte[] b,int offset, int length);

    /**
     * Put a byte to the current getIndex and increment the getIndex.
     * @param b a <code>byte</code> value
     * @return The number of bytes actually poked
     */
    int put(byte[] b);

    /**
     * The index of the first element that should not be read.
     * @return an <code>int</code> value >= getIndex() 
     */
    int putIndex();
    
    /**
     * Reset the current getIndex to the mark 
     */
    void reset();
    
    /**
     * Set the buffers start getIndex.
     * @param newStart an <code>int</code> value
     */
    void setGetIndex(int newStart);
    
    /**
     * Set a specific value for the mark.
     * @param newMark an <code>int</code> value
     */
    void setMarkIndex(int newMark);
    
    /**
     * 
     * @param newLimit an <code>int</code> value
     */
    void setPutIndex(int newLimit);
    
    /**
     * Skip _content. The getIndex is updated by min(remaining(), n)
     * @param n The number of bytes to skip
     * @return the number of bytes skipped.
     */
    int skip(int n);

    /**
     * 
     * @return a volitile <code>Buffer</code> from the postion to the putIndex.
     */
    Buffer slice();
    
    /**
     * 
     *
     * @return a volitile <code>Buffer</code> value from the mark to the putIndex
     */
    Buffer sliceFromMark();
    
    /**
     * 
     *
     * @param length an <code>int</code> value
     * @return a valitile <code>Buffer</code> value from the mark of the length requested.
     */
    Buffer sliceFromMark(int length);
    
    /**
     * 
     * @return a <code>String</code> value describing the state and contents of the buffer.
     */
    String toDetailString();

    /* ------------------------------------------------------------ */
    /** Write the buffer's contents to the output stream
     * @param out
     */
    void writeTo(OutputStream out) throws IOException;
    
    /* 
     * Buffers implementing this interface should be compared with case insensitive equals
     *
     */
    public interface CaseInsensitve
    {}

    
}