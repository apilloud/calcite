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
package org.eigenbase.sql.validate;

import org.eigenbase.sql.*;


/**
 * The name-resolution context for expression inside a multiset call. The
 * objects visible are multiset expressions, and those inherited from the parent
 * scope.
 *
 * @author wael
 * @version $Id$
 * @see CollectNamespace
 * @since Mar 25, 2003
 */
class CollectScope
    extends ListScope
{
    //~ Instance fields --------------------------------------------------------

    private final SqlValidatorScope usingScope;
    private final SqlCall child;

    //~ Constructors -----------------------------------------------------------

    CollectScope(
        SqlValidatorScope parent,
        SqlValidatorScope usingScope,
        SqlCall child)
    {
        super(parent);
        this.usingScope = usingScope;
        this.child = child;
    }

    //~ Methods ----------------------------------------------------------------

    public SqlNode getNode()
    {
        return child;
    }
}

// End CollectScope.java
