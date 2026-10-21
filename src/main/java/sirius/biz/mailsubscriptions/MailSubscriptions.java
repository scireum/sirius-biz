/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.mailsubscriptions;

import sirius.biz.tenants.UserAccount;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.kernel.settings.Extension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the mail types a user can subscribe to.
 * <p>
 * The available types are defined in the system configuration under <tt>mail-subscriptions.types</tt> and can be
 * extended by applications and customizations. All types are disabled by default, so a user only receives mails
 * for the types they explicitly subscribed to.
 */
@Register(classes = MailSubscriptions.class, framework = MailSubscriptions.FRAMEWORK_MAIL_SUBSCRIPTIONS)
public class MailSubscriptions {

    /**
     * Names the framework which must be enabled to activate mail subscriptions.
     */
    public static final String FRAMEWORK_MAIL_SUBSCRIPTIONS = "biz.mail-subscriptions";

    private static final String PREFERENCE_KEY = "mail-subscriptions";
    private static final String PARAMETER_PREFIX = "mailSubscriptions_";

    /**
     * Returns all mail types a user can subscribe to.
     *
     * @return the ids of all configured mail types, ordered by their priority
     */
    public List<String> getMailTypes() {
        return Sirius.getSettings()
                     .getExtensions("mail-subscriptions.types")
                     .stream()
                     .map(Extension::getId)
                     .toList();
    }

    /**
     * Returns the label of the given mail type.
     *
     * @param mailType the mail type to return the label for
     * @return the mail type's label
     */
    public String getMailTypeLabel(String mailType) {
        return NLS.get(Strings.apply("MailSubscriptions.%s.label", mailType));
    }

    /**
     * Returns the help text of the given mail type.
     *
     * @param mailType the mail type to return the help text for
     * @return the mail type's help text
     */
    public String getMailTypeHelpText(String mailType) {
        return NLS.get(Strings.apply("MailSubscriptions.%s.help", mailType));
    }

    /**
     * Computes the name of the form parameter which is used to select the given mail type.
     *
     * @param mailType the mail type to compute the parameter name for
     * @return the name of the form parameter for the given mail type
     */
    public String computeParameterName(String mailType) {
        return PARAMETER_PREFIX + mailType;
    }

    /**
     * Determines if the given user subscribed to the given mail type.
     *
     * @param userAccount the user to check
     * @param mailType    the mail type to check
     * @return <tt>true</tt> if the user subscribed to the mail type, <tt>false</tt> otherwise
     */
    public boolean isSubscribed(UserAccount<?, ?> userAccount, String mailType) {
        return getSubscribedMailTypes(userAccount).contains(mailType);
    }

    /**
     * Returns all mail types the given user subscribed to.
     *
     * @param userAccount the user to read the subscriptions for
     * @return the ids of all mail types the user subscribed to
     */
    public Set<String> getSubscribedMailTypes(UserAccount<?, ?> userAccount) {
        return Arrays.stream(userAccount.readPreference(PREFERENCE_KEY).asString().split(","))
                     .map(String::trim)
                     .filter(Strings::isFilled)
                     .collect(Collectors.toSet());
    }

    /**
     * Replaces the subscriptions of the given user with the given mail types.
     * <p>
     * All subscriptions are stored in a single preference, so that they are written at once. Storing an empty
     * collection removes the preference entirely, as all mail types are disabled by default.
     *
     * @param userAccount the user to update
     * @param mailTypes   the ids of all mail types the user subscribes to
     */
    public void updateSubscriptions(UserAccount<?, ?> userAccount, Collection<String> mailTypes) {
        String subscriptions = mailTypes.stream()
                                        .filter(Strings::isFilled)
                                        .distinct()
                                        .sorted()
                                        .collect(Collectors.joining(","));
        userAccount.updatePreference(PREFERENCE_KEY, Strings.isFilled(subscriptions) ? subscriptions : null);
    }
}
