/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tenants.jdbc;

import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.ExportMode;
import sirius.biz.jobs.batch.file.LineBasedExportJob;
import sirius.biz.jobs.batch.file.LineBasedExportJobFactory;
import sirius.biz.model.LoginData;
import sirius.biz.model.PersonData;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.Tenants;
import sirius.biz.tenants.UserAccountData;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.query.BaseQuery;
import sirius.kernel.commons.Callback;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Provides an export job for {@link SQLUserAccount user accounts} stored in a JDBC database.
 */
@Register(classes = JobFactory.class, framework = SQLTenants.FRAMEWORK_TENANTS_JDBC)
public class SQLUserAccountExportJobFactory extends LineBasedExportJobFactory {

    @Part
    private OMA oma;

    @Part
    private Tenants<?, ?, ?> tenants;

    @Override
    protected LineBasedExportJob<?> createJob(ProcessContext process,
                                              ExportMode mode,
                                              Callback<Object[]> rowConsumer,
                                              Runnable completionHandler) {
//        return new LineBasedExportJob<SQLUserAccount>(createProcessTitle(process.getContext()),
//                                                      mode,
//                                                      process,
//                                                      createProcessor(process),
//                                                      rowConsumer,
//                                                      completionHandler) {
//            @Override
//            protected Class<SQLUserAccount> getType() {
//                return SQLUserAccount.class;
//            }
//
//            @Override
//            protected void collectDefaultOutputProperties(Consumer<String> properties) {
//                properties.accept(SQLUserAccount.ID.getName());
//                properties.accept(SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.PERSON)
//                                                                  .inner(PersonData.LASTNAME)
//                                                                  .getName());
//            }
//
//            @Override
//            protected BaseQuery<?, SQLUserAccount> createExportQuery() {
//                return oma.select(SQLUserAccount.class)
//                          .eq(SQLUserAccount.TENANT, tenants.getRequiredTenant())
//                          .orderAsc(SQLUserAccount.USER_ACCOUNT_DATA.inner(UserAccountData.LOGIN)
//                                                                    .inner(LoginData.USERNAME));
//            }
//        };
        return null;
    }

    @Nonnull
    @Override
    public String getName() {
        return "export-sql-user-accounts";
    }
}
