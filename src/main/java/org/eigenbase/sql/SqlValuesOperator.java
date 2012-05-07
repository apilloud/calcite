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
package org.eigenbase.sql;

/**
 * The <code>VALUES</code> operator.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class SqlValuesOperator
    extends SqlSpecialOperator
{
    //~ Constructors -----------------------------------------------------------

    public SqlValuesOperator()
    {
        super("VALUES", SqlKind.VALUES);
    }

    //~ Methods ----------------------------------------------------------------

    public void unparse(
        SqlWriter writer,
        SqlNode [] operands,
        int leftPrec,
        int rightPrec)
    {
        final SqlWriter.Frame frame = writer.startList("VALUES", "");
        for (int i = 0; i < operands.length; i++) {
            writer.sep(",");
            SqlNode operand = operands[i];
            operand.unparse(writer, 0, 0);
        }
        writer.endList(frame);
    }
}

// End SqlValuesOperator.java
