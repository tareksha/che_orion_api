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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.UriBuilder;

import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.orion.dto.FileMetadata;
import org.eclipse.che.orion.dto.FileMetadata.FileParent;
import org.eclipse.che.orion.dto.req.FileOptions;
import org.eclipse.jetty.util.URIUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ServiceBase {

    // The REST cleint
    private final Client _client = ClientBuilder.newClient();
    // JSON manipulation
    private final Gson _gson = new GsonBuilder().create();

    static class PathInfo {
        public String workspaceId;
        public String path;
    }

    protected static PathInfo splitWorkspacePath(String path) {
        // Split the path
        int firstSlash = path.indexOf('/');
        PathInfo pi = new PathInfo();
        if (firstSlash < 0) {
            pi.workspaceId = path;
            pi.path = "";
        } else {
            pi.workspaceId = path.substring(0, firstSlash);
            pi.path = path.substring(firstSlash + 1);
        }
        return pi;
    }

    protected Response commonCreateFileOrFolder(String parentPathStr, String fileName, String createOptions,
            String optionsStr) throws IOException, URISyntaxException {
        FileOptions fileOptions = parseJson(optionsStr, FileOptions.class);
        // Split the path
        PathInfo parentPath = splitWorkspacePath(parentPathStr);
        // Pick correct file/folder name
        final String actualName = (fileName == null || fileName.isEmpty() ? fileOptions.Name : fileName);
        // Is it a direct?
        Invocation.Builder invocBuilder;
        if (fileOptions.Directory) {
            // Create a directory
            // Che: POST /project/{ws-id}/folder/{path:.*}
            invocBuilder = getProjectTarget(parentPath.workspaceId).path("folder").path(parentPath.path)
                    .path(actualName).request();
        } else {
            // Create a file
            // Che: POST /project/{ws-id}/file/{parent:.*}
            invocBuilder = getFileTarget(parentPath.workspaceId, parentPath.path).queryParam("name", actualName)
                    .request().header(HttpHeaders.CONTENT_TYPE, fileOptions.ContentType);
        }
        Response cheResp = invocBuilder.post(null);
        assertValidResponse(cheResp);
        ItemReference createdRef = unmarshalResponse(cheResp, ItemReference.class);
        FileMetadata md = createFileMD(createdRef, parentPath.workspaceId);
        // Location header is expected to be relative in Orion, thus providing it through created() is incorrect
        // TODO tests fails if 'file' is preceded with '/' !
        return Response.created(encodeLocation(md.Location.substring(1))).entity(md).build();
    }

    /**
     * Create Orion file meta-data from Che information.
     */
    protected static FileMetadata createFileMD(ItemReference itemRef, String workspaceId) {
        // NOTE: This function should generate largely the same result from Orion's ServletFileStoreHandler.toJSON()
        final String itemPath = itemRef.getPath();
        final String createdPath = "/" + workspaceId + itemPath;
        FileMetadata md = new FileMetadata();
        md.ImportLocation = "/xfer/import" + createdPath;
        md.Location = "/file" + createdPath;
        md.Name = itemRef.getName();
        if (isDirectoryItem(itemRef)) {
            // Directory stuff
            md.Directory = true;
            md.Location += "/"; // Orion does that explicitly
            md.ChildrenLocation = md.Location + "?depth=1";
            md.Children = new ArrayList<FileMetadata>();
        } else {
            // File stuff
            md.Length = 0;
            md.LocalTimeStamp = itemRef.getModified();

        }
        // TODO maybe this should be retrieved from Che?
        // Path to parent
        final int lastSlashIdx = itemPath.lastIndexOf('/');
        if (lastSlashIdx > 0) {
            final String parentPath = itemPath.substring(0, lastSlashIdx + 1);
            FileParent parentMeta = new FileParent();
            parentMeta.Location = "/file/" + workspaceId + parentPath;
            parentMeta.ChildrenLocation = parentMeta.Location + "?depth=1";
            parentMeta.Name = new File(parentPath).getName();
            md.Parents.add(parentMeta);
        }
        return md;
    }

    public static boolean isDirectoryItem(ItemReference itemRef) {
        return "folder".equals(itemRef.getType());
    }

    protected Response copyOrMoveItem(String fullParentPath, String fileName, String createOptions,
            FileOptions fileOptions) throws IOException, URISyntaxException {
        // Split the path
        int firstSlash = fullParentPath.indexOf('/');
        if (firstSlash < 0) {
            throw new BadRequestException();
        }
        final String workspaceId = fullParentPath.substring(0, firstSlash);
        final String parentPath = fullParentPath.substring(firstSlash + 1);
        // Pick correct file/folder name
        final String actualName = (fileName == null || fileName.isEmpty() ? fileOptions.Name : fileName);
        // Is it a direct?
        WebTarget target = getProjectTarget(workspaceId);
        Invocation.Builder invocBuilder;
        if (fileOptions.Directory) {
            // Create a directory
            // Che: POST /project/{ws-id}/folder/{path:.*}
            invocBuilder = target.path("folder").path(parentPath).path(actualName).request();
        } else {
            // Create a file
            // Che: POST /project/{ws-id}/file/{parent:.*}
            invocBuilder = target.path("file").path(parentPath).queryParam("name", actualName).request()
                    .header(HttpHeaders.CONTENT_TYPE, fileOptions.ContentType);
        }
        Response cheResp = invocBuilder.post(null);
        assertValidResponse(cheResp);
        ItemReference createdRef = unmarshalResponse(cheResp, ItemReference.class);
        final String createdPath = "/" + workspaceId + createdRef.getPath();
        FileMetadata md = new FileMetadata();
        md.Directory = fileOptions.Directory;
        md.ImportLocation = "/xfer/import" + createdPath;
        md.Length = 0;
        md.LocalTimeStamp = createdRef.getModified();
        md.Location = "/file" + createdPath;
        md.Name = createdRef.getName();
        // TODO maybe this should be retrieved from Che?
        FileParent parentMeta = new FileParent();
        parentMeta.Location = "/file/" + workspaceId + "/" + (parentPath.isEmpty() ? "" : parentPath + "/");
        parentMeta.ChildrenLocation = parentMeta.Location + "?depth=1";
        parentMeta.Name = new File(parentPath).getName();
        md.Parents.add(parentMeta);
        // Location header is expected to be relative in Orion, thus providing it through created() is incorrect
        // TODO tests fails if 'file' is preceded with '/' !
        return Response.created(encodeLocation("file" + createdPath)).entity(md).build();
    }

    protected <T> T parseJson(String json, Class<T> clazz) {
        return _gson.fromJson(json, clazz);
    }

    protected static Entity<String> createEntityFromDto(Object dtoObj) {
        return Entity.entity(DtoFactory.getInstance().toJson(dtoObj), MediaType.APPLICATION_JSON_TYPE);
    }

    protected static <T> T unmarshalResponse(Response resp, Class<T> clz) throws IOException {
        InputStream is = (InputStream) resp.getEntity();
        return DtoFactory.getInstance().createDtoFromJson(is, clz);
    }

    protected static <T> List<T> unmarshalListResponse(Response resp, Class<T> clz) throws IOException {
        InputStream is = (InputStream) resp.getEntity();
        return DtoFactory.getInstance().createListDtoFromJson(is, clz);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // Targets
    protected WebTarget getFileTarget(String wsId, String path) {
        return getTarget().path("project").path(wsId).path("file").path(path);
    }

    protected WebTarget getProjectTarget(String wsId, String projId) {
        return getTarget().path("project").path(wsId).path(projId);
    }

    protected WebTarget getProjectTarget(String wsId) {
        return getTarget().path("project").path(wsId);
    }

    protected WebTarget getWsTarget(String wsId) {
        return getWsTarget().path(wsId);
    }

    protected WebTarget getWsTarget() {
        return getTarget().path("workspace");
    }

    protected WebTarget getTarget() {
        return _client.target("http://localhost:8080/api");
    }

    protected UriBuilder getServiceUriBuilder() {
        return UriBuilder.fromPath("/");
        // return _uriInfo.getBaseUriBuilder().path("rest");
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods

    protected static Set<String> parseCommaSeparatedList(String list) {
        return list != null ? new HashSet<String>(Arrays.asList(list.split(","))) : new HashSet<String>();
    }

    protected static URI encodeLocation(String uri) throws URISyntaxException {
        return new URI(Constants.LOCATION_PREFIX + URIUtil.encodePath(uri));
    }

    protected static void assertValidResponse(Response resp) {
        if (resp.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new WebApplicationException("Errro in Che response", resp.getStatus());
        }
    }

}
