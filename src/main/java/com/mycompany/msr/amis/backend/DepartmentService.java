package com.mycompany.msr.amis;

import java.util.List;

public interface DepartmentService {

    List<String> getDepartments();

    void createDepartment(String name) throws Exception;

    void updateDepartment(String oldName, String newName) throws Exception;

    void deleteDepartment(String name) throws Exception;
}
