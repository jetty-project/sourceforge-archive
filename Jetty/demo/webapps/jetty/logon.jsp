<HTML>
<H1>FORM Authentication demo</H1>
<% out.print("<form method=\"POST\" action=\"");
   out.print(response.encodeURL("j_security_check"));
   out.print("\">");
 %>
<table border="0" cellspacing="2" cellpadding="1">
<tr>
  <td>Username:</td>
  <td><input size="12" value="" name="j_username" maxlength="25" type="text"></td>
</tr>
<tr>
  <td>Password:</td>
  <td><input size="12" value="" name="j_password" maxlength="25" type="password"></td>
</tr>
<tr>
  <td colspan="2" align="center">
    <input name="submit" type="submit" value="Login">
  </td>
</tr>
</table>
</form>
</HTML>