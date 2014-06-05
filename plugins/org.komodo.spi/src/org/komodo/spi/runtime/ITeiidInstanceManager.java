/*************************************************************************************
 * Copyright (c) 2014 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     JBoss by Red Hat - Initial implementation.
 ************************************************************************************/
package org.komodo.spi.runtime;

import java.util.Collection;

import org.komodo.spi.runtime.version.ITeiidVersion;

/**
 *
 */
public interface ITeiidInstanceManager extends EventManager {

    /**
     * State of the teiid instance manager
     */
    public enum RuntimeState {
        /**
         * State when the instance is first constructed
         */
        INVALID,

        /**
         * State when the instance is fully restored and ready to be used
         */
        STARTED,

        /**
         * State when the instance is restoring teiid instance configurations
         */
        RESTORING,

        /**
         * State when the instance is in the process of shutting down
         */
        SHUTTING_DOWN,

        /**
         * State when the instance has fully shutdown
         */
        SHUTDOWN
    }

    /**
     * Default teiid instance version property id
     */
    String DEFAULT_TEIID_INSTANCE_VERSION_ID = "defaultTeiidInstanceVersion"; //$NON-NLS-1$

    /**
     * Extension Point Element ID
     */
    String TEIID_INSTANCE_MANAGER_ELEMENT_ID = "instanceManager"; //$NON-NLS-1$

    /**
     * Registers the specified <code>PersistedInstance</code>.
     * 
     * @param teiidInstance the teiid instance being added (never <code>null</code>)
     * @return a true if the instance was added to the registry
     */
    boolean addInstance(ITeiidInstance teiidInstance);

    /**
     * @return defaultInstance
     */
    ITeiidInstance getDefaultInstance();

    /**
     * @param id the id of the teiid instance being requested (never <code>null</code> )
     * @return the requested teiid instance or <code>null</code> if not found in the registry
     */
    ITeiidInstance getInstance(String id);

    /**
     * @param teiidParent the parent of the requested Teiid Instance
     * @return the requested teiid parent or <code>null</code> if not found in the registry
     */
    ITeiidInstance getInstance(ITeiidParent teiidParent);

    /**
     * @return an unmodifiable collection of registered instances (never <code>null</code>)
     */
    Collection<ITeiidInstance> getInstances();

    /**
     * @return the state
     */
    RuntimeState getState();

    /**
     * @return true if manager is started
     */
    boolean isStarted();

    /**
     * Get the targeted Teiid Instance version
     *
     * @return Teiid Instance version
     */
    ITeiidVersion getDefaultVersion();

    /**
     * Is this teiid instance the default
     * 
     * @param teiidInstance
     * 
     * @return true if this teiid instance is the default, false otherwise.
     */
    boolean isDefaultInstance(ITeiidInstance teiidInstance);

    /**
     * @param teiidInstance the teiid instance being tested (never <code>null</code>)
     * @return <code>true</code> if the teiid instance has been registered
     */
    boolean isRegistered(ITeiidInstance teiidInstance);

    /**
     * @param teiidInstance the instance being removed (never <code>null</code>)
     * @return a status indicating if the specified instance was removed from the registry (never <code>null</code>)
     */
    boolean removeInstance(ITeiidInstance teiidInstance);

    /**
     * @param teiidInstance Sets default instance to the specified value. May be null.
     */
    void setDefaultInstance(ITeiidInstance teiidInstance);

    /**
     * Try and restore the manager's prior state
     */
    void restoreState();

    /**
     * Disposes of this manager.
     *
     * @throws Exception 
     */
    void dispose() throws Exception;

    /**
     * Add a listener to be notified in the event the default teiid instance
     * version is changed
     * 
     * @param listener
     */
    void addTeiidInstanceVersionListener(ITeiidInstanceVersionListener listener);

    /**
     * Remove a listener no longer interested in listening
     * to changes in instance version
     * 
     * @param listener
     */
    void removeTeiidInstanceVersionListener(ITeiidInstanceVersionListener listener);
}