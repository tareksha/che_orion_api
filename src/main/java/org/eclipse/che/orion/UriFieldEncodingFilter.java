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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import org.eclipse.che.orion.dto.UriField;
import org.eclipse.jetty.util.URIUtil;

/**
 * This WS RS filter goes over the fields in the response entity and makes sure they are encoded for URI use.
 * 
 * @author Tareq Sharafy (tareq.sha@gmail.com)
 */
public class UriFieldEncodingFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        Object entity = responseContext.getEntity();
        encodeUriFields(entity);

    }

    /**
     * Encode the fields that are annotated with {@link UriField}
     */
    public static void encodeUriFields(Object object) {
        if (object == null) {
            return;
        }
        Field[] fields = object.getClass().getFields();
        for (Field field : fields) {
            try {
                Object fieldValue = field.get(object);
                if (fieldValue == null) {
                    continue;
                }
                if (fieldValue instanceof String) {
                    // Encode string fields that are annotated
                    if (field.isAnnotationPresent(UriField.class)) {
                        String encodedValue = URIUtil.encodePath(fieldValue.toString());
                        field.set(object, encodedValue);
                    }
                } else if (fieldValue instanceof Collection<?>) {
                    // Recursively scan items inside collections
                    for (Object item : (Collection<?>) fieldValue) {
                        encodeUriFields(item);
                    }
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
            }
        }
    }

}
