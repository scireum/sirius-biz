/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import com.google.common.collect.Lists;
import parsii.tokenizer.LookaheadReader;
import sirius.db.mixing.Column;
import sirius.db.mixing.Constraint;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.Property;
import sirius.db.mixing.constraints.And;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.db.mixing.constraints.Like;
import sirius.db.mixing.constraints.Or;
import sirius.db.mixing.properties.EntityRefProperty;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.io.StringReader;
import java.util.List;

/**
 * Parses an SQL like query and compiles it into a {@link Constraint}.
 * <p>
 * It also provides support for {@link QueryTag}s embedded in the given query.
 */
class QueryCompiler {

    private EntityDescriptor descriptor;
    private final Column[] searchFields;
    private final LookaheadReader reader;

    @Part
    private static GlobalContext ctx;

    QueryCompiler(EntityDescriptor descriptor, String query, Column... searchFields) {
        this.descriptor = descriptor;
        this.searchFields = searchFields;
        this.reader = new LookaheadReader(new StringReader(query));
    }

    private void skipWhitespace(LookaheadReader reader) {
        while (reader.current().isWhitepace()) {
            reader.consume();
        }
    }

    public Constraint compile() {
        return parseOR();
    }

    private boolean isAtOR(LookaheadReader reader) {
        return (reader.current().is('o', 'O') && reader.next().is('r', 'R')) || (reader.current().is('|')
                                                                                 && reader.next().is('|'));
    }

    private boolean isAtAND(LookaheadReader reader) {
        return reader.current().is('a', 'A') && reader.next().is('n', 'N') && reader.next(2).is('d', 'D');
    }

    private Constraint parseOR() {
        List<Constraint> constraints = Lists.newArrayList();
        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            Constraint inner = parseAND();
            if (inner != null) {
                constraints.add(inner);
            }
            if (!isAtOR(reader)) {
                break;
            } else {
                reader.consume(2);
            }
        }

        if (constraints.isEmpty()) {
            return null;
        } else {
            return Or.of(constraints);
        }
    }

    private Constraint parseAND() {
        List<Constraint> constraints = Lists.newArrayList();
        while (!reader.current().isEndOfInput() && !reader.current().is(')')) {
            Constraint inner = parseExpression();
            if (inner != null) {
                constraints.add(inner);
            }
            skipWhitespace(reader);
            if (isAtOR(reader)) {
                break;
            }
            if (isAtAND(reader)) {
                reader.consume(3);
            }
        }

        if (constraints.isEmpty()) {
            return null;
        } else {
            return And.of(constraints);
        }
    }

    private Constraint parseExpression() {
        skipWhitespace(reader);
        if (reader.current().is('(')) {
            return parseBrackets();
        }

        if (reader.current().is(':') && reader.next().is(':')) {
            return parseTag();
        }

        String token = readToken();
        skipWhitespace(reader);
        if (isAtOperator()) {
            return parseOperation(token);
        }

        List<Constraint> fieldConstraints = Lists.newArrayList();
        for (Column field : searchFields) {
            fieldConstraints.add(Like.on(field).contains(token).ignoreCase().ignoreEmpty());
        }

        return Or.of(fieldConstraints);
    }

    private Constraint parseOperation(String field) {
        Operation operation = readOp();
        Object value = compileValue(field, parseValue());
        FieldOperator op = FieldOperator.on(Column.named(field));
        switch (operation) {
            case GT:
                return op.greaterThan(value);
            case GTEQ:
                return op.greaterOrEqual(value);
            case LTEQ:
                return op.lessOrEqual(value);
            case LT:
                return op.lessThan(value);
            case NE:
                return op.notEqual(value);
            default:
                return op.eq(value);
        }
    }

    private String parseValue() {
        skipWhitespace(reader);
        StringBuilder result = new StringBuilder();
        if (reader.current().is('"')) {
            reader.consume();
            while (!reader.current().isEndOfInput() && !reader.current().is('"')) {
                result.append(reader.consume());
            }
            reader.consume();
        } else {
            while (!reader.current().isEndOfInput()
                   && !reader.current().is(')')
                   && !reader.current().isWhitepace()
                   && !isAtOperator()) {
                result.append(reader.consume());
            }
        }

        return result.toString();
    }

    private Object compileValue(String field, String value) {
        Property property = resolveProperty(field);
        if (property == null) {
            return value;
        }

        return property.transformValue(Value.of(value));
    }

    @Nullable
    private Property resolveProperty(String property) {
        EntityDescriptor effectiveDescriptor = descriptor;
        String[] path = property.split("\\.");
        for (int i = 0; i < path.length - 2; i++) {
            Property reference = effectiveDescriptor.findProperty(path[i]);
            if (reference instanceof EntityRefProperty) {
                effectiveDescriptor = ((EntityRefProperty) reference).getReferencedDescriptor();
            } else {
                return null;
            }
        }

        return effectiveDescriptor.findProperty(path[path.length - 1]);
    }

    private enum Operation {
        EQ, NE, LT, LTEQ, GT, GTEQ
    }

    private Operation readOp() {
        if (isNotEqual()) {
            reader.consume(2);
            return Operation.NE;
        }
        if (reader.current().is('<') && reader.next().is('=')) {
            reader.consume(2);
            return Operation.LTEQ;
        }
        if (reader.current().is('>') && reader.next().is('=')) {
            reader.consume(2);
            return Operation.GTEQ;
        }
        if (reader.current().is('=')) {
            reader.consume();
            return Operation.EQ;
        }
        if (reader.current().is('>')) {
            reader.consume();
            return Operation.GT;
        }
        if (reader.current().is('<')) {
            reader.consume();
            return Operation.LT;
        } else {
            throw new IllegalStateException(reader.current().toString());
        }
    }

    private boolean isNotEqual() {
        if (reader.current().is('!') && reader.next().is('=')) {
            return true;
        }

        return reader.current().is('<') && reader.next().is('>');
    }

    private String readToken() {
        StringBuilder token = new StringBuilder();
        while (!reader.current().isEndOfInput()
               && !reader.current().is(')')
               && !reader.current().isWhitepace()
               && !isAtOperator()) {
            token.append(reader.consume());
        }
        return token.toString();
    }

    private Constraint parseTag() {
        StringBuilder tag = new StringBuilder();
        tag.append(reader.consume());
        tag.append(reader.consume());
        while (!reader.current().isEndOfInput() && !(reader.current().is(':') && reader.next().is(':'))) {
            tag.append(reader.consume());
        }
        tag.append(reader.consume());
        tag.append(reader.consume());

        QueryTag queryTag = QueryTag.parse(tag.toString());
        if (queryTag.getType() != null && Strings.isFilled(queryTag.getValue())) {
            QueryTagHandler handler = ctx.getPart(queryTag.getType(), QueryTagHandler.class);
            if (handler != null) {
                return handler.generateConstraint(descriptor, queryTag.getValue());
            }
        }

        return null;
    }

    private Constraint parseBrackets() {
        reader.consume();
        Constraint inner = parseOR();
        if (reader.current().is(')')) {
            reader.consume();
        }

        return inner;
    }

    private boolean isAtOperator() {
        if (reader.current().is('=')) {
            return true;
        }
        if (reader.current().is('!') && reader.next().is('=')) {
            return true;
        }

        return reader.current().is('<') || reader.current().is('>');
    }
}
