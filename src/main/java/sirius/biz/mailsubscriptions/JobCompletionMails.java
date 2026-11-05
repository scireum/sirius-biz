/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.mailsubscriptions;

import sirius.biz.process.Process;
import sirius.biz.process.ProcessState;
import sirius.biz.tenants.UserAccount;
import sirius.biz.tenants.UserAccountData;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.web.mails.Mails;
import sirius.web.security.UserContext;
import sirius.web.security.UserInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sends a short mail to the user who started a job once the job has completed.
 * <p>
 * Provides the mail types <tt>job-failed</tt>, <tt>job-warnings</tt> and <tt>job-success</tt> and selects one of
 * them depending on the outcome of the job. A mail is only sent if the user subscribed to this type via
 * {@link MailSubscriptions}.
 */
@Register(classes = {JobCompletionMails.class, MailTypeProvider.class},
        framework = MailSubscriptions.FRAMEWORK_MAIL_SUBSCRIPTIONS)
public class JobCompletionMails implements MailTypeProvider {

    private static final String MAIL_TYPE_JOB_FAILED = "job-failed";
    private static final String MAIL_TYPE_JOB_WARNINGS = "job-warnings";
    private static final String MAIL_TYPE_JOB_SUCCESS = "job-success";

    @Part
    private MailSubscriptions mailSubscriptions;

    @Part
    private Mails mails;

    @Override
    public List<String> getMailTypes() {
        return List.of(MAIL_TYPE_JOB_FAILED, MAIL_TYPE_JOB_WARNINGS, MAIL_TYPE_JOB_SUCCESS);
    }

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }

    /**
     * Notifies the user who started the given job about its outcome, if they subscribed to the matching mail type.
     *
     * @param process the process of the job which has just completed
     */
    public void handleCompletedJob(Process process) {
        String mailType = determineMailType(process).orElse(null);
        if (mailType == null) {
            return;
        }

        fetchRecipient(process.getUserId()).filter(recipient -> isSubscribed(recipient, mailType))
                                           .ifPresent(recipient -> sendMail(recipient, process, mailType));
    }

    private boolean isSubscribed(UserInfo recipient, String mailType) {
        return mailSubscriptions.isSubscribed(recipient.getUserObject(UserAccount.class), mailType);
    }

    /**
     * Selects the mail type which matches the outcome of the given process.
     *
     * @param process the process to inspect
     * @return the matching mail type or an empty optional if the process did not terminate regularly, e.g. as it
     * was canceled
     */
    private Optional<String> determineMailType(Process process) {
        // Canceled jobs were stopped by the user, standby processes never complete.
        if (process.getState() != ProcessState.TERMINATED) {
            return Optional.empty();
        }
        if (process.isErrorneous()) {
            return Optional.of(MAIL_TYPE_JOB_FAILED);
        }
        if (process.isWarnings()) {
            return Optional.of(MAIL_TYPE_JOB_WARNINGS);
        }

        return Optional.of(MAIL_TYPE_JOB_SUCCESS);
    }

    /**
     * Fetches the user who started the job.
     *
     * @param userId the id of the user who started the job
     * @return the user or an empty optional if there is no such user, the user has no account or the account is
     * locked
     */
    private Optional<UserInfo> fetchRecipient(String userId) {
        if (Strings.isEmpty(userId)) {
            return Optional.empty();
        }

        return Optional.ofNullable(UserContext.get().getUserManager().findUserByUserId(userId)).filter(user -> {
            UserAccount<?, ?> account = user.getUserObject(UserAccount.class);
            return account != null && !account.getUserAccountData().getLogin().isAccountLocked();
        });
    }

    /**
     * Sends the mail of the given type about the given process to the recipient.
     * <p>
     * The mail is written in the language of the recipient. Users without an email address are skipped.
     *
     * @param recipient the user to notify
     * @param process   the process of the completed job
     * @param mailType  the mail type which determines the subject and the text of the mail
     */
    private void sendMail(UserInfo recipient, Process process, String mailType) {
        UserAccountData userAccountData = recipient.getUserObject(UserAccount.class).getUserAccountData();
        if (Strings.isEmpty(userAccountData.getEmail())) {
            return;
        }

        // Runs as the recipient, so that the salutation is translated via their tenant and into their language.
        UserContext.get().runAs(recipient, () -> {
            Context mailContext = Context.create()
                                         .set("process", process)
                                         .set("mailType", mailType)
                                         .set("name", userAccountData.getAddressableName())
                                         .set("url", BizController.getBaseUrl() + "/ps/" + process.getIdAsString());

            mails.createEmail()
                 .to(userAccountData.getEmail(), userAccountData.toString())
                 .setLanguage(recipient.getLanguage())
                 .nlsSubject(Strings.apply("JobCompletionMails.%s.subject", mailType),
                             Map.of("title", process.getTitle()))
                 .textTemplate("/mail/jobs/job-completion.pasta", mailContext)
                 .htmlTemplate("/mail/jobs/job-completion.html.pasta", mailContext)
                 .send();
        });
    }
}
