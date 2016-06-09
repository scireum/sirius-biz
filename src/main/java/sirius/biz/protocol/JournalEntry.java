/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.Lob;

import java.time.LocalDateTime;

/**
 * Created by aha on 18.02.16.
 */
public class JournalEntry {

    private LocalDateTime tod;

    @Length(length = 255)
    private String username;

    @Length(length = 255)
    private String userId;

    @Length(length = 255)
    private String subsystem;

    @Length(length = 255)
    private String targetType;

    private long targetId;

    @Length(length = 255)
    private String targetName;

    @Lob
    private String message;
}
