/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.biz.codelists.CodeLists;
import sirius.biz.isenguard.Isenguard;
import sirius.biz.jobs.Jobs;
import sirius.biz.password.PasswordSettings;
import sirius.biz.process.Processes;
import sirius.biz.storage.layer3.VirtualFileSystem;
import sirius.biz.tenants.Tenants;
import sirius.db.es.Elastic;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.OMA;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.annotations.Mixin;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.redis.Redis;
import sirius.kernel.di.Injector;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.pasta.noodle.ClassAliasProvider;

import java.util.function.BiConsumer;

/**
 * Provides some class aliases which are commonly used within scripts targeting the components of sirius-biz.
 */
@Register
public class BizClassAliasProvider implements ClassAliasProvider {

    @Part
    private Mixing mixing;

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public void collectAliases(BiConsumer<String, Class<?>> consumer) {
        consumer.accept("Databases", Databases.class);
        consumer.accept("OMA", OMA.class);
        consumer.accept("Mongo", Mongo.class);
        consumer.accept("Mango", Mango.class);
        consumer.accept("Elastic", Elastic.class);
        consumer.accept("Redis", Redis.class);
        consumer.accept("Mixing", Mixing.class);
        consumer.accept("Mapping", Mapping.class);

        consumer.accept("CodeLists", CodeLists.class);
        consumer.accept("VirtualFileSystem", VirtualFileSystem.class);
        consumer.accept("Jobs", Jobs.class);
        consumer.accept("Processes", Processes.class);
        consumer.accept("Tenants", Tenants.class);
        consumer.accept("Isenguard", Isenguard.class);
        consumer.accept("BizController", BizController.class);
        consumer.accept("PasswordSettings", PasswordSettings.class);
    }

    @Override
    public void collectOptionalAliases(BiConsumer<String, Class<?>> consumer) {
        autoImportEntities(consumer);
        autoImportMixins(consumer);
    }

    private void autoImportMixins(BiConsumer<String, Class<?>> consumer) {
        for (Object mixin : Injector.context().getParts(Mixin.class)) {
            consumer.accept(mixin.getClass().getSimpleName(), mixin.getClass());
        }
    }

    private void autoImportEntities(BiConsumer<String, Class<?>> consumer) {
        for (EntityDescriptor descriptor : mixing.getDescriptors()) {
            consumer.accept(descriptor.getType().getSimpleName(), descriptor.getType());
        }
    }
}
