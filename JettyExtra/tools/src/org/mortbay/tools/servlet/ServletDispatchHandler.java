// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.tools.servlet;

import org.mortbay.util.Code;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Interface for handling unmatched path methods.
 * <p> A class should implement this interface if it wishes to handle
 * unmatched methods to be called by the ServletDispatch.dispatch method.
 *
 * @see org.mortbay.util.ServletDispatch
 * @version $Version: $
 * @author Matthew Watson (watsonm)
 */
public interface ServletDispatchHandler
{
    /* ------------------------------------------------------------ */
    /** Handle an unmatched method
     * @param method The name of the unmatched method - this will be null if
     *               there was no path left to handle
     * @param dispatch The dispatch object
     * @param context
     * @param req
     * @param res 
     * @return non-null if the request was handled
     */
    Object defaultDispatch(String method,
                           ServletDispatch dispatch,
                           Object context,
                           HttpServletRequest req,
                           HttpServletResponse res)
        throws Exception;
    /* ------------------------------------------------------------ */
}
