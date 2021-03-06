// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.tools;
import java.io.OutputStream;

/* ------------------------------------------------------------ */
/** Filtered OutputStream that summarized throughput on stderr.
 * <p>
 *
 * @see FilterOutputStream
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class DumpFilterOutputStream extends SummaryFilterOutputStream
{
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param out 
     */
    public DumpFilterOutputStream(OutputStream out)
    {
        super(out,null,0);
    }
    
    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param out 
     */
    public DumpFilterOutputStream(OutputStream out,
                                  String name)
    {
        super(out,name,0);
    }

}







