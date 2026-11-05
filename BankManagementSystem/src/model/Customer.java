package model;

import java.io.Serializable;

/** A bank customer (FR1, FR3). */
public class Customer implements Serializable {
    private final String customerId;
    private String name;
    private String address;
    private String phone;
    private final String idProof;

    private Customer(String customerId, String name, String address, String phone, String idProof) {
        this.customerId = customerId;
        this.name = requireText(name, "Name");
        this.address = requireText(address, "Address");
        this.phone = requirePhone(phone);
        this.idProof = requireText(idProof, "ID proof");
    }

    /** register(): creates a customer after validating the recorded details. */
    public static Customer register(String customerId, String name, String address, String phone, String idProof) {
        return new Customer(customerId, name, address, phone, idProof);
    }

    /** updateProfile(): null or blank arguments keep the current value. */
    public void updateProfile(String name, String address, String phone) {
        String n = isBlank(name) ? this.name : requireText(name, "Name");
        String a = isBlank(address) ? this.address : requireText(address, "Address");
        String p = isBlank(phone) ? this.phone : requirePhone(phone);
        this.name = n;
        this.address = a;
        this.phone = p;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String requireText(String v, String field) {
        if (isBlank(v)) throw new IllegalArgumentException(field + " is required");
        return v.trim();
    }

    private static String requirePhone(String v) {
        if (isBlank(v) || !v.trim().matches("\\d{7,15}")) throw new IllegalArgumentException("Phone must be 7 to 15 digits");
        return v.trim();
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getIdProof() { return idProof; }
}
