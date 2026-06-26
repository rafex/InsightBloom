package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class User {
    private final String id;
    private final String uuid;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private List<SocialLink> socialLinks;
    private boolean emailVerified;
    private boolean phoneVerified;
    private UserRole role;
    private UserStatus status;
    private String passwordHash;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(String uuid, String username, String displayName, String email, UserRole role) {
        this(uuid, username, displayName, email, null, List.of(), false, false, role);
    }

    public User(String uuid, String username, String displayName, String email, String phone,
                List<SocialLink> socialLinks, boolean emailVerified, boolean phoneVerified, UserRole role) {
        this.id = null;
        this.uuid = uuid != null ? uuid : UUID.randomUUID().toString();
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.socialLinks = socialLinks != null ? socialLinks : List.of();
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public User(String id, String uuid, String username, String displayName, String email, String phone,
                List<SocialLink> socialLinks, boolean emailVerified, boolean phoneVerified,
                UserRole role, UserStatus status, String passwordHash, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.socialLinks = socialLinks != null ? socialLinks : List.of();
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.role = role;
        this.status = status;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markEmailVerified() { this.emailVerified = true; this.updatedAt = Instant.now(); }
    public void markPhoneVerified() { this.phoneVerified = true; this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getUuid() { return uuid; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public List<SocialLink> getSocialLinks() { return socialLinks; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
