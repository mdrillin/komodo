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

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.net.URI;
import java.util.Collection;
import java.util.Properties;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.komodo.rest.KomodoRestV1Application.V1Constants;
import org.komodo.rest.RestLink;
import org.komodo.rest.RestLink.LinkType;
import org.komodo.rest.relational.AbstractKomodoServiceTest;
import org.komodo.rest.relational.KomodoRestUriBuilder.SettingNames;
import org.komodo.rest.relational.dataservice.RestDataservice;
import org.komodo.rest.relational.datasource.RestDataSource;
import org.komodo.rest.relational.json.KomodoJsonMarshaller;
import org.komodo.rest.relational.request.KomodoDataserviceUpdateAttributes;
import org.komodo.rest.relational.response.RestDataSourceDriver;
import org.komodo.rest.relational.response.RestVdb;
import org.komodo.spi.runtime.version.DefaultTeiidVersion.Version;
import org.komodo.test.utils.TestUtilities;

@SuppressWarnings( {"javadoc", "nls", "deprecation"} )
public final class KomodoDataserviceServiceTest extends AbstractKomodoServiceTest {

    public static final String DATASERVICE_NAME = "MyDataService"; 
    
    @Rule
    public TestName testName = new TestName();

    @Test
    public void shouldGetDataservices() throws Exception {
        createDataservice(DATASERVICE_NAME);

        // get
        URI uri = _uriBuilder.workspaceDataservicesUri();
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        assertNotNull(response.getEntity());

        final String entities = response.getEntity();
        assertThat(entities, is(notNullValue()));

        // make sure the Dataservice JSON document is returned for each dataservice
        RestDataservice[] dataservices = KomodoJsonMarshaller.unmarshallArray(entities, RestDataservice[].class);

        assertEquals(1, dataservices.length);
        RestDataservice myService = dataservices[0];
        assertNotNull(myService.getId());
        assertTrue(DATASERVICE_NAME.equals(myService.getId()));
        assertNotNull(myService.getDataPath());
        assertNotNull(myService.getkType());
    }
    
    @Test
    public void shouldReturnEmptyListWhenNoDataservicesInWorkspace() throws Exception {
        URI uri = _uriBuilder.workspaceDataservicesUri();
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        RestDataservice[] dataservices = KomodoJsonMarshaller.unmarshallArray(entity, RestDataservice[].class);
        assertNotNull(dataservices);
        assertEquals(0, dataservices.length);
    }
    
    @Test
    public void shouldGetDataservice() throws Exception {
        createDataservice(DATASERVICE_NAME);

        // get
        Properties settings = _uriBuilder.createSettings(SettingNames.DATA_SERVICE_NAME, DATASERVICE_NAME);
        _uriBuilder.addSetting(settings, SettingNames.DATA_SERVICE_PARENT_PATH, _uriBuilder.workspaceDataservicesUri());

        URI uri = _uriBuilder.dataserviceUri(LinkType.SELF, settings);
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        RestDataservice dataservice = KomodoJsonMarshaller.unmarshall(entity, RestDataservice.class);
        assertNotNull(dataservice);
        
        assertEquals(dataservice.getId(), DATASERVICE_NAME);
    }

    @Test
    public void shouldGetDataserviceConnections() throws Exception {
        loadStatesDataService();

        // get
        String dsName = TestUtilities.US_STATES_DATA_SERVICE_NAME;
        Properties settings = _uriBuilder.createSettings(SettingNames.DATA_SERVICE_NAME, dsName);
        _uriBuilder.addSetting(settings, SettingNames.DATA_SERVICE_PARENT_PATH, _uriBuilder.workspaceDataservicesUri());

        URI uri = _uriBuilder.dataserviceUri(LinkType.CONNECTIONS, settings);
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        System.out.println("Response:\n" + entity);

        RestDataSource[] datasources = KomodoJsonMarshaller.unmarshallArray(entity, RestDataSource[].class);
        assertNotNull(datasources);
        assertEquals(1, datasources.length);

        RestDataSource dataSource = datasources[0];
        assertEquals("MySqlPool", dataSource.getId());
        assertEquals("java:/MySqlDS", dataSource.getJndiName());
        if (getTeiidVersion().isGreaterThanOrEqualTo(Version.TEIID_9_0))
            assertEquals("mysql-connector-java-5.1.39-bin.jar_com.mysql.jdbc.Driver_5_1", dataSource.getDriverName());
        else
            assertEquals("mysql-connector-java-5.1.39-bin.jarcom.mysql.jdbc.Driver_5_1", dataSource.getDriverName());

        Collection<RestLink> links = dataSource.getLinks();
        assertNotNull(links);
        assertEquals(3, links.size());

        for(RestLink link : links) {
            LinkType rel = link.getRel();
            assertTrue(LinkType.SELF.equals(rel) || LinkType.PARENT.equals(rel) || LinkType.CHILDREN.equals(rel));

            if (LinkType.SELF.equals(rel)) {
                String href = _uriBuilder.workspaceDatasourcesUri() + FORWARD_SLASH + dataSource.getId();
                assertEquals(href, link.getHref().toString());
            }
        }
    }
    
    @Test
    public void shouldGetDataserviceDrivers() throws Exception {
        loadStatesDataService();

        // get
        String dsName = TestUtilities.US_STATES_DATA_SERVICE_NAME;
        Properties settings = _uriBuilder.createSettings(SettingNames.DATA_SERVICE_NAME, dsName);
        _uriBuilder.addSetting(settings, SettingNames.DATA_SERVICE_PARENT_PATH, _uriBuilder.workspaceDataservicesUri());

        URI uri = _uriBuilder.dataserviceUri(LinkType.DRIVERS, settings);
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        System.out.println("Response:\n" + entity);

        RestDataSourceDriver[] drivers = KomodoJsonMarshaller.unmarshallArray(entity, RestDataSourceDriver[].class);
        assertNotNull(drivers);
        assertEquals(1, drivers.length);

        RestDataSourceDriver dataSourceDriver = drivers[0];
        assertEquals("mysql-connector-java-5.1.39-bin.jar", dataSourceDriver.getName());
    }

    @Test
    public void shouldGetViewInfoForDataService() throws Exception {
        loadStatesDataService();

        // get
        String dsName = TestUtilities.US_STATES_DATA_SERVICE_NAME;
        Properties settings = _uriBuilder.createSettings(SettingNames.DATA_SERVICE_NAME, dsName);
        _uriBuilder.addSetting(settings, SettingNames.DATA_SERVICE_PARENT_PATH, _uriBuilder.workspaceDataservicesUri());

        URI uri = _uriBuilder.dataserviceUri(LinkType.SERVICE_VIEW_INFO, settings);
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        assertTrue(entity.contains("sourceVdbName"));
        assertTrue(entity.contains("ServiceSource"));
        assertTrue(entity.contains("tableName"));
        assertTrue(entity.contains("state"));
        assertTrue(entity.contains("columnNames"));
        assertTrue(entity.contains("id"));
    }
    
    @Test
    public void shouldGetWorkspaceSourceVdbForDataService() throws Exception {
        loadServiceSourceVdb();
        loadStatesDataService();
        
        // get
        String dsName = TestUtilities.US_STATES_DATA_SERVICE_NAME;
        Properties settings = _uriBuilder.createSettings(SettingNames.DATA_SERVICE_NAME, dsName);
        _uriBuilder.addSetting(settings, SettingNames.DATA_SERVICE_PARENT_PATH, _uriBuilder.workspaceDataservicesUri());

        URI uri = _uriBuilder.dataserviceUri(LinkType.SOURCE_VDB_MATCHES, settings);
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        RestVdb[] vdbs = KomodoJsonMarshaller.unmarshallArray(entity, RestVdb[].class);
        assertNotNull(vdbs);
        assertEquals(1, vdbs.length);

        RestVdb vdb = vdbs[0];
        assertEquals("ServiceSource", vdb.getId());
    }
    
    @Test
    public void shouldNotGetWorkspaceSourceVdbForDataService() throws Exception {
        loadStatesDataService();
        
        // get
        String dsName = TestUtilities.US_STATES_DATA_SERVICE_NAME;
        Properties settings = _uriBuilder.createSettings(SettingNames.DATA_SERVICE_NAME, dsName);
        _uriBuilder.addSetting(settings, SettingNames.DATA_SERVICE_PARENT_PATH, _uriBuilder.workspaceDataservicesUri());

        URI uri = _uriBuilder.dataserviceUri(LinkType.SOURCE_VDB_MATCHES, settings);
        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        ClientResponse<String> response = request.get(String.class);

        final String entity = response.getEntity();
        assertThat(entity, is(notNullValue()));

        RestVdb[] vdbs = KomodoJsonMarshaller.unmarshallArray(entity, RestVdb[].class);
        assertNotNull(vdbs);
        assertEquals(0, vdbs.length);
    }

    @Test
    public void shouldNotSetServiceVdbForSingleTableMissingParameter() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_SINGLE_TABLE).build();

        // Required parms (dataserviceName, modelSourcePath, tablePath).
        // fails due to missing modelSourcePath
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/table");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("missing one or more required parameters"));
    }

    @Test
    public void shouldNotSetServiceVdbForSingleTableBadTablePath() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_SINGLE_TABLE).build();

        // Required parms (dataserviceName, modelSourcePath, tablePath).
        // fails due to bad table path - (doesnt resolve to a table)
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/table");
        updateAttr.setModelSourcePath("/path/to/ModelSource");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("specified Table does not exist"));
    }

    @Test
    public void shouldNotSetServiceVdbForSingleTableBadDdl() throws Exception {
        loadVdbs();
        createDataservice(DATASERVICE_NAME);
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_SINGLE_TABLE).build();

        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");
        updateAttr.setModelSourcePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/Accounts/vdb:sources/h2-connector");
        updateAttr.setViewDdl("CREATE VIEW blah blah");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("DDL Parsing encountered a problem"));
    }

    @Test
    public void shouldSetServiceVdbForSingleTable() throws Exception {
        loadVdbs();
        createDataservice(DATASERVICE_NAME);
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_SINGLE_TABLE).build();

        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");
        updateAttr.setModelSourcePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/Accounts/vdb:sources/h2-connector");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("Successfully updated"));
    }
    
    @Test
    public void shouldNotSetServiceVdbForJoinTablesMissingParameter() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_JOIN_TABLES).build();

        // Required parms (dataserviceName, modelSourcePath, rhModelSourcePath, tablePath, rhTablePath).
        // fails due to missing rhModelSourcePath
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/lhTable");
        updateAttr.setRhTablePath("/path/to/rhTable");
        updateAttr.setModelSourcePath("/path/to/lhModelSource");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("missing one or more required parameters"));
    }

    @Test
    public void shouldNotSetServiceVdbForJoinTablesBadTablePath() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_JOIN_TABLES).build();

        // Required parms (dataserviceName, modelSourcePath, rhModelSourcePath, tablePath, rhTablePath).
        // Must also have 'viewDdl' OR 
        // fails due to bad table path
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/lhTable");
        updateAttr.setRhTablePath("/path/to/rhTable");
        updateAttr.setModelSourcePath("/path/to/lhModelSource");
        updateAttr.setRhModelSourcePath("/path/to/rhModelSource");
        updateAttr.setViewDdl("CREATE VIEW blah blah");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("specified Table does not exist"));
    }

    @Test
    public void shouldNotSetServiceVdbForJoinTablesBadDdl() throws Exception {
        loadVdbs();
        createDataservice(DATASERVICE_NAME);
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VDB_FOR_JOIN_TABLES).build();

        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");
        updateAttr.setRhTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");
        updateAttr.setModelSourcePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/Accounts/vdb:sources/h2-connector");
        updateAttr.setRhModelSourcePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/Accounts/vdb:sources/h2-connector");
        updateAttr.setViewDdl("CREATE VIEW blah blah");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        // Bogus DDL results in an error
        final String entity = response.getEntity();
        assertTrue(entity.contains("DDL Parsing encountered a problem"));
    }
    
    @Test
    public void shouldNotGenerateViewDdlForSingleTableMissingParameter() throws Exception {
        loadVdbs();
        createDataservice(DATASERVICE_NAME);
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VIEW_DDL_FOR_SINGLE_TABLE).build();

        // Required parms (dataserviceName, tablePath).
        // fails due to missing table path
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        // Bogus DDL results in an error
        final String entity = response.getEntity();
        assertTrue(entity.contains("missing one or more required parameters"));
    }
    
    @Test
    public void shouldNotGenerateViewDdlForSingleTableBadTablePath() throws Exception {
        loadVdbs();
        createDataservice(DATASERVICE_NAME);
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VIEW_DDL_FOR_SINGLE_TABLE).build();

        // Required parms (dataserviceName, tablePath).
        // fails due to bad table path (does not resolve to table)
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/table");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        // Bogus DDL results in an error
        final String entity = response.getEntity();
        assertTrue(entity.contains("specified Table does not exist"));
    }
    
    @Test
    public void shouldGenerateViewDdlForSingleTable() throws Exception {
        loadVdbs();
        createDataservice(DATASERVICE_NAME);
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VIEW_DDL_FOR_SINGLE_TABLE).build();

        // Required parms (dataserviceName, tablePath).
        // Valid entries - should generate DDL
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("infoType"));
        assertTrue(entity.contains("CREATE VIEW MyDataServiceView"));
    }
    
    @Test
    public void shouldNotGenerateViewDdlForJoinTablesMissingParameter() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VIEW_DDL_FOR_JOIN_TABLES).build();

        // Required parms (dataserviceName, tablePath, rhTablePath, joinType).
        // fails due to missing joinType
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/lhTable");
        updateAttr.setRhTablePath("/path/to/rhTable");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("missing one or more required parameters"));
    }

    @Test
    public void shouldNotGenerateViewDdlForJoinTablesBadTablePath() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VIEW_DDL_FOR_JOIN_TABLES).build();

        // Required parms (dataserviceName, tablePath, rhTablePath, joinType).
        // fails due to bad table path (does not resolve to a Table)
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("/path/to/lhTable");
        updateAttr.setRhTablePath("/path/to/rhTable");
        updateAttr.setJoinType("INNER");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("specified Table does not exist"));
    }
    
    @Test
    public void shouldGenerateViewDdlForJoinTables() throws Exception {
        createDataservice(DATASERVICE_NAME);

        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.SERVICE_VIEW_DDL_FOR_JOIN_TABLES).build();

        // Required parms (dataserviceName, tablePath, rhTablePath, joinType).
        // Valid entries - should generate DDL
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setDataserviceName(DATASERVICE_NAME);
        updateAttr.setTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");
        updateAttr.setRhTablePath("tko:komodo/tko:workspace/"+USER_NAME+"/Portfolio/PersonalValuations/Sheet1");
        updateAttr.setJoinType("INNER");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("infoType"));
        assertTrue(entity.contains("CREATE VIEW MyDataServiceView"));
    }
    
    @Test
    public void shouldFailDdlValidationNoDDL() throws Exception {
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.VALIDATE_DDL).build();

        // Required param (viewDdl).
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setViewDdl("");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("No DDL to validate"));
    }
    
    @Test
    public void shouldFailDdlValidationUnknownToken() throws Exception {
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.VALIDATE_DDL).build();

        // Required param (viewDdl).
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setViewDdl("CRE");

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("Check your DDL for completeness"));
    }
    
    @Test
    public void shouldValidateDdl() throws Exception {
        String VALID_DDL = "CREATE VIEW MyView (RowId integer PRIMARY KEY, Col1 string, Col2 string) AS \n"
        + "SELECT ROW_NUMBER() OVER (ORDER BY Col1), Col1, Col2 \n"
        + "FROM MyTable;";
        
        URI dataservicesUri = _uriBuilder.workspaceDataservicesUri();
        URI uri = UriBuilder.fromUri(dataservicesUri).path(V1Constants.VALIDATE_DDL).build();

        // Required param (viewDdl).
        KomodoDataserviceUpdateAttributes updateAttr = new KomodoDataserviceUpdateAttributes();
        updateAttr.setViewDdl(VALID_DDL);

        ClientRequest request = request(uri, MediaType.APPLICATION_JSON_TYPE);
        addJsonConsumeContentType(request);
        addBody(request, updateAttr);

        ClientResponse<String> response = request.post(String.class);

        final String entity = response.getEntity();
        assertTrue(entity.contains("Success"));
    }

}
