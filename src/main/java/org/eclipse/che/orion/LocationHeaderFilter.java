/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.orion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.HttpHeaders;

/**
 * This post-filter takes the special schema place-holder 'LOC:' and removes it to restore the intended value.
 * 
 * @author Tareq Sharafy (tareq.sharafy@sap.com)
 */
public class LocationHeaderFilter implements Filter {

    /**
     * The specialized servlet output stream returns a temporary in-memory stream.
     * 
     * @author Tareq Sharafy (tareq.sharafy@sap.com)
     */
    private static class SimpleServletOutputStream extends ServletOutputStream {

        final ByteArrayOutputStream _out = new ByteArrayOutputStream();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener arg0) {
            try {
                arg0.onWritePossible();
            } catch (IOException e) {
            }
        }

        @Override
        public void write(int b) throws IOException {
            _out.write(b);
        }

    }

    /**
     * The wrapper response holds an instance of the temporary servlet stream above and uses it as the primary stream
     * until it is told by the filter to terminate it, after which it writes the temporary contents to the actual
     * servlet's stream and stops buffering. The temporary buffering state is important because the headers of the inner
     * response can not be changed after writing to the stream starts.
     * 
     * @author Tareq Sharafy (tareq.sharafy@sap.com)
     */
    private static class RealtiveLocationResponse extends HttpServletResponseWrapper {

        SimpleServletOutputStream _tmpStream = new SimpleServletOutputStream();

        public RealtiveLocationResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return _tmpStream != null ? _tmpStream : super.getOutputStream();
        }

        public void terminateTemporaryBuffer() throws IOException {
            if (_tmpStream == null) {
                return;
            }
            ServletOutputStream parentStream = super.getOutputStream();
            _tmpStream._out.writeTo(parentStream);
            _tmpStream = null;
        }

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        RealtiveLocationResponse wrapperResp = null;
        if (response instanceof HttpServletResponse) {
            wrapperResp = new RealtiveLocationResponse((HttpServletResponse) response);
        }
        // First, execute the handler chain
        chain.doFilter(request, wrapperResp != null ? wrapperResp : response);
        // Reset the location header and stop buffering
        restoreOriginalLocationHeader(wrapperResp);
        wrapperResp.terminateTemporaryBuffer();
    }

    private static void restoreOriginalLocationHeader(HttpServletResponse response) {
        final String location = response.getHeader(HttpHeaders.LOCATION);
        if (location != null && location.startsWith(Constants.LOCATION_PREFIX)) {
            final String finalLocation = location.substring(Constants.LOCATION_PREFIX.length());
            response.setHeader(HttpHeaders.LOCATION, finalLocation);
        }
    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
