/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences.mongo;

import sirius.biz.sequences.Sequences;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.RelationName;
import sirius.db.mixing.annotations.Unique;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.di.std.Framework;

/**
 * Stores the next counter value for a sequence.
 * <p>
 * This is used by {@link Sequences} to store and generate the next value for a sequence.
 */
@Framework(Sequences.FRAMEWORK_SEQUENCES)
@Index(name = "nameIndex", columns = "name", columnSettings = Mango.INDEX_ASCENDING, unique = true)
@RelationName("sequences")
public class MongoSequenceCounter extends MongoEntity {

    /**
     * Contains the name of the sequence.
     */
    public static final Mapping NAME = Mapping.named("name");
    @Unique
    @Length(100)
    private String name;

    /**
     * Contains the next value which will be generated for this sequence.
     */
    public static final Mapping NEXT_VALUE = Mapping.named("nextValue");
    private long nextValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}
