/*
  GNUJSP - a free JSP1.0 implementation
  Copyright (C) 1999, Yaroslav Faybishenko <yaroslav@cs.berkeley.edu>

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  as published by the Free Software Foundation; either version 2
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.gjt.jsp;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;

class JavaEmitter extends Emitter 
    implements JspMsg
{
    public static final String HTTP_JSP_PAGE_IMPL = "org.gjt.jsp.HttpJspPageImpl";
    public static final String HTTP_JSP_PAGE_UTIL = "org.gjt.jsp.HttpJspPageUtil";

    public static final void emitPos(Writer w, JspNode node) 
	throws IOException
    {
	Pos pos = node.getPos();

	if(pos != null) {
	    w.write (pos.toJavaCode()); w.write("\n");
	}
    }

    public void emitBeginPage (Writer w, JspNode node) throws IOException {
	Pos pos = node.getPos();

	String import_ = node.getAttribute("import","") 
	    + ",java.io.IOException,javax.servlet.*,javax.servlet.http.*,javax.servlet.jsp.*";
	String declaration = node.getAttribute("declaration","");
	String package_ = node.getAttribute("package");
	String class_ = node.getAttribute("class");
	String info = node.getAttribute("info");

	if (class_ == null)
	    throw new ParseException("className not set");

	String isThreadSafeStr = node.getAttribute("isThreadSafe","true");
	if (!("true".equals(isThreadSafeStr) || "false".equals(isThreadSafeStr))) 
	    throw new ParseException(pos, ERR_sp10_2_7_1_invalid_value_isthreadsafe);

	boolean isThreadSafe = Boolean.valueOf(isThreadSafeStr).booleanValue();
	String extends_ = node.getAttribute("extends", HTTP_JSP_PAGE_IMPL);
	String contentType = node.getAttribute("contentType","text/html");
	String errorPage = node.getAttribute("errorPage");

	String isErrorPageStr = node.getAttribute("isErrorPage","false");
	if (!("true".equals(isErrorPageStr) || "false".equals(isErrorPageStr))) 
	    throw new ParseException(pos, ERR_sp10_2_7_1_invalid_value_iserrorpage);

	boolean isErrorPage = Boolean.valueOf(isErrorPageStr).booleanValue();
	boolean session = Boolean.valueOf(node.getAttribute("session","true")).booleanValue();
	String bstemp = node.getAttribute("buffer","8kb");
	int bufferSize = 8192;
	if (bstemp.endsWith("kb")) {
	    try {
		bufferSize = 1024 * Integer.parseInt(bstemp.substring(0, bstemp.length() - 2));
	    } catch (NumberFormatException nfe) { 
		throw new ParseException(pos, ERR_sp10_2_7_1_invalid_value_buffersize);
	    }
	} else if (bstemp.equals("none")) {
	    bufferSize = 0;
	} else 
	    throw new ParseException(pos, ERR_sp10_2_7_1_invalid_value_buffersize);

	boolean autoFlush = Boolean.valueOf(node.getAttribute("autoFlush", "true")).booleanValue();

	if (!autoFlush && bufferSize == 0)
	    throw new ParseException(pos, ERR_sp10_2_7_1_invalid_value_no_buffer_no_autoflush);

	w.write ("/*\n");
	w.write ("   Generated by JavaEmitter on "+new Date () + "\n");
	w.write ("   Please do not modify.\n");
	w.write (" */\n");
	w.write ("\n");

	if (package_ != null) w.write ("package " + package_ + ";\n\n");

	StringTokenizer ie = new StringTokenizer(import_, ",");
	while (ie.hasMoreElements ()) {
	    String imp = ie.nextToken();
	    if (!("".equals(imp))) {
		w.write ("import " + imp + ";\n");
	    }
	}

	w.write ("\n");
	w.write ("public class " + class_);

	w.write (" extends " + extends_);

	if (!isThreadSafe) {
	    w.write (" implements SingleThreadModel");
	}

	w.write("{\n");
	// FIXME: no node abstraction for declaration means no pos info (alph)
	//	if(pos != null) {
	// w.write (pos.toJavaCode()); w.write("\n");
	// }
	w.write(declaration);
	w.write("\n");

	// We add static methods to generated class for dependency info
	w.write("   public final long _gnujspGetTimestamp() {\n");
	w.write("      return " + System.currentTimeMillis() + "L;\n");
	w.write("   }\n");

	w.write("   private static final String[] _gnujspDeps = new String[] { ");
	String deps = node.getAttribute("gnujspDeps");
	StringTokenizer toke = new StringTokenizer(deps, ",");
	int len = toke.countTokens();
	for (int i = 0; i < len; i++) {
	    if (i > 0) w.write(", ");
	    enquote(w, toke.nextToken());
	}
	w.write(" };\n");
	w.write("   public final String[] _gnujspGetDeps() {\n");
	w.write("      return _gnujspDeps;\n");
	w.write("   }\n");

	w.write("   public final long _gnujspGetCompilerVersion() {\n");
	w.write("      return " + node.getAttribute("gnujspCompilerVersion") + "L;\n");
	w.write("   }\n");

	if (info != null) {
	    w.write("   public String getServletInfo() {\n");
	    w.write("     return ");
	    enquote(w, info);
	    w.write(";\n");
	    w.write("   }\n");
	    w.write("\n");
	}
	w.write ("   public void _jspService (HttpServletRequest  request,\n");
	w.write ("                            HttpServletResponse response)\n");
	w.write ("       throws ServletException, IOException\n");
	w.write ("   {\n");
	w.write ("       response.setContentType (\""+ contentType + "\");\n");
	w.write ("");
	w.write ("       JspFactory  factory     = " +
		     "JspFactory.getDefaultFactory ();\n");
	w.write ("       PageContext pageContext = " +
		     "factory.getPageContext (this,\n");
	w.write ("                                  request,\n");
	w.write ("                                  response,\n");
	if(errorPage != null) {
	    w.write ("                                  \"" + errorPage +
		     "\",\n");
	} else {
	    w.write ("                                  null,\n");
	}
	w.write ("                                  "+ session +",\n");
	w.write ("                                  "+ bufferSize +",\n");
	w.write ("                                  "+ autoFlush +");\n");
	if (session)
	    w.write ("       HttpSession session   = pageContext.getSession ();\n");

	// FIXME: do something if exception is null?
	if (isErrorPage) {
	    w.write ("       Throwable exception = pageContext.getException ();\n");
	}
	w.write ("       ServletContext application = pageContext.getServletConfig().getServletContext();\n");
	w.write ("       JspWriter   out       = pageContext.getOut ();\n");
	w.write ("       ServletConfig config  = pageContext.getServletConfig();\n");
	w.write ("       Object      page      = this;\n");
	w.write ("\n");
	w.write ("       try {\n");
	w.write ("\n");

	w.flush ();
    }
   
    public void emitBeginScriptlet (Writer w, JspNode n) throws IOException {
	emitPos(w, n);
	// do nothing for direct java code generation
    }

    public void emitScriptlet (Writer w, JspNode n, String code) throws IOException {
	w.write(code);
    }

    public void emitEndScriptlet (Writer w) throws IOException {
	w.write("\n"); // Make sure next output starts on new line
    }

    public void emitBeginPrintExprCall (Writer w, JspNode n) throws IOException {
	emitPos(w, n);
	w.write ("out.print (");
    }

    public void emitPrintExprCall (Writer w, JspNode n, String expr) throws IOException {
	w.write(expr);
    }

    public void emitEndPrintExprCall (Writer w) throws IOException {
	w.write (");\n");
    }

    public void emitTemplateText (Writer w, JspNode n, String t) throws IOException {
	w.write("\n"); // make sure we start pos on new line
	emitPos(w, n);
	w.write ("out.print (");
	enquote(w,t);
	w.write (");\n");
    }

    public void emitForward (Writer w, JspNode n, String page) throws IOException {
	Pos pos = n.getPos();
	
	if (page == null) {
	    throw new ParseException(pos, ERR_sp10_2_13_5_missing_attr_page);
	}
	if(pos != null) {
	    w.write (pos.toJavaCode()); w.write("\n");
	}
	// 2.13.5 "A jsp:forward effectively terminates the execution of
	// the current page."
	w.write ("if(true) {\n");
	// 2.12.1 "The following attributes accept request-time attribute
	// expressions... The page attribute of jsp:forward"
	w.write ("    pageContext.forward ("+inlineAttribute(pos, page)+");\n");
	w.write ("    return;\n");
	w.write ("}\n");
    }

    public void emitInclude (Writer w, JspNode node, String page, String flush) throws IOException {
	Pos pos = node.getPos();

	if (page == null) {
	    throw new ParseException(pos, ERR_sp10_2_13_4_missing_attr_page);
	}
	if (flush == null) {
	    throw new ParseException(pos, ERR_sp10_2_13_4_missing_attr_flush);
	}
	boolean flushVal = Boolean.valueOf(flush).booleanValue();
	if (!flushVal) {
	    throw new ParseException (pos, ERR_sp10_2_13_4_illegal_value_flush_not_true);
	}
	if(pos != null) {
	    w.write (pos.toJavaCode()); w.write("\n");
	}
	
	// 2.12.1 "The following attributes accept request-time attribute
	// expressions... The page attribute of jsp:include"
	w.write ("pageContext.include ("+inlineAttribute (pos, page)+");\n");
    }

    public void emitBeginUseBean (Writer w, JspNode node, String id, String scope,
			   String type, String clas, String beanName)
	throws IOException
    {
	Pos pos = node.getPos();

	if (type == null && clas==null) 
	    throw new ParseException(pos, ERR_sp10_2_13_1_missing_attr_type_or_class);
	if (id == null) 
	    throw new ParseException(pos, ERR_sp10_2_13_1_missing_attr_id);
	if (scope == null) scope = "page";
	if (("session".equals(scope)) && "false".equals(node.getPageAttribute("session")))
	    throw new ParseException(pos, ERR_sp10_2_13_1_illegal_session_scope);
	
	if (type == null)  type = clas;
	if ((clas != null) && (beanName != null))
	    throw new ParseException(pos, 
				     JspConfig.getLocalizedMsg(ERR_sp10_2_13_1_illegal_attr_class_and_beanname)
				     + " " + id);
	if(pos != null) {
	    w.write (pos.toJavaCode("+")); w.write("\n");
	}

	w.write ("    " + type + " " + id + " = (" + type + ")\n");
	w.write ("       pageContext.getAttribute (\"" +
		 id + "\", PageContext." +
		 scope.toUpperCase () +"_SCOPE);\n");

	if (clas != null) {
	    w.write ("    if (" + id + " == null) {\n");
	    w.write ("        " + id + " = new " + clas + " ();\n");
	   
	} else if (beanName != null) {
	    w.write ("    if (" + id + " == null) {\n");
	    // 2.12.1 "The following attributes accept request-time attribute
	    // expressions... The beanName attribute of [jsp:useBean]"
	    // Make better error message by catching ClassNotFoundException
	    w.write ("      try {\n");
	    w.write ("        " + id + " = (" + type + ") java.beans.Beans.instantiate ("+
		     "this.getClass ().getClassLoader (), " + 
		     inlineAttribute(pos, beanName) +");\n");
	    w.write ("      } catch(ClassNotFoundException _gnujspex) {");
	    w.write ("        throw new InstantiationException(\""
		     + JspConfig.getLocalizedMsg(ERR_sp10_2_13_1_could_not_instantiate_bean_class_not_found)
		     + " '\"+"
		     + inlineAttribute(pos, beanName) +"+\"'\");\n");
	    w.write (" }\n");
	}

	// test if bean could be instantiated, runtime exception !
	w.write("        if (" + id + " == null)\n");        
	w.write("            throw new InstantiationException(\""
		+ JspConfig.getLocalizedMsg(ERR_sp10_2_13_1_could_not_instantiate_bean)
		+ ": " + id + "\");");

	if (clas != null || beanName != null)
	    w.write ("       pageContext.setAttribute (\"" +
		     id + "\", " + id + ", PageContext." +
		     scope.toUpperCase () +"_SCOPE);\n");

	// type only
	if(clas == null && beanName == null) {
	    // 2.13.1 If the object is found ... 
	    // If the jsp:useBean element had a non-empty body it is ignored. 
	    // This completes the processing of the useBean action
	    w.write("    if (false) {\n");
	}
    }

    public void emitEndUseBean (Writer w) throws IOException {
	w.write ("    }\n");
    }

    public void emitGetProperty (Writer w, JspNode node, String name, String property) 
	throws IOException
    {
	Pos pos = node.getPos();

	if (name == null) throw new ParseException
			      (pos, ERR_sp10_2_13_3_missing_attr_name);
	if (property == null) throw new ParseException
				  (pos, ERR_sp10_2_13_3_missing_attr_property);
	
	if(pos != null) {
	    w.write (pos.toJavaCode()); w.write("\n");
	}
	w.write ("    out.print("
		 +HTTP_JSP_PAGE_UTIL+".getProperty (" 
		 + name + ", \"" + property+ "\", \"" + name + "\"));\n");
    }

    public void emitSetProperty (Writer w, JspNode node,
				 String name, String property, 
				 String param, String value)
	throws IOException
    {
	Pos pos = node.getPos();

	if (property == null) {
	    throw new ParseException (pos, 
				      ERR_sp10_2_13_2_missing_attr_property);
	}
	
	if (name == null) throw new ParseException
			      (pos, ERR_sp10_2_13_2_missing_attr_name);
	if (param != null && value != null) 
	    throw new ParseException
		(pos, ERR_sp10_2_13_2_illegal_attr_param_and_value);

	if(pos != null) {
	    w.write (pos.toJavaCode()); w.write("\n");
	}

	// setProperty name="foo" property="bar" looks for param "bar".
	if (param == null && value == null) param = property;

	if (property.equals ("*")) {
	    w.write ("    "+HTTP_JSP_PAGE_UTIL
		     +".setProperties (" + name + ", request);\n");

	} else if (param != null) {
	    w.write ("    if (true) {\n"); // Needed for scoping s.
	    // 2.13.2 If parameter has a value of "" the
	    // corresponding property is not modified.
	    // we detect this by the following logic on request.getParameterValues():
	    // null OR length is 0 OR length is 1 AND (element 0 is null
	    // or the empty string).

	    // Always pass param as a String[]; Util class decides whether
	    // to use it as a String or an array.
	    w.write ("      String[] s = request.getParameterValues(\""
		     + param + "\");\n");
	    w.write ("      if ((s != null && s.length > 0) && ");
	    w.write ("(!(s.length == 1 && (s[0] == null || s[0].equals(\"\"))))) \n");
	    
	    w.write ("           "+HTTP_JSP_PAGE_UTIL
		     +".setProperty (" + name + ",\n");
	    w.write ("                       \"" + property + "\",\n");
	    w.write ("                       s);\n");
	    w.write ("    }\n");
	} else if (value != null) {
	    // 2.12.1 "The following attributes accept request-time attribute
	    // expressions... The value attribute of jsp:setProperty"
	    w.write ("    "+HTTP_JSP_PAGE_UTIL+".setProperty (" 
		     + name + ", " + "\"" + property+
		     "\", "+HTTP_JSP_PAGE_UTIL+".objectify(" 
		     + inlineAttribute (pos, value) + "));\n");
	}
    }

    public void emitEndPage (Writer w, JspNode n) throws IOException {
	w.write ("        } catch (Exception e) {\n");
	//	w.write ("             out.clear ();\n");
	// I know, this does not follow the example in the 1.0 spec
	// but we need to handle and report exceptions after 
	// flush has been halled (e.g. by pageCOntext.include())
	// if someone comes with a better solutiion, tell me. (alph)
	w.write ("             out.clearBuffer ();\n");
	w.write ("             pageContext.handlePageException (e);\n");
	w.write ("        } finally {\n");
	w.write ("             out.flush();\n");
	//	w.write ("             out.close ();\n");
	w.write ("             factory.releasePageContext (pageContext);\n");
	w.write ("        }\n");
	w.write ("\n");
	w.write ("   }\n");
	w.write ("\n");
	w.write ("}\n");

	w.flush ();
    }

    /**
     * Converts a string into a format that can be used in a 
     * print() function, surrounding it with double quotes and
     * escaping any necessary control characters.
     */
    void enquote(Writer w, String t) throws IOException {
	w.write('"');
	char c;
	for (int i = 0; i < t.length(); i++) {
	    switch (c = t.charAt(i)) {
	    case '\t': 
		w.write ("\\t");
		break;
	    case '\n': 
		w.write ("\\n");
		break;
	    case '\r': 
		w.write ("\\r"); 
		break;
	    case '\\': 
		w.write ("\\\\"); 
		break;
	    case '\"': 
		w.write ("\\");
	    default: 
		w.write (c);
	    }
	}
	w.write('"');
    }

    /**
     * Translates a String argument, encoding any necessary request-time
     * arguments, and prepending and appending a double-quote character.
     * Also, escape any double-quotes, etc., in the string.
     *
     * Example 1:               "foo" --> "\"foo\""
     * Example 2:       "<%= name %>" --> "\"" + name + "\"" 
     */
    String inlineAttribute (Pos pos, String val) throws IOException {
	int pos1 = val.indexOf("<%=");
	int pos2 = val.indexOf("%>");
	if ((pos1 == 0) && (pos2 == val.length() - 2)) {
	    return val.substring(3, val.length() - 2);
	}
	if ((pos1 >= 0) && (pos1 < pos2)) {
	    throw new ParseException
		(pos, 
		 JspConfig.getLocalizedMsg
		 (ERR_sp10_2_12_1_invalid_value_expression_and_string)
		 +": " + val );
	}

	StringBuffer b = new StringBuffer();
	int prev = 0;
	int i = 0;
	do {
	    pos1 = val.indexOf("<\\%", i);
	    pos2 = val.indexOf("%\\>", i);
	    if (pos1 == pos2) break; // both are -1, we're done.
	    i = 1 + (((pos2 == -1) || ((pos1 >= 0) && (pos1 < pos2))) 
		     ? pos1 : pos2);
	    b.append(val.substring(prev, i));
	    prev = ++i;
	} while (true);
	b.append(val.substring(prev));
	StringWriter w = new StringWriter();
	enquote(w,b.toString());
	return w.toString();
    }
}