package com.spin.FamilySpin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;
    private String password;
    private String familyMemberName;
    private boolean isAdmin;

    public User() {}

    public User(String username, String email, String password, String familyMemberName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.familyMemberName = familyMemberName;
        this.isAdmin = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFamilyMemberName() { return familyMemberName; }
    public void setFamilyMemberName(String familyMemberName) { this.familyMemberName = familyMemberName; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
}
