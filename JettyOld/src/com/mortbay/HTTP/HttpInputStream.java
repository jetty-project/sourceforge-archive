// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP;
import com.mortbay.Base.*;

import javax.servlet.*;
import java.io.*;

/** HTTP input stream
 * @version $Id$
 * @author Greg Wilkins
*/
public class HttpInputStream extends ServletInputStream
{
    /* ------------------------------------------------------------ */
    /** Limit max line length */
    public static int __maxLineLength=8192;
    
    /* ------------------------------------------------------------ */
    /** The actual input stream.
     */
    private BufferedInputStream in;
    private int chunksize=0;
    com.mortbay.HTTP.HttpHeader footers=null;
    private boolean chunking=false;
    private int contentLength=-1;

    /* ------------------------------------------------------------ */
    /** Buffer for readLine.
     * The buffer gets reused across calls.
     * Note that the readCharBufferLine method breaks encapsulation
     * by returning this buffer, so BE CAREFUL!!!
     */
    static class CharBuffer
    {
        char[] chars = new char[128];
        int size=0;
    };
    CharBuffer charBuffer = new CharBuffer();
    
    
    /* ------------------------------------------------------------ */
    /** Constructor
     */
    public HttpInputStream( InputStream in)
    {
        this.in = new BufferedInputStream(in);
    }

    /* ------------------------------------------------------------ */
    /** 
     * @param chunking 
     */
    public void chunking(boolean chunking)
    {
        this.chunking=chunking;
    }

    /* ------------------------------------------------------------ */
    /** Set the content length.
     * Only this number of bytes can be read before EOF is returned.
     * @param len length.
     */
    public void setContentLength(int len)
    {
        contentLength=len;
    }
    
    /* ------------------------------------------------------------ */
    /** Get the content length.
     * Only this number of bytes can be read before EOF is returned.
     * @return int number of bytes can be read before EOF is returned.
     */
    int getContentLength()
    {
        return contentLength;
    }
        
    /* ------------------------------------------------------------ */
    /** Read a line ended by CR or CRLF or LF.
     * More forgiving of line termination than ServletInputStream.readLine().
     * This method only read raw data, that may be chunked.  Calling
     * ServletInputStream.readLine() will always return unchunked data.
     */
    public String readLine() throws IOException
    {
        CharBuffer buf = readCharBufferLine();
        if (buf==null)
            return null;
        return new String(buf.chars,0,buf.size);
    }
    
    /* ------------------------------------------------------------ */
    /** Read a line ended by CR or CRLF or LF.
     * More forgiving of line termination than ServletInputStream.readLine().
     * This method only read raw data, that may be chunked.  Calling
     * ServletInputStream.readLine() will always return unchunked data.
     */
    CharBuffer readCharBufferLine() throws IOException
    {
        BufferedInputStream in = this.in;
        
        int room = charBuffer.chars.length;
        charBuffer.size=0;
        int c=0;  
        boolean cr = false;
        boolean lf = false;

    LineLoop:
        while (charBuffer.size<__maxLineLength &&
               (c=chunking?read():in.read())!=-1)
        {
            switch(c)
            {
              case 10:
                  lf = true;
                  break LineLoop;
        
              case 13:
                  cr = true;
                  if (!chunking)
                      in.mark(2);
                  break;
        
              default:
                  if(cr)
                  {
                      if (chunking)
                          Code.fail("Cannot handle CR in chunking mode");
                      in.reset();
                      break LineLoop;
                  }
                  else
                  {
                      if (--room < 0)
                      {
                          // Double the size of the buffer but don't
                          // wastefully overshoot any contentLength limit.
                          int newLength = charBuffer.chars.length << 1;
                          newLength = Math.min(__maxLineLength,newLength);
                          char[] old = charBuffer.chars;
                          charBuffer.chars =new char[newLength];
                          room = charBuffer.chars.length-charBuffer.size-1;
                          System.arraycopy(old,0,charBuffer.chars,0,charBuffer.size);
                      }
                      charBuffer.chars[charBuffer.size++] = (char) c;
                  }
                  break;
            }    
        }

        if (c==-1 && charBuffer.size==0)
            return null;

        return charBuffer;
    }
    
    /* ------------------------------------------------------------ */
    public int read() throws IOException
    {
        if (chunking)
        {   
            int b=-1;
            if (chunksize<=0 && getChunkSize()<=0)
                return -1;
            b=in.read();
            chunksize=(b<0)?-1:(chunksize-1);
            return b;
        }

        if (contentLength==0)
            return -1;
        int b=in.read();
        if (contentLength>0)
            contentLength--;
        return b;
    }
 
    /* ------------------------------------------------------------ */
    public int read(byte b[]) throws IOException
    {
        int len = b.length;
    
        if (chunking)
        {   
            if (chunksize<=0 && getChunkSize()<=0)
                return -1;
            if (len > chunksize)
                len=chunksize;
            len=in.read(b,0,len);
            chunksize=(len<0)?-1:(chunksize-len);
        }
        else
        {
            if (contentLength==0)
                return -1;
            if (len>contentLength && contentLength>=0)
                len=contentLength;
            len=in.read(b,0,len);
            if (contentLength>0 && len>0)
                contentLength-=len;
        }

        return len;
    }
 
    /* ------------------------------------------------------------ */
    public int read(byte b[], int off, int len) throws IOException
    {
        if (chunking)
        {   
            if (chunksize<=0 && getChunkSize()<=0)
                return -1;
            if (len > chunksize)
                len=chunksize;
            len=in.read(b,off,len);
            chunksize=(len<0)?-1:(chunksize-len);
        }
        else
        {
            if (contentLength==0)
                return -1;
            if (len>contentLength && contentLength>=0)
                len=contentLength;
            len=in.read(b,off,len);
            if (contentLength>0 && len>0)
                contentLength-=len;
        }

        return len;
    }
    
    /* ------------------------------------------------------------ */
    public long skip(long len) throws IOException
    {
        if (chunking)
        {   
            if (chunksize<=0 && getChunkSize()<=0)
                return -1;
            if (len > chunksize)
                len=chunksize;
            len=in.skip(len);
            chunksize=(len<0)?-1:(chunksize-(int)len);
        }
        else
        {
            len=in.skip(len);
            if (contentLength>0 && len>0)
                contentLength-=len;
        }
        return len;
    }

    /* ------------------------------------------------------------ */
    /** Available bytes to read without blocking.
     * If you are unlucky may return 0 when there are more
     */
    public int available() throws IOException
    {
        if (chunking)
        {
            int len = in.available();
            if (len<=chunksize)
                return len;
            return chunksize;
        }
        
        return in.available();
    }
 
    /* ------------------------------------------------------------ */
    /** Close stream.
     * The close is not passed to the wrapped stream, as this is
     * needed for the response
     */
    public void close() throws IOException
    {
        Code.debug("Close");
        chunksize=-1;
    }
 
    /* ------------------------------------------------------------ */
    /** Mark is not supported
     * @return false
     */
    public boolean markSupported()
    {
        return false;
    }
    
    /* ------------------------------------------------------------ */
    /** Not Implemented
     */
    public void reset()
    {
        Code.notImplemented();
    }

    /* ------------------------------------------------------------ */
    /** Not Implemented
     * @param readlimit 
     */
    public void mark(int readlimit)
    {
        Code.notImplemented();
    }    
    
    /* ------------------------------------------------------------ */
    private int getChunkSize()
        throws IOException
    {
        if (chunksize<0)
            return -1;
        
        footers=null;
        chunksize=-1;

        // Get next non blank line
        chunking=false;
        String line=readLine();
        while(line!=null && line.length()==0)
            line=readLine();
        chunking=true;
        
        // Handle early EOF or error in format
        if (line==null)
            return -1;
        
        // Get chunksize
        int i=line.indexOf(';');
        if (i>0)
            line=line.substring(0,i).trim();
        chunksize = Integer.parseInt(line,16);
        
        // check for EOF
        if (chunksize==0)
        {
            chunksize=-1;
            // Look for footers
            footers = new com.mortbay.HTTP.HttpHeader();
            chunking=false;
            footers.read(this);
        }

        return chunksize;
    }
    
    /* ------------------------------------------------------------ */
    /** Get footers
     * Only valid after EOF has been returned
     * @return HttpHeader containing footer fields
     */
    public com.mortbay.HTTP.HttpHeader getFooters()
    {
        return footers;
    }

};


