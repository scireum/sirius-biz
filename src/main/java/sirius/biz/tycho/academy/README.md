# Video Academies for onboarding

Provides a framework which collects a bunch of videos from a given source
(defined by an AcademyProvider). These academy videos are then "multiplied out"
for all participants of the academy as onboarding videos. The academy UI will show
these videos to its participants while performing some tracking (which video has
been watched etc.).

Note that this is quite a generic framework, but it also contains concrete implementations:
The [OXOMIAcademyProvider](OXOMIAcademyProvider.java) can use OXOMI videos as source. 
The [RecomputeOnboardingVideosCheck](RecomputeOnboardingVideosCheck.java) can be defined for
any entity which should be considered a participant of the framework. A base implementation
exists for **UserAccount** (SQLUserAccount and MongoUserAccount) to provide an academy for the
main users of the system.

The required configuration is placed in the **tycho.onboarding** section in
[component-biz.conf](../../../../../resources/component-biz.conf).
