// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTML;
import com.mortbay.Base.*;
import java.io.*;
import java.util.*;

/* ---------------------------------------------------------------- */
/** HTML Image Tag
 * @see com.mortbay.HTML.Block
 * @version $Id$
 * @author Greg Wilkins
*/
public class Image extends Tag
{
    /* ------------------------------------------------------------ */
    public Image(String src)
    {
        super("IMG");
        attribute("SRC",src);
    }
    
    /* ------------------------------------------------------------ */
    /** Construct from Gif file.
     */
    public Image(String dirname, String src)
    {
        super("IMG");
        attribute("SRC",src);
        setSizeFromGif(dirname,src);
    }
    
    /* ------------------------------------------------------------ */
    /** Construct from Gif file.
     */
    public Image(File gif)
    {
        super("IMG");
        attribute("SRC",gif.getName());
        setSizeFromGif(gif);
    }

    /* ------------------------------------------------------------ */
    public Image(String src,int width, int height, int border)
    {
        this(src);
        width(width);
        height(height);
        border(border);
    }
    
    /* ------------------------------------------------------------ */
    public Image border(int b)
    {
        attribute("BORDER",b);
        return this;
    }
    
    /* ------------------------------------------------------------ */
    public Image alt(String alt)
    {
        attribute("ALT",alt);
        return this;
    }
    
    /* ------------------------------------------------------------ */
    /** Set the image size from the header of a GIF file
     * @param dirname The directory name, expected to be in OS format
     * @param pathname The image path name relative to the directory.
     *                 Expected to be in WWW format (i.e. with slashes)
     *                 and will be converted to OS format.
     */
    public Image setSizeFromGif(String dirname,
                                String pathname)
    {
        String filename =dirname + pathname.replace('/',File.separatorChar);
        return setSizeFromGif(filename);
    }
    
    /* ------------------------------------------------------------ */
    /** Set the image size from the header of a GIF file
     */
    public Image setSizeFromGif(String filename)
    {
        return setSizeFromGif(new File(filename));
    }
    
    /* ------------------------------------------------------------ */
    /** Set the image size from the header of a GIF file
     */
    public Image setSizeFromGif(File gif)
    {
        if (gif.canRead())
        {
            try{
                byte [] buf = new byte[10];
                FileInputStream in = new FileInputStream(gif);
                if (in.read(buf,0,10)==10)
                {
                    Code.debug("Image "+gif.getName()+
                               " is " +
                               ((0x00ff&buf[7])*256+(0x00ff&buf[6])) +
                               " x " +
                               (((0x00ff&buf[9])*256+(0x00ff&buf[8]))));
                    width((0x00ff&buf[7])*256+(0x00ff&buf[6]));
                    height(((0x00ff&buf[9])*256+(0x00ff&buf[8])));
                }
            }
            catch (IOException e){
                Code.ignore(e);
            }
        }
        
        return this;
    }
    
}



