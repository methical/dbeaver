/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.impl.data;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.data.DBDDataReceiver;
import org.jkiss.dbeaver.model.data.DBDInsertReplaceMethod;
import org.jkiss.dbeaver.model.data.DBDValueBinder;
import org.jkiss.dbeaver.model.data.DBDValueHandler;
import org.jkiss.dbeaver.model.exec.*;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTable;
import org.jkiss.dbeaver.model.impl.sql.BaseInsertMethod;
import org.jkiss.dbeaver.model.sql.SQLConstants;
import org.jkiss.dbeaver.model.struct.DBSAttributeBase;
import org.jkiss.dbeaver.model.struct.DBSDataManipulator;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;

import java.util.Map;

public class ExecuteInsertBatchImpl extends ExecuteBatchImpl {

    private DBCSession session;
    private final DBCExecutionSource source;
    private DBSTable table;
    private boolean useUpsert;
    private boolean allNulls;

    /**
     * Constructs new batch
     *
     * @param attributes     array of attributes used in batch
     * @param keysReceiver   keys receiver (or null)
     * @param reuseStatement true if engine should reuse single prepared statement for each execution.
     */
    public ExecuteInsertBatchImpl(@NotNull DBSAttributeBase[] attributes, @Nullable DBDDataReceiver keysReceiver, boolean reuseStatement, @NotNull DBCSession session, @NotNull final DBCExecutionSource source, @NotNull DBSTable table, boolean useUpsert) {
        super(attributes, keysReceiver, reuseStatement);
        this.session = session;
        this.source = source;
        this.table = table;
        this.useUpsert = useUpsert;
    }

    protected int getNextUsedParamIndex(Object[] attributeValues, int paramIndex) {
        paramIndex++;
        DBSAttributeBase attribute = attributes[paramIndex];
        while (DBUtils.isPseudoAttribute(attribute) || (!allNulls && DBUtils.isNullValue(attributeValues[paramIndex]))) {
            paramIndex++;
        }
        return paramIndex;
    }

    @NotNull
    @Override
    protected DBCStatement prepareStatement(@NotNull DBCSession session, DBDValueHandler[] handlers, Object[] attributeValues, Map<String, Object> options) throws DBCException {
        StringBuilder queryForStatement = prepareQueryForStatement(session, handlers, attributeValues, attributes, table,false, options);
        // Execute
        DBCStatement dbStat = session.prepareStatement(DBCStatementType.QUERY, queryForStatement.toString(), false, false, keysReceiver != null);
        dbStat.setStatementSource(source);
        return dbStat;
    }

    @Override
    protected void bindStatement(@NotNull DBDValueHandler[] handlers, @NotNull DBCStatement statement, Object[] attributeValues) throws DBCException {
        int paramIndex = 0;
        for (int k = 0; k < handlers.length; k++) {
            DBSAttributeBase attribute = attributes[k];
            if (DBUtils.isPseudoAttribute(attribute) || (!allNulls && DBUtils.isNullValue(attributeValues[k]))) {
                continue;
            }
            handlers[k].bindValueObject(statement.getSession(), statement, attribute, paramIndex++, attributeValues[k]);
            if (session.getProgressMonitor().isCanceled()) {
                break;
            }
        }
    }

    StringBuilder prepareQueryForStatement(
        @NotNull DBCSession session,
        DBDValueHandler[] handlers,
        Object[] attributeValues,
        DBSAttributeBase[] attributes,
        DBSTable table,
        boolean useMultiInsert,
        Map<String, Object> options) throws DBCException {

        // Make query
        String tableName = DBUtils.getEntityScriptName(table, options);
        StringBuilder query = new StringBuilder(200);

        DBDInsertReplaceMethod method = (DBDInsertReplaceMethod) options.get(DBSDataManipulator.OPTION_INSERT_REPLACE_METHOD);
        if (method == null) {
            method = new BaseInsertMethod();
        }

        if (useUpsert) {
            query.append(SQLConstants.KEYWORD_UPSERT).append(" INTO");
        } else {
            query.append(method.getOpeningClause(table, session.getProgressMonitor()));
        }
        query.append(" ").append(tableName).append(" ("); //$NON-NLS-1$ //$NON-NLS-2$


        allNulls = true;
        for (int i = 0; i < attributes.length; i++) {
            if (!DBUtils.isNullValue(attributeValues[i])) {
                allNulls = false;
                break;
            }
        }
        boolean hasKey = false;
        for (int i = 0; i < attributes.length; i++) {
            DBSAttributeBase attribute = attributes[i];
            if (DBUtils.isPseudoAttribute(attribute) || (!useMultiInsert && (!allNulls && DBUtils.isNullValue(attributeValues[i])))) {
                continue;
            }
            if (hasKey) query.append(","); //$NON-NLS-1$
            hasKey = true;
            String attributeName;
            if (table instanceof JDBCTable) {
                attributeName = ((JDBCTable)table).getAttributeName(attribute);
            } else {
                attributeName = DBUtils.getObjectFullName(table.getDataSource(), attribute, DBPEvaluationContext.DML);
            }
            query.append(attributeName);
        }
        query.append(")\n\tVALUES "); //$NON-NLS-1$
        StringBuilder valuesPart = new StringBuilder(64);
        valuesPart.append("(");
        hasKey = false;
        for (int i = 0; i < attributes.length; i++) {
            DBSAttributeBase attribute = attributes[i];
            if (DBUtils.isPseudoAttribute(attribute) || (!useMultiInsert && (!allNulls && DBUtils.isNullValue(attributeValues[i])))) {
                continue;
            }
            if (hasKey) valuesPart.append(","); //$NON-NLS-1$
            hasKey = true;

            DBDValueHandler valueHandler = handlers[i];
            if (valueHandler instanceof DBDValueBinder) {
                valuesPart.append(((DBDValueBinder) valueHandler).makeQueryBind(attribute, attributeValues[i]));
            } else {
                valuesPart.append("?"); //$NON-NLS-1$
            }
        }
        valuesPart.append(")"); //$NON-NLS-1$
        if (useMultiInsert) {
            for (int i = 0; i < attributeValues.length / attributes.length; i++) {
                if (i != 0) {
                    query.append(",");
                }
                query.append(valuesPart);
            }
        } else {
            query.append(valuesPart);
        }

        String trailingClause = method.getTrailingClause(table, session.getProgressMonitor(), attributes);
        if (trailingClause != null) {
            query.append(trailingClause);
        }

        return query;
    }
}
