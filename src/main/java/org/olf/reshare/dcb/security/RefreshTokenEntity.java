package org.olf.reshare.dcb.security;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@MappedEntity 
public class RefreshTokenEntity {

    @Id 
    @GeneratedValue 
    @NonNull
    private Long id;

    @NonNull
    @NotBlank
    private String username;

    @NonNull
    @NotBlank
    private String refreshToken;

    @NonNull
    @NotNull
    private Boolean revoked;

    @DateCreated 
    @NonNull
    @NotNull
    private Instant dateCreated;

    public RefreshTokenEntity() {
    }
    
    // getters and setters...
    
    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getRefreshToken() {
      return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
    }

    public Boolean getRevoked() {
      return revoked;
    }

    public void setRevoked(Boolean revoked) {
      this.revoked = revoked;
    }

    public Instant getDateCreated() {
      return dateCreated;
    }

    public void setDateCreated(Instant dateCreated) {
      this.dateCreated = dateCreated;
    }
}