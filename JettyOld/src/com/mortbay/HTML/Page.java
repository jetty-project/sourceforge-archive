// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTML;
import com.mortbay.Base.*;
import java.io.*;
import java.util.*;
import javax.servlet.*;

/* --------------------------------------------------------------------- */
/** HTML Page
 * A HTML Page extends composite with the addition of the HTML Header
 * tags, fields and elements.
 * Furthermore, individual parts of the page may be written or the
 * progressive page be output with flush.
 * <p>
 * Pages contain parameters and named sections. These are used by
 * derived Page classes that implement a Look and Feel.  Page users
 * may add to name sections such as "Margin" or "Footer" and set
 * parameters such as "HelpUrl" without knowledge of how the look and feel
 * will arrange these.  To assist with standard look and feel creation
 * Page defines a set of standard names for many common parameters
 * and sections.
 * <p>
 * PageFactory classes can be statically registered with the
 * Page class and these are used to instantiate Pages in the getPage
 * method.
 * <p>
 * If named sections are used, the page constructor or completeSections
 * must add the named section to the page in the appropriate places.
 * If named sections are not added to the page, then they can only be
 * written with an explicit call to write(out,"section",end);
 * Changes in behaviour to section creation and adding, should be controlled
 * via page properties.
 * <p>
 * @see class Composite
 * @version $Id$
 * @author Greg Wilkins
 */
public class Page extends Composite
{
    /* ----------------------------------------------------------------- */
    public static final String
        Request="Request",
        Response="Response",
        Header="Header",
        Title="Title",
        Section="Section",
        HeaderSize="HdrSize",  // HeaderSize string suitable for FRAMESET
        Footer="Footer",
        FooterSize="FtrSize",  // FooterSize string suitable for FRAMESET
        Content="Content",
        ContentSize="CntSize",
        Margin="Margin",
        MarginSize="MrgSize",
        LeftMargin="Left",
        LeftMarginSize="LMSize",
        RightMargin="Right",
        RightMarginSize="RMSize",
        Help="Help",
        Home="Home",
        Heading="Heading", 
        Up="Up",
        Prev="Prev",
        Next="Next",
        Back="Back",
        Target="Target",
        BaseUrl="BaseUrl",
        FgColour="FgColour",
        BgColour="BgColour",
        HighlightColour="HlColour",
        FileBase="FileBase",
        PageType="PageType",
        NoTitle="No Title"
        ;

    /* ----------------------------------------------------------------- */
    private static Vector pageFactories = new Vector();
    private static String defaultPage = "com.mortbay.HTML.Page";
    private static Hashtable classCache = new Hashtable(10);
    
    /* ----------------------------------------------------------------- */
    protected Hashtable properties = new Hashtable(10);

    /* ----------------------------------------------------------------- */
    Hashtable sections = new Hashtable(10);
    private Composite head= new Composite();
    private String base="";
    private boolean writtenHtmlHead = false;
    private boolean writtenBodyTag = false;

    /* ----------------------------------------------------------------- */
    public Page()
    {
        this(NoTitle);
    }

    /* ----------------------------------------------------------------- */
    public Page(String title)
    {
        title(title);
    }

    /* ----------------------------------------------------------------- */
    public Page(String title, String attributes)
    {
        title(title);
        attributes(attributes);
    }

    /* ----------------------------------------------------------------- */
    /** Set page title
     * @return This Page (for chained commands)
     */
    public Page title(String title)
    {
        properties.put(Title,title);
        String heading = (String)properties.get(Heading);
        if (heading==null||heading.equals(NoTitle))
            properties.put(Heading,title);
        return this;
    }

    /* ----------------------------------------------------------------- */
    /** Add element or object to the page header
     * @param o The Object to add. If it is a String or Element, it is
     * added directly, otherwise toString() is called.
     * @return This Page (for chained commands)
     */    
    public Page addHeader(Object o)
    {
        head.add("\n");
        head.add(o);
        return this;
    }
  
    /* ----------------------------------------------------------------- */
    /** Set page background image
     * @return This Page (for chained commands)
     */
    public final Page setBackGroundImage(String bg)
    {
        attribute("BACKGROUND",bg);
        return this;
    }
  
    /* ----------------------------------------------------------------- */
    /** Set page background color
     * @return This Page (for chained commands)
     */
    public final Page setBackGroundColor(String color)
    {
        properties.put(BgColour,color);
        attribute("BGCOLOR",color);
        return this;
    }
  
    /* ----------------------------------------------------------------- */
    /** Set the URL Base for the Page
     * @param target Default link target, null if none.
     * @param href Default absolute href, null if none.
     * @return This Page (for chained commands)
     */
    public final Page setBase(String target, String href)
    {
        base="<BASE " +
            ((target!=null)?("TARGET="+target):"") +
            ((href!=null)?("HREF="+href):"") +
            ">";
        return this;
    }

    /* ----------------------------------------------------------------- */
    /** Write the entire page by calling:<BR>
     * writeHtmlHead(out)<BR>
     * writeBodyTag(out)<BR>
     * writeElements(out)<BR>
     * writeHtmlEnd(out)
     */
    public void write(Writer out)
         throws IOException
    {
        writeHtmlHead(out);
        writeBodyTag(out);
        writeElements(out);
        writeHtmlEnd(out);
        out.flush();
    }
    
    /* ------------------------------------------------------------ */
    /** Write HTML page head tags
     * Write tags &ltHTML&gt&ltHEAD&gt .... &lt/HEAD&gt
     */
    public void writeHtmlHead(Writer out)
         throws IOException
    {
        if (!writtenHtmlHead)
        {
            writtenHtmlHead=true;
            completeSections();
            out.write("<HTML><HEAD>");
            String title=(String)properties.get(Title);
            if (title!=null && title.length()>0 && !title.equals(NoTitle))
                out.write("<TITLE>"+title+"</TITLE>");
            head.write(out);
            out.write(base);
            out.write("\n</HEAD>\n");
        }
    }
    
    /* ------------------------------------------------------------ */
    /** Write HTML page body tag
     * Write tags &ltBODY page attributes&gt.
     */
    public void writeBodyTag(Writer out)
         throws IOException
    {
        if (!writtenBodyTag)
        {
            writtenBodyTag = true;          
            out.write("<BODY "+attributes()+">\n");
        }
    }

    /* ------------------------------------------------------------ */
    /** Write end BODY and end HTML tags
     */
    public void writeHtmlEnd(Writer out)
         throws IOException
    {
        out.write("</BODY>");
        out.write("</HTML>");
    }
    
    /* ------------------------------------------------------------ */
    /** Write any body elements of the page
     */
    public void writeElements(Writer out)
         throws IOException
    {
        super.write(out);
    }
    
    /* ------------------------------------------------------------ */
    /** Write page section
     * The page is written containing only the named section.
     * If a head and bodyTag have not been written, then they
     * are written before the section. If endHtml is true, the
     * end HTML tag is also written.
     * If the named section is Content and it cannot be found,
     * then the normal page contents are written.
     */
    public void write(Writer out,
                      String section,
                      boolean endHtml)
         throws IOException
    {
        writeHtmlHead(out);
        writeBodyTag(out);
        Composite s = getSection(section);
        if (s==null)
        {
            if (section.equals(Content))
                writeElements(out);
        }
        else
            s.write(out);
        if (endHtml)
            writeHtmlEnd(out);
        out.flush();
    }
    
    /* ------------------------------------------------------------ */
    /* Flush the current contents of the page.
     * writeHtmlEnd() is not called and should either be
     * explicitly called or called via an eventual call to write()
     */
    public void flush(Writer out)
         throws IOException
    {
        writeHtmlHead(out);
        writeBodyTag(out);
        super.flush(out);
    }
    
    /* ------------------------------------------------------------ */
    /* Reset the page status to not written.
     * This is useful if you want to send a page more than once.
     */
     public void rewind()
    {
        writtenHtmlHead = false;
        writtenBodyTag = false;
    }
    
    /* ------------------------------------------------------------ */
    /** Access the page properties.  It is up to a derived Page class
     * to interpret these properties.
     */
    public Dictionary properties()
    {
        return properties;
    }

    /* ------------------------------------------------------------ */
    /** Return the preferred FrameSet to be used with a specialized Page
     * The Frames will be named after the sections they are to
     * contain.
     * The default implementation returns null
     */
    public FrameSet frameSet()
    {
        return null;
    }

    /* ------------------------------------------------------------ */
    /** Set a composite as a named section.  Other Page users may
     * add to the section by calling addTo().  It is up to the section
     * creator to add the section to the page in it appropriate position.
     */
    public void setSection(String section, Composite composite)
    {
        sections.put(section,composite);
    }
    
    /* ------------------------------------------------------------ */
    /** Set a composite as a named section and add it to the
     * contents of the page
     */
    public void addSection(String section, Composite composite)
    {
        sections.put(section,composite);
        add(composite);
    }
    
    /* ------------------------------------------------------------ */
    /** Get a composite as a named section. 
     */
    public Composite getSection(String section)
    {
        return (Composite)sections.get(section);
    }

    /* ------------------------------------------------------------ */
    /** Add content to a named sections.  If the named section cannot
     * be found, the content is added to the page.
     */
    public void addTo(String section, Object element)
    {
        Composite s = (Composite)sections.get(section);
        if (s==null)
            add(element);
        else
            s.add(element);
    }
    
    /* ------------------------------------------------------------ */
    /** This call back is called just before writeHeaders() actually
     * writes the HTML page headers. It can be implemented by a derived
     * Page class to complete a named section after the rest of the Page
     * has been created and appropriate properties set.
     */
    protected void completeSections()
    {
    }
    
    /* ------------------------------------------------------------ */
    /** Add a PageFactory to the static factory list. This
     * is used by the getPage() method when creating a new
     * page instance.
     */
    public static void addPageFactory(PageFactory f)
    {
        pageFactories.addElement(f);
    }
    
    /* ------------------------------------------------------------ */
    /** Delete a PageFactory from the static factory list.
     */
    public static void delPageFactory(PageFactory f)
    {
        pageFactories.removeElement(f);
    }
    
    /* ------------------------------------------------------------ */
    /** Get a new Page instance from a Page factory by
     * a named Page specialization of Page.
     * If no factory creates the page required, then the name is
     * tried as a ClassName. 
     * @param name The name of the page to pass to any
     *        registered PageFactories, otherwise the
     *        class name.
     * @param request The request that this page is being created
     *        for. The request can be used to select or tailor the
     *        that page and it is also placed in the pages
     *        Request property.
     */
    public static Page manufacturePage(String name,
                                       ServletRequest request,
                                       ServletResponse response)
         throws ClassNotFoundException,
                InstantiationException,
                IllegalAccessException,
                ClassCastException
    {
        Code.debug("Search pageFactories for "+name);
        for ( int f=0; f<pageFactories.size() ; f++)
        {
            PageFactory factory =
                (PageFactory) pageFactories.elementAt(f);
            Page p = factory.getPage(name,request,response);
            if (p!=null)
                return p;
        }

        Code.debug("Try instantiate "+name+" as Page subclass");
        Class c = (Class)classCache.get(name);
        if (c==null)
            c = Class.forName(name);
        classCache.put(name,c);
        Page p =(Page)c.newInstance();

        p.properties().put(Request,request);
        p.properties().put(Response,response);
        return p;
    }
    
    /* ------------------------------------------------------------ */
    /** Get a new Page instance from a Page factory by
     * calling manufacturePage(). If any error occurs, a vanilla
     * page is returned.
     * @param name The name of the page to pass to any
     *        registered PageFactories, otherwise the
     *        class name.
     * @param request The request that this page is being created
     *        for. The request can be used to select or tailor the
     *        that page and it is also placed in the pages
     *        Request property.
     */
    public static Page getPage(String name,
                               ServletRequest request,
                               ServletResponse response)
    {
        Page p=null;
        try{
            p = manufacturePage(name,request,response);
            if (p!=null)
            {
                
                return p;
            }
        }
        catch(ClassNotFoundException e){
            Code.debug("getSafePage handled",e);
        }
        catch(InstantiationException e){
            Code.debug("getSafePage handled",e);
        }
        catch(IllegalAccessException e){
            Code.debug("getSafePage handled",e);
        }
        catch(ClassCastException e){
            Code.debug("getSafePage handled",e);
        }
        
        Code.debug("Vanilla Page for "+name);
        p= new Page();
        p.properties().put(Request,request);
        p.properties().put(Response,response);
        return p;
    }


    /* ------------------------------------------------------------ */
    /** Get default Page
     * The default Page is set per JVM
     */
    public static String getDefaultPageType()
    {
        return defaultPage;
    }
    
    /* ------------------------------------------------------------ */
    /** set default Page
     * The default Page is set per JVM
     */
    public static void setDefaultPageType(String name)
    {
        defaultPage=name;
    }
    
}






