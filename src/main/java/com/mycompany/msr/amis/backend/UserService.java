package com.mycompany.msr.amis;

import java.util.List;

public interface UserService {

    List<User> getUsers();

    List<String> getDepartments();

    void createUser(String name, String password, String role, String department, String email) throws Exception;

    boolean updateUser(int id, String name, String password, String role, String department, String email) throws Exception;

    boolean updateUserStatus(int id, String status) throws Exception;

    void deleteUser(int id) throws Exception;

    void completeTemporarySetup(String email) throws Exception;

    String resetRemoteDemoData() throws Exception;
}
