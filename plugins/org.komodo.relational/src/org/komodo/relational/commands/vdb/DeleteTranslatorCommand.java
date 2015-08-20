/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.komodo.relational.commands.vdb;

import static org.komodo.relational.commands.vdb.VdbCommandMessages.MISSING_TRANSLATOR_NAME;
import org.komodo.relational.vdb.Vdb;
import org.komodo.shell.api.WorkspaceStatus;

/**
 * A shell command to delete a translator from a VDB.
 */
public class DeleteTranslatorCommand extends VdbShellCommand {

    static final String NAME = "delete-translator"; //$NON-NLS-1$

    /**
     * @param status
     *        the shell's workspace status (cannot be <code>null</code>)
     */
    public DeleteTranslatorCommand( final WorkspaceStatus status ) {
        super( NAME, true, status );
    }

    /**
     * {@inheritDoc}
     *
     * @see org.komodo.shell.api.ShellCommand#execute()
     */
    @Override
    protected boolean doExecute() throws Exception {
        final String translatorName = requiredArgument( 0, MISSING_TRANSLATOR_NAME.getMessage() );

        final Vdb vdb = getVdb();
        vdb.removeTranslator( getTransaction(), translatorName );

        return true;
    }

}
