/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.komodo.relational.commands.condition;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import java.util.Collections;
import org.junit.Test;
import org.komodo.relational.commands.AbstractCommandTest;
import org.komodo.relational.commands.storedprocedure.StoredProcedureRenameCommand;
import org.komodo.shell.api.CommandResult;

/**
 * Test Class to test {@link StoredProcedureRenameCommand}.
 */
@SuppressWarnings( { "javadoc",
                     "nls" } )
public final class ConditionRenameCommandTest extends AbstractCommandTest {

    @Test( expected = AssertionError.class )
    public void shouldFailWhenTooManyArgs() throws Exception {
        setup( "commandFiles", "addConditions.cmd" );

        final String[] commands = { "cd myCondition1",
                                    "rename foo bar" };
        execute( commands );
    }

    @Test
    public void shouldNotHaveTabCompletion() throws Exception {
        setup( "commandFiles", "addConditions.cmd" );

        final String[] commands = { "cd myCondition1" };
        final CommandResult result = execute( commands );
        assertCommandResultOk( result );
        assertTabCompletion( "rename ", Collections.<CharSequence>emptyList() );
    }

    @Test
    public void shouldRenameSelf() throws Exception {
        setup( "commandFiles", "addConditions.cmd" );

        final String newName = "blah";
        final String[] commands = { "cd myCondition1",
                                    "rename " + newName };
        final CommandResult result = execute( commands );
        assertCommandResultOk( result );

        assertThat( this.wsStatus.getCurrentContext().getName( getTransaction() ), is( newName ) );
    }

}
