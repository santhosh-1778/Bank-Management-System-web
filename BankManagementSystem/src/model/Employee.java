package model;

import java.io.Serializable;

/** Abstract bank employee. Teller and Manager reuse its fields and operations. */
public abstract class Employee implements Serializable {
    private final String empId;
    private final String name;

    protected Employee(String empId, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        this.empId = empId;
        this.name = name.trim();
    }

    public abstract String getTitle();

    public String processRequest(String request) {
        return getTitle() + " " + name + " (" + empId + ") processed: " + request;
    }

    public String getEmpId() { return empId; }
    public String getName() { return name; }
}
