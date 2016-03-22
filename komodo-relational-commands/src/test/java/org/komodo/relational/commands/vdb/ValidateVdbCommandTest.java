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
package org.komodo.relational.commands.vdb;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.komodo.relational.commands.AbstractCommandTest;
import org.komodo.shell.api.CommandResult;

/**
 * Test Class to test {@link ValidateVdbCommand}.
 */
@SuppressWarnings( {"javadoc", "nls"} )
public final class ValidateVdbCommandTest extends AbstractCommandTest {

    @Test
    public void testValidateVdbFull() throws Exception {
        final String[] commands = {
            "create-vdb testVdb vdbPath",
            "cd testVdb",
            "validate-vdb"};
        final CommandResult result = execute( commands );
        assertCommandResultOk(result);

        // Check the output
        String writerOutput = getCommandOutput();
        assertTrue( writerOutput,
                    writerOutput.contains( "The VDB 'vdb:connectionType' property is required and must match the specified pattern" ) );
    }

    @Test
    public void testValidateVdb() throws Exception {
        final String[] commands = {
            "create-vdb testVdb vdbPath",
            "cd testVdb",
            "validate-vdb false"};
        final CommandResult result = execute( commands );
        assertCommandResultOk(result);

        // Check the output
        String writerOutput = getCommandOutput();
        assertTrue( writerOutput,
                    writerOutput.contains( "The VDB 'vdb:connectionType' property is required and must match the specified pattern" ) );
    }

}
