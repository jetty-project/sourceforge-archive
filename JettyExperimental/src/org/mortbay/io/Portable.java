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

/**
 * Portability class containing methods not available on all JVMs (specifically SuperWaba).
 * @author gregw
 *
 */
public class Portable
{
    public static void arraycopy(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length)
    {
        System.arraycopy(src, srcOffset, dst, dstOffset, length);
    }

    public static void throwNotSupported()
    {
        throw new RuntimeException("Not Supported");
    }

    public static void throwIllegalArgument(String msg)
    {
        throw new IllegalArgumentException(msg);
    }

    public static void throwIllegalState(String msg)
    {
        throw new IllegalStateException(msg);
    }

    public static void throwRuntime(String msg)
    {
        throw new RuntimeException(msg);
    }

    public static void throwNumberFormat(String string)
    {
        throw new NumberFormatException(string);
    }

    public static void throwIO(String string) throws IOException
    {
        throw new IOException(string);
    }

    public static byte[] getBytes(String s)
    {
        try
        {
            return s.getBytes("ISO8859_1");
        }
        catch (Exception e)
        {
            return s.getBytes();
        }
    }
}
