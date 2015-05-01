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
import java.util.List;

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
    public Response getFile(@PathParam(FILEPATH_PARAM) String filePath,
            @QueryParam(ProtocolConstants.PARM_DEPTH) int depth, @QueryParam(HEADER_PARTS) String parts)
            throws IOException {
        final PathInfo pathInfo = splitWorkspacePath(filePath);
        // Get the metadata
        Response cheResp = getProjectTarget().path("item").path(pathInfo.path).request().get();
        assertValidResponse(cheResp);
        ItemReference itemRef = unmarshalResponse(cheResp, ItemReference.class);
        // If the body is requested, retrieve the file
        if ((!isDirectoryItem(itemRef)) && (null == parts || "body".equals(parts))) {
            // Retrieve the file
            Response cheFileResp = getProjectTarget().path("file").path(pathInfo.path).request().get();
            assertValidResponse(cheResp);
            String fileContentType = cheFileResp.getHeaderString(HttpHeaders.CONTENT_TYPE);
            Object fileConent = cheFileResp.getEntity();
            return Response.ok().header(HttpHeaders.CONTENT_TYPE, fileContentType).entity(fileConent).build();
        }
        //
        FileMetadata fileMeta = createFileMD(itemRef, Constants.CHE_WORKSPACE);
        // Children?
        if (fileMeta.Directory) {
            fetchChildren(fileMeta, pathInfo.path, depth);
        }
        return Response.ok().entity(fileMeta).build();
    }

    private void fetchChildren(FileMetadata parent, String parentPath, int depth) throws IOException {
        if (depth <= 0) {
            return;
        }
        // The request to use is Che's /project/{ws}/children/{path}
        Response cheResp = getProjectTarget().path("children").path(parentPath).request().get();
        assertValidResponse(cheResp);
        List<ItemReference> childRefs = unmarshalListResponse(cheResp, ItemReference.class);
        // Convert each child to its Orion meta-data equivalent
        for (ItemReference childRef : childRefs) {
            // Create a meta-data for the child
            FileMetadata childMeta = createFileMD(childRef, Constants.CHE_WORKSPACE);
            parent.Children.add(childMeta);
            // Recursively fetch children of child directories
            if (childMeta.Directory) {
                fetchChildren(childMeta, parentPath + '/' + childMeta.Name, depth - 1);
            }
        }
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
