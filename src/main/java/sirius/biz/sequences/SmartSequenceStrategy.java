/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences;

import sirius.biz.sequences.jdbc.SQLSequenceStrategy;
import sirius.biz.sequences.mongo.MongoSequenceStrategy;
import sirius.db.jdbc.schema.Schema;
import sirius.db.mixing.Mixing;
import sirius.db.mongo.Mongo;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

@Register
public class SmartSequenceStrategy implements SequenceStrategy {

    private SequenceStrategy delegate;

    @Part
    private Schema schema;

    @Part
    private Mongo mongo;

    @Part
    private GlobalContext ctx;

    @Nonnull
    @Override
    public String getName() {
        return "smart";
    }

    private SequenceStrategy getDelegate() {
        if (delegate == null) {
            if (schema.isConfigured(Mixing.DEFAULT_REALM)) {
                Sequences.LOG.INFO("Using SQL based sequences...");
                delegate = ctx.getPart(SQLSequenceStrategy.TYPE, SequenceStrategy.class);
            } else {
                Sequences.LOG.INFO("Using MongoDB for sequences...");
                delegate = ctx.getPart(MongoSequenceStrategy.TYPE, SequenceStrategy.class);
                if (!mongo.isConfigured()) {
                    Sequences.LOG.SEVERE(
                            "Sequences cannot determine a valid strategy! Neigher JDBC nor MongoDB are available!");
                }
            }
        }

        return delegate;
    }

    @Override
    public Long tryGenerateId(String sequence) throws Exception {
        return getDelegate().tryGenerateId(sequence);
    }

    @Override
    public long peekNextValue(String sequence) throws Exception {
        return delegate.peekNextValue(sequence);
    }

    @Override
    public void setNextValue(String sequence, long nextValue, boolean force) throws Exception {
        delegate.setNextValue(sequence, nextValue, force);
    }

    @Override
    public void collectKnownSequences(Consumer<String> nameConsumer) throws Exception {
        delegate.collectKnownSequences(nameConsumer);
    }
}
