package dev.rafex.insightbloom.users.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private Set<UserRole> roles;
    private UserStatus status;
    private String passwordHash;
    private final Instant createdAt;
    private Instant updatedAt;
    private String firstName;
    private String lastName;
    private Instant lastLoginAt;

    public User(String uuid, String username, String displayName, String email, UserRole role) {
        this(uuid, username, displayName, email, null, List.of(), false, false, Set.of(role));
    }

    public User(String uuid, String username, String displayName, String email, String phone,
                List<SocialLink> socialLinks, boolean emailVerified, boolean phoneVerified, UserRole role) {
        this(uuid, username, displayName, email, phone, socialLinks, emailVerified, phoneVerified, Set.of(role));
    }

    public User(String uuid, String username, String displayName, String email, String phone,
                List<SocialLink> socialLinks, boolean emailVerified, boolean phoneVerified, Set<UserRole> roles) {
        this.id = null;
        this.uuid = uuid != null ? uuid : UUID.randomUUID().toString();
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.socialLinks = socialLinks != null ? socialLinks : List.of();
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.roles = new LinkedHashSet<>(roles);
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public User(String id, String uuid, String username, String displayName, String email, String phone,
                List<SocialLink> socialLinks, boolean emailVerified, boolean phoneVerified,
                Set<UserRole> roles, UserStatus status, String passwordHash, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.socialLinks = socialLinks != null ? socialLinks : List.of();
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.roles = new LinkedHashSet<>(roles);
        this.status = status;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void markEmailVerified() { this.emailVerified = true; this.updatedAt = Instant.now(); }
    public void markPhoneVerified() { this.phoneVerified = true; this.updatedAt = Instant.now(); }

    public void setFirstName(String firstName) { this.firstName = firstName; this.updatedAt = Instant.now(); }
    public void setLastName(String lastName) { this.lastName = lastName; this.updatedAt = Instant.now(); }
    public void setDisplayName(String displayName) { this.displayName = displayName; this.updatedAt = Instant.now(); }
    public void setEmail(String email) { this.email = email; this.updatedAt = Instant.now(); }
    public void setPhone(String phone) { this.phone = phone; this.updatedAt = Instant.now(); }
    public void setRoles(Set<UserRole> roles) { this.roles = new LinkedHashSet<>(roles); this.updatedAt = Instant.now(); }
    public void addRole(UserRole role) { this.roles.add(role); this.updatedAt = Instant.now(); }
    public void setStatus(UserStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getId() { return id; }
    public String getUuid() { return uuid; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public List<SocialLink> getSocialLinks() { return socialLinks; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public Set<UserRole> getRoles() { return roles; }
    public boolean hasRole(UserRole role) { return roles.contains(role); }
    public UserStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
