package ro.isdc.wro.http.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.model.resource.locator.UriLocator;
import ro.isdc.wro.model.resource.locator.UrlUriLocator;


/**
 * A specialized {@link HttpServletResponseWrapper} responsible for streaming response to provided {@link OutputStream}.
 * 
 * @author Alex Objelean
 * @since 1.4.5
 * @created 18 Mar 2012
 */
public class RedirectedStreamServletResponseWrapper
    extends HttpServletResponseWrapper {
  private static final Logger LOG = LoggerFactory.getLogger(RedirectedStreamServletResponseWrapper.class);
  /**
   * PrintWrapper of wrapped response.
   */
  private PrintWriter printWriter;
  /**
   * Servlet output stream of wrapped response.
   */
  private ServletOutputStream servletOutputStream;
  /**
   * Used to locate external resources. No wildcard handling is required.
   */
  private UriLocator externalResourceLocator = new UrlUriLocator() {
    protected boolean disableWildcards() {
      return true;
    };
  };
  
  /**
   * @param outputStream
   *          the stream where the response will be written.
   * @param response
   *          decorated response.
   */
  public RedirectedStreamServletResponseWrapper(final OutputStream outputStream, final HttpServletResponse response) {
    super(response);
    Validate.notNull(outputStream);
    // Both servletOutputStream and PrintWriter must be overriden in order to be sure that dispatched servlet will write
    // to the pipe.
    printWriter = new PrintWriter(outputStream);
    servletOutputStream = new DelegatingServletOutputStream(outputStream);
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void sendError(final int sc)
      throws IOException {
    onError(sc, "");
    super.sendError(sc);
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void sendError(final int sc, final String msg)
      throws IOException {
    onError(sc, msg);
    super.sendError(sc, msg);
  }
  
  /**
   * Use an empty stream to avoid container writing unwanted message when a resource is missing.
   * 
   * @param sc
   *          status code.
   * @param msg
   */
  private void onError(final int sc, final String msg) {
    LOG.debug("Error detected with code: {} and message: {}", sc, msg);
    final OutputStream emptyStream = new ByteArrayOutputStream();
    printWriter = new PrintWriter(emptyStream);
    servletOutputStream = new DelegatingServletOutputStream(emptyStream);
  }
  
  @Override
  public ServletOutputStream getOutputStream()
      throws IOException {
    return servletOutputStream;
  }
  
  /**
   * By default, redirect does not allow writing to output stream its content. In order to support this use-case, we
   * need to open a new connection and read the content manually.
   */
  @Override
  public void sendRedirect(final String location)
      throws IOException {
    try {
      LOG.debug("redirecting to: {}", location);
      final InputStream is = externalResourceLocator.locate(location);
      IOUtils.copy(is, servletOutputStream);
      is.close();
    } catch (final IOException e) {
      LOG.warn("{}: Invalid response for location: {}", e.getClass().getName(), location);
      throw e;
    }
  }
  
  @Override
  public PrintWriter getWriter()
      throws IOException {
    return printWriter;
  }
}
