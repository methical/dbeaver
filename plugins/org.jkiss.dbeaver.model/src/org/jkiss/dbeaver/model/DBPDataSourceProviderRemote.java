/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2022 DBeaver Corp and others
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
package org.jkiss.dbeaver.model;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public interface DBPDataSourceProviderRemote extends DBPDataSourceProvider {
    /**
     * Synchronized the local data source with the remote data source.
     *
     * @param monitor   progress monitor which is used for tracking synchronization progress
     * @param container data source container that needs synchronization
     * @throws DBException on any database error
     */
    void syncLocalDataSource(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container) throws DBException;

    /**
     * Synchronized the remote data source with the local data source.
     *
     * @param monitor   progress monitor which is used for tracking synchronization progress
     * @param container data source container that needs synchronization
     * @throws DBException on any database error
     */
    void syncRemoteDataSource(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container) throws DBException;

    /**
     * Checks whether the local data source is synchronized with the remote one.
     *
     * @param monitor   progress monitor which is used for tracking synchronization progress
     * @param container data source container that needs synchronization
     * @return {@code true} if the local data source is synchronized, {@code false} otherwise
     * @throws DBException on any database error
     */
    boolean isSynchronized(@NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container) throws DBException;
}
