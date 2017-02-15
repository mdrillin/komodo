/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.komodo.rest.service;

import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_CREATE_DATASERVICE_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_DELETE_DATASERVICE_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_FIND_SOURCE_VDB_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_FIND_VIEW_INFO_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_GET_CONNECTIONS_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_GET_DATASERVICES_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_GET_DATASERVICE_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_GET_DRIVERS_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_SERVICE_NAME_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_ERROR;
import static org.komodo.rest.relational.RelationalMessages.Error.DATASERVICE_SERVICE_UPDATE_DATASERVICE_ERROR;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.komodo.core.KEngine;
import org.komodo.relational.ViewBuilderCriteriaPredicate;
import org.komodo.relational.ViewDdlBuilder;
import org.komodo.relational.dataservice.Dataservice;
import org.komodo.relational.datasource.Datasource;
import org.komodo.relational.model.Model;
import org.komodo.relational.model.Model.Type;
import org.komodo.relational.model.Table;
import org.komodo.relational.model.View;
import org.komodo.relational.resource.Driver;
import org.komodo.relational.vdb.ModelSource;
import org.komodo.relational.vdb.Vdb;
import org.komodo.relational.workspace.WorkspaceManager;
import org.komodo.repository.ObjectImpl;
import org.komodo.rest.KomodoRestException;
import org.komodo.rest.KomodoRestV1Application.V1Constants;
import org.komodo.rest.KomodoService;
import org.komodo.rest.relational.KomodoProperties;
import org.komodo.rest.relational.RelationalMessages;
import org.komodo.rest.relational.dataservice.RestDataservice;
import org.komodo.rest.relational.datasource.RestDataSource;
import org.komodo.rest.relational.json.KomodoJsonMarshaller;
import org.komodo.rest.relational.request.KomodoDataserviceUpdateAttributes;
import org.komodo.rest.relational.response.KomodoStatusObject;
import org.komodo.rest.relational.response.RestDataSourceDriver;
import org.komodo.rest.relational.response.RestDataserviceViewInfo;
import org.komodo.rest.relational.response.RestVdb;
import org.komodo.spi.KException;
import org.komodo.spi.constants.ExportConstants;
import org.komodo.spi.constants.StringConstants;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.spi.repository.Repository.UnitOfWork.State;
import org.komodo.spi.runtime.DataSourceDriver;
import org.komodo.utils.StringUtils;
import org.modeshape.common.text.ParsingException;
import org.teiid.language.SQLConstants;
import org.teiid.modeshape.sequencer.dataservice.lexicon.DataVirtLexicon;
import org.teiid.modeshape.sequencer.ddl.DdlParser;
import org.teiid.modeshape.sequencer.ddl.DdlParserProblem;
import org.teiid.modeshape.sequencer.ddl.StandardDdlLexicon;
import org.teiid.modeshape.sequencer.ddl.StandardDdlParser;
import org.teiid.modeshape.sequencer.ddl.TeiidDdlParser;
import org.teiid.modeshape.sequencer.ddl.node.AstNode;
import org.teiid.modeshape.sequencer.ddl.node.AstNodeFactory;
import org.teiid.modeshape.sequencer.vdb.lexicon.VdbLexicon;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * A Komodo REST service for obtaining Dataservice information from the workspace.
 */
@Path(V1Constants.WORKSPACE_SEGMENT + StringConstants.FORWARD_SLASH +
           V1Constants.DATA_SERVICES_SEGMENT)
@Api(tags = {V1Constants.DATA_SERVICES_SEGMENT})
public final class KomodoDataserviceService extends KomodoService {

    private static final int ALL_AVAILABLE = -1;

    private static final String SERVICE_VDB_SUFFIX = "VDB"; //$NON-NLS-1$
    private static final String SERVICE_VDB_VIEW_MODEL = "views"; //$NON-NLS-1$
    private static final String SERVICE_VDB_VIEW_SUFFIX = "View"; //$NON-NLS-1$
    private static final String LH_TABLE_ALIAS = "A"; //$NON-NLS-1$
    private static final String LH_TABLE_ALIAS_DOT = "A."; //$NON-NLS-1$
    private static final String RH_TABLE_ALIAS = "B"; //$NON-NLS-1$
    private static final String RH_TABLE_ALIAS_DOT = "B."; //$NON-NLS-1$
    private static final String INNER_JOIN = "INNER JOIN"; //$NON-NLS-1$
    private static final String LEFT_OUTER_JOIN = "LEFT OUTER JOIN"; //$NON-NLS-1$
    private static final String RIGHT_OUTER_JOIN = "RIGHT OUTER JOIN"; //$NON-NLS-1$
    private static final String FULL_OUTER_JOIN = "FULL OUTER JOIN"; //$NON-NLS-1$
    private static final String OR = "OR"; //$NON-NLS-1$
    private static final String AND = "AND"; //$NON-NLS-1$
    private static final String EQ = "="; //$NON-NLS-1$
    private static final String NE = "<>"; //$NON-NLS-1$
    private static final String LT = "<"; //$NON-NLS-1$
    private static final String GT = ">"; //$NON-NLS-1$
    private static final String LE = "<="; //$NON-NLS-1$
    private static final String GE = ">="; //$NON-NLS-1$
    
    /**
     * @param engine
     *        the Komodo Engine (cannot be <code>null</code> and must be started)
     * @throws WebApplicationException
     *         if there is a problem obtaining the {@link WorkspaceManager workspace manager}
     */
    public KomodoDataserviceService( final KEngine engine ) throws WebApplicationException {
        super( engine );
    }

    /**
     * Get the Dataservices from the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @return a JSON document representing all the Dataservices in the Komodo workspace (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem constructing the Dataservices JSON document
     */
    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Display the collection of data services",
                            response = RestDataservice[].class)
    @ApiResponses(value = {
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getDataservices( final @Context HttpHeaders headers,
                                     final @Context UriInfo uriInfo ) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;

        try {
            final String searchPattern = uriInfo.getQueryParameters().getFirst( QueryParamKeys.PATTERN );

            // find Data services
            uow = createTransaction(principal, "getDataservices", true ); //$NON-NLS-1$
            Dataservice[] dataServices = null;

            if ( StringUtils.isBlank( searchPattern ) ) {
                dataServices = getWorkspaceManager(uow).findDataservices( uow );
                LOGGER.debug( "getDataservices:found '{0}' Dataservices", dataServices.length ); //$NON-NLS-1$
            } else {
                final String[] dataservicePaths = getWorkspaceManager(uow).findByType( uow, DataVirtLexicon.DataService.NODE_TYPE, null, searchPattern, false );

                if ( dataservicePaths.length == 0 ) {
                    dataServices = Dataservice.NO_DATASERVICES;
                } else {
                    dataServices = new Dataservice[ dataservicePaths.length ];
                    int i = 0;

                    for ( final String path : dataservicePaths ) {
                        dataServices[ i++ ] = getWorkspaceManager(uow).resolve( uow, new ObjectImpl( getWorkspaceManager(uow).getRepository(), path, 0 ), Dataservice.class );
                    }

                    LOGGER.debug( "getDataservices:found '{0}' DataServices using pattern '{1}'", dataServices.length, searchPattern ); //$NON-NLS-1$
                }
            }

            int start = 0;

            { // start query parameter
                final String qparam = uriInfo.getQueryParameters().getFirst( QueryParamKeys.START );

                if ( qparam != null ) {

                    try {
                        start = Integer.parseInt( qparam );

                        if ( start < 0 ) {
                            start = 0;
                        }
                    } catch ( final Exception e ) {
                        start = 0;
                    }
                }
            }

            int size = ALL_AVAILABLE;

            { // size query parameter
                final String qparam = uriInfo.getQueryParameters().getFirst( QueryParamKeys.SIZE );

                if ( qparam != null ) {

                    try {
                        size = Integer.parseInt( qparam );

                        if ( size <= 0 ) {
                            size = ALL_AVAILABLE;
                        }
                    } catch ( final Exception e ) {
                        size = ALL_AVAILABLE;
                    }
                }
            }

            final List< RestDataservice > entities = new ArrayList< >();
            int i = 0;

            KomodoProperties properties = new KomodoProperties();
            for ( final Dataservice dataService : dataServices ) {
                if ( ( start == 0 ) || ( i >= start ) ) {
                    if ( ( size == ALL_AVAILABLE ) || ( entities.size() < size ) ) {
                        RestDataservice entity = entityFactory.create(dataService, uriInfo.getBaseUri(), uow, properties);
                        entities.add(entity);
                        LOGGER.debug("getDataservices:Dataservice '{0}' entity was constructed", dataService.getName(uow)); //$NON-NLS-1$
                    } else {
                        break;
                    }
                }

                ++i;
            }

            // create response
            return commit( uow, mediaTypes, entities );

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_GET_DATASERVICES_ERROR);
        }
    }

    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the id of the Dataservice being retrieved (cannot be empty)
     * @return the JSON representation of the Dataservice (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem finding the specified workspace Dataservice or constructing the JSON representation
     */
    @GET
    @Path( V1Constants.DATA_SERVICE_PLACEHOLDER )
    @Produces( { MediaType.APPLICATION_JSON } )
    @ApiOperation(value = "Find dataservice by name", response = RestDataservice.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No Dataservice could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getDataservice( final @Context HttpHeaders headers,
                                    final @Context UriInfo uriInfo,
                                    @ApiParam(value = "Id of the dataservice to be fetched", required = true)
                                    final @PathParam( "dataserviceName" ) String dataserviceName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "getDataservice", true ); //$NON-NLS-1$

            Dataservice dataservice = findDataservice(uow, dataserviceName);
            if (dataservice == null)
                return commitNoDataserviceFound(uow, mediaTypes, dataserviceName);

            KomodoProperties properties = new KomodoProperties();
            final RestDataservice restDataservice = entityFactory.create(dataservice, uriInfo.getBaseUri(), uow, properties);
            LOGGER.debug("getDataservice:Dataservice '{0}' entity was constructed", dataservice.getName(uow)); //$NON-NLS-1$
            return commit( uow, mediaTypes, restDataservice );

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_GET_DATASERVICE_ERROR, dataserviceName);
        }
    }

    /**
     * Create a new DataService in the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the dataservice name (cannot be empty)
     * @param dataserviceJson
     *        the dataservice JSON representation (cannot be <code>null</code>)
     * @return a JSON representation of the new dataservice (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error creating the DataService
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.DATA_SERVICE_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Create a dataservice in the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response createDataservice( final @Context HttpHeaders headers,
                                       final @Context UriInfo uriInfo,
                                       final @PathParam( "dataserviceName" ) String dataserviceName,
                                       final String dataserviceJson) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Error if the dataservice name is missing
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_CREATE_MISSING_NAME);
        }

        final RestDataservice restDataservice = KomodoJsonMarshaller.unmarshall( dataserviceJson, RestDataservice.class );
        final String jsonDataserviceName = restDataservice.getId();
        // Error if the name is missing from the supplied json body
        if ( StringUtils.isBlank( jsonDataserviceName ) ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_JSON_MISSING_NAME);
        }

        // Error if the name parameter is different than JSON name
        final boolean namesMatch = dataserviceName.equals( jsonDataserviceName );
        if ( !namesMatch ) {
            return createErrorResponseWithForbidden(mediaTypes, DATASERVICE_SERVICE_SERVICE_NAME_ERROR, dataserviceName, jsonDataserviceName);
        }

        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "createDataservice", false ); //$NON-NLS-1$
            
            // Error if the repo already contains a dataservice with the supplied name.
            if ( getWorkspaceManager(uow).hasChild( uow, dataserviceName ) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_CREATE_ALREADY_EXISTS);
            }

            // create new Dataservice
            return doAddDataservice( uow, uriInfo.getBaseUri(), mediaTypes, restDataservice );

        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_CREATE_DATASERVICE_ERROR, dataserviceName);
        }
    }

    /**
     * Clone a DataService in the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the dataservice name (cannot be empty)
     * @param newDataserviceName
     *        the new dataservice name (cannot be empty)
     * @return a JSON representation of the new dataservice (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error creating the DataService
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.CLONE_SEGMENT + StringConstants.FORWARD_SLASH + V1Constants.DATA_SERVICE_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Clone a dataservice in the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response cloneDataservice( final @Context HttpHeaders headers,
                                       final @Context UriInfo uriInfo,
                                       final @PathParam( "dataserviceName" ) String dataserviceName,
                                       final String newDataserviceName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Error if the dataservice name is missing
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_CLONE_MISSING_NAME);
        }

        // Error if the new dataservice name is missing
        if ( StringUtils.isBlank( newDataserviceName ) ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_CLONE_MISSING_NEW_NAME);
        }

        // Error if the name parameter and new name are the same
        final boolean namesMatch = dataserviceName.equals( newDataserviceName );
        if ( namesMatch ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_CLONE_SAME_NAME_ERROR, newDataserviceName);
        }

        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "cloneDataservice", false ); //$NON-NLS-1$
            
            // Error if the repo already contains a dataservice with the supplied name.
            if ( getWorkspaceManager(uow).hasChild( uow, newDataserviceName ) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_CLONE_ALREADY_EXISTS);
            }

            // create new Dataservice
            // must be an update
            final KomodoObject kobject = getWorkspaceManager(uow).getChild( uow, dataserviceName, DataVirtLexicon.DataService.NODE_TYPE );
            final Dataservice oldDataservice = getWorkspaceManager(uow).resolve( uow, kobject, Dataservice.class );
            final RestDataservice oldEntity = entityFactory.create(oldDataservice, uriInfo.getBaseUri(), uow );
            
            final Dataservice dataservice = getWorkspaceManager(uow).createDataservice( uow, null, newDataserviceName);

            setProperties( uow, dataservice, oldEntity );

            final RestDataservice entity = entityFactory.create(dataservice, uriInfo.getBaseUri(), uow );
            final Response response = commit( uow, mediaTypes, entity );
            return response;
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, RelationalMessages.Error.DATASERVICE_SERVICE_CLONE_DATASERVICE_ERROR);
        }
    }

    /**
     * Sets the service VDB for the specified Dataservice using the specified table and sourceModel
     * The supplied table is used to generate the view DDL for the service vdb's view
     * The supplied modelSource is used to generate the sourceModel for the service vdb
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceUpdateAttributes
     *        the attributes for the update (cannot be empty)
     * @return a JSON representation of the updated dataservice (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error updating the VDB
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.SERVICE_VDB_FOR_SINGLE_TABLE )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Sets the dataservice vdb using parameters provided in the request body",
                  notes = "Syntax of the json request body is of the form " +
                          "{ dataserviceName='serviceName', tablePath='path/to/table', modelSourcePath='path/to/modelSource' }")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response setServiceVdbForSingleTable( final @Context HttpHeaders headers,
    		final @Context UriInfo uriInfo,
    		final String dataserviceUpdateAttributes) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Get the attributes for doing the service update for single table view
        KomodoDataserviceUpdateAttributes attr;
        try {
        	attr = KomodoJsonMarshaller.unmarshall(dataserviceUpdateAttributes, KomodoDataserviceUpdateAttributes.class);
            Response response = checkDataserviceUpdateAttributesSingleTableView(attr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.DATASERVICE_SERVICE_REQUEST_PARSING_ERROR);
        }
        
        // Inputs for constructing the Service VDB.  The paths should be obtained from the Attributes passed in.
        String dataserviceName = attr.getDataserviceName();
        // Error if the dataservice name is missing 
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_NAME);
        }
        String serviceVdbName = dataserviceName+SERVICE_VDB_SUFFIX;

        // Determine if viewDdl was supplied.  If so, it will override the supplied table info.
        String viewDdl = attr.getViewDdl();
        boolean viewDdlSupplied = false;
        if( !StringUtils.isBlank( viewDdl ) ) {
            viewDdlSupplied = true;
        }
        
        String absTablePath = attr.getTablePath();
        // Error if the viewTablePath is missing 
        if (StringUtils.isBlank( absTablePath )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_TABLEPATH, dataserviceName);
        }
        
        String absServiceModelSourcePath = attr.getModelSourcePath();
        // Error if the modelSourcePath is missing 
        if (StringUtils.isBlank( absServiceModelSourcePath )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_MODELSOURCE_PATH, dataserviceName);
        }

        // Desired column names for the service (may be empty)
        List<String> columnNames = attr.getColumnNames();
        
        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "setDataserviceServiceVDB", false ); //$NON-NLS-1$

            // Check for existence of Dataservice, Table and ModelSource before continuing...
            WorkspaceManager wkspMgr = getWorkspaceManager(uow);
            
            // Check for existence of DataService
            final boolean exists = wkspMgr.hasChild( uow, dataserviceName );
            if ( !exists ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SERVICE_DNE);
            }
            final KomodoObject kobject = wkspMgr.getChild( uow, dataserviceName, DataVirtLexicon.DataService.NODE_TYPE );
            final Dataservice dataservice = wkspMgr.resolve( uow, kobject, Dataservice.class );

            // Check for existence of Table
            List<KomodoObject> tableObjs = wkspMgr.getRepository().searchByPath(uow, absTablePath);
            if( tableObjs.isEmpty() || !Table.RESOLVER.resolvable(uow, tableObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SOURCE_TABLE_DNE, absTablePath);
            }
            Table sourceTable = Table.RESOLVER.resolve(uow, tableObjs.get(0));

            // Check for existence of ModelSource
            List<KomodoObject> modelObjs = wkspMgr.getRepository().searchByPath(uow, absServiceModelSourcePath);
            if( modelObjs.isEmpty() || !ModelSource.RESOLVER.resolvable(uow, modelObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MODEL_SOURCE_DNE, absServiceModelSourcePath);
            }
            ModelSource svcModelSource = ModelSource.RESOLVER.resolve(uow, modelObjs.get(0));

            // Find the service VDB definition for this Dataservice.  If one exists already, it is replaced.
            dataservice.setServiceVdb(uow,null);
            if(wkspMgr.hasChild(uow, serviceVdbName, VdbLexicon.Vdb.VIRTUAL_DATABASE)) {
            	KomodoObject svcVdbObj = wkspMgr.getChild(uow, serviceVdbName, VdbLexicon.Vdb.VIRTUAL_DATABASE);
            	svcVdbObj.remove(uow);
            }
            KomodoObject vdbObj = wkspMgr.createVdb(uow, null, serviceVdbName, serviceVdbName);
            Vdb serviceVdb = Vdb.RESOLVER.resolve(uow, vdbObj);
            
            // Add to the ServiceVdb a virtual model for the View
            Model viewModel = serviceVdb.addModel(uow, SERVICE_VDB_VIEW_MODEL);
            viewModel.setModelType(uow, Type.VIRTUAL);
            
            // If viewDdl wasn't supplied, generate it using the specified table, then set the viewModel content.
            if(!viewDdlSupplied) {
                viewDdl = ViewDdlBuilder.getODataViewDdl(uow, dataserviceName+SERVICE_VDB_VIEW_SUFFIX, sourceTable, columnNames);
            }
            viewModel.setModelDefinition(uow, viewDdl);

        	// Add a physical model to the VDB for the sources
        	// physicalModelName ==> sourceVDBName
        	// physicalModelSourceName ==> sourceVDBModelName
        	String physicalModelName = svcModelSource.getParent(uow).getParent(uow).getName(uow);
        	String physicalModelSourceName = svcModelSource.getParent(uow).getName(uow);
        	
        	Model sourceModel = serviceVdb.addModel(uow, physicalModelName);
        	sourceModel.setModelType(uow, Type.PHYSICAL);

            // The source model DDL contains the table DDL only.  This limits the source metadata which is loaded on deployment.
            Properties exportProps = new Properties();
            exportProps.put( ExportConstants.EXCLUDE_TABLE_CONSTRAINTS_KEY, true );
        	byte[] bytes = sourceTable.export(uow, exportProps);
        	String tableString = new String(bytes);
            sourceModel.setModelDefinition(uow, tableString);
            
        	// Add a ModelSource of same name to the physical model and set its Jndi and translator
        	ModelSource modelSource = sourceModel.addSource(uow, physicalModelSourceName);
        	modelSource.setJndiName(uow, svcModelSource.getJndiName(uow));
        	modelSource.setTranslatorName(uow, svcModelSource.getTranslatorName(uow));     
            
            // Set the service VDB on the dataservice
            dataservice.setServiceVdb(uow, serviceVdb);

            KomodoStatusObject kso = new KomodoStatusObject("Update DataService Status"); //$NON-NLS-1$
            kso.addAttribute(dataserviceName, "Successfully updated"); //$NON-NLS-1$

            return commit(uow, mediaTypes, kso);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_SET_SERVICE_ERROR);
        }
    }
    
    /**
     * Get the generated View DDL for the supplied attributes
     * The supplied table is used to generate the view DDL
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceUpdateAttributes
     *        the attributes for generating the DDL (cannot be empty)
     * @return a JSON representation of the DDL (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error generating the DDL
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.SERVICE_VIEW_DDL_FOR_SINGLE_TABLE )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Gets generated View DDL using parameters provided in the request body",
                  notes = "Syntax of the json request body is of the form " +
                          "{ dataserviceName='serviceName', tablePath='path/to/table', modelSourcePath='path/to/modelSource', colNames='columnNames' }")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getServiceViewDdlForSingleTable( final @Context HttpHeaders headers,
                                                     final @Context UriInfo uriInfo,
                                                     final String dataserviceUpdateAttributes) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Get the attributes for doing the service update for single table view
        KomodoDataserviceUpdateAttributes attr;
        try {
            attr = KomodoJsonMarshaller.unmarshall(dataserviceUpdateAttributes, KomodoDataserviceUpdateAttributes.class);
            Response response = checkDataserviceUpdateAttributesGetSingleTableDdl(attr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.DATASERVICE_SERVICE_REQUEST_PARSING_ERROR);
        }
        
        // Inputs for constructing the Service VDB.  The paths should be obtained from the Attributes passed in.
        String dataserviceName = attr.getDataserviceName();
        // Error if the dataservice name is missing 
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_NAME);
        }
        
        String absTablePath = attr.getTablePath();
        // Error if the viewTablePath is missing 
        if (StringUtils.isBlank( absTablePath )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_TABLEPATH, dataserviceName);
        }
        
        // Desired column names for the service (may be empty)
        List<String> columnNames = attr.getColumnNames();
        
        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "getDataserviceServiceVDB", true ); //$NON-NLS-1$

            // Check for existence of Dataservice, Table and ModelSource before continuing...
            WorkspaceManager wkspMgr = getWorkspaceManager(uow);
            
            // Check for existence of Table
            List<KomodoObject> tableObjs = wkspMgr.getRepository().searchByPath(uow, absTablePath);
            if( tableObjs.isEmpty() || !Table.RESOLVER.resolvable(uow, tableObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SOURCE_TABLE_DNE, absTablePath);
            }
            Table sourceTable = Table.RESOLVER.resolve(uow, tableObjs.get(0));
            
            // Generate the ViewDDL using the specified table, then set the viewModel content.
            String viewDdl = ViewDdlBuilder.getODataViewDdl(uow, dataserviceName+SERVICE_VDB_VIEW_SUFFIX, sourceTable, columnNames);

            // Add info for the raw view ddl
            RestDataserviceViewInfo viewInfo = new RestDataserviceViewInfo();
            viewInfo.setInfoType(RestDataserviceViewInfo.DDL_INFO);
            viewInfo.setViewDdl(viewDdl);

            return commit(uow, mediaTypes, viewInfo);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_SET_SERVICE_ERROR);
        }
    }
    
    /**
     * Sets the service VDB for the specified Dataservice using the specified tables and sourceModels
     * The supplied table is used to generate the view DDL for the service vdb's view
     * The supplied modelSource is used to generate the sourceModel for the service vdb
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceUpdateAttributes
     *        the attributes for the update (cannot be empty)
     * @return a JSON representation of the updated dataservice (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error updating the VDB
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.SERVICE_VDB_FOR_JOIN_TABLES )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Sets the dataservice vdb using parameters provided in the request body",
                  notes = "Syntax of the json request body is of the form " +
                          "{ dataserviceName='serviceName', "+ 
                             "tablePath='path/to/table', modelSourcePath='path/to/modelSource', " +
                             "rhTablePath='path/to/rhTable', rhModelSourcePath='path/to/modelSource', " +
                             "colNames='columnNames', rhColNames='rhColumnNames', joinType='joinType', " +
                             "criteriaPredicates='[]', viewDdl='viewddl' }" )
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response setServiceVdbForTwoTables( final @Context HttpHeaders headers,
            final @Context UriInfo uriInfo,
            final String dataserviceUpdateAttributes) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Get the attributes for doing the service update
        KomodoDataserviceUpdateAttributes attr;
        try {
            attr = KomodoJsonMarshaller.unmarshall(dataserviceUpdateAttributes, KomodoDataserviceUpdateAttributes.class);
            Response response = checkDataserviceUpdateAttributesJoinView(attr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.DATASERVICE_SERVICE_REQUEST_PARSING_ERROR);
        }
        
        // Inputs for constructing the Service VDB.  The paths should be obtained from the Attributes passed in.
        String dataserviceName = attr.getDataserviceName();
        // Error if the dataservice name is missing 
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_NAME);
        }
        String serviceVdbName = dataserviceName+SERVICE_VDB_SUFFIX;
        
        String absLhTablePath = attr.getTablePath();
        String absRhTablePath = attr.getRhTablePath();
        // Error if the viewTablePath is missing 
        if (StringUtils.isBlank( absLhTablePath ) || StringUtils.isBlank( absRhTablePath )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_TABLEPATH, dataserviceName);
        }
        
        String absLhModelSourcePath = attr.getModelSourcePath();
        String absRhModelSourcePath = attr.getRhModelSourcePath();
        // Error if the modelSourcePath is missing 
        if (StringUtils.isBlank( absLhModelSourcePath ) || StringUtils.isBlank( absRhModelSourcePath )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_MODELSOURCE_PATH, dataserviceName);
        }
        // Determine if the tables are from the same model
        boolean sameLeftAndRightModel = absLhModelSourcePath.trim().equals(absRhModelSourcePath.trim());

        // Determine if viewDdl was supplied.  If so, it will override the supplied table info.
        String viewDdl = attr.getViewDdl();
        boolean viewDdlSupplied = false;
        if( !StringUtils.isBlank( viewDdl ) ) {
            viewDdlSupplied = true;
        }
        
        // JoinType
        String joinType = attr.getJoinType();
        // Error if the join type is missing
        if (!viewDdlSupplied && StringUtils.isBlank( joinType )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_JOIN_TYPE, dataserviceName);
        }

        List<Map<String,String>> predicateMaps = attr.getCriteriaPredicates();
        List<ViewBuilderCriteriaPredicate> criteriaPredicates = new ArrayList<ViewBuilderCriteriaPredicate>(predicateMaps.size());
        for(Map<String,String> predicateMap : predicateMaps) {
            criteriaPredicates.add(new ViewBuilderCriteriaPredicate(predicateMap));
        }

        // Desired column names for the service (may be empty)
        List<String> lhColumnNames = attr.getColumnNames();
        List<String> rhColumnOriginalNames = attr.getRhColumnNames();
        
        // Disallow duplicate column names.  Remove duplicates from the RH table
        List<String> rhColumnNames = new ArrayList<String>();
        for(String rhCol : rhColumnOriginalNames) {
            if(!lhColumnNames.contains(rhCol)) {
                rhColumnNames.add(rhCol);
            }
        }
        
        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "setDataserviceServiceVDB", false ); //$NON-NLS-1$

            // -------------------------------------------------------------------------------------
            // Check for existence of necessary objects
            // -------------------------------------------------------------------------------------
            WorkspaceManager wkspMgr = getWorkspaceManager(uow);
            
            // Check for existence of DataService
            final boolean exists = wkspMgr.hasChild( uow, dataserviceName );
            if ( !exists ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SERVICE_DNE);
            }
            final KomodoObject kobject = wkspMgr.getChild( uow, dataserviceName, DataVirtLexicon.DataService.NODE_TYPE );
            final Dataservice dataservice = wkspMgr.resolve( uow, kobject, Dataservice.class );

            // Check for existence of LH Table
            List<KomodoObject> tableObjs = wkspMgr.getRepository().searchByPath(uow, absLhTablePath);
            if( tableObjs.isEmpty() || !Table.RESOLVER.resolvable(uow, tableObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SOURCE_TABLE_DNE, absLhTablePath);
            }
            Table lhSourceTable = Table.RESOLVER.resolve(uow, tableObjs.get(0));

            // Check for existence of RH Table
            tableObjs = wkspMgr.getRepository().searchByPath(uow, absRhTablePath);
            if( tableObjs.isEmpty() || !Table.RESOLVER.resolvable(uow, tableObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SOURCE_TABLE_DNE, absRhTablePath);
            }
            Table rhSourceTable = Table.RESOLVER.resolve(uow, tableObjs.get(0));

            // Check for existence of LH ModelSource
            List<KomodoObject> modelObjs = wkspMgr.getRepository().searchByPath(uow, absLhModelSourcePath);
            if( modelObjs.isEmpty() || !ModelSource.RESOLVER.resolvable(uow, modelObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MODEL_SOURCE_DNE, absLhModelSourcePath);
            }
            ModelSource lhModelSource = ModelSource.RESOLVER.resolve(uow, modelObjs.get(0));

            ModelSource rhModelSource = lhModelSource;
            if(!sameLeftAndRightModel) {
                // Check for existence of RH ModelSource
                modelObjs = wkspMgr.getRepository().searchByPath(uow, absRhModelSourcePath);
                if( modelObjs.isEmpty() || !ModelSource.RESOLVER.resolvable(uow, modelObjs.get(0)) ) {
                    return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MODEL_SOURCE_DNE, absRhModelSourcePath);
                }
                rhModelSource = ModelSource.RESOLVER.resolve(uow, modelObjs.get(0));
            }

            // ----------------------------------------------------------------------------------------------
            // Find the service VDB definition for this Dataservice.  If one exists already, it is replaced.
            // ----------------------------------------------------------------------------------------------
            dataservice.setServiceVdb(uow,null);
            if(wkspMgr.hasChild(uow, serviceVdbName, VdbLexicon.Vdb.VIRTUAL_DATABASE)) {
                KomodoObject svcVdbObj = wkspMgr.getChild(uow, serviceVdbName, VdbLexicon.Vdb.VIRTUAL_DATABASE);
                svcVdbObj.remove(uow);
            }
            KomodoObject vdbObj = wkspMgr.createVdb(uow, null, serviceVdbName, serviceVdbName);
            Vdb serviceVdb = Vdb.RESOLVER.resolve(uow, vdbObj);
            
            // ----------------------------------------------------------------
            // Add to the ServiceVdb a virtual model for the View
            // ----------------------------------------------------------------
            Model viewModel = serviceVdb.addModel(uow, SERVICE_VDB_VIEW_MODEL);
            viewModel.setModelType(uow, Type.VIRTUAL);
            
            // If view DDL not supplied, generate it using the specified tables and info, then set the viewModel content.
            if(!viewDdlSupplied) {
                viewDdl = ViewDdlBuilder.getODataViewJoinDdl(uow, dataserviceName+SERVICE_VDB_VIEW_SUFFIX, 
                                                                  lhSourceTable, LH_TABLE_ALIAS, lhColumnNames, 
                                                                  rhSourceTable, RH_TABLE_ALIAS, rhColumnNames, 
                                                                  joinType, criteriaPredicates);
            }
            viewModel.setModelDefinition(uow, viewDdl);

            // --------------------------------------------------
            // Add physical model for lh modelSource
            // --------------------------------------------------
            // Add a physical model to the VDB for the sources
            // physicalModelName ==> sourceVDBName
            // physicalModelSourceName ==> sourceVDBModelName
            String lhPhysicalModelName = lhModelSource.getParent(uow).getParent(uow).getName(uow);
            String lhPhysicalModelSourceName = lhModelSource.getParent(uow).getName(uow);

            Model lhPhysicalModel = serviceVdb.addModel(uow, lhPhysicalModelName);
            lhPhysicalModel.setModelType(uow, Type.PHYSICAL);

            // The source model DDL contains the table DDL only.  This limits the source metadata which is loaded on deployment.
            Properties exportProps = new Properties();
            exportProps.put( ExportConstants.EXCLUDE_TABLE_CONSTRAINTS_KEY, true );
            // LH Table DDL
            byte[] bytes = lhSourceTable.export(uow, exportProps);
            String lhTableDdl = new String(bytes);
            // RH Table DDL
            bytes = rhSourceTable.export(uow, exportProps);
            String rhTableDdl = new String(bytes);
            
            // If same LH and RH Model, add the RH table DDL as well
            if(sameLeftAndRightModel) {
                lhPhysicalModel.setModelDefinition(uow, lhTableDdl+rhTableDdl);
            } else {
                lhPhysicalModel.setModelDefinition(uow, lhTableDdl);
            }
            
            // Add a ModelSource of same name to the physical model and set its Jndi and translator
            ModelSource lhPhysicalModelSource = lhPhysicalModel.addSource(uow, lhPhysicalModelSourceName);
            lhPhysicalModelSource.setJndiName(uow, lhModelSource.getJndiName(uow));
            lhPhysicalModelSource.setTranslatorName(uow, lhModelSource.getTranslatorName(uow));
            
            // --------------------------------------------------
            // Add physical model for rh modelSource
            // - (dont add duplicate if same as left)
            // --------------------------------------------------
            if(!sameLeftAndRightModel) {
                // Add a physical model to the VDB for the sources
                // physicalModelName ==> sourceVDBName
                // physicalModelSourceName ==> sourceVDBModelName
                String rhPhysicalModelName = rhModelSource.getParent(uow).getParent(uow).getName(uow);
                String rhPhysicalModelSourceName = rhModelSource.getParent(uow).getName(uow);

                Model rhPhysicalModel = serviceVdb.addModel(uow, rhPhysicalModelName);
                rhPhysicalModel.setModelType(uow, Type.PHYSICAL);

                rhPhysicalModel.setModelDefinition(uow, rhTableDdl);
                
                // Add a ModelSource of same name to the physical model and set its Jndi and translator
                ModelSource rhPhysicalModelSource = rhPhysicalModel.addSource(uow, rhPhysicalModelSourceName);
                rhPhysicalModelSource.setJndiName(uow, rhModelSource.getJndiName(uow));
                rhPhysicalModelSource.setTranslatorName(uow, rhModelSource.getTranslatorName(uow));     
            }
            
            // -------------------------------------------
            // Set the service VDB on the dataservice
            // -------------------------------------------
            dataservice.setServiceVdb(uow, serviceVdb);

            KomodoStatusObject kso = new KomodoStatusObject("Update DataService Status"); //$NON-NLS-1$
            kso.addAttribute(dataserviceName, "Successfully updated"); //$NON-NLS-1$

            return commit(uow, mediaTypes, kso);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_SET_SERVICE_ERROR);
        }
    }
    
    /**
     * Get the generated View DDL for the supplied attributes
     * The supplied tables and join details are used to generate the view DDL
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceUpdateAttributes
     *        the attributes for generating the DDL (cannot be empty)
     * @return a JSON representation of the generated DDL (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error generating the DDL
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.SERVICE_VIEW_DDL_FOR_JOIN_TABLES )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Gets generated View DDL using parameters provided in the request body",
                  notes = "Syntax of the json request body is of the form " +
                          "{ dataserviceName='serviceName', "+ 
                             "tablePath='path/to/table', rhTablePath='path/to/rhTable', " +
                             "colNames='columnNames', rhColNames='rhColumnNames', " +
                             "joinType='joinType', criteriaPredicates='[]' }" )
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getServiceViewDdlForTwoTables( final @Context HttpHeaders headers,
            final @Context UriInfo uriInfo,
            final String dataserviceUpdateAttributes) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Get the attributes for doing the service update
        KomodoDataserviceUpdateAttributes attr;
        try {
            attr = KomodoJsonMarshaller.unmarshall(dataserviceUpdateAttributes, KomodoDataserviceUpdateAttributes.class);
            Response response = checkDataserviceUpdateAttributesGetJoinDdl(attr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.DATASERVICE_SERVICE_REQUEST_PARSING_ERROR);
        }
        
        // Inputs for constructing the Service VDB.  The paths should be obtained from the Attributes passed in.
        String dataserviceName = attr.getDataserviceName();
        // Error if the dataservice name is missing 
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_NAME);
        }

        String absLhTablePath = attr.getTablePath();
        String absRhTablePath = attr.getRhTablePath();
        // Error if the viewTablePath is missing 
        if (StringUtils.isBlank( absLhTablePath ) || StringUtils.isBlank( absRhTablePath )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_TABLEPATH, dataserviceName);
        }
        
        // JoinType
        String joinType = attr.getJoinType();
        // Error if the join type is missing
        if (StringUtils.isBlank( joinType )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SET_SERVICE_MISSING_JOIN_TYPE, dataserviceName);
        }

        List<Map<String,String>> predicateMaps = attr.getCriteriaPredicates();
        List<ViewBuilderCriteriaPredicate> criteriaPredicates = new ArrayList<ViewBuilderCriteriaPredicate>(predicateMaps.size());
        for(Map<String,String> predicateMap : predicateMaps) {
            criteriaPredicates.add(new ViewBuilderCriteriaPredicate(predicateMap));
        }

        // Desired column names for the service (may be empty)
        List<String> lhColumnNames = attr.getColumnNames();
        List<String> rhColumnOriginalNames = attr.getRhColumnNames();
        
        // Disallow duplicate column names.  Remove duplicates from the RH table
        List<String> rhColumnNames = new ArrayList<String>();
        for(String rhCol : rhColumnOriginalNames) {
            if(!lhColumnNames.contains(rhCol)) {
                rhColumnNames.add(rhCol);
            }
        }
        
        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "getViewDdl", true ); //$NON-NLS-1$

            // -------------------------------------------------------------------------------------
            // Check for existence of necessary objects
            // -------------------------------------------------------------------------------------
            WorkspaceManager wkspMgr = getWorkspaceManager(uow);
            
            // Check for existence of LH Table
            List<KomodoObject> tableObjs = wkspMgr.getRepository().searchByPath(uow, absLhTablePath);
            if( tableObjs.isEmpty() || !Table.RESOLVER.resolvable(uow, tableObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SOURCE_TABLE_DNE, absLhTablePath);
            }
            Table lhSourceTable = Table.RESOLVER.resolve(uow, tableObjs.get(0));

            // Check for existence of RH Table
            tableObjs = wkspMgr.getRepository().searchByPath(uow, absRhTablePath);
            if( tableObjs.isEmpty() || !Table.RESOLVER.resolvable(uow, tableObjs.get(0)) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SOURCE_TABLE_DNE, absRhTablePath);
            }
            Table rhSourceTable = Table.RESOLVER.resolve(uow, tableObjs.get(0));

            // Generate the ViewDDL using the specified tables, then set the viewModel content.
            String viewDdl = ViewDdlBuilder.getODataViewJoinDdl(uow, dataserviceName+SERVICE_VDB_VIEW_SUFFIX, 
                                                                lhSourceTable, LH_TABLE_ALIAS, lhColumnNames, 
                                                                rhSourceTable, RH_TABLE_ALIAS, rhColumnNames, 
                                                                joinType, criteriaPredicates);

            // Add info for the raw view ddl
            RestDataserviceViewInfo viewInfo = new RestDataserviceViewInfo();
            viewInfo.setInfoType(RestDataserviceViewInfo.DDL_INFO);
            viewInfo.setViewDdl(viewDdl);

            return commit(uow, mediaTypes, viewInfo);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_SET_SERVICE_ERROR);
        }
    }
    
    /**
     * Update a Dataservice in the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the dataservice name (cannot be empty)
     * @param dataserviceJson
     *        the dataservice JSON representation (cannot be <code>null</code>)
     * @return a JSON representation of the updated dataservice (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error updating the VDB
     */
    @PUT
    @Path( StringConstants.FORWARD_SLASH + V1Constants.DATA_SERVICE_PLACEHOLDER )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Update a dataservice in the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response updateDataservice( final @Context HttpHeaders headers,
                                       final @Context UriInfo uriInfo,
                                       final @PathParam( "dataserviceName" ) String dataserviceName,
                                       final String dataserviceJson) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Error if the dataservice name is missing 
        if (StringUtils.isBlank( dataserviceName )) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_UPDATE_MISSING_NAME);
        }


        final RestDataservice restDataservice = KomodoJsonMarshaller.unmarshall( dataserviceJson, RestDataservice.class );
        final String jsonDataserviceName = restDataservice.getId();
        // Error if the name is missing from the supplied json body
        if ( StringUtils.isBlank( jsonDataserviceName ) ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_JSON_MISSING_NAME);
        }

        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "updateDataservice", false ); //$NON-NLS-1$

            final boolean exists = getWorkspaceManager(uow).hasChild( uow, dataserviceName );
            // Error if the specified service does not exist
            if ( !exists ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SERVICE_DNE);
            }

            // must be an update
            final KomodoObject kobject = getWorkspaceManager(uow).getChild( uow, dataserviceName, DataVirtLexicon.DataService.NODE_TYPE );
            final Dataservice dataservice = getWorkspaceManager(uow).resolve( uow, kobject, Dataservice.class );

            // Transfers the properties from the rest object to the created komodo service.
            setProperties(uow, dataservice, restDataservice);

            // rename if names did not match
            final boolean namesMatch = dataserviceName.equals( jsonDataserviceName );
            if ( !namesMatch ) {
                dataservice.rename( uow, jsonDataserviceName );
            }

            KomodoProperties properties = new KomodoProperties();
            final RestDataservice entity = entityFactory.create(dataservice, uriInfo.getBaseUri(), uow, properties);
            LOGGER.debug("updateDataservice: dataservice '{0}' entity was updated", dataservice.getName(uow)); //$NON-NLS-1$
            final Response response = commit( uow, headers.getAcceptableMediaTypes(), entity );
            return response;
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_UPDATE_DATASERVICE_ERROR);
        }
    }

    private Response doAddDataservice( final UnitOfWork uow,
                                       final URI baseUri,
                                       final List<MediaType> mediaTypes,
                                       final RestDataservice restDataservice ) throws KomodoRestException {
        assert( !uow.isRollbackOnly() );
        assert( uow.getState() == State.NOT_STARTED );
        assert( restDataservice != null );

        final String dataserviceName = restDataservice.getId();
        try {
            final Dataservice dataservice = getWorkspaceManager(uow).createDataservice( uow, null, dataserviceName);

            // Transfers the properties from the rest object to the created komodo service.
            setProperties(uow, dataservice, restDataservice);

            final RestDataservice entity = entityFactory.create(dataservice, baseUri, uow );
            final Response response = commit( uow, mediaTypes, entity );
            return response;
        } catch ( final Exception e ) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            throw new KomodoRestException( RelationalMessages.getString( DATASERVICE_SERVICE_CREATE_DATASERVICE_ERROR, dataserviceName ), e );
        }
    }

    // Sets Dataservice properties using the supplied RestDataservice object
    private void setProperties(final UnitOfWork uow, Dataservice dataService, RestDataservice restDataService) throws KException {
        // 'New' = requested RestDataservice properties
        String newDescription = restDataService.getDescription();

        // 'Old' = current Dataservice properties
        String oldDescription = dataService.getDescription(uow);

        // Description
        if ( !StringUtils.equals(newDescription, oldDescription) ) {
            dataService.setDescription( uow, newDescription );
        }
    }

    /**
     * Delete the specified Dataservice from the komodo repository
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the name of the data service to remove (cannot be <code>null</code>)
     * @return a JSON document representing the results of the removal
     * @throws KomodoRestException
     *         if there is a problem performing the delete
     */
    @DELETE
    @Path("{dataserviceName}")
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Delete a dataservice from the workspace")
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response deleteDataservice( final @Context HttpHeaders headers,
                                       final @Context UriInfo uriInfo,
                                       final @PathParam( "dataserviceName" ) String dataserviceName) throws KomodoRestException {
        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();

        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "removeDataserviceFromWorkspace", false); //$NON-NLS-1$

            final WorkspaceManager wkspMgr = getWorkspaceManager(uow);
            final boolean exists = wkspMgr.hasChild( uow, dataserviceName );
            // Error if the specified service does not exist
            if ( !exists ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_SERVICE_DNE);
            }

            KomodoObject dataservice = wkspMgr.getChild(uow, dataserviceName, DataVirtLexicon.DataService.NODE_TYPE);
            
            wkspMgr.delete(uow, dataservice);

            KomodoStatusObject kso = new KomodoStatusObject("Delete Status"); //$NON-NLS-1$
            kso.addAttribute(dataserviceName, "Successfully deleted"); //$NON-NLS-1$

            return commit(uow, mediaTypes, kso);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_DELETE_DATASERVICE_ERROR);
        }
    }
    
    /*
     * Checks the supplied attributes for single table view
     *  - dataserviceName, table path and modelSource path are required
     *  - columnNames is optional
     */
    private Response checkDataserviceUpdateAttributesSingleTableView(KomodoDataserviceUpdateAttributes attr,
                                                                     List<MediaType> mediaTypes) throws Exception {

        if (attr == null || attr.getDataserviceName() == null || attr.getTablePath() == null || attr.getModelSourcePath() == null) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MISSING_PARAMETER_ERROR);
        }

        return Response.ok().build();
    }

    /*
     * Checks the supplied attributes for getting the single table DDL
     *  - dataserviceName and table path are required
     *  - columnNames is optional
     */
    private Response checkDataserviceUpdateAttributesGetSingleTableDdl(KomodoDataserviceUpdateAttributes attr,
                                                                       List<MediaType> mediaTypes) throws Exception {

        if (attr == null || attr.getDataserviceName() == null || attr.getTablePath() == null) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MISSING_PARAMETER_ERROR);
        }

        return Response.ok().build();
    }

    /*
     * Checks the supplied attributes for join table view
     *  - dataserviceName, modelSourcePath, rhModelSourcePath, tablePath, rhTablePath are required
     *  - Either joinType, lhJoinColumn, rhJoinColumn must be supplied -OR- view Ddl
     *  - columnNames and rhColumnNames are optional
     */
    private Response checkDataserviceUpdateAttributesJoinView(KomodoDataserviceUpdateAttributes attr,
                                                              List<MediaType> mediaTypes) throws Exception {

        // service name, modelSourcePath, rhModelSourcePath must not be null
        if (attr == null || attr.getDataserviceName() == null || attr.getModelSourcePath() == null
                         || attr.getRhModelSourcePath() == null || attr.getTablePath() == null || attr.getRhTablePath() == null) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MISSING_PARAMETER_ERROR);
        }
        
        // Either the join tables and info must be non null, or the view Ddl must be supplied
        if( ( attr.getJoinType() == null || 
              attr.getCriteriaPredicates() == null ) && (attr.getViewDdl() == null) ) {
                return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MISSING_PARAMETER_ERROR);
        }

        return Response.ok().build();
    }
    
    /*
     * Checks the supplied attributes for getting the join DDL
     *  - dataserviceName, tablePath, rhTablePath, joinType are required
     *  - columnNames and rhColumnNames are optional
     */
    private Response checkDataserviceUpdateAttributesGetJoinDdl(KomodoDataserviceUpdateAttributes attr,
                                                                List<MediaType> mediaTypes) throws Exception {

        if (attr == null || attr.getDataserviceName() == null || attr.getJoinType() == null 
                         || attr.getTablePath() == null || attr.getRhTablePath() == null ) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MISSING_PARAMETER_ERROR);
        }

        return Response.ok().build();
    }

    /*
     * Checks the supplied attributes for viewDdl
     *  - view ddl is required
     */
    private Response checkDataserviceUpdateAttributesDdlValidation(KomodoDataserviceUpdateAttributes attr,
                                                                   List<MediaType> mediaTypes) throws Exception {

        if (attr == null || attr.getViewDdl() == null) {
            return createErrorResponseWithForbidden(mediaTypes, RelationalMessages.Error.DATASERVICE_SERVICE_MISSING_PARAMETER_ERROR);
        }

        return Response.ok().build();
    }

    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the id of the Dataservice of the connections being retrieved (cannot be empty)
     * @return the JSON representation of the Connections (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem finding the specified workspace Dataservice connections
     *         or constructing the JSON representation
     */
    @GET
    @Path( V1Constants.DATA_SERVICE_PLACEHOLDER +
                   StringConstants.FORWARD_SLASH + V1Constants.CONNECTIONS_SEGMENT)
    @Produces( { MediaType.APPLICATION_JSON } )
    @ApiOperation(value = "Find a dataservice's connections ", response = RestDataservice.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No Dataservice could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getConnections( final @Context HttpHeaders headers,
                                    final @Context UriInfo uriInfo,
                                    @ApiParam(value = "Id of the dataservice connections to be fetched", required = true)
                                    final @PathParam( "dataserviceName" ) String dataserviceName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "getDataservice", true ); //$NON-NLS-1$

            Dataservice dataservice = findDataservice(uow, dataserviceName);
            if (dataservice == null)
                return commitNoDataserviceFound(uow, mediaTypes, dataserviceName);

            Datasource[] connections = dataservice.getConnections(uow);
            List<RestDataSource> restConnections = new ArrayList<>(connections.length);
            for (Datasource connection : connections) {
                RestDataSource entity = entityFactory.create(connection, uriInfo.getBaseUri(), uow);
                restConnections.add(entity);
                LOGGER.debug("getConnections:Connections from Dataservice '{0}' entity was constructed", dataserviceName); //$NON-NLS-1$
            }

            return commit( uow, mediaTypes, restConnections);

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_GET_CONNECTIONS_ERROR, dataserviceName);
        }
    }
    
    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the id of the Dataservice (cannot be empty)
     * @return the JSON representation of the Drivers (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem finding the specified workspace Dataservice drivers
     *         or constructing the JSON representation
     */
    @GET
    @Path( V1Constants.DATA_SERVICE_PLACEHOLDER +
                   StringConstants.FORWARD_SLASH + V1Constants.DRIVERS_SEGMENT)
    @Produces( { MediaType.APPLICATION_JSON } )
    @ApiOperation(value = "Find a dataservice's drivers ", response = RestDataSourceDriver.class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No Dataservice could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getDrivers( final @Context HttpHeaders headers,
                                    final @Context UriInfo uriInfo,
                                    @ApiParam(value = "Id of the dataservice drivers to be fetched", required = true)
                                    final @PathParam( "dataserviceName" ) String dataserviceName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "getDataservice", true ); //$NON-NLS-1$

            Dataservice dataservice = findDataservice(uow, dataserviceName);
            if (dataservice == null)
                return commitNoDataserviceFound(uow, mediaTypes, dataserviceName);

            Driver[] drivers = dataservice.getDrivers(uow);
            List<RestDataSourceDriver> restDrivers = new ArrayList<>(drivers.length);
            for (Driver driver : drivers) {
                DataSourceDriver aDriver = new DataSourceDriver(driver.getName(uow),null);
                RestDataSourceDriver entity = new RestDataSourceDriver(aDriver);
                restDrivers.add(entity);
                LOGGER.debug("getDrivers:Drivers from Dataservice '{0}' entity was constructed", dataserviceName); //$NON-NLS-1$
            }

            return commit( uow, mediaTypes, restDrivers);

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_GET_DRIVERS_ERROR, dataserviceName);
        }
    }
    
    /**
     * Validate the supplied DDL.
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceUpdateAttributes
     *        the attributes for the update (cannot be empty)
     * @return a JSON representation of the status (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is an error getting the validation result
     */
    @POST
    @Path( StringConstants.FORWARD_SLASH + V1Constants.VALIDATE_DDL )
    @Produces( MediaType.APPLICATION_JSON )
    @ApiOperation(value = "Validate the viewDdl provided in the request body",
                  notes = "Syntax of the json request body is of the form " +
                          "{ viewDdl='viewddl' }" )
    @ApiResponses(value = {
        @ApiResponse(code = 406, message = "Only JSON is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getDdlValidStatus( final @Context HttpHeaders headers,
                                       final @Context UriInfo uriInfo,
                                       final String dataserviceUpdateAttributes) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        if (! isAcceptable(mediaTypes, MediaType.APPLICATION_JSON_TYPE))
            return notAcceptableMediaTypesBuilder().build();

        // Get the attributes for doing the DDL validation
        KomodoDataserviceUpdateAttributes attr;
        try {
            attr = KomodoJsonMarshaller.unmarshall(dataserviceUpdateAttributes, KomodoDataserviceUpdateAttributes.class);
            Response response = checkDataserviceUpdateAttributesDdlValidation(attr, mediaTypes);
            if (response.getStatus() != Status.OK.getStatusCode())
                return response;

        } catch (Exception ex) {
            return createErrorResponseWithForbidden(mediaTypes, ex, RelationalMessages.Error.DATASERVICE_SERVICE_REQUEST_PARSING_ERROR);
        }
        
        // Get the supplied viewDdl.
        String viewDdl = attr.getViewDdl();
        
        UnitOfWork uow = null;
        try {
            uow = createTransaction(principal, "validateDdl", false ); //$NON-NLS-1$

            String parseErrorMsg = null;
            boolean ddlValid = false;
            if(!StringUtils.isBlank(viewDdl)) {
                final DdlParser teiidParser = new TeiidDdlParser();
                AstNodeFactory nodeFactory = new AstNodeFactory();
                final AstNode tempNode = nodeFactory.node(StandardDdlLexicon.STATEMENTS_CONTAINER);

                // Attempt to parse the supplied DDL
                try {
                    teiidParser.parse(viewDdl, tempNode, null);
                    
                    // determine if any problems
                    List<DdlParserProblem> problems = ((StandardDdlParser)teiidParser).getProblems();
                    if(problems.size() == 0) {
                        ddlValid = true;
                    } else {
                        parseErrorMsg = problems.get(0).getMessage();
                    }
                } catch (ParsingException e) {
                    parseErrorMsg = "Failed to validate DDL: " + e.getLocalizedMessage();  //$NON-NLS-1$
                } catch (Exception e) {
                    parseErrorMsg = "Failed to validate DDL: unknown issue";  //$NON-NLS-1$
                }
            } else {
                parseErrorMsg = "No DDL to validate"; //$NON-NLS-1$
            }
            
            // Return the status and message
            KomodoStatusObject kso = new KomodoStatusObject("DDL Validation"); //$NON-NLS-1$
            if(ddlValid) 
                kso.addAttribute("validationResult", "Success"); //$NON-NLS-1$ //$NON-NLS-2$
            else {
                if(parseErrorMsg!=null) {
                    kso.addAttribute("validationResult", parseErrorMsg); //$NON-NLS-1$
                } else {
                    kso.addAttribute("validationResult", "Error"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }

            return commit(uow, mediaTypes, kso);
        } catch (final Exception e) {
            if ((uow != null) && (uow.getState() != State.ROLLED_BACK)) {
                uow.rollback();
            }

            if (e instanceof KomodoRestException) {
                throw (KomodoRestException)e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_SET_SERVICE_ERROR);
        }
    }
    
    /**
     * Find source VDBs in the workspace whose jndi matches the dataservice source model jndi
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the id of the Dataservice (cannot be empty)
     * @return the JSON representation of the VDB (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem finding the specified workspace VDB or constructing the JSON representation
     */
    @GET
    @Path( V1Constants.DATA_SERVICE_PLACEHOLDER + StringConstants.FORWARD_SLASH + V1Constants.SOURCE_VDB_MATCHES)
    @Produces( { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML } )
    @ApiOperation(value = "Find workspace source VDB matches for a Dataservice", response = RestVdb[].class)
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No dataservice could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON or XML is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getSourceVdbsForDataService( final @Context HttpHeaders headers,
                                                 final @Context UriInfo uriInfo,
                                                 @ApiParam(value = "Id of the dataservice", required = true)
                                                 final @PathParam( "dataserviceName" ) String dataserviceName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "getSourceVdbsForDataservice", true ); //$NON-NLS-1$

            Dataservice dataservice = findDataservice(uow, dataserviceName);
            if (dataservice == null)
                return commitNoDataserviceFound(uow, mediaTypes, dataserviceName);

            // They physical source models are named with the name of the service source VDB
            Vdb serviceVdb = dataservice.getServiceVdb(uow);
            List<String> sourceVdbNames = new ArrayList<String>();
            Model[] models = serviceVdb.getModels(uow);
            for(Model model : models) {
                if(model.getModelType(uow) == Model.Type.PHYSICAL) {
                    sourceVdbNames.add(model.getName(uow));
                }
            }
            
            List<Vdb> sourceVdbs = new ArrayList<Vdb>();
            if(!sourceVdbNames.isEmpty()) {
                // Add Vdbs with the source vdb names
                WorkspaceManager wsMgr = getWorkspaceManager(uow);
                Vdb[] wsVdbs = wsMgr.findVdbs(uow);
                for(Vdb wsVdb : wsVdbs) {
                    if(sourceVdbNames.contains(wsVdb.getName(uow))) {
                        sourceVdbs.add(wsVdb);
                    }
                }
            }
            
            final List< RestVdb > entities = new ArrayList< >();
            KomodoProperties properties = new KomodoProperties();
            properties.addProperty(VDB_EXPORT_XML_PROPERTY, false);
            for ( final Vdb vdb : sourceVdbs ) {
                RestVdb entity = entityFactory.create(vdb, uriInfo.getBaseUri(), uow, properties);
                entities.add(entity);
            }

            // create response
            return commit( uow, mediaTypes, entities );

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_FIND_SOURCE_VDB_ERROR, dataserviceName);
        }
    }
    
    /**
     * @param headers
     *        the request headers (never <code>null</code>)
     * @param uriInfo
     *        the request URI information (never <code>null</code>)
     * @param dataserviceName
     *        the id of the Dataservice (cannot be empty)
     * @return the info response (never <code>null</code>)
     * @throws KomodoRestException
     *         if there is a problem finding the specified workspace Dataservice or constructing the info response.
     */
    @GET
    @Path( V1Constants.DATA_SERVICE_PLACEHOLDER + StringConstants.FORWARD_SLASH + V1Constants.SERVICE_VIEW_INFO)
    @Produces( { MediaType.APPLICATION_JSON } )
    @ApiOperation(value = "retrieve the service view information for a dataservice")
    @ApiResponses(value = {
        @ApiResponse(code = 404, message = "No dataservice could be found with name"),
        @ApiResponse(code = 406, message = "Only JSON or XML is returned by this operation"),
        @ApiResponse(code = 403, message = "An error has occurred.")
    })
    public Response getServiceViewInfoForDataService( final @Context HttpHeaders headers,
                                                      final @Context UriInfo uriInfo,
                                                      @ApiParam(value = "Id of the dataservice", required = true)
                                                      final @PathParam( "dataserviceName" ) String dataserviceName) throws KomodoRestException {

        SecurityPrincipal principal = checkSecurityContext(headers);
        if (principal.hasErrorResponse())
            return principal.getErrorResponse();

        List<MediaType> mediaTypes = headers.getAcceptableMediaTypes();
        UnitOfWork uow = null;

        try {
            uow = createTransaction(principal, "getServiceViewInfoForDataService", true ); //$NON-NLS-1$

            Dataservice dataservice = findDataservice(uow, dataserviceName);
            if (dataservice == null)
                return commitNoDataserviceFound(uow, mediaTypes, dataserviceName);

            // Get the View Query Expression and extract the table names from the query
            String viewSql = null;
            String viewDdl = null;
            Map<String,String> tableSourceVdbMap = new HashMap<String, String>();
            
            Vdb serviceVdb = dataservice.getServiceVdb(uow);
            Model[] models = serviceVdb.getModels(uow);
            for(Model model : models) {
                // Get the view DDL from the virtual model
                if(model.getModelType(uow) == Model.Type.VIRTUAL) {
                    View[] views = model.getViews(uow);
                    viewSql = views[0].getQueryExpression(uow);
                    byte[] ddlBytes = model.export(uow, new Properties());
                    if (ddlBytes == null)
                        viewDdl = EMPTY_STRING;
                    else
                        viewDdl = new String(ddlBytes);
                // get the tables for each physical model
                } else if(model.getModelType(uow) == Model.Type.PHYSICAL) {
                    // saves mapping of table to source vdb name (physical models were named using ServiceSourceVdb name)
                    Table[] tables = model.getTables(uow);
                    for(Table table : tables) {
                        tableSourceVdbMap.put(table.getName(uow), model.getName(uow));
                    }
                }
            }
            
            // Generate source table info for each view table
            final List< RestDataserviceViewInfo > viewInfos = new ArrayList<RestDataserviceViewInfo>();
            Map<String, List<String>> tableColumnMap = getTableColumnNameMap(viewSql);
            
            // Determine LHS vs RHS for SQL tables (the map keys are aliased for joins)
            Set<String> sqlTables = tableColumnMap.keySet();
            String leftTableName = StringConstants.EMPTY_STRING;
            String rightTableName = StringConstants.EMPTY_STRING;
            boolean leftTableAliased = false;
            boolean rightTableAliased = false;
            for(String sqlTable : sqlTables) {
                // Left aliased
                if(sqlTable.endsWith(StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+LH_TABLE_ALIAS)) {
                    int aliasIndx = sqlTable.indexOf(StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+LH_TABLE_ALIAS);
                    leftTableName = sqlTable.substring(0,aliasIndx);
                    leftTableAliased = true;
                // Right aliased
                } else if(sqlTable.endsWith(StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+RH_TABLE_ALIAS)) {
                    int aliasIndx = sqlTable.indexOf(StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+RH_TABLE_ALIAS);
                    rightTableName = sqlTable.substring(0,aliasIndx);
                    rightTableAliased = true;
                // No alias - left
                } else {
                    leftTableName = sqlTable;
                }
            }
            
            // Create the view infos for the left and right tables
            for (String viewTable : tableSourceVdbMap.keySet()) {
                RestDataserviceViewInfo viewInfo = new RestDataserviceViewInfo();
                
                // Set LH vs RH on info
                if(viewTable.equals(leftTableName)) {
                    viewInfo.setInfoType(RestDataserviceViewInfo.LH_TABLE_INFO);
                } else if(viewTable.equals(rightTableName)){
                    viewInfo.setInfoType(RestDataserviceViewInfo.RH_TABLE_INFO);
                }
                // Source VDB and table
                viewInfo.setSourceVdbName(tableSourceVdbMap.get(viewTable));
                viewInfo.setTableName(viewTable);
                
                String mapKey = viewTable;
                if(viewInfo.getInfoType().equals(RestDataserviceViewInfo.LH_TABLE_INFO) && leftTableAliased) {
                    mapKey = mapKey+StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+LH_TABLE_ALIAS;
                } else if(viewInfo.getInfoType().equals(RestDataserviceViewInfo.RH_TABLE_INFO) && rightTableAliased) {
                    mapKey = mapKey+StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+RH_TABLE_ALIAS;
                } 
                List<String> colsForTable = tableColumnMap.get(mapKey);
                if(colsForTable!=null && !colsForTable.isEmpty()) {
                    viewInfo.setColumnNames(colsForTable);
                }
                viewInfos.add(viewInfo);
            }
            
            // Get criteria info for the view - if two tables were found
            RestDataserviceViewInfo criteriaInfo = getCriteriaInfo(viewSql);
            if(viewInfos.size()==2) {
                if( criteriaInfo != null ) {
                    viewInfos.add(criteriaInfo);
                }
            }
            
            // Add info for the raw view ddl
            RestDataserviceViewInfo viewInfo = new RestDataserviceViewInfo();
            viewInfo.setInfoType(RestDataserviceViewInfo.DDL_INFO);
            viewInfo.setViewDdl(viewDdl);
            
            // Determine whether the DDL is 'editable' - whether it has enough info for editor wizard.
            // Problem if either of the maps is empty
            if( tableSourceVdbMap.isEmpty() || tableColumnMap.isEmpty() ) {
                viewInfo.setViewEditable(false);
            // Problem if the map sizes dont match
            } else if ( tableSourceVdbMap.size() != tableColumnMap.size() ) {
                viewInfo.setViewEditable(false);
            // Problem if 2 tables (join) and there were no criteria found
            } else if ( tableColumnMap.size() == 2 && criteriaInfo == null ) {
                viewInfo.setViewEditable(false);
            } else {
                viewInfo.setViewEditable(true);
            }
            viewInfos.add(viewInfo);

            return commit(uow, mediaTypes, viewInfos);

        } catch ( final Exception e ) {
            if ( ( uow != null ) && ( uow.getState() != State.ROLLED_BACK ) ) {
                uow.rollback();
            }

            if ( e instanceof KomodoRestException ) {
                throw ( KomodoRestException )e;
            }

            return createErrorResponseWithForbidden(mediaTypes, e, DATASERVICE_SERVICE_FIND_VIEW_INFO_ERROR, dataserviceName);
        }
    }
    
    /*
     * Generate a mapping of tableName to columnName list from the SQL.  
     *    - if the SQL cannot be fully parsed, an empty map is returned.
     *    - TODO: replace the current string parsing with an appropriate SQL parser when available
     */
    private Map<String,List<String>> getTableColumnNameMap(String viewSql) {
        Map<String,List<String>> tableColumnMap = new HashMap<String,List<String>>();
        
        List<String> colNames = new ArrayList<String>();

        if(!StringUtils.isEmpty(viewSql)) {
            // Collect the ColumnNames - after the SELECT and before the FROM
            int startIndex = viewSql.indexOf(SQLConstants.Reserved.SELECT)+(SQLConstants.Reserved.SELECT).length();
            int endIndex = viewSql.indexOf(SQLConstants.Reserved.FROM+StringConstants.SPACE);
            // If SELECT or FROM not found, assume empty columns
            String viewColumnsStr = StringConstants.EMPTY_STRING;
            String[] viewCols = new String[0];
            if(startIndex > -1 && endIndex > startIndex) {
                viewColumnsStr = viewSql.substring(startIndex,endIndex);
            }
            if(!viewColumnsStr.trim().isEmpty()) {
                viewCols = viewColumnsStr.split(COMMA);
            }
            for(String cName : viewCols ) {
                String trimmedName = cName.trim();
                if(!trimmedName.startsWith("ROW_NUMBER()")) { //$NON-NLS-1$
                    colNames.add(cName.trim());
                }
            }
            
            // Get the FROM clause - if this is a join - determine how the tables are aliased.
            String fromStr = StringConstants.EMPTY_STRING;
            int fromStartIndex = viewSql.indexOf(SQLConstants.Reserved.FROM+StringConstants.SPACE);
            if(fromStartIndex > -1) {
                fromStr = viewSql.substring(fromStartIndex + (SQLConstants.Reserved.FROM+StringConstants.SPACE).length());
            }
            // If this is a join, extract the table names / aliases
            if(fromStr.contains(INNER_JOIN) || fromStr.contains(LEFT_OUTER_JOIN) || fromStr.contains(RIGHT_OUTER_JOIN) || fromStr.contains(FULL_OUTER_JOIN)) {
                int indxStart = 0;
                int indxEnd = fromStr.indexOf(SQLConstants.Reserved.AS+StringConstants.SPACE+LH_TABLE_ALIAS); 
                String lhTable = null;
                if(indxEnd > -1) {
                    lhTable = fromStr.substring(indxStart, indxEnd).trim();
                }

                indxStart = fromStr.indexOf(SQLConstants.Reserved.JOIN+StringConstants.SPACE);
                indxEnd = fromStr.indexOf(SQLConstants.Reserved.AS+StringConstants.SPACE+RH_TABLE_ALIAS); 
                String rhTable = null;
                if(indxStart > -1 && indxEnd > -1) {
                    rhTable = fromStr.substring(indxStart+(SQLConstants.Reserved.JOIN+StringConstants.SPACE).length(), indxEnd).trim();
                }
                
                // Now the table aliases have been determined - separate the original column names with the appropriate alias
                List<String> lhCols = new ArrayList<>();
                List<String> rhCols = new ArrayList<>();
                
                for(String colName : colNames) {
                    if(colName.startsWith(LH_TABLE_ALIAS_DOT)) {
                        lhCols.add(colName.substring(LH_TABLE_ALIAS_DOT.length()));
                    } else if(colName.startsWith(RH_TABLE_ALIAS_DOT)) {
                        rhCols.add(colName.substring(RH_TABLE_ALIAS_DOT.length()));
                    }
                }
                // If either of the tables is blank, there was a problem - leave the map empty
                // For joins the table alias is included to determine left and right
                if(!StringUtils.isBlank(lhTable) && !StringUtils.isBlank(rhTable)) {
                    tableColumnMap.put(lhTable+StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+LH_TABLE_ALIAS, lhCols);
                    tableColumnMap.put(rhTable+StringConstants.SPACE+SQLConstants.Reserved.AS+StringConstants.SPACE+RH_TABLE_ALIAS, rhCols);
                }
            } else {
                String tableName = fromStr.trim();
                if(!StringUtils.isBlank(tableName)) {
                    tableColumnMap.put(tableName, colNames);
                }
            }
            
        }

        return tableColumnMap;
    }
    
    /*
     * create the criteria info
     */
    private RestDataserviceViewInfo getCriteriaInfo(String viewSql) {
        // If sql does not have a join, return null info
        if(!hasJoin(viewSql)) {
            return null;
        }
        RestDataserviceViewInfo criteriaInfo = new RestDataserviceViewInfo();
        criteriaInfo.setInfoType(RestDataserviceViewInfo.CRITERIA_INFO);
        
        if(!StringUtils.isEmpty(viewSql)) {
            if(viewSql.indexOf(INNER_JOIN) != -1) {
                criteriaInfo.setJoinType(RestDataserviceViewInfo.JOIN_INNER);
            } else if(viewSql.indexOf(LEFT_OUTER_JOIN) != -1) {
                criteriaInfo.setJoinType(RestDataserviceViewInfo.JOIN_LEFT_OUTER);
            } else if(viewSql.indexOf(RIGHT_OUTER_JOIN) != -1) {
                criteriaInfo.setJoinType(RestDataserviceViewInfo.JOIN_RIGHT_OUTER);
            } else if(viewSql.indexOf(FULL_OUTER_JOIN) != -1) {
                criteriaInfo.setJoinType(RestDataserviceViewInfo.JOIN_FULL_OUTER);
            }
            
            // Find the criteria string
            String asRhAliasOn = SQLConstants.Reserved.AS + StringConstants.SPACE + RH_TABLE_ALIAS + StringConstants.SPACE + SQLConstants.Reserved.ON;
            int startIndex = viewSql.indexOf(asRhAliasOn);
            String criteriaStr = StringConstants.EMPTY_STRING;
            if(startIndex > -1) {
                criteriaStr = viewSql.substring(startIndex+(asRhAliasOn).length());
            }
            
            List<ViewBuilderCriteriaPredicate> predicates = new ArrayList<ViewBuilderCriteriaPredicate>();
            if(!StringUtils.isEmpty(criteriaStr)) {
                // Process the criteriaStr predicates
                while ( !StringUtils.isEmpty(criteriaStr) ) {
                    int orIndex = criteriaStr.indexOf(StringConstants.SPACE+OR+StringConstants.SPACE);
                    int andIndex = criteriaStr.indexOf(StringConstants.SPACE+AND+StringConstants.SPACE);
                    // No OR or AND.  Either a single predicate or the last predicate
                    if( orIndex == -1 && andIndex == -1 ) {
                        ViewBuilderCriteriaPredicate predicate = parsePredicate(criteriaStr, AND);
                        if(predicate.isComplete()) {
                            predicates.add(predicate);
                        }
                        criteriaStr = StringConstants.EMPTY_STRING;
                    // Has OR but no AND.
                    } else if( orIndex > -1 && andIndex == -1 ) {
                        String predicateStr = criteriaStr.substring(0, orIndex);
                        ViewBuilderCriteriaPredicate predicate = parsePredicate(predicateStr, OR);
                        if(predicate.isComplete()) {
                            predicates.add(predicate);
                        }
                        criteriaStr = criteriaStr.substring(orIndex + (StringConstants.SPACE+OR+StringConstants.SPACE).length());
                    // Has AND but no OR.
                    } else if( orIndex == -1 && andIndex > -1 ) {
                        String predicateStr = criteriaStr.substring(0, andIndex);
                        ViewBuilderCriteriaPredicate predicate = parsePredicate(predicateStr, AND);
                        if(predicate.isComplete()) {
                            predicates.add(predicate);
                        }
                        criteriaStr = criteriaStr.substring(andIndex + (StringConstants.SPACE+AND+StringConstants.SPACE).length());
                    // Has both - OR is first.
                    } else if( orIndex < andIndex ) {
                        String predicateStr = criteriaStr.substring(0, orIndex);
                        ViewBuilderCriteriaPredicate predicate = parsePredicate(predicateStr, OR);
                        if(predicate.isComplete()) {
                            predicates.add(predicate);
                        }
                        criteriaStr = criteriaStr.substring(orIndex + (StringConstants.SPACE+OR+StringConstants.SPACE).length());
                    // Has both - AND is first.
                    } else {
                        String predicateStr = criteriaStr.substring(0, andIndex);
                        ViewBuilderCriteriaPredicate predicate = parsePredicate(predicateStr, AND);
                        if(predicate.isComplete()) {
                            predicates.add(predicate);
                        }
                        criteriaStr = criteriaStr.substring(andIndex + (StringConstants.SPACE+AND+StringConstants.SPACE).length());
                    }
                }
                criteriaInfo.setCriteriaPredicates(predicates);                
            }
            // Check that at least one predicate was found, and it is complete
            if( predicates.size() == 0 ) {
                criteriaInfo = null;
            } else {
                for(ViewBuilderCriteriaPredicate predicate : predicates) {
                    if(!predicate.isComplete()) {
                        criteriaInfo = null;
                        break;
                    }
                }
            }
        }
        
        return criteriaInfo;
    }
    
    private ViewBuilderCriteriaPredicate parsePredicate(String predicateStr, String combineKeyword) {
        ViewBuilderCriteriaPredicate predicate = new ViewBuilderCriteriaPredicate();
        predicate.setCombineKeyword(combineKeyword);

        String[] criteriaCols = null;
        if( predicateStr.indexOf(StringConstants.SPACE+EQ+StringConstants.SPACE) > -1 ) {
            predicate.setOperator(EQ);
            criteriaCols = predicateStr.split(EQ);
        } else if( predicateStr.indexOf(StringConstants.SPACE+LT+StringConstants.SPACE) > -1) {
            predicate.setOperator(LT);
            criteriaCols = predicateStr.split(LT);
        } else if( predicateStr.indexOf(StringConstants.SPACE+GT+StringConstants.SPACE) > -1) {
            predicate.setOperator(GT);
            criteriaCols = predicateStr.split(GT);
        } else if( predicateStr.indexOf(StringConstants.SPACE+NE+StringConstants.SPACE) > -1) {
            predicate.setOperator(NE);
            criteriaCols = predicateStr.split(NE);
        } else if( predicateStr.indexOf(StringConstants.SPACE+LE+StringConstants.SPACE) > -1) {
            predicate.setOperator(LE);
            criteriaCols = predicateStr.split(LE);
        } else if( predicateStr.indexOf(StringConstants.SPACE+GE+StringConstants.SPACE) > -1) {
            predicate.setOperator(GE);
            criteriaCols = predicateStr.split(GE);
        }
        if(criteriaCols!=null) {
            for(int i=0; i<criteriaCols.length; i++ ) {
                String cCol = criteriaCols[i].trim();
                if(!StringUtils.isBlank(cCol)) {
                    if(cCol.startsWith(LH_TABLE_ALIAS_DOT)) {
                        predicate.setLhColumn(cCol.substring(LH_TABLE_ALIAS_DOT.length()));
                    } else if(cCol.startsWith(RH_TABLE_ALIAS_DOT)) {
                        predicate.setRhColumn(cCol.substring(RH_TABLE_ALIAS_DOT.length()));
                    }
                }
            }
        }
        return predicate;
    }
    
    /*
     * Determine if the ddl has a join
     */
    private boolean hasJoin(String viewDdl) {
        if( !StringUtils.isEmpty(viewDdl) && 
            ( viewDdl.indexOf(INNER_JOIN)!=-1 || viewDdl.indexOf(LEFT_OUTER_JOIN)!=-1 || viewDdl.indexOf(RIGHT_OUTER_JOIN)!=-1 || viewDdl.indexOf(FULL_OUTER_JOIN)!=-1 ) ) {
            return true;
        }
        return false;
    }
    
}
