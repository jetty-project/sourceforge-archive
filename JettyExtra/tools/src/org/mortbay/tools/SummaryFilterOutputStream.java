// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.tools;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/* ------------------------------------------------------------ */
/** Filtered OutputStream that summarized throughput on stderr.
 * <p>
 *
 * @see FilterOutputStream
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class SummaryFilterOutputStream extends FilterOutputStream
{
    String _msg=null;
    int _size=60;
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param out 
     */
    public SummaryFilterOutputStream(OutputStream out)
    {
        this(out,null);
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param out 
     */
    public SummaryFilterOutputStream(OutputStream out,
                                     String name)
    {
        super(out);
        if (name==null)
            name="to "+out;
        _msg=" bytes written "+name+":\n";
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param out 
     */
    public SummaryFilterOutputStream(OutputStream out,
                                     String name,
                                     int size)
    {
        this(out,name);
        _size=size;
    }

    
    /* ------------------------------------------------------------ */
    /** 
     * @param b 
     * @exception IOException 
     */
    public void write(int b)
        throws IOException
    {
        byte[] ba = new byte[1];
        ba[0]=(byte)b;
        write(ba,0,1);
    }
    

    /* ------------------------------------------------------------ */
    /** 
     * @param b 
     * @exception IOException 
     */
    public void write(byte b[])
        throws IOException
    {
        write(b,0,b.length);
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param b 
     * @param off 
     * @param len 
     * @exception IOException 
     */
    public void write(byte b[],
                      int off,
                      int len)
        throws IOException
    {
        out.write(b,off,len);
        StringBuffer buf=new
            StringBuffer("=============================================================================\n"+
                         Thread.currentThread().getName()+": "+
                         len+
                         _msg+
                         "-----------------------------------------------------------------------------\n");
        int i=0;
        for (i=0; (_size==0 || i<_size) && i<len; i++)
        {
            char c = (char)b[off + i];
            if (Character.isISOControl(c) && c!=10 && c!=13)
                buf.append('.');
            else 
                buf.append(c);
        }   
        buf.append((i<len)?"\n...\n":"\n");
        synchronized(System.err)
        {
            System.err.println(buf.toString());
        }
    }
}







