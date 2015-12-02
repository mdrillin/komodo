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
package org.komodo.relational.datasource;

import org.komodo.core.KomodoLexicon;
import org.komodo.relational.RelationalObject;
import org.komodo.relational.RelationalProperties;
import org.komodo.relational.TypeResolver;
import org.komodo.relational.datasource.internal.DatasourceImpl;
import org.komodo.relational.workspace.WorkspaceManager;
import org.komodo.repository.ObjectImpl;
import org.komodo.spi.KException;
import org.komodo.spi.repository.KomodoObject;
import org.komodo.spi.repository.KomodoType;
import org.komodo.spi.repository.Repository;
import org.komodo.spi.repository.Repository.UnitOfWork;
import org.komodo.spi.repository.Repository.UnitOfWork.State;

/**
 * A model of a datasource instance
 */
public interface Datasource extends RelationalObject {

    /**
     * The type identifier.
     */
    int TYPE_ID = Datasource.class.hashCode();

    /**
     * Identifier of this object
     */
    KomodoType IDENTIFIER = KomodoType.DATASOURCE;

    /**
     * An empty array of teiids.
     */
    Datasource[] NO_DATASOURCES = new Datasource[0];

    /**
     * The resolver of a {@link Datasource}.
     */
    public static final TypeResolver< Datasource > RESOLVER = new TypeResolver< Datasource >() {
    
        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.TypeResolver#create(org.komodo.spi.repository.Repository.UnitOfWork,
         *      org.komodo.spi.repository.Repository, org.komodo.spi.repository.KomodoObject, java.lang.String,
         *      org.komodo.relational.RelationalProperties)
         */
        @Override
        public Datasource create( final UnitOfWork transaction,
                             final Repository repository,
                             final KomodoObject parent,
                             final String id,
                             final RelationalProperties properties ) throws KException {
            final WorkspaceManager mgr = WorkspaceManager.getInstance( repository );
            return mgr.createDatasource( transaction, parent, id );
        }
    
        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.TypeResolver#identifier()
         */
        @Override
        public KomodoType identifier() {
            return IDENTIFIER;
        }
    
        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.TypeResolver#owningClass()
         */
        @Override
        public Class< DatasourceImpl > owningClass() {
            return DatasourceImpl.class;
        }
    
        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.TypeResolver#resolvable(org.komodo.spi.repository.Repository.UnitOfWork,
         *      org.komodo.spi.repository.KomodoObject)
         */
        @Override
        public boolean resolvable( final UnitOfWork transaction,
                                   final KomodoObject kobject ) throws KException {
            return ObjectImpl.validateType( transaction, kobject.getRepository(), kobject, KomodoLexicon.DataSource.NODE_TYPE );
        }
    
        /**
         * {@inheritDoc}
         *
         * @see org.komodo.relational.TypeResolver#resolve(org.komodo.spi.repository.Repository.UnitOfWork,
         *      org.komodo.spi.repository.KomodoObject)
         */
        @Override
        public Datasource resolve( final UnitOfWork transaction,
                              final KomodoObject kobject ) throws KException {
            return new DatasourceImpl( transaction, kobject.getRepository(), kobject.getAbsolutePath() );
        }
    
    };

    /**
     * @param uow
     *        the transaction (cannot be <code>null</code> or have a state that is not {@link State#NOT_STARTED})
     * @return id of this teiid model
     * @throws KException
     */
    String getId(UnitOfWork uow) throws KException;

    /**
     * @param transaction
     *        the transaction (cannot be <code>null</code> or have a state that is not {@link State#NOT_STARTED})
     * @return jndi name of this datasource.
     * @throws KException if error occurs
     */
    String getJndiName(UnitOfWork transaction) throws KException;

    /**
     * @param transaction
     *        the transaction (cannot be <code>null</code> or have a state that is not {@link State#NOT_STARTED})
     * @param jndiName jndi name of this datasource
     * @throws KException if error occurs
     */
    void setJndiName(UnitOfWork transaction, String jndiName) throws KException;

    /**
     * @param transaction
     *        the transaction (cannot be <code>null</code> or have a state that is not {@link State#NOT_STARTED})
     * @return driver name of this datasource.
     * @throws KException if error occurs
     */
    String getDriverName(UnitOfWork transaction) throws KException;

    /**
     * @param transaction
     *        the transaction (cannot be <code>null</code> or have a state that is not {@link State#NOT_STARTED})
     * @param driverName driver name of this datasource
     * @throws KException if error occurs
     */
    void setDriverName(UnitOfWork transaction, String driverName) throws KException;
}
