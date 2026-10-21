package dev.rafex.insightbloom.users.domain.model;

public record OrganizerInviteSettings(boolean referralsEnabled, int invitationTtlDays, int referralLimit) {
    public OrganizerInviteSettings {
        if (invitationTtlDays < 1 || invitationTtlDays > 30) throw new IllegalArgumentException("invitation_ttl_out_of_range");
        if (referralLimit < 1 || referralLimit > 10) throw new IllegalArgumentException("referral_limit_out_of_range");
    }
}
