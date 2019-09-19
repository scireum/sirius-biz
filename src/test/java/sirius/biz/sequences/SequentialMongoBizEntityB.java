/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences;

import sirius.biz.mongo.MongoBizEntity;

public class SequentialMongoBizEntityB extends MongoBizEntity {

    @Override
    protected IdGeneratorType getIdGeneratorType() {
        return IdGeneratorType.SEQUENCE_FOR_TYPE;
    }
}
