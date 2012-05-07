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
package org.eigenbase.sql.pretty;

import java.io.*;

import java.lang.reflect.*;

import java.util.*;
import java.util.logging.*;

import org.eigenbase.sql.*;
import org.eigenbase.sql.util.*;
import org.eigenbase.trace.*;
import org.eigenbase.util.*;


/**
 * Pretty printer for SQL statements.
 *
 * <p>There are several options to control the format.
 *
 * <table>
 * <tr>
 * <th>Option</th>
 * <th>Description</th>
 * <th>Default</th>
 * </tr>
 * <tr>
 * <td>{@link #setSelectListItemsOnSeparateLines SelectListItemsOnSeparateLines}
 * </td>
 * <td>Whether each item in the select clause is on its own line</td>
 * <td>false</td>
 * </tr>
 * <tr>
 * <td>{@link #setCaseClausesOnNewLines CaseClausesOnNewLines}</td>
 * <td>Whether the WHEN, THEN and ELSE clauses of a CASE expression appear at
 * the start of a new line.</td>
 * <td>false</td>
 * </tr>
 * <tr>
 * <td>{@link #setIndentation Indentation}</td>
 * <td>Number of spaces to indent</td>
 * <td>4</td>
 * </tr>
 * <tr>
 * <td>{@link #setKeywordsLowerCase KeywordsLowerCase}</td>
 * <td>Whether to print keywords (SELECT, AS, etc.) in lower-case.</td>
 * <td>false</td>
 * </tr>
 * <tr>
 * <td>{@link #isAlwaysUseParentheses ParenthesizeAllExprs}</td>
 * <td>Whether to enclose all expressions in parentheses, even if the operator
 * has high enough precedence that the parentheses are not required.
 *
 * <p>For example, the parentheses are required in the expression <code>(a + b)
 * c</code> because the '*' operator has higher precedence than the '+'
 * operator, and so without the parentheses, the expression would be equivalent
 * to <code>a + (b * c)</code>. The fully-parenthesized expression, <code>((a +
 * b) * c)</code> is unambiguous even if you don't know the precedence of every
 * operator.</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>{@link #setQuoteAllIdentifiers QuoteAllIdentifiers}</td>
 * <td>Whether to quote all identifiers, even those which would be correct
 * according to the rules of the {@link SqlDialect} if quotation marks were
 * omitted.</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>{@link #setSelectListItemsOnSeparateLines SelectListItemsOnSeparateLines}
 * </td>
 * <td>Whether each item in the select clause is on its own line.</td>
 * <td>false</td>
 * </tr>
 * <tr>
 * <td>{@link #setSubqueryStyle SubqueryStyle}</td>
 * <td>Style for formatting sub-queries. Values are: {@link
 * org.eigenbase.sql.SqlWriter.SubqueryStyle#Hyde Hyde}, {@link
 * org.eigenbase.sql.SqlWriter.SubqueryStyle#Black Black}.</td>
 * <td>{@link org.eigenbase.sql.SqlWriter.SubqueryStyle#Hyde Hyde}</td>
 * </tr>
 * <tr>
 * <td>{@link #setLineLength LineLength}</td>
 * <td>Set the desired maximum length for lines (to look nice in editors,
 * printouts, etc.).</td>
 * <td>0</td>
 * </tr>
 * </table>
 *
 * @author Julian Hyde
 * @version $Id$
 * @since 2005/8/24
 */
public class SqlPrettyWriter
    implements SqlWriter
{
    //~ Static fields/initializers ---------------------------------------------

    protected static final EigenbaseLogger logger =
        new EigenbaseLogger(
            Logger.getLogger("org.eigenbase.sql.pretty.SqlPrettyWriter"));

    /**
     * Bean holding the default property values.
     */
    private static final Bean defaultBean =
        new SqlPrettyWriter(SqlDialect.DUMMY).getBean();
    protected static final String NL = System.getProperty("line.separator");

    private static final String [] spaces =
    {
        "",
        " ",
        "  ",
        "   ",
        "    ",
        "     ",
        "      ",
        "       ",
        "        ",
    };

    //~ Instance fields --------------------------------------------------------

    private final SqlDialect dialect;
    private final StringWriter sw = new StringWriter();
    protected final PrintWriter pw;
    private final Stack<FrameImpl> listStack = new Stack<FrameImpl>();
    protected FrameImpl frame;
    private boolean needWhitespace;
    protected String nextWhitespace;
    protected boolean alwaysUseParentheses;
    private boolean keywordsLowerCase;
    private Bean bean;
    private boolean quoteAllIdentifiers;
    private int indentation;
    private boolean clauseStartsLine;
    private boolean selectListItemsOnSeparateLines;
    private boolean selectListExtraIndentFlag;
    private int currentIndent;
    private boolean windowDeclListNewline;
    private boolean updateSetListNewline;
    private boolean windowNewline;
    private SubqueryStyle subqueryStyle;
    private boolean whereListItemsOnSeparateLines;

    private boolean caseClausesOnNewLines;
    private int lineLength;
    private int charCount;

    //~ Constructors -----------------------------------------------------------

    public SqlPrettyWriter(
        SqlDialect dialect,
        boolean alwaysUseParentheses,
        PrintWriter pw)
    {
        if (pw == null) {
            pw = new PrintWriter(sw);
        }
        this.pw = pw;
        this.dialect = dialect;
        this.alwaysUseParentheses = alwaysUseParentheses;
        resetSettings();
        reset();
    }

    public SqlPrettyWriter(
        SqlDialect dialect,
        boolean alwaysUseParentheses)
    {
        this(dialect, alwaysUseParentheses, null);
    }

    public SqlPrettyWriter(SqlDialect dialect)
    {
        this(dialect, true);
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Sets whether the WHEN, THEN and ELSE clauses of a CASE expression appear
     * at the start of a new line. The default is false.
     */
    public void setCaseClausesOnNewLines(boolean caseClausesOnNewLines)
    {
        this.caseClausesOnNewLines = caseClausesOnNewLines;
    }

    /**
     * Sets the subquery style. Default is {@link
     * org.eigenbase.sql.SqlWriter.SubqueryStyle#Hyde}.
     */
    public void setSubqueryStyle(SubqueryStyle subqueryStyle)
    {
        this.subqueryStyle = subqueryStyle;
    }

    public void setWindowNewline(boolean windowNewline)
    {
        this.windowNewline = windowNewline;
    }

    public void setWindowDeclListNewline(boolean windowDeclListNewline)
    {
        this.windowDeclListNewline = windowDeclListNewline;
    }

    public int getIndentation()
    {
        return indentation;
    }

    public boolean isAlwaysUseParentheses()
    {
        return alwaysUseParentheses;
    }

    public boolean inQuery()
    {
        return (frame == null)
            || (frame.frameType == FrameTypeEnum.OrderBy)
            || (frame.frameType == FrameTypeEnum.Setop);
    }

    public boolean isQuoteAllIdentifiers()
    {
        return quoteAllIdentifiers;
    }

    public boolean isClauseStartsLine()
    {
        return clauseStartsLine;
    }

    public boolean isSelectListItemsOnSeparateLines()
    {
        return selectListItemsOnSeparateLines;
    }

    public boolean isWhereListItemsOnSeparateLines()
    {
        return whereListItemsOnSeparateLines;
    }

    public boolean isSelectListExtraIndentFlag()
    {
        return selectListExtraIndentFlag;
    }

    public boolean isKeywordsLowerCase()
    {
        return keywordsLowerCase;
    }

    public int getLineLength()
    {
        return lineLength;
    }

    public void resetSettings()
    {
        reset();
        indentation = 4;
        clauseStartsLine = true;
        selectListItemsOnSeparateLines = false;
        selectListExtraIndentFlag = true;
        keywordsLowerCase = false;
        quoteAllIdentifiers = true;
        windowDeclListNewline = true;
        updateSetListNewline = true;
        windowNewline = false;
        subqueryStyle = SubqueryStyle.Hyde;
        alwaysUseParentheses = false;
        whereListItemsOnSeparateLines = false;
        lineLength = 0;
        charCount = 0;
    }

    public void reset()
    {
        pw.flush();
        sw.getBuffer().setLength(0);
        setNeedWhitespace(false);
        nextWhitespace = " ";
    }

    /**
     * Returns an object which encapsulates each property as a get/set method.
     */
    private Bean getBean()
    {
        if (bean == null) {
            bean = new Bean(this);
        }
        return bean;
    }

    /**
     * Sets the number of spaces indentation.
     *
     * @see #getIndentation()
     */
    public void setIndentation(int indentation)
    {
        this.indentation = indentation;
    }

    /**
     * Prints the property settings of this pretty-writer to a writer.
     *
     * @param pw Writer
     * @param omitDefaults Whether to omit properties whose value is the same as
     * the default
     */
    public void describe(PrintWriter pw, boolean omitDefaults)
    {
        final Bean properties = getBean();
        final String [] propertyNames = properties.getPropertyNames();
        int count = 0;
        for (int i = 0; i < propertyNames.length; i++) {
            String key = propertyNames[i];
            final Object value = bean.get(key);
            final Object defaultValue = defaultBean.get(key);
            if (Util.equal(value, defaultValue)) {
                continue;
            }
            if (count++ > 0) {
                pw.print(",");
            }
            pw.print(key + "=" + value);
        }
    }

    /**
     * Sets settings from a properties object.
     */
    public void setSettings(Properties properties)
    {
        resetSettings();
        final Bean bean = getBean();
        final String [] propertyNames = bean.getPropertyNames();
        for (int i = 0; i < propertyNames.length; i++) {
            String propertyName = propertyNames[i];
            final String value = properties.getProperty(propertyName);
            if (value != null) {
                bean.set(propertyName, value);
            }
        }
    }

    /**
     * Sets whether a clause (FROM, WHERE, GROUP BY, HAVING, WINDOW, ORDER BY)
     * starts a new line. Default is true. SELECT is always at the start of a
     * line.
     */
    public void setClauseStartsLine(boolean clauseStartsLine)
    {
        this.clauseStartsLine = clauseStartsLine;
    }

    /**
     * Sets whether each item in a SELECT list, GROUP BY list, or ORDER BY list
     * is on its own line. Default false.
     */
    public void setSelectListItemsOnSeparateLines(boolean b)
    {
        this.selectListItemsOnSeparateLines = b;
    }

    /**
     * Sets whether to use a fix for SELECT list indentations.
     *
     * <ul>
     * <li>If set to "false":
     *
     * <pre>
     * SELECT
     *     A as A
     *         B as B
     *         C as C
     *     D
     * </pre>
     * <li>If set to "true":
     *
     * <pre>
     * SELECT
     *     A as A
     *     B as B
     *     C as C
     *     D
     * </pre>
     * </ul>
     */
    public void setSelectListExtraIndentFlag(boolean b)
    {
        this.selectListExtraIndentFlag = b;
    }

    /**
     * Sets whether to print keywords (SELECT, AS, etc.) in lower-case. The
     * default is false: keywords are printed in upper-case.
     */
    public void setKeywordsLowerCase(boolean b)
    {
        this.keywordsLowerCase = b;
    }

    /**
     * Sets whether to print a newline before each AND or OR (whichever is
     * higher level) in WHERE clauses. NOTE: <i>Ignored when
     * alwaysUseParentheses is set to true.</i>
     */

    public void setWhereListItemsOnSeparateLines(boolean b)
    {
        this.whereListItemsOnSeparateLines = b;
    }

    public void setAlwaysUseParentheses(boolean b)
    {
        this.alwaysUseParentheses = b;
    }

    public void newlineAndIndent()
    {
        pw.println();
        charCount = 0;
        indent(currentIndent);
        setNeedWhitespace(false); // no further whitespace necessary
    }

    void indent(int indent)
    {
        if (indent < 0) {
            throw new IllegalArgumentException("negative indent " + indent);
        } else if (indent <= 8) {
            pw.print(spaces[indent]);
        } else {
            // Print space in chunks of 8 to amortize cost of calls to print.
            final int rem = indent % 8;
            final int div = indent / 8;
            for (int i = 0; i < div; ++i) {
                pw.print(spaces[8]);
            }
            if (rem > 0) {
                pw.print(spaces[rem]);
            }
        }
        charCount += indent;
    }

    /**
     * Sets whether to quote all identifiers, even those which would be correct
     * according to the rules of the {@link SqlDialect} if quotation marks were
     * omitted.
     *
     * <p>Default true.
     */
    public void setQuoteAllIdentifiers(boolean b)
    {
        this.quoteAllIdentifiers = b;
    }

    /**
     * Creates a list frame.
     *
     * <p>Derived classes should override this method to specify the indentation
     * of the list.
     *
     * @param frameType What type of list
     * @param keyword The keyword to be printed at the start of the list
     * @param open The string to print at the start of the list
     * @param close The string to print at the end of the list
     *
     * @return A frame
     */
    protected FrameImpl createListFrame(
        FrameType frameType,
        String keyword,
        String open,
        String close)
    {
        int indentation = getIndentation();
        if (frameType instanceof FrameTypeEnum) {
            FrameTypeEnum frameTypeEnum = (FrameTypeEnum) frameType;

            switch (frameTypeEnum) {
            case WindowDeclList:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    false,
                    indentation,
                    windowDeclListNewline,
                    false,
                    false);

            case UpdateSetList:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    updateSetListNewline,
                    indentation,
                    false,
                    false,
                    false);

            case SelectList:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    selectListExtraIndentFlag ? indentation : 0,
                    selectListItemsOnSeparateLines,
                    false,
                    indentation,
                    selectListItemsOnSeparateLines,
                    false,
                    false);

            case OrderByList:
            case GroupByList:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    selectListItemsOnSeparateLines,
                    false,
                    indentation,
                    selectListItemsOnSeparateLines,
                    false,
                    false);

            case Subquery:
                switch (subqueryStyle) {
                case Black:

                    // Generate, e.g.:
                    //
                    // WHERE foo = bar IN
                    // (   SELECT ...
                    open = "(" + spaces(indentation - 1);
                    return new FrameImpl(
                        frameType,
                        keyword,
                        open,
                        close,
                        0,
                        false,
                        true,
                        indentation,
                        false,
                        false,
                        false)
                    {
                        protected void _before()
                        {
                            newlineAndIndent();
                        }
                    };
                case Hyde:

                    // Generate, e.g.:
                    //
                    // WHERE foo IN (
                    //     SELECT ...
                    return new FrameImpl(
                        frameType,
                        keyword,
                        open,
                        close,
                        0,
                        false,
                        true,
                        0,
                        false,
                        false,
                        false)
                    {
                        protected void _before()
                        {
                            nextWhitespace = NL;
                        }
                    };
                default:
                    throw Util.unexpected(subqueryStyle);
                }

            case OrderBy:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    0,
                    false,
                    true,
                    0,
                    false,
                    false,
                    false);

            case Select:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    isClauseStartsLine(), // newline before FROM, WHERE etc.
                    0, // all clauses appear below SELECT
                    false,
                    false,
                    false);

            case Setop:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    isClauseStartsLine(), // newline before UNION, EXCEPT
                    0, // all clauses appear below SELECT
                    isClauseStartsLine(), // newline after UNION, EXCEPT
                    false,
                    false);

            case Window:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    windowNewline,
                    0,
                    false,
                    false,
                    false);

            case FunCall:
                setNeedWhitespace(false);
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    false,
                    indentation,
                    false,
                    false,
                    false);

            case Identifier:
            case Simple:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    false,
                    indentation,
                    false,
                    false,
                    false);

            case WhereList:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    whereListItemsOnSeparateLines,
                    0,
                    false,
                    false,
                    false);

            case FromList:
                return new FrameImpl(
                    frameType,
                    keyword,
                    open,
                    close,
                    indentation,
                    false,
                    isClauseStartsLine(), // newline before UNION, EXCEPT
                    0, // all clauses appear below SELECT
                    isClauseStartsLine(), // newline after UNION, EXCEPT
                    false,
                    false)
                {
                    protected void sep(boolean printFirst, String sep)
                    {
                        boolean newlineBefore =
                            newlineBeforeSep
                            && !sep.equals(",");
                        boolean newlineAfter =
                            (newlineAfterSep && sep.equals(","));
                        if ((itemCount > 0) || printFirst) {
                            if (newlineBefore && (itemCount > 0)) {
                                pw.println();
                                charCount = 0;
                                indent(currentIndent + sepIndent);
                                setNeedWhitespace(false);
                            }
                            keyword(sep);
                            nextWhitespace = (newlineAfter) ? NL : " ";
                        }
                        ++itemCount;
                    }
                };
            default:
                // fall through
            }
        }
        boolean newlineAfterOpen = false;
        boolean newlineBeforeSep = false;
        boolean newlineBeforeClose = false;
        int sepIndent = indentation;
        if (frameType.getName().equals("CASE")) {
            if (caseClausesOnNewLines) {
                newlineAfterOpen = true;
                newlineBeforeSep = true;
                newlineBeforeClose = true;
                sepIndent = 0;
            }
        }
        return new FrameImpl(
            frameType,
            keyword,
            open,
            close,
            indentation,
            newlineAfterOpen,
            newlineBeforeSep,
            sepIndent,
            false,
            newlineBeforeClose,
            false);
    }

    /**
     * Returns a string of N spaces.
     */
    private static String spaces(int i)
    {
        if (i <= 8) {
            return spaces[i];
        } else {
            char [] chars = new char[i];
            Arrays.fill(chars, ' ');
            return new String(chars);
        }
    }

    /**
     * Starts a list.
     *
     * @param frameType Type of list. For example, a SELECT list will be
     * governed according to SELECT-list formatting preferences.
     * @param open String to print at the start of the list; typically "(" or
     * the empty string.
     * @param close String to print at the end of the list.
     */
    protected Frame startList(
        FrameType frameType,
        String keyword,
        String open,
        String close)
    {
        assert frameType != null;
        if (frame != null) {
            ++frame.itemCount;

            // REVIEW jvs 9-June-2006:  This is part of the fix for FRG-149
            // (extra frame for identifier was leading to extra indentation,
            // causing select list to come out raggedy with identifiers
            // deeper than literals); are there other frame types
            // for which extra indent should be suppressed?
            if (frameType.needsIndent()) {
                currentIndent += frame.extraIndent;
            }
            assert !listStack.contains(frame);
            listStack.push(frame);
        }
        frame = createListFrame(frameType, keyword, open, close);
        frame.before();
        return frame;
    }

    public void endList(Frame frame)
    {
        FrameImpl endedFrame = (FrameImpl) frame;
        Util.pre(
            frame == this.frame,
            "Frame " + endedFrame.frameType
            + " does not match current frame " + this.frame.frameType);
        if (this.frame == null) {
            throw new RuntimeException("No list started");
        }
        if (this.frame.open.equals("(")) {
            if (!this.frame.close.equals(")")) {
                throw new RuntimeException("Expected ')'");
            }
        }
        if (this.frame.newlineBeforeClose) {
            newlineAndIndent();
        }
        keyword(this.frame.close);
        if (this.frame.newlineAfterClose) {
            newlineAndIndent();
        }

        // Pop the frame, and move to the previous indentation level.
        if (listStack.isEmpty()) {
            this.frame = null;
            assert currentIndent == 0 : currentIndent;
        } else {
            this.frame = listStack.pop();
            if (endedFrame.frameType.needsIndent()) {
                currentIndent -= this.frame.extraIndent;
            }
        }
    }

    public String format(SqlNode node)
    {
        assert frame == null;
        node.unparse(this, 0, 0);
        assert frame == null;
        return toString();
    }

    public String toString()
    {
        pw.flush();
        return sw.toString();
    }

    public SqlString toSqlString()
    {
        return new SqlBuilder(dialect, toString()).toSqlString();
    }

    public SqlDialect getDialect()
    {
        return dialect;
    }

    public void literal(String s)
    {
        print(s);
        setNeedWhitespace(true);
    }

    public void keyword(String s)
    {
        maybeWhitespace(s);
        pw.print(
            isKeywordsLowerCase() ? s.toLowerCase() : s.toUpperCase());
        charCount += s.length();
        if (!s.equals("")) {
            setNeedWhitespace(needWhitespaceAfter(s));
        }
    }

    private void maybeWhitespace(String s)
    {
        if (tooLong(s) || (needWhitespace && needWhitespaceBefore(s))) {
            whiteSpace();
        }
    }

    private static boolean needWhitespaceBefore(String s)
    {
        return !(s.equals(",")
            || s.equals(".")
            || s.equals(")")
            || s.equals("]")
            || s.equals(""));
    }

    private static boolean needWhitespaceAfter(String s)
    {
        return !(s.equals("(")
            || s.equals("[")
            || s.equals("."));
    }

    protected void whiteSpace()
    {
        if (needWhitespace) {
            if (nextWhitespace == NL) {
                newlineAndIndent();
            } else {
                pw.print(nextWhitespace);
                charCount += nextWhitespace.length();
            }
            nextWhitespace = " ";
            setNeedWhitespace(false);
        }
    }

    protected boolean tooLong(String s)
    {
        boolean result =
            ((lineLength > 0)
                && (charCount > currentIndent)
                && ((charCount + s.length()) >= lineLength));
        if (result) {
            nextWhitespace = NL;
        }
        logger.finest("Token is '" + s + "'; result is " + result);
        return result;
    }

    public void print(String s)
    {
        if (s.equals("(")) {
            throw new RuntimeException("Use 'startList'");
        }
        if (s.equals(")")) {
            throw new RuntimeException("Use 'endList'");
        }
        maybeWhitespace(s);
        pw.print(s);
        charCount += s.length();
    }

    public void print(int x)
    {
        maybeWhitespace("0");
        pw.print(x);
        charCount += String.valueOf(x).length();
    }

    public void identifier(String name)
    {
        String qName = name;
        if (isQuoteAllIdentifiers()
            || dialect.identifierNeedsToBeQuoted(name))
        {
            qName = dialect.quoteIdentifier(name);
        }
        maybeWhitespace(qName);
        pw.print(qName);
        charCount += qName.length();
        setNeedWhitespace(true);
    }

    public Frame startFunCall(String funName)
    {
        keyword(funName);
        setNeedWhitespace(false);
        return startList(FrameTypeEnum.FunCall, "(", ")");
    }

    public void endFunCall(Frame frame)
    {
        endList(this.frame);
    }

    public Frame startList(String open, String close)
    {
        return startList(FrameTypeEnum.Simple, null, open, close);
    }

    public Frame startList(FrameTypeEnum frameType)
    {
        assert frameType != null;
        return startList(frameType, null, "", "");
    }

    public Frame startList(FrameType frameType, String open, String close)
    {
        assert frameType != null;
        return startList(frameType, null, open, close);
    }

    public void sep(String sep)
    {
        sep(sep, !(sep.equals(",") || sep.equals(".")));
    }

    public void sep(String sep, boolean printFirst)
    {
        if (frame == null) {
            throw new RuntimeException("No list started");
        }
        if (sep.startsWith(" ") || sep.endsWith(" ")) {
            throw new RuntimeException("Separator must not contain whitespace");
        }
        frame.sep(printFirst, sep);
    }

    public void setNeedWhitespace(boolean needWhitespace)
    {
        this.needWhitespace = needWhitespace;
    }

    public void setLineLength(int lineLength)
    {
        this.lineLength = lineLength;
    }

    public void setFormatOptions(SqlFormatOptions options)
    {
        if (options == null) {
            return;
        }
        setAlwaysUseParentheses(options.isAlwaysUseParentheses());
        setCaseClausesOnNewLines(options.isCaseClausesOnNewLines());
        setClauseStartsLine(options.isClauseStartsLine());
        setKeywordsLowerCase(options.isKeywordsLowercase());
        setQuoteAllIdentifiers(options.isQuoteAllIdentifiers());
        setSelectListItemsOnSeparateLines(
            options.isSelectListItemsOnSeparateLines());
        setWhereListItemsOnSeparateLines(
            options.isWhereListItemsOnSeparateLines());
        setWindowNewline(options.isWindowDeclarationStartsLine());
        setWindowDeclListNewline(options.isWindowListItemsOnSeparateLines());
        setIndentation(options.getIndentation());
        setLineLength(options.getLineLength());
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * Implementation of {@link org.eigenbase.sql.SqlWriter.Frame}.
     */
    protected class FrameImpl
        implements Frame
    {
        final FrameType frameType;
        final String keyword;
        final String open;
        final String close;

        /**
         * Indent of sub-frame with respect to this one.
         */
        final int extraIndent;

        /**
         * Indent of separators with respect to this frame's indent. Typically
         * zero.
         */
        final int sepIndent;

        /**
         * Number of items which have been printed in this list so far.
         */
        int itemCount;

        /**
         * Whether to print a newline before each separator.
         */
        public boolean newlineBeforeSep;

        /**
         * Whether to print a newline after each separator.
         */
        public boolean newlineAfterSep;
        private final boolean newlineBeforeClose;
        private final boolean newlineAfterClose;
        private boolean newlineAfterOpen;

        FrameImpl(
            FrameType frameType,
            String keyword,
            String open,
            String close,
            int extraIndent,
            boolean newlineAfterOpen,
            boolean newlineBeforeSep,
            int sepIndent,
            boolean newlineAfterSep,
            boolean newlineBeforeClose,
            boolean newlineAfterClose)
        {
            this.frameType = frameType;
            this.keyword = keyword;
            this.open = open;
            this.close = close;
            this.extraIndent = extraIndent;
            this.newlineAfterOpen = newlineAfterOpen;
            this.newlineBeforeSep = newlineBeforeSep;
            this.newlineAfterSep = newlineAfterSep;
            this.newlineBeforeClose = newlineBeforeClose;
            this.newlineAfterClose = newlineAfterClose;
            this.sepIndent = sepIndent;
        }

        protected void before()
        {
            if ((open != null) && !open.equals("")) {
                keyword(open);
            }
        }

        protected void after()
        {
        }

        protected void sep(boolean printFirst, String sep)
        {
            if ((newlineBeforeSep && (itemCount > 0))
                || (newlineAfterOpen && (itemCount == 0)))
            {
                newlineAndIndent();
            }
            if ((itemCount > 0) || printFirst) {
                keyword(sep);
                nextWhitespace = (newlineAfterSep) ? NL : " ";
            }
            ++itemCount;
        }
    }

    /**
     * Helper class which exposes the get/set methods of an object as
     * properties.
     */
    private static class Bean
    {
        private final SqlPrettyWriter o;
        private final Map<String, Method> getterMethods =
            new HashMap<String, Method>();
        private final Map<String, Method> setterMethods =
            new HashMap<String, Method>();

        Bean(SqlPrettyWriter o)
        {
            this.o = o;

            // Figure out the getter/setter methods for each attribute.
            final Method [] methods = o.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (method.getName().startsWith("set")
                    && (method.getReturnType() == Void.class)
                    && (method.getParameterTypes().length == 1))
                {
                    String attributeName =
                        stripPrefix(
                            method.getName(),
                            3);
                    setterMethods.put(attributeName, method);
                }
                if (method.getName().startsWith("get")
                    && (method.getReturnType() != Void.class)
                    && (method.getParameterTypes().length == 0))
                {
                    String attributeName =
                        stripPrefix(
                            method.getName(),
                            3);
                    getterMethods.put(attributeName, method);
                }
                if (method.getName().startsWith("is")
                    && (method.getReturnType() == Boolean.class)
                    && (method.getParameterTypes().length == 0))
                {
                    String attributeName =
                        stripPrefix(
                            method.getName(),
                            2);
                    getterMethods.put(attributeName, method);
                }
            }
        }

        private String stripPrefix(String name, int offset)
        {
            return name.substring(offset, offset + 1).toLowerCase()
                + name.substring(offset + 1);
        }

        public void set(String name, String value)
        {
            final Method method = setterMethods.get(name);
            try {
                method.invoke(
                    o,
                    value);
            } catch (IllegalAccessException e) {
                throw Util.newInternal(e);
            } catch (InvocationTargetException e) {
                throw Util.newInternal(e);
            }
        }

        public Object get(String name)
        {
            final Method method = getterMethods.get(name);
            try {
                return method.invoke(o);
            } catch (IllegalAccessException e) {
                throw Util.newInternal(e);
            } catch (InvocationTargetException e) {
                throw Util.newInternal(e);
            }
        }

        public String [] getPropertyNames()
        {
            final Set<String> names = new HashSet<String>();
            names.addAll(getterMethods.keySet());
            names.addAll(setterMethods.keySet());
            return (String []) names.toArray(new String[names.size()]);
        }
    }
}

// End SqlPrettyWriter.java
