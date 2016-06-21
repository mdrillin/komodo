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
package org.komodo.relational.dataservice.internal;

import org.komodo.importer.ImportMessages;
import org.komodo.importer.ImportOptions;
import org.komodo.importer.ImportOptions.OptionKeys;
import org.komodo.relational.dataservice.Dataservice;
import org.komodo.relational.importer.vdb.VdbImporter;
import org.komodo.spi.KException;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.Repository;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.modeshape.jcr.JcrLexicon;

/**
 *
 */
public class DataserviceImporter extends VdbImporter {

    /**
     * constructor
     *
     * @param repository repository into which ddl should be imported
     *
     */
    public DataserviceImporter(Repository repository) {
        super(repository);
    }

    @Override
    protected void executeImport(UnitOfWork transaction,
                                 String content,
                                 KomodoObject parentObject,
                                 ImportOptions importOptions,
                                 ImportMessages importMessages) throws KException {

        String dataserviceName = importOptions.getOption(OptionKeys.NAME).toString();

        Dataservice dataservice = getWorkspaceManager().createDataservice(transaction, parentObject, dataserviceName);
        KomodoObject fileNode = dataservice.addChild(transaction, JcrLexicon.CONTENT.getString(), null);
        fileNode.setProperty(transaction, JcrLexicon.DATA.getString(), content);
    }

}
