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

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class OrioApiApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> s = new HashSet<Class<?>>();
        // Classes
        s.add(FileService.class);
        s.add(WorkspaceService.class);
        // Filters
        s.add(UriFieldEncodingFilter.class);
        return s;
    }

}
