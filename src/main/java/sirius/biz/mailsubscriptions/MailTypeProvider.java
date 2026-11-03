/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.mailsubscriptions;

import sirius.kernel.di.std.Priorized;

import java.util.List;

/**
 * Provides mail types a user can subscribe to via {@link MailSubscriptions}.
 * <p>
 * A provider also sends the mails of its types, but only to users who subscribed to them (see
 * {@link MailSubscriptions#isSubscribed(sirius.biz.tenants.UserAccount, String)}). Implementations have to be
 * registered for this interface, e.g. via <tt>@Register(classes = {MyMails.class, MailTypeProvider.class})</tt>.
 * {@link JobCompletionMails} is an example of such a provider.
 * <p>
 * Each type needs the NLS keys <tt>MailSubscriptions.&lt;type&gt;.label</tt> and
 * <tt>MailSubscriptions.&lt;type&gt;.help</tt>.
 */
public interface MailTypeProvider extends Priorized {

    /**
     * Returns the mail types this provider sends.
     *
     * @return the IDs of the mail types in the order in which they are shown to the user
     */
    List<String> getMailTypes();
}
