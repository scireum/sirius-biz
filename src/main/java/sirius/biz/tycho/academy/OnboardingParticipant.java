/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

/**
 * Marks an participant of the {@link OnboardingEngine} by ensuring it has a {@link OnboardingData}.
 */
public interface OnboardingParticipant {

    /**
     * Returns the onboarding metrics for this participant.
     *
     * @return the onboarding metrics
     */
    OnboardingData getOnboardingData();
}
