package com.butterzhub.muzic.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

// @Entity maps this identity to a row; @Table avoids PostgreSQL's reserved user name.
@Entity
@Table(name = "muzic_users")
@Getter
@Setter
public class User {
    @Id @GeneratedValue
    private UUID id;
    @Column(nullable = false, unique = true)
    private String googleId;
    private String email;
    private String name;
    @Column(length = 2048)
    private String pictureUrl;
    @Column(length = 8000)
    private String youtubeHistoryContext;
}
