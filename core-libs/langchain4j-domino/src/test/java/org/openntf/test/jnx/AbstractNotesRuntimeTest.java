/*
 * ==========================================================================
 * Copyright (C) 2019-2022 HCL America, Inc. ( http://www.hcl.com/ )
 *                            All rights reserved.
 * ==========================================================================
 * Licensed under the  Apache License, Version 2.0  (the "License").  You may
 * not use this file except in compliance with the License.  You may obtain a
 * copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.
 *
 * Unless  required  by applicable  law or  agreed  to  in writing,  software
 * distributed under the License is distributed on an  "AS IS" BASIS, WITHOUT
 * WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the  specific language  governing permissions  and limitations
 * under the License.
 * ==========================================================================
 */
package org.openntf.test.jnx;

import com.hcl.domino.DominoClient;
import com.hcl.domino.DominoClient.Encryption;
import com.hcl.domino.DominoClientBuilder;
import com.hcl.domino.DominoProcess;
import com.hcl.domino.commons.util.StringUtil;
import com.hcl.domino.data.Database;
import com.hcl.domino.exception.FileDoesNotExistException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Copied over and simplified from HCL Domino API - Integration Testing module
 * https://github.com/HCL-TECH-SOFTWARE/domino-jnx/blob/f9efb8c04df2bfdf65f7581823eefa455536de01/test/it-domino-jnx/src/test/java/it/com/hcl/domino/test/AbstractNotesRuntimeTest.java
 */
public abstract class AbstractNotesRuntimeTest {
    @FunctionalInterface
    public interface DatabaseConsumer {
        void accept(Database database) throws Exception;
    }

    private static final ThreadLocal<DominoClient> threadClient = new ThreadLocal<>();

    private static boolean initialized = false;

    protected static <T> T call(final Callable<T> callable) {
        try {
            return callable.call();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static Database createTempDb(final DominoClient client) throws IOException {
        final Path tempDest = Files.createTempFile(AbstractNotesRuntimeTest.class.getName(), ".nsf"); //$NON-NLS-1$
        Files.delete(tempDest);
        final Database database = client.createDatabase(null, tempDest.toString(), false, true, Encryption.None);
        Assertions.assertNotNull(database);
        return database;
    }

    @BeforeAll
    public static void initRuntime() {
        if (!initialized) {
            initialized = true;

            // Set the jnx.skipNotesThread property to true to avoid creating a NotesThread.
            // Otherwise, we are going to spend precious time to find a non-error exception!
            System.setProperty("jnx.skipNotesThread", "true");

            final String notesProgramDir = System.getenv("Notes_ExecDirectory");
            final String notesIniPath = System.getenv("NotesINI");
            if (StringUtil.isNotEmpty(notesProgramDir)) {
                final String[] initArgs = new String[] {
                        notesProgramDir,
                        StringUtil.isEmpty(notesIniPath) ? "" : "=" + notesIniPath //$NON-NLS-1$
                };

                DominoProcess.get().initializeProcess(initArgs);
            } else {
                throw new IllegalStateException("Unable to locate Notes runtime");
            }

            // prevent ID password prompt
            final String idFilePath = System.getenv("Notes_IDPath");
            final String idPassword = System.getenv("Notes_IDPassword");
            if (!StringUtil.isEmpty(idPassword)) {
                DominoProcess.get().initializeThread();
                try {
                    DominoProcess.get().switchToId(StringUtil.isEmpty(idFilePath) ? null : Paths.get(idFilePath), idPassword, true);
                } finally {
                    DominoProcess.get().terminateThread();
                }
            }

            if (!"true".equalsIgnoreCase(System.getProperty("no_domino_shutdownhook"))) {
                Runtime.getRuntime()
                       .addShutdownHook(new Thread(() -> DominoProcess.get().terminateProcess()));
            }
        }
    }


    @AfterAll
    public static void termRuntime() {
        if ("true".equalsIgnoreCase(System.getProperty("no_domino_shutdownhook"))) {
            if (initialized) {
                DominoProcess.get().terminateProcess();
                initialized = false;
            }
        }
    }

    protected final Logger log = Logger.getLogger(this.getClass().getPackage().getName());

    public DominoClient getClient() {
        return threadClient.get();
    }

    @BeforeEach
    public void initClient() {
        if (this.isRestrictThreadAccess()) {
            System.setProperty("jnx.allowCrossThreadAccess", "false");
        }
        if (threadClient.get() == null) {
            DominoProcess.get().initializeThread();

            threadClient.set(DominoClientBuilder.newDominoClient().build());
        } else {
            System.out.println("ThreadClient already set!");
        }
    }

    protected boolean isRestrictThreadAccess() {
        return false;
    }


    public DominoClient reloadClient() {
        if (threadClient.get() != null) {
            threadClient.get().close();

            threadClient.set(DominoClientBuilder.newDominoClient().build());
        } else {
            threadClient.set(DominoClientBuilder.newDominoClient().build());
        }

        return this.getClient();
    }

    @AfterEach
    public void termClient() {
        if (threadClient.get() != null) {
            threadClient.get().close();
            threadClient.remove();

            DominoProcess.get().terminateThread();
        }
        System.setProperty("jnx.allowCrossThreadAccess", "");
    }


    protected void withTempDb(final DatabaseConsumer c) throws Exception {
        final DominoClient client = this.getClient();
        this.withTempDb(client, c);
    }

    protected void withTempDb(final DominoClient client, final DatabaseConsumer c) throws Exception {
        final Database database = createTempDb(client);
        final String tempDest = database.getAbsoluteFilePath();
        try {
            c.accept(database);
        } finally {
            database.close();
            try {
                client.deleteDatabase(null, tempDest);
            } catch (final Throwable t) {
                System.err.println("Unable to delete database " + tempDest + ": " + t);
            }
        }
    }

    protected void withTempDbFromTemplate(final String templateServer, final String templatePath, final DatabaseConsumer c)
            throws Exception {
        final DominoClient client = this.getClient();
        final Path tempDest = Files.createTempFile(this.getClass().getName(), ".nsf"); //$NON-NLS-1$
        Files.delete(tempDest);
        Database database;
        try {
            database = client.createDatabaseFromTemplate(templateServer, templatePath, null, tempDest.toString(), Encryption.None);
        } catch (final FileDoesNotExistException e) {
            // Try locally, in case the template is here but not remote
            if (StringUtil.isNotEmpty(templateServer)) {
                database = client.createDatabaseFromTemplate(null, templatePath, null, tempDest.toString(), Encryption.None);
            } else {
                throw e;
            }
        }
        Assertions.assertNotNull(database);
        try {
            c.accept(database);
        } finally {
            database.close();
            try {
                client.deleteDatabase(null, tempDest.toString());
            } catch (final Throwable t) {
                System.err.println("Unable to delete database " + tempDest + ": " + t);
            }
        }
    }

}
