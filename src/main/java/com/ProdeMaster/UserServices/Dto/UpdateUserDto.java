package com.ProdeMaster.UserServices.Dto;

public class UpdateUserDto {
    private String username;
    private String email;

    public UpdateUserDto() {
    }

    public UpdateUserDto(String username, String email) {
        this.username = username;
        this.email = email;
    }

    // Getters y setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

