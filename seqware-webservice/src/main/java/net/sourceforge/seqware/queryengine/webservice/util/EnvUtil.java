/**
 * 
 */
package net.sourceforge.seqware.queryengine.webservice.util;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * <p>EnvUtil class.</p>
 *
 * @author boconnor
 * @version $Id: $Id
 */
public class EnvUtil {

  /**
   * <p>getProperty.</p>
   *
   * @param name a {@link java.lang.String} object.
   * @return a {@link java.lang.String} object.
   */
  public static String getProperty(String name) {
    
    String value = null;
    
    // try pulling from standard env variable for running as standalone web server 
    value = System.getProperty(name);
    
    // if that's null then try pulling from web.xml or context.xml
    if (value == null) {
      Context initCtx;
      try {
        initCtx = new InitialContext();
        Context envCtx = (Context) initCtx.lookup("java:comp/env");
        value = (String)envCtx.lookup(name);
      } catch (NamingException e) {
        e.printStackTrace();
      }
    }
    
    return value;
    
  }
  
}
