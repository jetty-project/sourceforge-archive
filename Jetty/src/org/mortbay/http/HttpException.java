// ========================================================================
// $Id$
// Copyright 1999-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.http;
import java.io.IOException;

import org.mortbay.util.TypeUtil;


/* ------------------------------------------------------------ */
/** Exception for known HTTP error status. 
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
public class HttpException extends IOException
{
    private int _code;

    public int getCode()
    {
        return _code;
    }
    
    public String getReason()
    {
        return (String)HttpResponse.__statusMsg.get(TypeUtil.newInteger(_code));
    }
    
    public HttpException()
    {
        _code=HttpResponse.__400_Bad_Request ;
    }
    
    public HttpException(int code)
    {
        _code=code;
    }
    
    public HttpException(int code, String message)
    {
        super(message);
        _code=code;
    }

    public String toString()
    {
        String message=getMessage();
        String reason=getReason();
        return "HttpException("+_code+","+reason+","+message+")";
    }
}

