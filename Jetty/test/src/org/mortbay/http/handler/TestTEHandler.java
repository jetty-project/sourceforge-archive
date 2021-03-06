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

package org.mortbay.http.handler;

import java.io.IOException;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

/* ------------------------------------------------------------ */
/** Handler to test TE transfer encoding.
 * If 'gzip' or 'deflate' is in the query string, the
 * response is given that transfer encoding
 *
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class TestTEHandler extends AbstractHttpHandler
{
    /* ------------------------------------------------------------ */
    public void handle(String pathInContext,
                       String pathParams,
                       HttpRequest request,
                       HttpResponse response)
        throws HttpException, IOException
    {
        // For testing set transfer encodings
        if (request.getQuery()!=null)
        {
            if (request.getQuery().indexOf("gzip")>=0)
            {
                response.setField(HttpFields.__TransferEncoding,"gzip");
                response.addField(HttpFields.__TransferEncoding,"chunked");
            }
            if (request.getQuery().indexOf("deflate")>=0)
            {
                response.setField(HttpFields.__TransferEncoding,"deflate");
                response.addField(HttpFields.__TransferEncoding,"chunked");
            }
        }
    }
}
