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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.xml.bind.JAXBException;

import org.eclipse.che.api.project.server.type.BaseProjectType;
import org.eclipse.che.api.project.shared.dto.NewProject;
import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.api.workspace.shared.dto.NewWorkspace;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDescriptor;
import org.eclipse.che.dto.server.DtoFactory;
import org.eclipse.che.orion.dto.ProjectMetadata;
import org.eclipse.che.orion.dto.ReferenceEntry;
import org.eclipse.che.orion.dto.WorkspaceMetadata;
import org.eclipse.che.orion.dto.WorkspaceMetadata.ChildProjectMeta;
import org.eclipse.che.orion.dto.req.ProjectProperties;
import org.eclipse.orion.server.core.ProtocolConstants;

/**
 * The root of all the Orion-compliant APIs that exposed from Che.
 * 
 * @author i062001 Tareq Sharafy
 */
@Path("/workspace")
public class WorkspaceService extends ServiceBase {

    // // Prefixes
    // Workspace prefixes
    private static final String WORKSPCE_ID_PARAM = "wsid";
    private static final String WORKSPACE_ID_PFX = "/{" + WORKSPCE_ID_PARAM + "}";
    // Project prefixes
    private static final String PROJECT_PFX = WORKSPACE_ID_PFX + "/project";
    private static final String PROJECT_ID_PARAM = "projid";
    private static final String PROJECT_ID_PFX = PROJECT_PFX + "/{" + PROJECT_ID_PARAM + "}";
    // Folder prefixes
    private static final String FOLDER_PATH_PARAM = "fpath";
    private static final String FOLDER_PATH_PFX = PROJECT_PFX + "/file/{" + FOLDER_PATH_PARAM + ":.*}";

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // Workspace operations

    /*
     * Get a list of the available workspaces
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listWorkspaces() {
        return Response.ok().build();
    }

    /*
     * Create a new workspace
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response createWorkspace(@HeaderParam(ProtocolConstants.HEADER_SLUG) String workspaceId,
            @Context SecurityContext context) throws IOException, URISyntaxException {
        NewWorkspace newWs = DtoFactory.getInstance().createDto(NewWorkspace.class);
        newWs.setName(workspaceId);
        newWs.setAccountId(Constants.DEFAULT_ACCOUNT);
        Response resp = getWsTarget().request().post(createEntityFromDto(newWs));
        WorkspaceDescriptor wsd = unmarshalResponse(resp, WorkspaceDescriptor.class);
        WorkspaceMetadata wsMeta = createWorkspaceMD(wsd, new ArrayList<ProjectDescriptor>());
        return Response.ok().header(HttpHeaders.LOCATION, wsMeta.Location).entity(wsMeta).build();
    }

    /*
     * Get workspace metadata
     */
    @GET
    @Path(WORKSPACE_ID_PFX)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WorkspaceMetadata getWorkspaceMetadata(@PathParam(WORKSPCE_ID_PARAM) String workspaceId,
            @Context SecurityContext context) throws IOException {
        // Get workspace meta-data
        Response resp1 = getWsTarget(workspaceId).request().get();
        assertValidResponse(resp1);
        WorkspaceDescriptor wsd = unmarshalResponse(resp1, WorkspaceDescriptor.class);
        // Get all sub-projects
        Response resp = getProjectTarget(workspaceId).request().get();
        List<ProjectDescriptor> projds = unmarshalListResponse(resp, ProjectDescriptor.class);
        return createWorkspaceMD(wsd, projds);
    }

    /*
     * Change workspace metadata
     */
    @PUT
    @Path(WORKSPACE_ID_PFX)
    public Response changeWorkspaceMetadata(@PathParam(WORKSPCE_ID_PARAM) String workspace) {
        return Response.ok().build();
    }

    /*
     * Remove a workspace
     */
    @DELETE
    @Path(WORKSPACE_ID_PFX)
    public Response removeWorkspace(@PathParam(WORKSPCE_ID_PARAM) String workspaceId) {
        return Response.ok().build();
    }

    private WorkspaceMetadata createWorkspaceMD(WorkspaceDescriptor wsd, List<ProjectDescriptor> projds) {
        WorkspaceMetadata md = new WorkspaceMetadata();
        md.Id = wsd.getId();
        md.Name = wsd.getName();
        md.Location = getServiceUriBuilder().path("workspace").path(wsd.getId()).build().toString();
        md.ChildrenLocation = md.Location;
        md.DriveLocation = md.Location + "/drive";
        for (ProjectDescriptor projd : projds) {
            ChildProjectMeta child = new ChildProjectMeta();
            fillProjectBasic(projd, child);
            child.LocalTimeStamp = projd.getModificationDate();
            child.ImportLocation = "/xfer/import/" + md.Id + "/" + child.Id;
            md.Children.add(child);
            ReferenceEntry ref = new ReferenceEntry();
            fillProjectRef(projd, ref);
            md.Projects.add(ref);
        }
        return md;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // Project operations

    @GET
    @Path(PROJECT_ID_PFX)
    public ProjectMetadata getProject(@PathParam(WORKSPCE_ID_PARAM) String workspaceId,
            @PathParam(PROJECT_ID_PARAM) String projectId) throws IOException {
        Response resp = getProjectTarget(workspaceId, projectId).request().get();
        ProjectDescriptor projd = unmarshalResponse(resp, ProjectDescriptor.class);
        return createProjectMD(projd);
    }

    private ProjectMetadata createProjectMD(ProjectDescriptor projd) {
        ProjectMetadata md = new ProjectMetadata();
        fillProjectBasic(projd, md);
        md.ContentLocation = getServiceUriBuilder().path("workspace").path(projd.getWorkspaceId()).path("project")
                .path("file").path(projd.getWorkspaceId()).path(projd.getName()).path("").build().toString();
        // TODO SearchLocation
        return md;
    }

    private void fillProjectBasic(ProjectDescriptor projd, ReferenceEntry md) {
        fillProjectRef(projd, md);
        md.Name = projd.getName();
    }

    private void fillProjectRef(ProjectDescriptor projd, ReferenceEntry md) {
        md.Id = projd.getPath().substring(1);
        md.Location = getServiceUriBuilder().path("workspace").path(projd.getWorkspaceId()).path("project").path(md.Id)
                .build().toString();
    }

    /*
     * Create a new project
     */
    @POST
    // @Consumes(MediaType.APPLICATION_JSON)
    @Path(WORKSPACE_ID_PFX)
    public Response createProject(@PathParam(WORKSPCE_ID_PARAM) String workspaceId,
            @HeaderParam(ProtocolConstants.HEADER_SLUG) String projectName,
            @HeaderParam(ProtocolConstants.HEADER_CREATE_OPTIONS) String createOptions, String optionsStr)
            throws IOException, URISyntaxException, JAXBException {
        // Parse input
        Set<String> options = parseCommaSeparatedList(createOptions);
        ProjectProperties properties = parseJson(optionsStr, ProjectProperties.class);
        // Is it a move/copy?
        String specialCreate = null;
        if (options.contains(ProtocolConstants.OPTION_COPY)) {
            specialCreate = "copy";
        } else if (options.contains(ProtocolConstants.OPTION_MOVE)) {
            specialCreate = "move";
        }
        // Create a default project with type "blank"
        NewProject newProj = DtoFactory.getInstance().createDto(NewProject.class);
        newProj.setName(projectName);
        newProj.setType(BaseProjectType.ID);
        Response cheResp = getProjectTarget(workspaceId).queryParam("name", projectName).request()
                .post(createEntityFromDto(newProj));
        // Handler request errors
        assertValidResponse(cheResp);
        ProjectDescriptor projd = unmarshalResponse(cheResp, ProjectDescriptor.class);
        ProjectMetadata md = createProjectMD(projd);
        // Location header is expected to be relative in Orion, thus providing it through created() is incorrect
        return Response.created(encodeLocation(md.Location)).entity(md).build();
    }

    /*
     * Remove a project.
     */
    @DELETE
    @Path(PROJECT_ID_PFX)
    public void removeProject(@PathParam(WORKSPCE_ID_PARAM) String workspaceId,
            @PathParam(PROJECT_ID_PARAM) String projectId) {
        Response resp = getProjectTarget(workspaceId).path(projectId).request().delete();
        assertValidResponse(resp);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // File operations

    @POST
    @Path(FOLDER_PATH_PFX)
    public Response createFileOrFolder(@PathParam(WORKSPCE_ID_PARAM) String workspaceId,
            @PathParam(FOLDER_PATH_PARAM) String parentPath,
            @HeaderParam(ProtocolConstants.HEADER_SLUG) String fileName, String optionsStr) throws IOException,
            URISyntaxException {
        return commonCreateFileOrFolder(parentPath, fileName, null, optionsStr);
    }
}
