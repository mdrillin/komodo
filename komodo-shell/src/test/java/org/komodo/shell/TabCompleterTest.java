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
package org.komodo.shell;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.aesh.complete.CompleteOperation;
import org.junit.Test;
import org.komodo.shell.api.CommandResult;

/**
 * Test Class to test completion of a command
 */
@SuppressWarnings({"javadoc", "nls"})
public class TabCompleterTest extends AbstractCommandTest {

    @Test
    public void shouldCompleteEmptyWorkspaceCommand()throws Exception{
        // Goto workspace
        final String[] commands = { "workspace" };
        final CommandResult result = execute(commands);
        assertCommandResultOk(result);
        
        // Init tab completer
        TabCompleter tabCompleter = new TabCompleter(wsStatus.getCommandFactory());
 
        // Get completion candidates for nothing entered
        CompleteOperation completeOp = new CompleteOperation("",0);
        tabCompleter.complete(completeOp); 
        
        assertThat(completeOp.getCompletionCandidates().size(), not(0));
    }

    @Test
    public void shouldTestCommandCaseVariations()throws Exception{
        // Goto workspace
        final String[] commands = { "workspace" };
        final CommandResult result = execute(commands);
        assertCommandResultOk(result);
        
        // Init tab completer
        TabCompleter tabCompleter = new TabCompleter(wsStatus.getCommandFactory());
 
        // Get completion candidates for partial "show-global" command
        
        // Lower case, one completion candidate
        CompleteOperation completionOp = new CompleteOperation("show-glo",0);
        tabCompleter.complete(completionOp); 
        List<String> expectedCandidates = new ArrayList<String>(1);
        expectedCandidates.add("show-global");
        assertCompletionCandidates(completionOp, expectedCandidates);
        
        // Mixed case, no completion candidate
        completionOp = new CompleteOperation("show-Gl",0);
        tabCompleter.complete(completionOp); 
        expectedCandidates.clear();
        assertCompletionCandidates(completionOp, expectedCandidates);

        // Upper case, no completion candidate
        completionOp = new CompleteOperation("SHOW-GL",0);
        tabCompleter.complete(completionOp); 
        assertCompletionCandidates(completionOp, expectedCandidates);
    }
    
    private void assertCompletionCandidates(CompleteOperation completeOp, List<String> expectedCandidates) {
        List<String> actualCandidates = completeOp.getCompletionCandidates();
        String compareMessage="Expected: "+Arrays.toString(expectedCandidates.toArray())+" Actual: "+Arrays.toString(actualCandidates.toArray());
        assertThat("Invalid number of command completion candidates "+compareMessage,actualCandidates.size(), is(expectedCandidates.size()));
        assertThat("Invalid elements: "+compareMessage, actualCandidates.containsAll(expectedCandidates), is(true));
    }

}
