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
package org.eclipse.che.orion.dto;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * String fields that are annotated with UriField in this application's responses are scanned and encoded for URI usage.
 * 
 * @author Tareq Sharafy (tareq.sha@gmail.com)
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface UriField {
}
