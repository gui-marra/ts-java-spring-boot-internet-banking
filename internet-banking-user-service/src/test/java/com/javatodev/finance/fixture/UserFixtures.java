package com.javatodev.finance.fixture;

import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.rest.response.UserResponse;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Collections;

public final class UserFixtures {
    public static final String USER_EMAIL = "sam@gmail.com";
    public static final String USER_IDENTIFICATION = "808829932V";
    public static final String USER_FIRST_NAME = "Sam";
    public static final String USER_LAST_NAME = "Silva";
    public static final String USER_PASSWORD = "Password123!";
    public static final String AUTH_ID = "8a8b9c0d-0000-4000-8000-000000000001";
    public static final Long USER_ID = 1L;
    public static final Integer CORE_USER_ID = 1;

    private UserFixtures() {
    }

    public static User aUser() {
        User user = new User();
        user.setEmail(USER_EMAIL);
        user.setIdentification(USER_IDENTIFICATION);
        user.setPassword(USER_PASSWORD);
        return user;
    }

    public static User aUser(Long id, String authId, Status status) {
        User user = aUser();
        user.setId(id);
        user.setAuthId(authId);
        user.setStatus(status);
        return user;
    }

    public static UserEntity aUserEntity(Long id, String authId, String identification, Status status) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setAuthId(authId);
        entity.setIdentification(identification);
        entity.setStatus(status);
        return entity;
    }

    public static UserEntity aUserEntity() {
        return aUserEntity(USER_ID, AUTH_ID, USER_IDENTIFICATION, Status.PENDING);
    }

    public static UserResponse aCoreUserResponse() {
        return aCoreUserResponse(CORE_USER_ID, USER_EMAIL);
    }

    public static UserResponse aCoreUserResponse(Integer id, String email) {
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setEmail(email);
        response.setIdentificationNumber(USER_IDENTIFICATION);
        response.setFirstName(USER_FIRST_NAME);
        response.setLastName(USER_LAST_NAME);
        response.setBankAccounts(Collections.emptyList());
        return response;
    }

    public static UserRepresentation aKeycloakUser(String id, String email) {
        UserRepresentation representation = new UserRepresentation();
        representation.setId(id);
        representation.setEmail(email);
        representation.setEnabled(false);
        representation.setEmailVerified(false);
        representation.setUsername(email);
        representation.setFirstName(USER_FIRST_NAME);
        representation.setLastName(USER_LAST_NAME);
        return representation;
    }

    public static UserUpdateRequest anUpdateRequest(Status status) {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setStatus(status);
        return request;
    }
}
