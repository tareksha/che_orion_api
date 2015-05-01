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

import java.util.ArrayList;
import java.util.List;

/**
 * Meta-data about a workspace. (Orion compliant)
 * 
 * @author i062001 Tareq Sharafy
 */
public class WorkspaceMetadata extends BasicChildDirectory {

	public static class ChildProjectMeta extends BasicChildDirectory {
		public String ImportLocation;
		public long LocalTimeStamp;
	}

	public List<ChildProjectMeta> Children = new ArrayList<ChildProjectMeta>();
	public String DriveLocation;
	public List<ReferenceEntry> Projects = new ArrayList<ReferenceEntry>();
}
