/* ==============================================
 * Copyright 2003 Mort Bay Consulting Pty Ltd. All rights reserved.
 * Distributed under the artistic license.
 * Created on 17-Apr-2003
 * $Id$
 * ============================================== */

package org.mortbay.http;

import java.io.IOException;
import java.io.OutputStream;
import org.mortbay.io.Buffer;

/* ------------------------------------------------------------------------------- */
/**
 * HTTP OutputStream. Based on HttpOutput and assumes a blocking buffer implementation.
 */
public class HttpOutputStream extends OutputStream
{
    private byte[] _b1=new byte[0];
    private HttpOutput _out;

    /**
     * 
     */
    public HttpOutputStream(Buffer buffer,HttpHeader header,int headerReserve)
    {
        _out=new HttpOutput(buffer,header,headerReserve);
    }

    /*
     * @see java.io.OutputStream#close()
     */
    public void close() throws IOException
    {
        if(_out.isClosed())
            return;
        _out.close();
        while(_out.isFlushing())
            _out.flush();
    }

    /* ------------------------------------------------------------------------------- */
    /**
     * destroy.
     */
    public void destroy()
    {
        if(_out!=null)
            _out.destroy();
        _out=null;
        _b1=null;
    }

    /*
     * @see java.io.OutputStream#flush()
     */
    public void flush() throws IOException
    {
        while(!_out.flush());
    }

    public Buffer getBuffer()
    {
        return _out.getBuffer();
    }

    public HttpHeader getHttpHeader()
    {
        return _out.getHttpHeader();
    }

    /**
     * @return True if header has been committed.
     */
    public boolean isCommitted()
    {
        return _out.isCommitted();
    }

    public boolean isPersistent()
    {
        return _out.isPersitent();
    }

    public void resetBuffer()
    {
        _out.resetBuffer();
    }

    public void resetStream()
    {
        _out.reset();
    }

    public void sendContinue()
    throws IOException
    {
        _out.sendContinue();
    }

    public void setHeadResponse(boolean h)
    {
        _out.setHeadResponse(h);
    }

    /**
     * Set the output version. For responses, the version may differ from that indicated by the
     * header.
     * 
     * @param ordinal HttpVersions ordinal
     */
    public void setVersionOrdinal(int ordinal)
    {
        _out.setVersionOrdinal(ordinal);
    }

    /*
     * @see java.io.OutputStream#write(byte[], int, int)
     */
    public void write(byte[] b,int offset,int length) throws IOException
    {
        if(_out.isFlushing())
            while(!_out.flush());
        while(length>0)
        {
            int len=_out.write(b,offset,length);
            if(len<length)
            {
                length-=len;
                offset+=len;
                while(!_out.flush());
            }
            else
            {
                length-=len;
                offset+=len;
            }
        }
    }

    /*
     * @see java.io.OutputStream#write(int)
     */
    public void write(int b) throws IOException
    {
        _b1[0]=(byte)b;
        write(_b1,0,1);
    }
    
}