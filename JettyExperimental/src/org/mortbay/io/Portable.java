/*
 * Created on 20-Mar-2003
 *
 */
package org.mortbay.io;

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
