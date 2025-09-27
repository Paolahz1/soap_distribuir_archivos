package org.example.domain.model;

public class User {
    private long id;
    private String email;
    private String passwordHash;


    public User(long id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Getter para id
    public long getId() {
        return id;
    }

    // Setter para id
    public void setId(long id) {
        this.id = id;
    }

    // Getter para email
    public String getEmail() {
        return email;
    }

    // Setter para email
    public void setEmail(String email) {
        this.email = email;
    }

    // Getter para passwordHash (si lo necesitas en algún momento)
    public String getPasswordHash() {
        return passwordHash;
    }

    // Setter para passwordHash
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
