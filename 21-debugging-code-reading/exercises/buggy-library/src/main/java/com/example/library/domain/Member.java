package com.example.library.domain;

import java.util.Objects;

/**
 * A library member. Email is optional (walk-in members may not have one).
 */
public final class Member {

    private final String id;
    private final String name;
    private final String email; // nullable
    private final MemberType type;

    public Member(String id, String name, String email, MemberType type) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = email;
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** @return the email address, or {@code null} if the member did not provide one */
    public String getEmail() {
        return email;
    }

    public MemberType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Member{id='" + id + "', name='" + name + "', type=" + type + "}";
    }
}
