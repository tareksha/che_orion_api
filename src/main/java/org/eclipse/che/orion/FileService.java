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
import java.io.InputStream;
import java.net.URISyntaxException;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.orion.dto.FileMetadata;
import org.eclipse.orion.server.core.ProtocolConstants;

@Path("/file")
public class FileService extends ServiceBase {

    private static final String HEADER_PARTS = "parts";

    private static final String FILEPATH_PARAM = "fpath";
    private static final String FILEPATH_PFX = "/{" + FILEPATH_PARAM + ":.*}";

    @GET
    @Path(FILEPATH_PFX)
    public Response getFile(@PathParam(FILEPATH_PARAM) String filePath, @QueryParam(HEADER_PARTS) String parts)
            throws IOException {
        final PathInfo pathInfo = splitWorkspacePath(filePath);
        // Content?
        if ("body".equals(parts)) {
            return null;
        }
        // Get the metadata
        Response cheResp = getProjectTarget().path("item").path(pathInfo.path).request().get();
        assertValidResponse(cheResp);
        ItemReference itemRef = unmarshalResponse(cheResp, ItemReference.class);
        FileMetadata fileMD = createFileMD(itemRef, Constants.CHE_WORKSPACE);
        return Response.ok().entity(fileMD).build();
    }

    @POST
    @Path(FILEPATH_PFX)
    public Response createEmptyFile(@PathParam(FILEPATH_PARAM) String parentPath,
            @HeaderParam(ProtocolConstants.HEADER_SLUG) String fileName, String optionsStr) throws IOException,
            URISyntaxException {
        return commonCreateFileOrFolder(parentPath, fileName, null, optionsStr);
    }

    @PUT
    @Path(FILEPATH_PFX)
    public Response updateFileContents(@PathParam(FILEPATH_PARAM) String filePathStr,
            @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType, InputStream contents) throws IOException,
            URISyntaxException {
        PathInfo filePath = splitWorkspacePath(filePathStr);
        Builder cheReqBuilder = getFileTarget(filePath.workspaceId, filePath.path).request();
        Response cheResp = cheReqBuilder.put(Entity.entity(contents, contentType));
        assertValidResponse(cheResp);
        return Response.ok().build();
    }

    @DELETE
    @Path(FILEPATH_PFX)
    public Response deleteFile(@PathParam(FILEPATH_PARAM) String filePath) {
        final PathInfo pathInfo = splitWorkspacePath(filePath);
        Builder cheReqBuilder = getProjectTarget().path(pathInfo.path).request();
        Response cheResp = cheReqBuilder.delete();
        assertValidResponse(cheResp);
        return Response.ok().build();
    }

    private WebTarget getProjectTarget() {
        return getTarget().path("project").path(Constants.CHE_WORKSPACE);
    }
}
