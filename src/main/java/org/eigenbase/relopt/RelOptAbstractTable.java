/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
package org.eigenbase.relopt;

import java.util.*;

import org.eigenbase.rel.*;
import org.eigenbase.reltype.*;


/**
 * A <code>RelOptAbstractTable</code> is a partial implementation of {@link
 * RelOptTable}.
 *
 * @author jhyde
 * @version $Id$
 * @since May 3, 2002
 */
public abstract class RelOptAbstractTable
    implements RelOptTable
{
    //~ Instance fields --------------------------------------------------------

    protected RelOptSchema schema;
    protected RelDataType rowType;
    protected String name;

    //~ Constructors -----------------------------------------------------------

    protected RelOptAbstractTable(
        RelOptSchema schema,
        String name,
        RelDataType rowType)
    {
        this.schema = schema;
        this.name = name;
        this.rowType = rowType;
    }

    //~ Methods ----------------------------------------------------------------

    public String getName()
    {
        return name;
    }

    public String [] getQualifiedName()
    {
        return new String[] { name };
    }

    public double getRowCount()
    {
        return 100;
    }

    public RelDataType getRowType()
    {
        return rowType;
    }

    public void setRowType(RelDataType rowType)
    {
        this.rowType = rowType;
    }

    public RelOptSchema getRelOptSchema()
    {
        return schema;
    }

    public List<RelCollation> getCollationList()
    {
        return Collections.<RelCollation>emptyList();
    }
}

// End RelOptAbstractTable.java
