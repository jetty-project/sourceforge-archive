// ===========================================================================
// Copyright (c) 2001 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.util;
import java.io.Writer;
import java.io.IOException;


/* ------------------------------------------------------------ */
/** A Writer to a StringBuffer.
 *
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class StringBufferWriter extends Writer
{
    /* ------------------------------------------------------------ */
    private StringBuffer _buffer;

    /* ------------------------------------------------------------ */
    /** Constructor. 
     */
    public StringBufferWriter()
    {
        _buffer=new StringBuffer();
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param buffer 
     */
    public StringBufferWriter(StringBuffer buffer)
    {
        _buffer=buffer;
    }

    /* ------------------------------------------------------------ */
    public void setStringBuffer(StringBuffer buffer)
    {
        _buffer=buffer;
    }
    
    /* ------------------------------------------------------------ */
    public StringBuffer getStringBuffer()
    {
        return _buffer;
    }
    
    /* ------------------------------------------------------------ */
    public void write(char c)
        throws IOException
    {
        _buffer.append(c);
    }
    
    /* ------------------------------------------------------------ */
    public void write(char[] ca)
        throws IOException
    {
        _buffer.append(ca);
    }
    
    
    /* ------------------------------------------------------------ */
    public void write(char[] ca,int offset, int length)
        throws IOException
    {
        _buffer.append(ca,offset,length);
    }
    
    /* ------------------------------------------------------------ */
    public void write(String s)
        throws IOException
    {
        _buffer.append(s);
    }
    
    /* ------------------------------------------------------------ */
    public void write(String s,int offset, int length)
        throws IOException
    {
        for (int i=0;i<length;i++)
            _buffer.append(s.charAt(offset+i));
    }
    
    /* ------------------------------------------------------------ */
    public void flush()
    {}

    /* ------------------------------------------------------------ */
    public void reset()
    {
        _buffer.setLength(0);
    }

    /* ------------------------------------------------------------ */
    public void close()
    {}

}
