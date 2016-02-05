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
package org.komodo.modeshape.lib.sequencer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.jcr.Binary;
import javax.jcr.NamespaceRegistry;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.modeshape.common.text.ParsingException;
import org.modeshape.common.util.IoUtil;
import org.modeshape.jcr.api.nodetype.NodeTypeManager;
import org.teiid.modeshape.sequencer.ddl.StandardDdlLexicon;
import org.teiid.modeshape.sequencer.ddl.TeiidDdlParser;
import org.teiid.modeshape.sequencer.ddl.TeiidDdlSequencer;
import org.teiid.modeshape.sequencer.ddl.node.AstNode;
import org.teiid.modeshape.sequencer.ddl.node.AstNodeFactory;

/**
 * Subclass of {@link TeiidDdlSequencer} that only allows the Teiid DDL dialect, avoiding confusion with other DDL parsers.
 */
public class KDdlSequencer extends TeiidDdlSequencer {

    private final TeiidDdlParser teiidParser = new TeiidDdlParser();

    @Override
    public boolean execute(Property inputProperty, Node outputNode, Context context) throws Exception {
        if (! super.execute(inputProperty, outputNode, context)) {

            //
            // We know the sequencer failed to execute but unfortunately the parsing exception
            // are handled and simply pushed to the logger. We want to throw them back up to
            // the calling transaction so they get some visibility
            //

            AstNodeFactory nodeFactory = new AstNodeFactory();
            final AstNode tempNode = nodeFactory.node(StandardDdlLexicon.STATEMENTS_CONTAINER);
            Binary ddlContent = inputProperty.getBinary();
            InputStream stream = null;

            try {
                stream = ddlContent.getStream();
                teiidParser.parse(IoUtil.read(stream), tempNode, null);
            } catch (ParsingException e) {
                throw new Exception(e);
            } catch (IOException e) {
                throw new Exception(e);
            } finally {
                if ( stream != null ) {
                    stream.close();
                }
            }

            //
            // Something went wrong but clearly not a parsing exception
            //
            return false;
        }

        if (! outputNode.hasNode(StandardDdlLexicon.STATEMENTS_CONTAINER))
            return false;

        Node ddlStmtsNode = outputNode.getNode(StandardDdlLexicon.STATEMENTS_CONTAINER);
        NodeIterator children = ddlStmtsNode.getNodes();

        Session session = ddlStmtsNode.getSession();
        if (! session.isLive())
            return false;

        while (children.hasNext()) {
            Node child = children.nextNode();
            session.move(child.getPath(), outputNode.getPath() + File.separator + child.getName());
        }

        session.removeItem(ddlStmtsNode.getPath());
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.teiid.modeshape.sequencer.ddl.TeiidDdlSequencer#initialize(javax.jcr.NamespaceRegistry,
     *      org.modeshape.jcr.api.nodetype.NodeTypeManager)
     */
    @Override
    public void initialize( final NamespaceRegistry registry,
                            final NodeTypeManager nodeTypeManager ) throws RepositoryException, IOException {
        registerNodeTypes( TeiidDdlSequencer.class.getResourceAsStream( "StandardDdl.cnd" ), nodeTypeManager, true ); //$NON-NLS-1$
        registerNodeTypes( TeiidDdlSequencer.class.getResourceAsStream( "TeiidDdl.cnd" ), nodeTypeManager, true ); //$NON-NLS-1$
    }

}
